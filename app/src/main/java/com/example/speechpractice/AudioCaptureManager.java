package com.example.speechpractice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures mono PCM16 audio for a future measurement path. It is deliberately
 * not started by PracticeActivity while SpeechRecognizer is active: Android
 * microphone ownership must belong to one capture path at a time.
 */
public final class AudioCaptureManager {
    public static final int DEFAULT_SAMPLE_RATE_HZ = 16000;
    private static final AtomicBoolean ANY_CAPTURE_RUNNING = new AtomicBoolean(false);

    public interface Listener {
        void onStarted(AudioConfiguration configuration);
        void onSamples(short[] samples, int count);
        void onStopped(AudioCaptureResult result);
        void onError(String reason, Throwable cause);
    }

    public static final class AudioConfiguration {
        public final int sampleRateHz;
        public final int bufferSizeBytes;
        public final String selectedInputName;
        public final String actualInputName;
        public final boolean externalRouteRequired;
        public final boolean externalRouteVerified;

        AudioConfiguration(int sampleRateHz, int bufferSizeBytes, String selectedInputName,
                           String actualInputName, boolean externalRouteRequired,
                           boolean externalRouteVerified) {
            this.sampleRateHz = sampleRateHz;
            this.bufferSizeBytes = bufferSizeBytes;
            this.selectedInputName = selectedInputName;
            this.actualInputName = actualInputName;
            this.externalRouteRequired = externalRouteRequired;
            this.externalRouteVerified = externalRouteVerified;
        }
    }

    public static final class AudioCaptureResult {
        public final long samplesRead;
        public final long durationMs;
        public final int readErrors;
        public final boolean externalRouteRequired;
        public final boolean externalRouteVerified;
        public final String selectedInputName;
        public final String actualInputName;

        AudioCaptureResult(long samplesRead, long durationMs, int readErrors,
                           boolean externalRouteRequired, boolean externalRouteVerified,
                           String selectedInputName, String actualInputName) {
            this.samplesRead = samplesRead;
            this.durationMs = durationMs;
            this.readErrors = readErrors;
            this.externalRouteRequired = externalRouteRequired;
            this.externalRouteVerified = externalRouteVerified;
            this.selectedInputName = selectedInputName;
            this.actualInputName = actualInputName;
        }
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private AudioRecord recorder;
    private ExecutorService worker;
    private volatile boolean stopRequested;
    private volatile Integer preferredExternalDeviceId;
    private volatile boolean externalRouteRequired;
    private volatile boolean externalRouteVerified;
    private volatile String selectedInputName = "Android default input";
    private volatile String actualInputName = "Not yet routed";
    private final java.util.concurrent.atomic.AtomicBoolean routeFailureReported =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private android.media.AudioRouting.OnRoutingChangedListener routingListener;

    public AudioCaptureManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRunning() {
        synchronized (lock) {
            return worker != null && !worker.isShutdown();
        }
    }

    public boolean start(int requestedSampleRateHz, Listener listener) {
        return start(requestedSampleRateHz, listener, null, false);
    }

