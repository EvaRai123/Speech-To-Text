package com.example.speechpractice;

import java.util.UUID;

public final class WearableProtocol {
    public static final int VERSION = 1;
    public static final int MAX_PACKET_BYTES = 182;
    public static final int MAX_ACCELEROMETER_SAMPLES_PER_PACKET = 12;
    public static final int MAX_SEMG_SAMPLES_PER_PACKET = 36;
    public static final int MAGIC_FIRST = 0x53;
    public static final int MAGIC_SECOND = 0x42;

    public static final int TYPE_STATUS = 0x01;
    public static final int TYPE_ERROR = 0x02;
    public static final int TYPE_TIME_SYNC_REQUEST = 0x20;
    public static final int TYPE_TIME_SYNC_RESPONSE = 0x21;
    public static final int TYPE_ACCELEROMETER_BATCH = 0x10;
    public static final int TYPE_SEMG_BATCH = 0x11;

    public static final int UNIT_METERS_PER_SECOND_SQUARED = 1;

    public static final UUID SERVICE_UUID = UUID.fromString("7b6e0001-8f2a-4a2d-9c31-535045454348");
    public static final UUID DATA_CHARACTERISTIC_UUID = UUID.fromString("7b6e0002-8f2a-4a2d-9c31-535045454348");
    public static final UUID CONTROL_CHARACTERISTIC_UUID = UUID.fromString("7b6e0003-8f2a-4a2d-9c31-535045454348");
    public static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private WearableProtocol() {
    }

    static int crc16Ccitt(byte[] bytes, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= (bytes[i] & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return crc;
    }
}
