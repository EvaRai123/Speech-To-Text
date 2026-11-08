package com.example.speechpractice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WearablePacketDecoder {
    private static final int HEADER_BYTES = 22;
    private static final int CRC_BYTES = 2;
    private static final int MAX_STRING_BYTES = 48;

    public WearablePacket decode(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER_BYTES + CRC_BYTES
                || bytes.length > WearableProtocol.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Packet length is outside the protocol bounds.");
        }
        if ((bytes[0] & 0xFF) != WearableProtocol.MAGIC_FIRST
                || (bytes[1] & 0xFF) != WearableProtocol.MAGIC_SECOND) {
            throw new IllegalArgumentException("Packet magic is invalid.");
        }
        int expectedCrc = (bytes[bytes.length - 2] & 0xFF)
                | ((bytes[bytes.length - 1] & 0xFF) << 8);
        if (WearableProtocol.crc16Ccitt(bytes, 0, bytes.length - CRC_BYTES) != expectedCrc) {
            throw new IllegalArgumentException("Packet checksum is invalid.");
        }
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        input.position(2);
        int version = input.get() & 0xFF;
        if (version != WearableProtocol.VERSION) {
            throw new IllegalArgumentException("Unsupported wearable protocol version " + version + ".");
        }
        int type = input.get() & 0xFF;
        long sessionId = Integer.toUnsignedLong(input.getInt());
        long sequence = Integer.toUnsignedLong(input.getInt());
        long timestampMicros = input.getLong();
        int payloadLength = input.getShort() & 0xFFFF;
        if (timestampMicros < 0L || payloadLength != bytes.length - HEADER_BYTES - CRC_BYTES) {
            throw new IllegalArgumentException("Timestamp or payload length is invalid.");
        }
        int payloadEnd = bytes.length - CRC_BYTES;
        WearableDeviceStatus status = null;
        List<WearablePacket.AccelerometerWireSample> accelerometer = Collections.emptyList();
        List<WearablePacket.SemgWireSample> semg = Collections.emptyList();
        Long echoedAndroidNanos = null;
        long firstSampleSequence = -1L;
        int errorCode = -1;
        int errorSource = -1;
        String errorMessage = "";
        switch (type) {
            case WearableProtocol.TYPE_STATUS:
                status = decodeStatus(input, payloadEnd);
                break;
            case WearableProtocol.TYPE_ACCELEROMETER_BATCH:
                firstSampleSequence = Integer.toUnsignedLong(input.getInt(HEADER_BYTES + 1 + 4));
                accelerometer = decodeAccelerometer(input, payloadEnd, timestampMicros);
                break;
            case WearableProtocol.TYPE_SEMG_BATCH:
                firstSampleSequence = Integer.toUnsignedLong(input.getInt(HEADER_BYTES + 1 + 4));
                semg = decodeSemg(input, payloadEnd, timestampMicros);
                break;
            case WearableProtocol.TYPE_TIME_SYNC_RESPONSE:
                requireRemaining(input, payloadEnd, 8);
                echoedAndroidNanos = input.getLong();
                if (echoedAndroidNanos < 0L || input.position() != payloadEnd) {
                    throw new IllegalArgumentException("Time synchronization response is invalid.");
                }
                break;
            case WearableProtocol.TYPE_ERROR:
                requireRemaining(input, payloadEnd, 3);
                errorCode = input.get() & 0xFF;
                errorSource = input.get() & 0xFF;
                errorMessage = readString(input, payloadEnd);
                if (input.position() != payloadEnd) {
                    throw new IllegalArgumentException("Error packet contains trailing fields.");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported wearable packet type " + type + ".");
        }
        return new WearablePacket(version, type, sessionId, sequence, timestampMicros,
                status, accelerometer, semg, echoedAndroidNanos, firstSampleSequence,
                errorCode, errorSource, errorMessage);
    }

    private WearableDeviceStatus decodeStatus(ByteBuffer input, int end) {
        requireRemaining(input, end, 20);
        int flags = input.get() & 0xFF;
        if ((flags & 0xE0) != 0) throw new IllegalArgumentException("Status flags contain unsupported bits.");
        int accelUnit = input.get() & 0xFF;
        double accelScale = input.getFloat();
        long accelPeriodUs = Integer.toUnsignedLong(input.getInt());
        double semgScale = input.getFloat();
        double semgRate = input.getFloat();
        int accelInit = input.get() & 0xFF;
        int semgInit = input.get() & 0xFF;
        String accelConfiguration = readString(input, end);
        String semgConfiguration = readString(input, end);
        String semgInputUnit = readString(input, end);
        String semgAnalogFrontEndId = readString(input, end);
        String deviceId = readString(input, end);
        String firmware = readString(input, end);
        if (input.position() != end) throw new IllegalArgumentException("Status packet contains trailing fields.");
        if (deviceId.isEmpty() || firmware.isEmpty()) {
            throw new IllegalArgumentException("Device status must include device and firmware identifiers.");
        }

        boolean accelAvailable = (flags & 0x01) != 0 && accelInit == 0
                && accelUnit == WearableProtocol.UNIT_METERS_PER_SECOND_SQUARED
                && Float.isFinite((float) accelScale) && accelScale > 0.0
                && accelPeriodUs > 0 && !accelConfiguration.isEmpty();
        boolean semgAvailable = (flags & 0x02) != 0 && semgInit == 0
                && (flags & 0x1C) == 0x1C
                && Double.isFinite(semgScale) && semgScale > 0.0
                && Double.isFinite(semgRate) && semgRate > 0.0
                && !semgConfiguration.isEmpty() && !semgInputUnit.isEmpty()
                && !semgAnalogFrontEndId.isEmpty();
        return new WearableDeviceStatus(deviceId, firmware, accelAvailable, semgAvailable,
                (flags & 0x1C) == 0x1C, accelInit, semgInit, accelUnit, accelScale,
                accelPeriodUs, semgScale,
                semgRate, semgInputUnit, semgAnalogFrontEndId, accelConfiguration, semgConfiguration);
    }

    private List<WearablePacket.AccelerometerWireSample> decodeAccelerometer(
            ByteBuffer input, int end, long frameTimestampMicros) {
        requireRemaining(input, end, 14);
        int count = input.get() & 0xFF;
        long intervalUs = Integer.toUnsignedLong(input.getInt());
        long firstSampleSequence = Integer.toUnsignedLong(input.getInt());
        int unitCode = input.get() & 0xFF;
        double scale = input.getFloat();
        if (count < 1 || count > WearableProtocol.MAX_ACCELEROMETER_SAMPLES_PER_PACKET || intervalUs == 0
                || unitCode != WearableProtocol.UNIT_METERS_PER_SECOND_SQUARED
                || !Double.isFinite(scale) || scale <= 0.0
                || end - input.position() != count * 12) {
            throw new IllegalArgumentException("Accelerometer batch metadata or sample count is invalid.");
        }
        List<WearablePacket.AccelerometerWireSample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long timestamp = safeSampleTimestamp(frameTimestampMicros, intervalUs, i);
            long sampleSequence = (firstSampleSequence + i) & 0xFFFFFFFFL;
            samples.add(new WearablePacket.AccelerometerWireSample(sampleSequence, timestamp,
                    input.getInt(), input.getInt(), input.getInt(), unitCode, scale));
        }
        return samples;
    }

    private List<WearablePacket.SemgWireSample> decodeSemg(
            ByteBuffer input, int end, long frameTimestampMicros) {
        requireRemaining(input, end, 13);
        int count = input.get() & 0xFF;
        long intervalUs = Integer.toUnsignedLong(input.getInt());
        long firstSampleSequence = Integer.toUnsignedLong(input.getInt());
        double scale = input.getFloat();
        if (count < 1 || count > WearableProtocol.MAX_SEMG_SAMPLES_PER_PACKET || intervalUs == 0
                || !Double.isFinite(scale) || scale <= 0.0
                || end - input.position() != count * 4) {
            throw new IllegalArgumentException("sEMG batch metadata or sample count is invalid.");
        }
        List<WearablePacket.SemgWireSample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long timestamp = safeSampleTimestamp(frameTimestampMicros, intervalUs, i);
            long sampleSequence = (firstSampleSequence + i) & 0xFFFFFFFFL;
            samples.add(new WearablePacket.SemgWireSample(sampleSequence, timestamp,
                    input.getInt(), scale));
        }
        return samples;
    }

    private long safeSampleTimestamp(long first, long interval, int index) {
        if (index > 0 && interval > (Long.MAX_VALUE - first) / index) {
            throw new IllegalArgumentException("Sample timestamp overflows the supported device clock range.");
        }
        return first + interval * index;
    }

    private String readString(ByteBuffer input, int end) {
        requireRemaining(input, end, 1);
        int length = input.get() & 0xFF;
        if (length > MAX_STRING_BYTES || end - input.position() < length) {
            throw new IllegalArgumentException("Status string length is invalid.");
        }
        byte[] bytes = new byte[length];
        input.get(bytes);
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned < 0x20 || unsigned > 0x7E) {
                throw new IllegalArgumentException("Protocol strings must be printable ASCII.");
            }
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private void requireRemaining(ByteBuffer input, int end, int required) {
        if (required < 0 || end - input.position() < required) {
            throw new IllegalArgumentException("Packet payload is truncated.");
        }
    }
}
