package com.example.speechpractice;

public final class AttemptSourceStatus {
    public final String status;
    public final String detail;

    public AttemptSourceStatus(String status, String detail) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("A source status is required.");
        }
        this.status = status;
        this.detail = detail == null ? "" : detail;
    }
}
