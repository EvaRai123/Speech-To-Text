package com.example.speechpractice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Owns the one microphone stream and fans each PCM frame to Vosk and analysis. */
public final class PcmPracticePipeline implements AudioCaptureManager.Listener {
    public interface Listener {
        void onReady();
        void onStarted();
        void onInputLevel(double dbfs, boolean speechLikely);
        void onPartialTranscript(String transcript);
        void onFinalTranscript(String transcript);
        default void onInputRoute(String selectedInput, String actualRoute, boolean verified) { }
        default void onAudioCaptured(short[] samples, int sampleRateHz) { }
        void onStopped(AudioMeasurementResult measurements);
        void onError(String message, Throwable cause);
    }

    private final AudioCaptureManager capture;
    private final AudioMeasurementEngine measurements = new AudioMeasurementEngine();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }
    private VoskPcmTranscriber transcriber;
    private ByteArrayOutputStream pcm;
    private short[] quietCalibration;
    private short[] calibrationWindow;
    private int calibrationSamples;
    private boolean running;
    private long lastLevelPostNanos;

    public PcmPracticePipeline(Context context) { capture = new AudioCaptureManager(context); }

    public boolean isRunning() { return running; }

    public void prepareAsync(File modelDirectory, Listener listener) {
        this.listener = listener;
        new Thread(() -> {
            try {
                transcriber = new VoskPcmTranscriber(modelDirectory.getAbsolutePath());
                mainHandler.post(listener::onReady);
            } catch (Throwable failure) {
                mainHandler.post(() -> listener.onError("The offline transcription model could not be loaded.", failure));
            }
        }, "vosk-model-loader").start();
    }

    public boolean start(short[] quietCalibration) {
        return startInternal(quietCalibration, true, null, false);
    }

    public boolean startForMeasurement(short[] quietCalibration) {
        return startInternal(quietCalibration, false, null, false);
    }

    public boolean startForMeasurement(short[] quietCalibration, Integer externalInputDeviceId,
                                       boolean requireExternalInput) {
        return startInternal(quietCalibration, false, externalInputDeviceId, requireExternalInput);
    }

    private boolean startInternal(short[] quietCalibration, boolean requireTranscriber,
                                  Integer externalInputDeviceId, boolean requireExternalInput) {
        if (running) return false;
        if (requireTranscriber && transcriber == null) return false;
        this.quietCalibration = quietCalibration;
        pcm = new ByteArrayOutputStream();
        calibrationWindow = new short[AudioCaptureManager.DEFAULT_SAMPLE_RATE_HZ / 2];
        calibrationSamples = 0;
        if (transcriber != null) transcriber.start();
        running = capture.start(AudioCaptureManager.DEFAULT_SAMPLE_RATE_HZ, this,
                externalInputDeviceId, requireExternalInput);
        if (!running && transcriber != null) { transcriber.close(); transcriber = null; }
        return running;
    }

    public void stop() {
        if (!running) return;
        capture.stop();
    }

    public void release() {
        capture.release();
        if (transcriber != null) { transcriber.close(); transcriber = null; }
        running = false;
    }

    @Override public void onStarted(AudioCaptureManager.AudioConfiguration configuration) {
        if (listener != null) {
            mainHandler.post(() -> listener.onInputRoute(configuration.selectedInputName,
                    configuration.actualInputName, configuration.externalRouteVerified));
        }
        if (listener != null) mainHandler.post(listener::onStarted);
    }

    @Override public void onSamples(short[] samples, int count) {
        if (!running) return;
        ByteBuffer bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) bytes.putShort(samples[i]);
        synchronized (this) { pcm.write(bytes.array(), 0, bytes.array().length); }
        synchronized (this) {
            if (quietCalibration == null && calibrationSamples < calibrationWindow.length) {
                int copyCount = Math.min(count, calibrationWindow.length - calibrationSamples);
                System.arraycopy(samples, 0, calibrationWindow, calibrationSamples, copyCount);
                calibrationSamples += copyCount;
            }
        }
        if (transcriber != null) {
            String transcript = transcriber.accept(samples, count);
            if (!transcript.isEmpty() && listener != null) mainHandler.post(() -> listener.onPartialTranscript(transcript));
        }
        long now = System.nanoTime();
        if (now - lastLevelPostNanos >= 100_000_000L) {
            lastLevelPostNanos = now;
            double dbfs = AudioMeasurementEngine.rmsDbfs(samples);
            double noiseDbfs = -60.0;
            synchronized (this) {
                if (calibrationSamples >= calibrationWindow.length) noiseDbfs = AudioMeasurementEngine.rmsDbfs(calibrationWindow);
            }
            boolean speechLikely = calibrationSamples >= calibrationWindow.length && dbfs >= noiseDbfs + 10.0;
            final double level = dbfs;
            final boolean active = speechLikely;
            if (listener != null) mainHandler.post(() -> listener.onInputLevel(level, active));
        }
    }

    @Override public void onStopped(AudioCaptureManager.AudioCaptureResult result) {
        running = false;
        if (result.externalRouteRequired && !result.externalRouteVerified) {
            if (listener != null) mainHandler.post(() -> listener.onError(
                    "The external microphone route was not verified. No audio measurements were accepted.", null));
            return;
        }
        // Finish Vosk on the capture worker, after the final PCM frame, so the
        // UI thread never blocks and the final transcript precedes measurement.
        String finalText = transcriber == null ? "" : transcriber.finish();
        if (!finalText.isEmpty() && listener != null) mainHandler.post(() -> listener.onFinalTranscript(finalText));
        short[] samples;
        synchronized (this) { byte[] bytes = pcm.toByteArray(); samples = new short[bytes.length / 2]; ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples); }
        short[] calibration = quietCalibration;
        synchronized (this) {
            if (calibration == null && calibrationSamples > 0) {
                calibration = new short[calibrationSamples];
                System.arraycopy(calibrationWindow, 0, calibration, 0, calibrationSamples);
            }
        }
        AudioMeasurementResult measurement = measurements.analyze(samples, AudioCaptureManager.DEFAULT_SAMPLE_RATE_HZ, calibration);
        calibrationWindow = null;
        if (listener != null) {
            short[] capturedSamples = samples;
            mainHandler.post(() -> listener.onAudioCaptured(capturedSamples, AudioCaptureManager.DEFAULT_SAMPLE_RATE_HZ));
        }
        if (listener != null) mainHandler.post(() -> listener.onStopped(measurement));
    }

    @Override public void onError(String reason, Throwable cause) { running = false; if (listener != null) mainHandler.post(() -> listener.onError(reason, cause)); }
}
