package com.example.speechpractice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

public class SessionRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String sessionId;
    public final long startedAtWallClock;
    public final long startedAtElapsed;
    public long finishedAtWallClock;
    public long finishedAtElapsed;
    public final List<AttemptRecord> attempts = new ArrayList<>();

    public SessionRecord(String sessionId, long startedAtWallClock, long startedAtElapsed) {
        this.sessionId = sessionId;
        this.startedAtWallClock = startedAtWallClock;
        this.startedAtElapsed = startedAtElapsed;
    }

    public long durationMs() {
        return finishedAtElapsed > startedAtElapsed ? finishedAtElapsed - startedAtElapsed : 0;
    }

    public int promptsAttempted() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if (attempt.attemptsStarted > 0) count++;
        return count;
    }

    public int promptsCompleted() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if (attempt.hasUsableTranscript()) count++;
        return count;
    }

    public int skipped() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if ("skipped".equals(attempt.status)) count++;
        return count;
    }

    public int recognitionResults() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if (!attempt.machineTranscript.isEmpty()) count++;
        return count;
    }

    public int recognitionFailures() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if ("recognition_error".equals(attempt.status)) count++;
        return count;
    }

    public int retries() {
        int count = 0;
        for (AttemptRecord attempt : attempts) count += attempt.retries;
        return count;
    }

    public int manuallyEdited() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if (attempt.manuallyEdited) count++;
        return count;
    }

    public int usableTranscripts() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if (attempt.hasUsableTranscript()) count++;
        return count;
    }

    public int missingTranscripts() {
        int count = 0;
        for (AttemptRecord attempt : attempts) if (!attempt.hasUsableTranscript()) count++;
        return count;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("sessionId", sessionId);
        json.put("startedAtWallClock", startedAtWallClock);
        json.put("startedAtElapsed", startedAtElapsed);
        json.put("finishedAtWallClock", finishedAtWallClock);
        json.put("finishedAtElapsed", finishedAtElapsed);
        JSONArray attemptArray = new JSONArray();
        for (AttemptRecord attempt : attempts) attemptArray.put(attempt.toJson());
        json.put("attempts", attemptArray);
        return json;
    }
}
