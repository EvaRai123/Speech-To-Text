package com.example.speechpractice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WearablePacketEncoder {
    private static final int HEADER_BYTES = 22;

    private WearablePacketEncoder() {
    }

    public static byte[] timeSyncRequest(long androidMonotonicNanos, long sequence) {
        if (androidMonotonicNanos < 0L || sequence < 0L || sequence > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Time synchronization request fields are invalid.");
        }
        ByteBuffer payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        payload.putLong(androidMonotonicNanos);
        return encode(WearableProtocol.TYPE_TIME_SYNC_REQUEST, 0L, sequence, 0L, payload.array());
    }

    static byte[] encode(int type, long sessionId, long sequence,
                         long timestampMicros, byte[] payload) {
        if (type < 0 || type > 255 || sessionId < 0L || sessionId > 0xFFFFFFFFL
                || sequence < 0L || sequence > 0xFFFFFFFFL || timestampMicros < 0L
                || payload == null || HEADER_BYTES + payload.length + 2 > WearableProtocol.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Packet fields exceed protocol limits.");
        }
        ByteBuffer packet = ByteBuffer.allocate(HEADER_BYTES + payload.length + 2)
                .order(ByteOrder.LITTLE_ENDIAN);
        packet.put((byte) WearableProtocol.MAGIC_FIRST);
        packet.put((byte) WearableProtocol.MAGIC_SECOND);
        packet.put((byte) WearableProtocol.VERSION);
        packet.put((byte) type);
        packet.putInt((int) sessionId);
        packet.putInt((int) sequence);
        packet.putLong(timestampMicros);
        packet.putShort((short) payload.length);
        packet.put(payload);
        int crc = WearableProtocol.crc16Ccitt(packet.array(), 0, packet.position());
        packet.put((byte) (crc & 0xFF));
        packet.put((byte) ((crc >>> 8) & 0xFF));
        return packet.array();
    }
}
