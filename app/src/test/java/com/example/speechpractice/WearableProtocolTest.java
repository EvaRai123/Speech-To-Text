package com.example.speechpractice;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WearableProtocolTest {
    private final WearablePacketDecoder decoder = new WearablePacketDecoder();

    @Test
    public void decodesStatusAndOnlyMarksExplicitlyConfiguredChannelsAvailable() {
        WearablePacket packet = decoder.decode(frame(WearableProtocol.TYPE_STATUS, 4L, 0L,
                1000L, statusPayload(0x01, 0, 1, 0.001f, 10_000, 0.25f,
                        1000.0f, "accel-config", "", "", "", "fixture-device", "fixture-fw")));

        assertEquals("fixture-device", packet.deviceStatus.deviceId);
        assertTrue(packet.deviceStatus.accelerometerAvailable);
        assertFalse(packet.deviceStatus.semgAvailable);
        assertEquals("accel-config", packet.deviceStatus.accelerometerConfigurationId);
    }

    @Test
    public void decodesTimestampedAccelerometerBatchAndSequence() {
        ByteBuffer payload = ByteBuffer.allocate(14 + 4 * 12).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 4).putInt(10_000).putInt(41).put((byte) 1).putFloat(0.001f);
        for (int i = 0; i < 4; i++) payload.putInt(100 + i).putInt(-50).putInt(20);

        WearablePacket packet = decoder.decode(frame(WearableProtocol.TYPE_ACCELEROMETER_BATCH,
                8L, 12L, 3_000_000L, payload.array()));

        assertEquals(41L, packet.firstSampleSequence);
        assertEquals(4, packet.accelerometerSamples.size());
        assertEquals(3_020_000L, packet.accelerometerSamples.get(2).deviceTimestampMicros);
        assertEquals(102.0, packet.accelerometerSamples.get(2).xRaw, 0.0);
        assertEquals(0.001, packet.accelerometerSamples.get(2)
                .scaleToMetersPerSecondSquared, 0.0000001);
    }

    @Test
    public void rejectsBadChecksumUnsupportedVersionAndMalformedSampleCount() {
        byte[] validStatus = frame(WearableProtocol.TYPE_STATUS, 1, 0, 1,
                statusPayload(0, 1, 1, 0.0f, 0, 0.0f, 0.0f,
                        "", "", "", "", "fixture-device", "fixture-fw"));
        validStatus[24] ^= 0x01;
        expectInvalid(validStatus);

        byte[] unsupported = frame(WearableProtocol.TYPE_STATUS, 1, 0, 1,
                statusPayload(0, 1, 1, 0.0f, 0, 0.0f, 0.0f,
                        "", "", "", "", "fixture-device", "fixture-fw"));
        unsupported[2] = 2;
        updateChecksum(unsupported);
        expectInvalid(unsupported);

        ByteBuffer malformed = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN);
        malformed.put((byte) 0).putInt(10_000).putInt(0).put((byte) 1).putFloat(0.001f);
        expectInvalid(frame(WearableProtocol.TYPE_ACCELEROMETER_BATCH, 1, 1, 1000,
                malformed.array()));
    }

    @Test
    public void sequenceTrackerDetectsGapsDuplicatesOrderingWrapAndDeviceSessionReset() {
        WearableSequenceTracker tracker = new WearableSequenceTracker();
        assertEquals(WearableSequenceTracker.Disposition.FIRST, tracker.observe(1, 0xFFFFFFFEL).disposition);
        assertEquals(WearableSequenceTracker.Disposition.IN_ORDER, tracker.observe(1, 0xFFFFFFFFL).disposition);
        assertEquals(WearableSequenceTracker.Disposition.IN_ORDER, tracker.observe(1, 0).disposition);
        WearableSequenceTracker.Result gap = tracker.observe(1, 3);
        assertEquals(WearableSequenceTracker.Disposition.GAP, gap.disposition);
        assertEquals(2L, gap.missingPackets);
        assertEquals(WearableSequenceTracker.Disposition.DUPLICATE, tracker.observe(1, 3).disposition);
        assertEquals(WearableSequenceTracker.Disposition.OUT_OF_ORDER, tracker.observe(1, 2).disposition);
        assertEquals(WearableSequenceTracker.Disposition.NEW_SESSION, tracker.observe(2, 0).disposition);
    }

    @Test
    public void sampleSequenceTrackerDetectsMissingSamplesWithoutInventingReplacementValues() {
        WearableSampleSequenceTracker tracker = new WearableSampleSequenceTracker();
        assertEquals(WearableSequenceTracker.Disposition.FIRST, tracker.observe(10, 4).disposition);
        WearableSequenceTracker.Result missing = tracker.observe(16, 3);
        assertEquals(WearableSequenceTracker.Disposition.GAP, missing.disposition);
        assertEquals(2L, missing.missingPackets);
        assertEquals(WearableSequenceTracker.Disposition.DUPLICATE, tracker.observe(16, 3).disposition);
        tracker.reset();
        assertEquals(WearableSequenceTracker.Disposition.FIRST, tracker.observe(3, 1).disposition);
    }

    @Test
    public void clockSynchronizerSeparatesDeviceTimeAndAndroidReceiptAndEstimatesDrift() {
        DeviceClockSynchronizer synchronizer = new DeviceClockSynchronizer();
        synchronizer.addExchange(1_000_000L, 10_000_000_000L, 10_000_020_000L);
        assertFalse(synchronizer.isAvailable());
        synchronizer.addExchange(2_000_000L, 11_000_500_000L, 11_000_520_000L);
        assertTrue(synchronizer.isAvailable());
        assertEquals(12_001_010_000L, synchronizer.toAndroidMonotonicNanos(3_000_000L));
        synchronizer.reset();
        assertFalse(synchronizer.isAvailable());
    }

    @Test
    public void validAccelerometerSamplesMapToExistingProcessorMeasurementContracts() {
        WearablePacket statusPacket = decoder.decode(frame(WearableProtocol.TYPE_STATUS, 9, 0, 1000,
                statusPayload(1, 0, 1, 0.001f, 10_000, 0.0f,
                        0.0f, "accel-config", "", "", "", "fixture-device", "fixture-fw")));
        assertTrue(statusPacket.deviceStatus.accelerometerAvailable);
        ByteBuffer payload = ByteBuffer.allocate(14 + 4 * 12).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 4).putInt(10_000).putInt(5).put((byte) 1).putFloat(0.001f);
        for (int i = 0; i < 4; i++) payload.putInt(100 + i * 10).putInt(20).putInt(-10);
        WearablePacket samples = decoder.decode(frame(WearableProtocol.TYPE_ACCELEROMETER_BATCH,
                9, 1, 3_000_000L, payload.array()));
        DeviceClockSynchronizer clock = synchronizedClock();
        final List<ProcessedSignalWindow> windows = new ArrayList<>();
        WearableProcessingBridge bridge = new WearableProcessingBridge(
                new AccelerometerProcessor.Config(100.0, 0.0, 100.0, 0.0, 3.0, 0.2),
                null, "accel-config", "", bridgeListener(windows));

        bridge.accept(samples, statusPacket.deviceStatus, clock);

        assertEquals(1, windows.size());
        assertEquals(MeasurementSource.ACCELEROMETER, windows.get(0).source);
        assertEquals(MeasurementStatus.AVAILABLE, windows.get(0).status);
        assertEquals(MeasurementType.ACCELERATION_RMS, windows.get(0).measurements.get(0).type);
        assertEquals("m/s^2", windows.get(0).measurements.get(0).unit);
    }

    @Test
    public void semgMappingRequiresMatchingExplicitInputUnitsAndUnavailableChannelsStayNull() {
        WearablePacket statusPacket = decoder.decode(frame(WearableProtocol.TYPE_STATUS, 9, 0, 1000,
                statusPayload(0x1E, 1, 0, 0.001f, 10_000, 0.25f,
                        1000.0f, "", "afe-config", "adc-count", "afe-part-id",
                        "fixture-device", "fixture-fw")));
        assertTrue(statusPacket.deviceStatus.semgAvailable);
        ByteBuffer payload = ByteBuffer.allocate(13 + 4 * 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) 4).putInt(1000).putInt(20).putFloat(0.25f);
        payload.putInt(100).putInt(120).putInt(110).putInt(130);
        WearablePacket samples = decoder.decode(frame(WearableProtocol.TYPE_SEMG_BATCH,
                9, 1, 3_000_000L, payload.array()));
        final List<ProcessedSignalWindow> windows = new ArrayList<>();
        WearableProcessingBridge bridge = new WearableProcessingBridge(null,
                new SemgProcessor.Config(1000.0, 1.0, 20.0, 100.0,
                        10.0, 0.0, 10.0, 3.0, 0.2, 0.1, "adc-count"),
                "", "afe-config", bridgeListener(windows));
        bridge.accept(samples, statusPacket.deviceStatus, synchronizedClock());
        assertEquals(1, windows.size());
        assertEquals(MeasurementSource.SEMG, windows.get(0).source);
        assertEquals(MeasurementStatus.AVAILABLE, windows.get(0).status);

        WearableDeviceStatus unreviewedStatus = decoder.decode(frame(WearableProtocol.TYPE_STATUS, 11, 0, 1,
                statusPayload(0x02, 1, 0, 0.001f, 10_000, 0.25f,
                        1000.0f, "", "afe-config", "adc-count", "afe-part-id",
                        "fixture-device", "fixture-fw"))).deviceStatus;
        assertFalse(unreviewedStatus.semgAvailable);
        assertFalse(unreviewedStatus.semgSafetyMetadataReported);
        assertTrue(unreviewedStatus.summary().contains("required safety metadata"));

        WearableDeviceStatus unavailable = decoder.decode(frame(WearableProtocol.TYPE_STATUS, 10, 0, 1,
                statusPayload(0, 1, 1, 0.0f, 0, 0.0f, 0.0f,
                        "", "", "", "", "fixture-device", "fixture-fw"))).deviceStatus;
        assertFalse(unavailable.semgAvailable);
        MeasurementRecord absent = MeasurementRecord.unavailable(MeasurementSource.SEMG,
                MeasurementType.SEMG_ENVELOPE_RMS, MeasurementStatus.UNAVAILABLE,
                "Synthetic test fixture: no channel availability.");
        assertEquals(null, absent.value);
        assertEquals(null, absent.unit);
    }

    @Test
    public void timeSyncRequestCarriesAndroidMonotonicTimestampAndProtocolHeader() {
        byte[] request = WearablePacketEncoder.timeSyncRequest(123_456_789L, 7L);
        assertEquals(WearableProtocol.MAGIC_FIRST, request[0] & 0xFF);
        assertEquals(WearableProtocol.MAGIC_SECOND, request[1] & 0xFF);
        assertEquals(WearableProtocol.VERSION, request[2] & 0xFF);
        assertEquals(WearableProtocol.TYPE_TIME_SYNC_REQUEST, request[3] & 0xFF);
        assertEquals(123_456_789L, ByteBuffer.wrap(request, 22, 8)
                .order(ByteOrder.LITTLE_ENDIAN).getLong());
    }

    private DeviceClockSynchronizer synchronizedClock() {
        DeviceClockSynchronizer synchronizer = new DeviceClockSynchronizer();
        synchronizer.addExchange(1000L, 999_999_000L, 1_000_001_000L);
        synchronizer.addExchange(2000L, 1_000_999_000L, 1_001_001_000L);
        return synchronizer;
    }

    private WearableProcessingBridge.Listener bridgeListener(List<ProcessedSignalWindow> windows) {
        return new WearableProcessingBridge.Listener() {
            @Override public void onProcessedWindow(ProcessedSignalWindow window) {
                windows.add(window);
            }

            @Override public void onRejectedBatch(MeasurementSource source, String reason) {
                throw new AssertionError(source + ": " + reason);
            }
        };
    }

    private byte[] statusPayload(int flags, int accelInit, int semgInit,
                                 float accelScale, int accelPeriod,
                                 float semgScale, float semgRate,
                                 String accelConfig, String semgConfig, String semgInputUnit,
                                 String semgAfe, String deviceId, String firmware) {
        byte[] strings = concatStrings(accelConfig, semgConfig, semgInputUnit, semgAfe, deviceId, firmware);
        ByteBuffer payload = ByteBuffer.allocate(20 + strings.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.put((byte) flags).put((byte) WearableProtocol.UNIT_METERS_PER_SECOND_SQUARED)
                .putFloat(accelScale).putInt(accelPeriod).putFloat(semgScale).putFloat(semgRate)
                .put((byte) accelInit).put((byte) semgInit).put(strings);
        return payload.array();
    }

    private byte[] concatStrings(String... values) {
        int size = 0;
        for (String value : values) size += 1 + value.getBytes(StandardCharsets.US_ASCII).length;
        ByteBuffer result = ByteBuffer.allocate(size);
        for (String value : values) {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            result.put((byte) bytes.length).put(bytes);
        }
        return result.array();
    }

    private byte[] frame(int type, long sessionId, long sequence, long timestampMicros, byte[] payload) {
        return WearablePacketEncoder.encode(type, sessionId, sequence, timestampMicros, payload);
    }

    private void updateChecksum(byte[] packet) {
        int crc = WearableProtocol.crc16Ccitt(packet, 0, packet.length - 2);
        packet[packet.length - 2] = (byte) crc;
        packet[packet.length - 1] = (byte) (crc >>> 8);
    }

    private void expectInvalid(byte[] bytes) {
        try {
            decoder.decode(bytes);
            throw new AssertionError("Malformed packet should have been rejected.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
