package com.example.speechpractice;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.Serializable;

public class AttemptRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String promptId;
    public final String targetWord;
    public int attemptsStarted;
    public int retries;
    public String transcript = "";
    public String machineTranscript = "";
    public boolean partialOnly;
    public boolean manuallyEdited;
    public String status = "not_attempted";
    public String errorMessage = "";
    public int errorCode;
    public long promptDisplayedAt;
    public long requestStartedAt;
    public long firstSpeechAt;
    public long endOfSpeechAt;
    public long finalResultAt;
    public long errorAt;
    public AudioSummaryRecord audioSummary;

    public AttemptRecord(String promptId, String targetWord) {
        this.promptId = promptId;
        this.targetWord = targetWord;
    }

    public boolean hasUsableTranscript() {
        return transcript != null && !transcript.trim().isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("promptId", promptId);
        json.put("targetWord", targetWord);
        json.put("attemptsStarted", attemptsStarted);
        json.put("retries", retries);
        json.put("transcript", transcript);
        json.put("machineTranscript", machineTranscript);
        json.put("partialOnly", partialOnly);
        json.put("manuallyEdited", manuallyEdited);
        json.put("status", status);
        json.put("errorMessage", errorMessage);
        json.put("errorCode", errorCode);
        json.put("promptDisplayedAt", promptDisplayedAt);
        json.put("requestStartedAt", requestStartedAt);
        json.put("firstSpeechAt", firstSpeechAt);
        json.put("endOfSpeechAt", endOfSpeechAt);
        json.put("finalResultAt", finalResultAt);
        json.put("errorAt", errorAt);
        if (audioSummary != null) json.put("audioSummary", audioSummary.toJson());
        return json;
    }
}
