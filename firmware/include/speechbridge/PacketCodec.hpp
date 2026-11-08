#pragma once

#include "speechbridge/DeviceStatus.hpp"
#include "speechbridge/Types.hpp"

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace speechbridge {

constexpr std::uint8_t kProtocolVersion = 1;
constexpr std::size_t kMaximumPacketBytes = 182;
constexpr std::uint8_t kTypeStatus = 0x01;
constexpr std::uint8_t kTypeError = 0x02;
constexpr std::uint8_t kTypeAccelerometerBatch = 0x10;
constexpr std::uint8_t kTypeSemgBatch = 0x11;
constexpr std::uint8_t kTypeTimeSyncRequest = 0x20;
constexpr std::uint8_t kTypeTimeSyncResponse = 0x21;
constexpr std::uint8_t kSiAccelerationUnit = 1;

struct Packet {
    std::uint8_t version = kProtocolVersion;
    std::uint8_t type = 0;
    std::uint32_t sessionId = 0;
    std::uint32_t sequence = 0;
    std::uint64_t deviceTimestampMicros = 0;
    std::vector<std::uint8_t> payload;
};

class PacketCodec {
public:
    static std::vector<std::uint8_t> encode(const Packet& packet);
    static std::optional<Packet> decode(const std::vector<std::uint8_t>& bytes,
                                        std::string& error);
    static Packet statusPacket(std::uint32_t sessionId, std::uint32_t sequence,
                               std::uint64_t timestampMicros, const DeviceStatus& status);
    static Packet errorPacket(std::uint32_t sessionId, std::uint32_t sequence,
                              std::uint64_t timestampMicros, ErrorCode error,
                              std::uint8_t source, const std::string& message);
    static Packet accelerometerBatch(std::uint32_t sessionId, std::uint32_t packetSequence,
                                     const SensorConfiguration& configuration,
                                     const std::vector<AccelerometerSample>& samples);
    static Packet semgBatch(std::uint32_t sessionId, std::uint32_t packetSequence,
                            const SemgConfiguration& configuration,
                            const std::vector<SemgSample>& samples);
    static Packet timeSyncResponse(const Packet& request, std::uint32_t responseSessionId,
                                   std::uint64_t responseDeviceMicros,
                                   std::uint32_t responseSequence);
    static std::uint16_t crc16Ccitt(const std::uint8_t* data, std::size_t length);
};

}  // namespace speechbridge
