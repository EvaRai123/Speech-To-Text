package com.example.speechpractice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class VoskModelManager {
    public static final String MODEL_NAME = "vosk-model-small-en-us-0.15";
    public static final String MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
    public static final long APPROXIMATE_DOWNLOAD_BYTES = 40L * 1024L * 1024L;
    private static final long MIN_FREE_BYTES = 120L * 1024L * 1024L;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public interface Listener {
        void onProgress(int percent);
        void onComplete(File modelDirectory);
        void onError(String message, Throwable cause);
    }

    public VoskModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public File modelDirectory() {
        return new File(context.getFilesDir(), MODEL_NAME);
    }

    public boolean isInstalled() {
        File model = modelDirectory();
        return model.isDirectory() && new File(model, "conf").isDirectory();
    }

    public void downloadAsync(Listener listener) {
        if (isInstalled()) { post(() -> listener.onComplete(modelDirectory())); return; }
        worker.execute(() -> {
            File zip = new File(context.getCacheDir(), MODEL_NAME + ".zip");
            File staging = new File(context.getFilesDir(), MODEL_NAME + ".staging");
            try {
                if (context.getFilesDir().getFreeSpace() < MIN_FREE_BYTES) throw new IOException("At least 120 MB of free storage is required for the model.");
                download(zip, listener);
                deleteRecursively(staging);
                if (!staging.mkdirs()) throw new IOException("Could not create model staging directory.");
                unzip(zip, staging);
                File extracted = new File(staging, MODEL_NAME);
                if (!extracted.isDirectory()) throw new IOException("Downloaded model has an unexpected layout.");
                File destination = modelDirectory();
                deleteRecursively(destination);
                if (!extracted.renameTo(destination)) throw new IOException("Could not install the downloaded model.");
                deleteRecursively(staging);
                // The zip is a cache artifact and is not retained after installation.
                if (!zip.delete()) zip.deleteOnExit();
                post(() -> listener.onComplete(destination));
            } catch (Throwable failure) {
                deleteRecursively(staging);
                post(() -> listener.onError("The offline English model could not be installed.", failure));
            }
        });
    }

    public void shutdown() { worker.shutdownNow(); }

    private void download(File destination, Listener listener) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_URL).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("Model download returned HTTP " + connection.getResponseCode());
        long total = connection.getContentLengthLong();
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[8192];
            long read = 0;
            int count;
            int lastPercent = -1;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                read += count;
                int percent = total > 0 ? (int) Math.min(99, read * 100 / total) : -1;
                if (percent != lastPercent) { lastPercent = percent; final int value = percent; post(() -> listener.onProgress(value)); }
            }
        } finally { connection.disconnect(); }
    }

    private void unzip(File zip, File target) throws IOException {
        String root = target.getCanonicalPath() + File.separator;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                File output = new File(target, entry.getName());
                if (!output.getCanonicalPath().startsWith(root)) throw new IOException("Unsafe model archive entry.");
                if (entry.isDirectory()) { if (!output.mkdirs() && !output.isDirectory()) throw new IOException("Could not create model directory."); }
                else {
                    File parent = output.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("Could not create model parent directory.");
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count; while ((count = input.read(buffer)) != -1) stream.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private void post(Runnable action) { mainHandler.post(action); }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursively(child); }
        file.delete();
    }
}
