package com.example.speechpractice;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SessionRecordTest {
    @Test
    public void summaryCountsUseRecordedAttemptState() {
        SessionRecord session = new SessionRecord("test", 100L, 10L);
        AttemptRecord recognized = new AttemptRecord("apple", "apple");
        recognized.attemptsStarted = 2;
        recognized.retries = 1;
        recognized.machineTranscript = "apple";
        recognized.transcript = "apple";
        recognized.status = "recognized";
        session.attempts.add(recognized);

        AttemptRecord skipped = new AttemptRecord("cup", "cup");
        skipped.status = "skipped";
        session.attempts.add(skipped);

        AttemptRecord failed = new AttemptRecord("book", "book");
        failed.attemptsStarted = 1;
        failed.status = "recognition_error";
        session.attempts.add(failed);

        assertEquals(2, session.promptsAttempted());
        assertEquals(1, session.promptsCompleted());
        assertEquals(1, session.skipped());
        assertEquals(1, session.recognitionResults());
        assertEquals(1, session.recognitionFailures());
        assertEquals(1, session.retries());
        assertEquals(1, session.usableTranscripts());
        assertEquals(2, session.missingTranscripts());
    }
}
