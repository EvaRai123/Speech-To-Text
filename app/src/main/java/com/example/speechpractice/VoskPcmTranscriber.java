package com.example.speechpractice;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class VoskPcmTranscriber implements AutoCloseable {
    private final Model model;
    private Recognizer recognizer;

    public VoskPcmTranscriber(String modelPath) {
        model = new Model(modelPath);
    }

    public void start() {
        recognizer = new Recognizer(model, AudioCaptureManager.DEFAULT_SAMPLE_RATE_HZ);
        recognizer.setWords(false);
    }

    public String accept(short[] samples, int count) {
        if (recognizer == null || samples == null || count <= 0) return "";
        ByteBuffer bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) bytes.putShort(samples[i]);
        if (recognizer.acceptWaveForm(bytes.array(), bytes.array().length)) return textFromJson(recognizer.getResult());
        return partialFromJson(recognizer.getPartialResult());
    }

    public String finish() {
        return recognizer == null ? "" : textFromJson(recognizer.getFinalResult());
    }

    @Override public void close() {
        if (recognizer != null) { recognizer.close(); recognizer = null; }
        model.close();
    }

    private String textFromJson(String json) { return value(json, "text"); }
    private String partialFromJson(String json) { return value(json, "partial"); }
    private String value(String json, String key) {
        try { return new JSONObject(json).optString(key, ""); } catch (Exception ignored) { return ""; }
    }
}