    public boolean start(int requestedSampleRateHz, Listener listener,
                         Integer preferredExternalDeviceId, boolean requireExternalRoute) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notifyError(listener, "Microphone permission is not granted.", null);
            return false;
        }
        if (!ANY_CAPTURE_RUNNING.compareAndSet(false, true)) {
            notifyError(listener, "Another audio capture session is already using the microphone.", null);
            return false;
        }
        synchronized (lock) {
            if (worker != null && !worker.isShutdown()) {
                ANY_CAPTURE_RUNNING.set(false);
                notifyError(listener, "This audio capture session is already running.", null);
                return false;
            }
            stopRequested = false;
            this.preferredExternalDeviceId = preferredExternalDeviceId;
            this.externalRouteRequired = requireExternalRoute;
            this.externalRouteVerified = false;
            this.selectedInputName = requireExternalRoute ? "Selected external input" : "Android default input";
            this.actualInputName = "Route pending";
            this.routeFailureReported.set(false);
            try {
                AudioConfiguration configuration = createConfiguration(requestedSampleRateHz);
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        configuration.sampleRateHz, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, configuration.bufferSizeBytes);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioRecord could not be initialized.");
                }
                if (preferredExternalDeviceId != null) {
                    AudioDeviceInfo preferred = findExternalInput(preferredExternalDeviceId);
                    if (preferred == null || !recorder.setPreferredDevice(preferred)) {
                        throw new IllegalStateException("The selected external microphone is no longer available.");
                    }
                    selectedInputName = String.valueOf(preferred.getProductName());
                }
                worker = Executors.newSingleThreadExecutor();
                AudioRecord localRecorder = recorder;
                worker.execute(() -> captureLoop(localRecorder, configuration, listener));
                return true;
            } catch (Throwable failure) {
                releaseLocked();
                ANY_CAPTURE_RUNNING.set(false);
                notifyError(listener, "The microphone configuration is unavailable.", failure);
                return false;
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            stopRequested = true;
            if (recorder != null) {
                try { recorder.stop(); } catch (IllegalStateException ignored) { }
            }
        }
    }

    public void release() {
        stop();
        synchronized (lock) {
            releaseLocked();
            ANY_CAPTURE_RUNNING.set(false);
        }
    }

    private AudioConfiguration createConfiguration(int requestedSampleRateHz) {
        int sampleRate = requestedSampleRateHz > 0 ? requestedSampleRateHz : DEFAULT_SAMPLE_RATE_HZ;
        int minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBytes <= 0) throw new IllegalArgumentException("Requested sample rate is unsupported.");
        return new AudioConfiguration(sampleRate, Math.max(minBytes, sampleRate / 5 * 2),
                "Android default input", "Route pending", false, false);
    }

    private void captureLoop(AudioRecord localRecorder, AudioConfiguration configuration, Listener listener) {
        short[] buffer = new short[configuration.bufferSizeBytes / 2];
        long samplesRead = 0;
        int readErrors = 0;
        long startedAt = System.nanoTime();
        try {
            localRecorder.startRecording();
            AudioDeviceInfo routed = awaitRoutedDevice(localRecorder, preferredExternalDeviceId != null);
            updateRouteState(routed);
            boolean routeVerified = AudioInputDeviceManager.isRouteVerified(
                    externalRouteRequired, preferredExternalDeviceId,
                    routed == null ? null : routed.getId(),
                    routed == null ? null : routed.getType(), android.os.Build.VERSION.SDK_INT);
            externalRouteVerified = routeVerified;
            AudioConfiguration actualConfiguration = new AudioConfiguration(configuration.sampleRateHz,
                    configuration.bufferSizeBytes, selectedInputName, actualInputName,
                    externalRouteRequired, routeVerified);
            postStarted(listener, actualConfiguration);
            if (externalRouteRequired && !routeVerified) {
                notifyError(listener, "Android did not route recording to the selected external microphone. No external-mic measurement was recorded.", null);
                stopRequested = true;
                return;
            }
            registerRouteListener(localRecorder, listener);
            while (!stopRequested) {
                int count = localRecorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (count > 0) {
                    samplesRead += count;
                    short[] copy = new short[count];
                    System.arraycopy(buffer, 0, copy, 0, count);
                    listener.onSamples(copy, count);
                } else if (count < 0) {
                    readErrors++;
                    if (readErrors >= 3) {
                        notifyError(listener, "The microphone returned repeated read errors.", null);
                        break;
                    }
                }
            }
        } catch (Throwable failure) {
            notifyError(listener, "Audio capture stopped unexpectedly.", failure);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            if (routingListener != null) {
                try { localRecorder.removeOnRoutingChangedListener(routingListener); }
                catch (RuntimeException ignored) { }
                routingListener = null;
            }
            try { localRecorder.stop(); } catch (IllegalStateException ignored) { }
            postStopped(listener, new AudioCaptureResult(samplesRead, durationMs, readErrors,
                    externalRouteRequired, externalRouteVerified, selectedInputName, actualInputName));
            synchronized (lock) {
                releaseLocked();
                ANY_CAPTURE_RUNNING.set(false);
            }
        }
    }

    private AudioDeviceInfo findExternalInput(int id) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return null;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (device.getId() == id && device.isSource()
                    && AudioInputDeviceManager.isExternalType(device.getType(), android.os.Build.VERSION.SDK_INT)) {
                return device;
            }
        }
        return null;
    }

    private AudioDeviceInfo awaitRoutedDevice(AudioRecord localRecorder, boolean waitForExternal) {
        long deadline = android.os.SystemClock.elapsedRealtime() + (waitForExternal ? 2000L : 0L);
        AudioDeviceInfo routed;
        do {
            routed = localRecorder.getRoutedDevice();
            if (routed != null && (!waitForExternal || routed.getId() == preferredExternalDeviceId)) return routed;
            if (android.os.SystemClock.elapsedRealtime() >= deadline) break;
            try { Thread.sleep(50L); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (!stopRequested);
        return localRecorder.getRoutedDevice();
    }

    private void updateRouteState(AudioDeviceInfo routed) {
        actualInputName = routed == null ? "Route unavailable" : String.valueOf(routed.getProductName());
    }

    private void registerRouteListener(AudioRecord localRecorder, Listener listener) {
        if (!externalRouteRequired) return;
        routingListener = router -> {
            AudioDeviceInfo routed = localRecorder.getRoutedDevice();
            updateRouteState(routed);
            boolean stillVerified = AudioInputDeviceManager.isRouteVerified(true, preferredExternalDeviceId,
                    routed == null ? null : routed.getId(),
                    routed == null ? null : routed.getType(), android.os.Build.VERSION.SDK_INT);
            externalRouteVerified = stillVerified;
            if (!stillVerified && routeFailureReported.compareAndSet(false, true)) {
                notifyError(listener, "The selected external microphone was disconnected or Android changed the recording route.", null);
                stopRequested = true;
                try { localRecorder.stop(); } catch (IllegalStateException ignored) { }
            }
        };
        localRecorder.addOnRoutingChangedListener(routingListener, mainHandler);
    }

    private void releaseLocked() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }

    private void postStarted(Listener listener, AudioConfiguration configuration) {
        mainHandler.post(() -> listener.onStarted(configuration));
    }

    private void postStopped(Listener listener, AudioCaptureResult result) {
        mainHandler.post(() -> listener.onStopped(result));
    }

    private void notifyError(Listener listener, String reason, Throwable cause) {
        mainHandler.post(() -> listener.onError(reason, cause));
    }
}
