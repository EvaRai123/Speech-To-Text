package com.example.speechpractice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AudioMeasurementEngineTest {
    private static final int SAMPLE_RATE = 16000;
    private final AudioMeasurementEngine engine = new AudioMeasurementEngine();

    @Test
    public void emptyInputIsInvalid() {
        AudioMeasurementResult result = engine.analyze(new short[0], SAMPLE_RATE, new short[100]);
        assertEquals("INVALID", result.quality);
    }

    @Test
    public void silenceHasNoSpeechActivity() {
        AudioMeasurementResult result = engine.analyze(new short[SAMPLE_RATE], SAMPLE_RATE, new short[SAMPLE_RATE / 4]);
        assertTrue(result.qualityFlags.contains("NO_SPEECH_ACTIVITY_DETECTED"));
        assertEquals("LIMITED", result.quality);
        assertEquals(0.0, result.speechActiveDurationMs, 0.001);
        assertEquals(0.0, result.voicedDurationMs, 0.001);
    }

    @Test
    public void lowAmplitudeSpeechAboveNoiseIsDetected() {
        short[] quiet = constantNoise(SAMPLE_RATE / 4, 40);
        short[] speech = sine(220, 900, SAMPLE_RATE);
        AudioMeasurementResult result = engine.analyze(speech, SAMPLE_RATE, quiet);
        assertTrue(result.speechActiveDurationMs > 0);
        assertTrue(result.rmsDbfs < -25.0);
    }

    @Test
    public void louderSpeechHasHigherRelativeLevel() {
        short[] quiet = constantNoise(SAMPLE_RATE / 4, 40);
        AudioMeasurementResult quietSpeech = engine.analyze(sine(180, 900, SAMPLE_RATE), SAMPLE_RATE, quiet);
        AudioMeasurementResult loudSpeech = engine.analyze(sine(180, 9000, SAMPLE_RATE), SAMPLE_RATE, quiet);
        assertTrue(loudSpeech.rmsDbfs > quietSpeech.rmsDbfs);
        assertTrue(loudSpeech.speechActiveDurationMs > 0);
    }

    @Test
    public void clippingIsFlagged() {
        short[] clipped = sine(180, 32767, SAMPLE_RATE);
        clipped[100] = Short.MAX_VALUE;
        AudioMeasurementResult result = engine.analyze(clipped, SAMPLE_RATE, constantNoise(SAMPLE_RATE / 4, 20));
        assertTrue(result.clippingDetected);
        assertTrue(result.qualityFlags.contains("CLIPPING"));
    }

    @Test
    public void syntheticPitchIsEstimatedNearKnownFrequency() {
        AudioMeasurementResult result = engine.analyze(sine(200, 8000, SAMPLE_RATE), SAMPLE_RATE, constantNoise(SAMPLE_RATE / 4, 20));
        assertTrue(result.medianF0Hz > 180.0 && result.medianF0Hz < 220.0);
        assertTrue(result.pitchConfidence > 0.30);
    }

    private short[] sine(double frequency, int amplitude, int sampleRate) {
        short[] samples = new short[sampleRate];
        for (int i = 0; i < samples.length; i++) samples[i] = (short) (Math.sin(2 * Math.PI * frequency * i / sampleRate) * amplitude);
        return samples;
    }

    private short[] constantNoise(int length, int amplitude) {
        short[] samples = new short[length];
        for (int i = 0; i < length; i++) samples[i] = (short) ((i % 7 - 3) * amplitude / 3);
        return samples;
    }
}
