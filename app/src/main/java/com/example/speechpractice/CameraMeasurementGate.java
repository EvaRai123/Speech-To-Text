package com.example.speechpractice;

final class CameraMeasurementGate {
    private long generation;
    private boolean active;

    synchronized long start() {
        active = true;
        return ++generation;
    }

    synchronized void stop() {
        active = false;
        generation++;
    }

    synchronized boolean isActive(long session) {
        return active && generation == session;
    }

    synchronized boolean runIfActive(long session, Runnable action) {
        if (!active || generation != session) return false;
        action.run();
        return true;
    }
}
