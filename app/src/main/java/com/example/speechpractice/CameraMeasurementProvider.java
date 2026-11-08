package com.example.speechpractice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CameraMeasurementProvider implements MeasurementProvider {
    private static final long MEASUREMENT_INTERVAL_NANOS = 1_000_000_000L;

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CameraMeasurementGate sessionGate = new CameraMeasurementGate();
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private ExecutorService analyzerExecutor;
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageAnalysis analysis;
    private FaceDetector detector;
    private Listener listener;
    private Set<MeasurementType> requestedTypes = Collections.emptySet();
    private volatile boolean running;
    private volatile long sessionGeneration;
    private long lastEmissionNanos;
    private long lastStatusNanos;
    private double baselineJawProxy = Double.NaN;

    public CameraMeasurementProvider(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
    }

    @Override public MeasurementSource getSource() { return MeasurementSource.CAMERA; }
    @Override public MeasurementStatus getStatus() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) return MeasurementStatus.PERMISSION_DENIED;
        return running ? MeasurementStatus.AVAILABLE : MeasurementStatus.UNAVAILABLE;
    }

    @Override
    public void start(Set<MeasurementType> types, Listener listener) {
        if (running || detector != null || analyzerExecutor != null) stop();
        if (listener == null) throw new IllegalArgumentException("A camera measurement listener is required.");
        this.listener = listener;
        EnumSet<MeasurementType> validTypes = EnumSet.noneOf(MeasurementType.class);
        if (types != null) {
            for (MeasurementType type : types) {
                if (type != null && type.source == MeasurementSource.CAMERA) validTypes.add(type);
            }
        }
        requestedTypes = Collections.unmodifiableSet(validTypes);
        if (requestedTypes.isEmpty()) {
            listener.onStatusChanged(MeasurementSource.CAMERA, MeasurementStatus.NOT_APPLICABLE,
                    "This lesson has no camera measurements.");
            return;
        }
        if (processing.get()) {
            emitUnavailableBeforeStart(MeasurementStatus.UNAVAILABLE,
                    "The previous camera frame is still closing. Wait briefly, then try again.");
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            emitUnavailableBeforeStart(MeasurementStatus.PERMISSION_DENIED, "Camera permission was not granted.");
            return;
        }

        sessionGeneration = sessionGate.start();
        running = true;
        baselineJawProxy = Double.NaN;
        lastEmissionNanos = 0L;
        lastStatusNanos = 0L;
        final long generation = sessionGeneration;
        analyzerExecutor = Executors.newSingleThreadExecutor();
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);
        try {
            ProcessCameraProvider.getInstance(context).addListener(() -> {
                try {
                    if (!sessionGate.isActive(generation) || detector == null) return;
                    cameraProvider = ProcessCameraProvider.getInstance(context).get();
                    bindUseCases(generation);
                } catch (Exception failure) {
                    fail("The front camera could not be initialized.", failure, generation);
                }
            }, ContextCompat.getMainExecutor(context));
            listener.onStatusChanged(MeasurementSource.CAMERA, MeasurementStatus.UNAVAILABLE,
                    "Camera starting. Position your face in the preview; frames are processed on this device and not saved.");
        } catch (RuntimeException failure) {
            fail("The front camera could not be initialized.", failure, generation);
        }
    }

    private void bindUseCases(long generation) {
        if (!sessionGate.isActive(generation) || cameraProvider == null || detector == null) return;
        try {
            preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            analysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analysis.setAnalyzer(analyzerExecutor, proxy -> analyzeFrame(proxy, generation));
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            sessionGate.runIfActive(generation, () -> listener.onStatusChanged(MeasurementSource.CAMERA,
                    MeasurementStatus.AVAILABLE,
                    "Front camera active. Keep your face centered and well lit. Images stay on this device and are not saved."));
        } catch (Exception failure) {
            fail("The front camera is unavailable or already in use.", failure, generation);
        }
    }

    private void analyzeFrame(ImageProxy proxy, long generation) {
        if (!sessionGate.isActive(generation) || !processing.compareAndSet(false, true)) {
            proxy.close();
            return;
        }
        try {
            Image image = proxy.getImage();
            if (image == null) {
                emitUnavailable(MeasurementStatus.POOR_SIGNAL,
                        "Camera frame was empty. Adjust the device and try again.", generation);
                finishFrame(proxy);
                return;
            }
            if (averageLuminance(proxy) < 25.0) {
                emitUnavailable(MeasurementStatus.POOR_SIGNAL,
                        "Lighting is too low for reliable face contours. Move to a brighter, even light.", generation);
                finishFrame(proxy);
                return;
            }
            long acquisitionTimestamp = proxy.getImageInfo().getTimestamp();
            InputImage input = InputImage.fromMediaImage(image, proxy.getImageInfo().getRotationDegrees());
            Task<java.util.List<Face>> task = detector.process(input);
            int rotation = proxy.getImageInfo().getRotationDegrees();
            int rotatedWidth = rotation == 90 || rotation == 270 ? proxy.getHeight() : proxy.getWidth();
            int rotatedHeight = rotation == 90 || rotation == 270 ? proxy.getWidth() : proxy.getHeight();
            task.addOnSuccessListener(faces -> processFaces(
                    faces, rotatedWidth, rotatedHeight, acquisitionTimestamp, generation))
                    .addOnFailureListener(failure -> {
                        emitUnavailable(MeasurementStatus.POOR_SIGNAL,
                                "Face tracking is unreliable. Improve lighting and keep your face in frame.",
                                generation);
                        sessionGate.runIfActive(generation, () -> {
                            if (listener != null) listener.onFailure(MeasurementSource.CAMERA,
                                    "On-device face contour analysis failed.", failure);
                        });
                    })
                    .addOnCompleteListener(ignored -> finishFrame(proxy));
        } catch (RuntimeException failure) {
            emitUnavailable(MeasurementStatus.FAILURE, "Camera frame processing failed.", generation);
            sessionGate.runIfActive(generation, () -> {
                if (listener != null) listener.onFailure(MeasurementSource.CAMERA,
                        "On-device face contour analysis could not process the frame.", failure);
            });
            finishFrame(proxy);
        }
    }

    private void finishFrame(ImageProxy proxy) {
        try {
            proxy.close();
        } finally {
            processing.set(false);
            if (!running) releaseAnalysisResources();
        }
    }

    private static double averageLuminance(ImageProxy proxy) {
                ImageProxy.PlaneProxy plane = proxy.getPlanes()[0];
                java.nio.ByteBuffer buffer = plane.getBuffer().duplicate();
                int rowStride = plane.getRowStride();
                int pixelStride = plane.getPixelStride();
                long total = 0L;
                int count = 0;
                int stepY = Math.max(1, proxy.getHeight() / 24);
                int stepX = Math.max(1, proxy.getWidth() / 24);
                for (int y = 0; y < proxy.getHeight(); y += stepY) {
                    for (int x = 0; x < proxy.getWidth(); x += stepX) {
                        int index = y * rowStride + x * pixelStride;
                        if (index >= 0 && index < buffer.limit()) {
                            total += buffer.get(index) & 0xff;
                            count++;
                        }
                    }
                }
                return count == 0 ? 0.0 : (double) total / count;
    }

    private void processFaces(java.util.List<Face> faces, int imageWidth, int imageHeight,
                              long acquiredAtNanos, long generation) {
        sessionGate.runIfActive(generation,
                () -> processFacesForActiveSession(faces, imageWidth, imageHeight, acquiredAtNanos, generation));
    }

    private void processFacesForActiveSession(java.util.List<Face> faces, int imageWidth, int imageHeight,
                                              long acquiredAtNanos, long generation) {
        long now = SystemClock.elapsedRealtimeNanos();
        if (now - lastEmissionNanos < MEASUREMENT_INTERVAL_NANOS) return;
        lastEmissionNanos = now;
        if (faces == null || faces.isEmpty()) {
            emitUnavailable(MeasurementStatus.UNAVAILABLE,
                    "No face detected. Center your face in the camera preview.", generation);
            return;
        }
        Face face = faces.get(0);
        android.graphics.Rect bounds = face.getBoundingBox();
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > imageWidth || bounds.bottom > imageHeight) {
            emitUnavailable(MeasurementStatus.POOR_SIGNAL,
                    "Face partly outside the frame. Move back to the center.", generation);
            return;
        }
        CameraMouthGeometry geometry = CameraMouthGeometry.derive(
                points(face, FaceContour.UPPER_LIP_BOTTOM),
                points(face, FaceContour.LOWER_LIP_TOP),
                join(points(face, FaceContour.UPPER_LIP_TOP), points(face, FaceContour.UPPER_LIP_BOTTOM),
                        points(face, FaceContour.LOWER_LIP_TOP), points(face, FaceContour.LOWER_LIP_BOTTOM)),
                points(face, FaceContour.FACE),
                points(face, FaceContour.NOSE_BOTTOM));
        if (geometry == null) {
            emitUnavailable(MeasurementStatus.POOR_SIGNAL,
                    "Mouth contours are incomplete. Improve lighting and face the camera directly.", generation);
            return;
        }

        long receivedAt = System.currentTimeMillis();
        Long acquiredTimestamp = acquiredAtNanos > 0L ? acquiredAtNanos : null;
        SignalQualityAssessment cameraQuality = SignalQualityAssessment.forCamera(
                true, true, false, false);
        for (MeasurementType type : requestedTypes) {
            if (!CameraMeasurementSemantics.isSupported(type)) {
                emitOneUnavailable(type, MeasurementStatus.UNSUPPORTED,
                        CameraMeasurementSemantics.unsupportedDetail(type), generation);
                continue;
            }
            Double value;
            String limitation;
            if (type == MeasurementType.LOWER_FACE_DISPLACEMENT_PROXY) {
                if (Double.isNaN(baselineJawProxy)) baselineJawProxy = geometry.mouthCenterYFromNoseRatio;
                value = Math.min(1.0, Math.abs(geometry.mouthCenterYFromNoseRatio - baselineJawProxy));
                limitation = "Change from the first observed mouth-center-to-nose distance, normalized by face height. A lower-face displacement proxy, not isolated jaw motion.";
            } else {
                value = geometry.ratios.get(type);
                limitation = detailFor(type);
                if (value == null) {
                    emitOneUnavailable(type, MeasurementStatus.UNSUPPORTED,
                            "The available facial landmarks do not support this measurement.", generation);
                    continue;
                }
            }
            MeasurementRecord record = MeasurementRecord.available(MeasurementSource.CAMERA, type,
                    value, type.canonicalUnit, acquiredTimestamp, receivedAt,
                    cameraQuality.quality, cameraQuality.confidence,
                    limitation + " " + cameraQuality.explanation);
            postMeasurement(record, generation);
        }
    }

    private String detailFor(MeasurementType type) {
        switch (type) {
            case LIP_CLOSURE_RATIO:
                return "One-frame estimate: 1 minus inner-lip gap divided by visible lip-contour width; relative geometric proxy, not proof of lip contact.";
            case LIP_SEPARATION_RATIO:
                return "One-frame estimate: inner-lip gap divided by visible lip-contour width; relative geometric proxy.";
            case LIP_SPREAD_WIDTH_RATIO:
                return "One-frame estimate: visible lip-contour width divided by face-contour width; relative spread-width proxy.";
            case LIP_ROUNDING_PROXY:
                return "One-frame estimate: visible lip-contour height divided by width; frontal shape proxy, not lip protrusion.";
            case MOUTH_OPENING_RATIO:
                return "One-frame estimate: inner-lip gap divided by face-contour height; relative mouth-opening proxy.";
            default:
                return "Relative facial-contour observation; not a calibrated physical measurement or pronunciation score.";
        }
    }

    private double[][] points(Face face, int contourType) {
        if (face.getContour(contourType) == null) return null;
        java.util.List<PointF> source = face.getContour(contourType).getPoints();
        if (source == null || source.isEmpty()) return null;
        double[][] result = new double[source.size()][2];
        for (int i = 0; i < source.size(); i++) {
            result[i][0] = source.get(i).x;
            result[i][1] = source.get(i).y;
        }
        return result;
    }

    private static double[][] join(double[][]... arrays) {
        int length = 0;
        for (double[][] array : arrays) if (array != null) length += array.length;
        double[][] joined = new double[length][2];
        int index = 0;
        for (double[][] array : arrays) {
            if (array == null) continue;
            for (double[] point : array) joined[index++] = point;
        }
        return joined;
    }

    private void emitUnavailable(MeasurementStatus status, String detail, long generation) {
        sessionGate.runIfActive(generation,
                () -> emitUnavailableForActiveSession(status, detail, generation));
    }

    private void emitUnavailableForActiveSession(MeasurementStatus status, String detail, long generation) {
        if (listener == null) return;
        long now = SystemClock.elapsedRealtimeNanos();
        if (now - lastStatusNanos < MEASUREMENT_INTERVAL_NANOS) return;
        lastStatusNanos = now;
        for (MeasurementType type : requestedTypes) emitOneUnavailable(type, status, detail, generation);
        Listener target = listener;
        dispatchOnMain(() -> sessionGate.runIfActive(generation,
                () -> target.onStatusChanged(MeasurementSource.CAMERA, status, detail)));
    }

    private void emitUnavailableBeforeStart(MeasurementStatus status, String detail) {
        for (MeasurementType type : requestedTypes) emitOneUnavailableBeforeStart(type, status, detail);
        if (listener != null) listener.onStatusChanged(MeasurementSource.CAMERA, status, detail);
    }

    private void emitOneUnavailable(MeasurementType type, MeasurementStatus status, String detail,
                                    long generation) {
        if (listener != null) {
            MeasurementRecord record = MeasurementRecord.unavailable(
                    MeasurementSource.CAMERA, type, status, detail);
            postMeasurement(record, generation);
        }
    }

    private void emitOneUnavailableBeforeStart(MeasurementType type, MeasurementStatus status, String detail) {
        Listener target = listener;
        if (target == null) return;
        MeasurementRecord record = MeasurementRecord.unavailable(
                MeasurementSource.CAMERA, type, status, detail);
        dispatchOnMain(() -> target.onMeasurement(record));
    }

    private void postMeasurement(MeasurementRecord record, long generation) {
        dispatchOnMain(() -> sessionGate.runIfActive(generation,
                () -> listener.onMeasurement(record)));
    }

    private void dispatchOnMain(Runnable callback) {
        if (Looper.myLooper() == Looper.getMainLooper()) callback.run();
        else mainHandler.post(callback);
    }

    private void fail(String detail, Throwable failure, long generation) {
        sessionGate.runIfActive(generation, () -> {
            running = false;
            for (MeasurementType type : requestedTypes) {
                emitOneUnavailable(type, MeasurementStatus.FAILURE, detail, generation);
            }
            if (listener != null) {
                Listener target = listener;
                dispatchOnMain(() -> {
                    target.onStatusChanged(MeasurementSource.CAMERA, MeasurementStatus.FAILURE, detail);
                    target.onFailure(MeasurementSource.CAMERA, detail, failure);
                });
            }
            sessionGate.stop();
        });
        stop();
    }

    @Override
    public void stop() {
        sessionGate.stop();
        running = false;
        if (analysis != null) {
            analysis.clearAnalyzer();
        }
        if (cameraProvider != null) {
            if (preview != null && analysis != null) cameraProvider.unbind(preview, analysis);
            else if (preview != null) cameraProvider.unbind(preview);
            else if (analysis != null) cameraProvider.unbind(analysis);
        }
        analysis = null;
        preview = null;
        releaseAnalysisResources();
        if (cameraProvider != null) cameraProvider = null;
        Listener target = listener;
        if (target != null) mainHandler.post(() -> target.onStatusChanged(MeasurementSource.CAMERA,
                MeasurementStatus.DISCONNECTED, "Camera stopped."));
    }

    private synchronized void releaseAnalysisResources() {
        if (processing.get()) return;
        if (detector != null) {
            detector.close();
            detector = null;
        }
        if (analyzerExecutor != null) {
            analyzerExecutor.shutdown();
            analyzerExecutor = null;
        }
    }
}
