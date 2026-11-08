package com.example.speechpractice;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WearableBleManager {
    private static final String TAG = "WearableBleManager";
    private static final long SCAN_TIMEOUT_MS = 12_000L;
    private static final long SYNC_INTERVAL_MS = 30_000L;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int REQUESTED_MTU = WearableProtocol.MAX_PACKET_BYTES + 3;

    public interface Listener {
        void onStateChanged(WearableConnectionState state, String detail);
        void onDeviceStatus(WearableDeviceStatus status);
        void onPacketSequence(WearableSequenceTracker.Result result);
        void onPacket(WearablePacket packet, long receivedAtAndroidMonotonicNanos);
        void onProcessedWindow(ProcessedSignalWindow window);
        void onRejectedBatch(MeasurementSource source, String reason);
        void onError(String detail);
    }

    private final Context appContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WearablePacketDecoder decoder = new WearablePacketDecoder();
    private final WearableSequenceTracker sequenceTracker = new WearableSequenceTracker();
    private final WearableSampleSequenceTracker accelerometerSampleSequence = new WearableSampleSequenceTracker();
    private final WearableSampleSequenceTracker semgSampleSequence = new WearableSampleSequenceTracker();
    private final DeviceClockSynchronizer clockSynchronizer = new DeviceClockSynchronizer();
    private final Map<Long, Long> pendingSyncRequests = new ConcurrentHashMap<>();
    private final Runnable scanTimeout = this::onScanTimeout;
    private Runnable reconnectTask;
    private final Runnable periodicSync = new Runnable() {
        @Override public void run() {
            sendTimeSyncRequest();
            if (state == WearableConnectionState.CONNECTED) {
                mainHandler.postDelayed(this, SYNC_INTERVAL_MS);
            }
        }
    };

    private volatile WearableConnectionState state = WearableConnectionState.NOT_CONFIGURED;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic controlCharacteristic;
    private BluetoothGattCharacteristic dataCharacteristic;
    private BluetoothDevice selectedDevice;
    private WearableDeviceStatus deviceStatus;
    private WearableProcessingBridge processingBridge;
    private long outgoingSequence;
    private boolean servicesDiscovered;
    private int reconnectAttempts;

    public WearableBleManager(Context context, Listener listener) {
        if (context == null || listener == null) {
            throw new IllegalArgumentException("Application context and listener are required.");
        }
        appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public boolean hasRequiredPermissions() {
        for (String permission : requiredPermissions()) {
            if (appContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    public WearableConnectionState getState() {
        return state;
    }

    public synchronized void setProcessingConfiguration(AccelerometerProcessor.Config accelerometerConfig,
                                                        SemgProcessor.Config semgConfig,
                                                        String expectedAccelerometerConfigurationId,
                                                        String expectedSemgConfigurationId) {
        processingBridge = new WearableProcessingBridge(accelerometerConfig, semgConfig,
                expectedAccelerometerConfigurationId, expectedSemgConfigurationId,
                new WearableProcessingBridge.Listener() {
                    @Override public void onProcessedWindow(ProcessedSignalWindow window) {
                        mainHandler.post(() -> listener.onProcessedWindow(window));
                    }

                    @Override public void onRejectedBatch(MeasurementSource source, String reason) {
                        mainHandler.post(() -> listener.onRejectedBatch(source, reason));
                    }
                });
    }

    @SuppressLint("MissingPermission")
    public synchronized void startScan() {
        if (!hasRequiredPermissions()) {
            setState(WearableConnectionState.PERMISSION_DENIED,
                    "Nearby-device permission is required before scanning or connecting.");
            return;
        }
        BluetoothManager bluetoothManager =
                (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            setState(WearableConnectionState.BLUETOOTH_UNAVAILABLE,
                    "Bluetooth is unavailable or turned off.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            setState(WearableConnectionState.BLUETOOTH_UNAVAILABLE,
                    "This device does not provide a BLE scanner.");
            return;
        }
        try {
            disconnectGatt();
            setState(WearableConnectionState.SCANNING,
                    "Scanning for the configured SpeechBridge wearable service.");
            List<ScanFilter> filters = Collections.singletonList(
                    new ScanFilter.Builder().setServiceUuid(
                            new android.os.ParcelUuid(WearableProtocol.SERVICE_UUID)).build());
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(filters, settings, scanCallback);
            mainHandler.removeCallbacks(scanTimeout);
            mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS);
        } catch (SecurityException exception) {
            setState(WearableConnectionState.PERMISSION_DENIED,
                    "BLE scan permission was revoked.");
        } catch (RuntimeException exception) {
            Log.e(TAG, "BLE scan could not start.", exception);
            setState(WearableConnectionState.CONNECTION_ERROR, "BLE scan could not start.");
        }
    }

    @SuppressLint("MissingPermission")
    public synchronized void disconnect() {
        mainHandler.removeCallbacks(scanTimeout);
        mainHandler.removeCallbacks(periodicSync);
        if (reconnectTask != null) mainHandler.removeCallbacks(reconnectTask);
        reconnectTask = null;
        selectedDevice = null;
        reconnectAttempts = 0;
        stopScan();
        disconnectGatt();
        clearSession();
        setState(WearableConnectionState.DISCONNECTED, "Wearable disconnected by user.");
    }

    public synchronized WearableDeviceStatus getDeviceStatus() {
        return deviceStatus;
    }

    public synchronized boolean isClockSynchronized() {
        return clockSynchronizer.isAvailable();
    }

    public synchronized void close() {
        disconnect();
        setState(WearableConnectionState.NOT_CONFIGURED,
                "Wearable connection is not active.");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            mainHandler.post(() -> connectTo(result.getDevice()));
        }

        @Override public void onScanFailed(int errorCode) {
            mainHandler.post(() -> setState(WearableConnectionState.CONNECTION_ERROR,
                    "BLE scan failed with platform error " + errorCode + "."));
        }
    };

    @SuppressLint("MissingPermission")
    private synchronized void connectTo(BluetoothDevice device) {
        if (state != WearableConnectionState.SCANNING || device == null) return;
        stopScan();
        selectedDevice = device;
        reconnectAttempts = 0;
        startConnection(device);
    }

    @SuppressLint("MissingPermission")
    private synchronized void startConnection(BluetoothDevice device) {
        if (!hasRequiredPermissions()) {
            setState(WearableConnectionState.PERMISSION_DENIED,
                    "Nearby-device permission was revoked before connection.");
            return;
        }
        setState(WearableConnectionState.CONNECTING, "Connecting to a BLE device advertising the service.");
        try {
            gatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                setState(WearableConnectionState.CONNECTION_ERROR, "Android could not create a GATT connection.");
                scheduleReconnect();
            }
        } catch (SecurityException exception) {
            setState(WearableConnectionState.PERMISSION_DENIED,
                    "Nearby-device permission was revoked before connection.");
        } catch (RuntimeException exception) {
            Log.e(TAG, "BLE connection could not start.", exception);
            setState(WearableConnectionState.CONNECTION_ERROR, "BLE connection could not start.");
            scheduleReconnect();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt callbackGatt, int status, int newState) {
            mainHandler.post(() -> {
                synchronized (WearableBleManager.this) {
                    if (callbackGatt != gatt) return;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        clearSession();
                        setState(WearableConnectionState.CONNECTION_ERROR,
                                "GATT connection failed with status " + status + ".");
                        closeGatt(callbackGatt);
                        scheduleReconnect();
                    } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                        servicesDiscovered = false;
                        setState(WearableConnectionState.CONNECTED,
                                "BLE link established; sensor channels remain unavailable until device status is validated.");
                        try {
                            if (!callbackGatt.requestMtu(REQUESTED_MTU)) discoverServices(callbackGatt);
                        } catch (RuntimeException exception) {
                            discoverServices(callbackGatt);
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        clearSession();
                        setState(WearableConnectionState.DISCONNECTED,
                                "Wearable disconnected. No new sensor values are being accepted.");
                        closeGatt(callbackGatt);
                        scheduleReconnect();
                    }
                }
            });
        }

        @Override public void onMtuChanged(BluetoothGatt callbackGatt, int mtu, int status) {
            mainHandler.post(() -> {
                synchronized (WearableBleManager.this) {
                    if (callbackGatt == gatt) discoverServices(callbackGatt);
                }
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt callbackGatt, int status) {
            mainHandler.post(() -> subscribeToDevice(callbackGatt, status));
        }

        @Override public void onDescriptorWrite(BluetoothGatt callbackGatt,
                                                BluetoothGattDescriptor descriptor, int status) {
            mainHandler.post(() -> {
                synchronized (WearableBleManager.this) {
                    if (callbackGatt != gatt) return;
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        protocolFailure("BLE data notifications could not be enabled.");
                        return;
                    }
                    setState(WearableConnectionState.CONNECTED,
                            "Connected; waiting for validated sensor status. Connection alone does not establish sensor availability.");
                    reconnectAttempts = 0;
                    sendTimeSyncRequest();
                    mainHandler.removeCallbacks(periodicSync);
                    mainHandler.postDelayed(periodicSync, SYNC_INTERVAL_MS);
                }
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt callbackGatt,
                                                    BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post(() -> listener.onError("BLE control write failed with status " + status + "."));
            }
        }

        @Override public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                                      BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            handleNotification(callbackGatt, characteristic, value);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                                      BluetoothGattCharacteristic characteristic,
                                                      byte[] value) {
            handleNotification(callbackGatt, characteristic, value);
        }
    };

    @SuppressLint("MissingPermission")
    private synchronized void discoverServices(BluetoothGatt callbackGatt) {
        if (callbackGatt != gatt || servicesDiscovered) return;
        servicesDiscovered = true;
        try {
            if (!callbackGatt.discoverServices()) protocolFailure("GATT service discovery could not start.");
        } catch (SecurityException exception) {
            setState(WearableConnectionState.PERMISSION_DENIED,
                    "Nearby-device permission was revoked during service discovery.");
        }
    }

    @SuppressLint("MissingPermission")
    private void subscribeToDevice(BluetoothGatt callbackGatt, int status) {
        synchronized (this) {
            if (callbackGatt != gatt) return;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                protocolFailure("Wearable GATT service discovery failed.");
                return;
            }
            BluetoothGattService service = callbackGatt.getService(WearableProtocol.SERVICE_UUID);
            if (service == null) {
                protocolFailure("The connected device does not expose the SpeechBridge service.");
                return;
            }
            dataCharacteristic = service.getCharacteristic(WearableProtocol.DATA_CHARACTERISTIC_UUID);
            controlCharacteristic = service.getCharacteristic(WearableProtocol.CONTROL_CHARACTERISTIC_UUID);
            if (dataCharacteristic == null || controlCharacteristic == null
                    || (dataCharacteristic.getProperties()
                    & (BluetoothGattCharacteristic.PROPERTY_NOTIFY
                    | BluetoothGattCharacteristic.PROPERTY_INDICATE)) == 0
                    || (controlCharacteristic.getProperties()
                    & (BluetoothGattCharacteristic.PROPERTY_WRITE
                    | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
                protocolFailure("Wearable service characteristics are missing required notification/write properties.");
                return;
            }
            BluetoothGattDescriptor descriptor =
                    dataCharacteristic.getDescriptor(WearableProtocol.CCCD_UUID);
            if (descriptor == null) {
                protocolFailure("Wearable data characteristic does not expose a notification descriptor.");
                return;
            }
            try {
                if (!callbackGatt.setCharacteristicNotification(dataCharacteristic, true)) {
                    protocolFailure("Android could not subscribe to wearable data notifications.");
                    return;
                }
                boolean started;
                byte[] subscriptionValue =
                        (dataCharacteristic.getProperties()
                                & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                                ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    started = callbackGatt.writeDescriptor(descriptor, subscriptionValue)
                            == BluetoothGatt.GATT_SUCCESS;
                } else {
                    descriptor.setValue(subscriptionValue);
                    started = callbackGatt.writeDescriptor(descriptor);
                }
                if (!started) protocolFailure("Android could not configure wearable notifications.");
            } catch (SecurityException exception) {
                setState(WearableConnectionState.PERMISSION_DENIED,
                        "Nearby-device permission was revoked while subscribing.");
            }
        }
    }

    private void handleNotification(BluetoothGatt callbackGatt,
                                   BluetoothGattCharacteristic characteristic, byte[] bytes) {
        if (characteristic == null || !WearableProtocol.DATA_CHARACTERISTIC_UUID
                .equals(characteristic.getUuid())) return;
        final long receivedAt = SystemClock.elapsedRealtimeNanos();
        mainHandler.post(() -> {
            synchronized (WearableBleManager.this) {
                if (callbackGatt != gatt || state != WearableConnectionState.CONNECTED) return;
                WearablePacket packet;
                try {
                    packet = decoder.decode(bytes == null ? null : bytes.clone());
                } catch (IllegalArgumentException exception) {
                    if (exception.getMessage() != null
                            && exception.getMessage().startsWith("Unsupported wearable protocol version")) {
                        setState(WearableConnectionState.INVALID_PROTOCOL, exception.getMessage());
                        closeGatt(callbackGatt);
                    }
                    listener.onError("Rejected malformed wearable packet: " + exception.getMessage());
                    return;
                }
                WearableSequenceTracker.Result sequence =
                        sequenceTracker.observe(packet.sessionId, packet.sequence);
                listener.onPacketSequence(sequence);
                if (!sequence.shouldAccept()) {
                    listener.onError(sequence.disposition == WearableSequenceTracker.Disposition.DUPLICATE
                            ? "Duplicate packet rejected."
                            : "Out-of-order packet rejected.");
                    return;
                }
                if (sequence.disposition == WearableSequenceTracker.Disposition.NEW_SESSION) {
                    clockSynchronizer.reset();
                    pendingSyncRequests.clear();
                    accelerometerSampleSequence.reset();
                    semgSampleSequence.reset();
                    deviceStatus = null;
                    if (processingBridge != null) processingBridge.reset();
                    setState(WearableConnectionState.CONNECTED,
                            "Device clock session changed; resynchronization is required before samples are processed.");
                    sendTimeSyncRequest();
                }
                if (packet.type == WearableProtocol.TYPE_STATUS) {
                    deviceStatus = packet.deviceStatus;
                    listener.onDeviceStatus(deviceStatus);
                } else if (packet.type == WearableProtocol.TYPE_ERROR) {
                    listener.onError("Wearable error " + packet.errorCode + " (source "
                            + packet.errorSource + "): " + packet.errorMessage);
                } else if (packet.type == WearableProtocol.TYPE_TIME_SYNC_RESPONSE) {
                    Long sent = pendingSyncRequests.remove(packet.echoedAndroidMonotonicNanos);
                    if (sent == null) {
                        listener.onError("Unmatched clock synchronization response rejected.");
                        return;
                    }
                    try {
                        clockSynchronizer.addExchange(packet.deviceTimestampMicros, sent, receivedAt);
                    } catch (IllegalArgumentException exception) {
                        listener.onError("Invalid clock synchronization response rejected.");
                        return;
                    }
                } else if (packet.type == WearableProtocol.TYPE_ACCELEROMETER_BATCH
                        || packet.type == WearableProtocol.TYPE_SEMG_BATCH) {
                    if (deviceStatus == null) {
                        listener.onError("Sensor batch rejected because no valid device status was received.");
                        return;
                    }
                    if (packet.type == WearableProtocol.TYPE_ACCELEROMETER_BATCH
                            && !deviceStatus.accelerometerAvailable
                            || packet.type == WearableProtocol.TYPE_SEMG_BATCH
                            && !deviceStatus.semgAvailable) {
                        listener.onError("Sensor batch rejected because that channel is unavailable in device status.");
                        return;
                    }
                    WearableSampleSequenceTracker sampleTracker =
                            packet.type == WearableProtocol.TYPE_ACCELEROMETER_BATCH
                                    ? accelerometerSampleSequence : semgSampleSequence;
                    int sampleCount = packet.type == WearableProtocol.TYPE_ACCELEROMETER_BATCH
                            ? packet.accelerometerSamples.size() : packet.semgSamples.size();
                    WearableSequenceTracker.Result sampleSequenceResult =
                            sampleTracker.observe(packet.firstSampleSequence, sampleCount);
                    if (sampleSequenceResult.disposition == WearableSequenceTracker.Disposition.DUPLICATE
                            || sampleSequenceResult.disposition
                            == WearableSequenceTracker.Disposition.OUT_OF_ORDER) {
                        listener.onError("Duplicate or out-of-order sensor sample batch rejected.");
                        return;
                    }
                    if (sampleSequenceResult.disposition == WearableSequenceTracker.Disposition.GAP) {
                        listener.onError("Sensor sample sequence gap detected: "
                                + sampleSequenceResult.missingPackets + " sample(s) missing; no zero samples were substituted.");
                    }
                    if (processingBridge != null) {
                        processingBridge.accept(packet, deviceStatus, clockSynchronizer);
                    }
                }
                listener.onPacket(packet, receivedAt);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private synchronized void sendTimeSyncRequest() {
        if (gatt == null || controlCharacteristic == null
                || state != WearableConnectionState.CONNECTED) return;
        if (!hasRequiredPermissions()) {
            setState(WearableConnectionState.PERMISSION_DENIED,
                    "Nearby-device permission was revoked; clock synchronization stopped.");
            return;
        }
        long sent = SystemClock.elapsedRealtimeNanos();
        long sequence = outgoingSequence++ & 0xFFFFFFFFL;
        byte[] request;
        try {
            request = WearablePacketEncoder.timeSyncRequest(sent, sequence);
        } catch (IllegalArgumentException exception) {
            listener.onError("Could not encode clock synchronization request.");
            return;
        }
        pendingSyncRequests.put(sent, sent);
        while (pendingSyncRequests.size() > 8) {
            Long oldest = pendingSyncRequests.keySet().iterator().next();
            pendingSyncRequests.remove(oldest);
        }
        try {
            boolean started;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                started = gatt.writeCharacteristic(controlCharacteristic, request,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS;
            } else {
                controlCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                controlCharacteristic.setValue(request);
                started = gatt.writeCharacteristic(controlCharacteristic);
            }
            if (!started) {
                pendingSyncRequests.remove(sent);
                listener.onError("Clock synchronization request could not be sent.");
            }
        } catch (SecurityException exception) {
            pendingSyncRequests.remove(sent);
            setState(WearableConnectionState.PERMISSION_DENIED,
                    "Nearby-device permission was revoked; clock synchronization stopped.");
        }
    }

    @SuppressLint("MissingPermission")
    private synchronized void stopScan() {
        mainHandler.removeCallbacks(scanTimeout);
        if (scanner != null && hasRequiredPermissions()) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException exception) {
                Log.w(TAG, "BLE scan stop was blocked by a permission change.");
            } catch (RuntimeException exception) {
                Log.w(TAG, "BLE scanner was already stopped.", exception);
            }
        }
        scanner = null;
    }

    private synchronized void onScanTimeout() {
        stopScan();
        if (state == WearableConnectionState.SCANNING) {
            setState(WearableConnectionState.DISCONNECTED,
                    "No compatible SpeechBridge BLE service was found during this scan.");
        }
    }

    private synchronized void disconnectGatt() {
        if (gatt == null) return;
        BluetoothGatt oldGatt = gatt;
        gatt = null;
        controlCharacteristic = null;
        dataCharacteristic = null;
        try {
            if (hasRequiredPermissions()) oldGatt.disconnect();
        } catch (SecurityException exception) {
            Log.w(TAG, "GATT disconnect was blocked by a permission change.");
        } finally {
            oldGatt.close();
        }
    }

    private void closeGatt(BluetoothGatt target) {
        if (target == gatt) {
            gatt = null;
            controlCharacteristic = null;
            dataCharacteristic = null;
        }
        target.close();
    }

    private synchronized void clearSession() {
        deviceStatus = null;
        sequenceTracker.reset();
        accelerometerSampleSequence.reset();
        semgSampleSequence.reset();
        clockSynchronizer.reset();
        pendingSyncRequests.clear();
        mainHandler.removeCallbacks(periodicSync);
        if (processingBridge != null) processingBridge.reset();
    }

    private synchronized void scheduleReconnect() {
        if (selectedDevice == null || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS
                || !hasRequiredPermissions()) {
            setState(WearableConnectionState.DISCONNECTED,
                    "Wearable disconnected. No new sensor values are being accepted.");
            return;
        }
        if (reconnectTask != null) mainHandler.removeCallbacks(reconnectTask);
        long delayMs = 1000L << reconnectAttempts;
        reconnectAttempts++;
        BluetoothDevice target = selectedDevice;
        setState(WearableConnectionState.DISCONNECTED,
                "Disconnected; attempting BLE reconnection " + reconnectAttempts
                        + " of " + MAX_RECONNECT_ATTEMPTS + ".");
        reconnectTask = () -> {
            synchronized (WearableBleManager.this) {
                if (selectedDevice == target
                        && state == WearableConnectionState.DISCONNECTED) {
                    startConnection(target);
                }
            }
        };
        mainHandler.postDelayed(reconnectTask, delayMs);
    }

    private void protocolFailure(String message) {
        setState(WearableConnectionState.INVALID_PROTOCOL, message);
        BluetoothGatt current = gatt;
        if (current != null) closeGatt(current);
    }

    private void setState(WearableConnectionState newState, String detail) {
        state = newState;
        mainHandler.post(() -> listener.onStateChanged(newState, detail));
    }
}
