#include "speechbridge/PacketCodec.hpp"

#include <cmath>
#include <cstring>
#include <limits>
#include <stdexcept>

namespace speechbridge {
namespace {

constexpr std::size_t kHeaderBytes = 22;
constexpr std::size_t kCrcBytes = 2;
constexpr std::size_t kMaximumAccelerometerSamples = 12;
constexpr std::size_t kMaximumSemgSamples = 36;
constexpr std::uint8_t kMagicFirst = 0x53;
constexpr std::uint8_t kMagicSecond = 0x42;

void appendU8(std::vector<std::uint8_t>& output, std::uint8_t value) {
    output.push_back(value);
}

void appendU16(std::vector<std::uint8_t>& output, std::uint16_t value) {
    output.push_back(static_cast<std::uint8_t>(value & 0xFF));
    output.push_back(static_cast<std::uint8_t>((value >> 8) & 0xFF));
}

void appendU32(std::vector<std::uint8_t>& output, std::uint32_t value) {
    for (unsigned shift = 0; shift < 32; shift += 8) {
        output.push_back(static_cast<std::uint8_t>((value >> shift) & 0xFF));
    }
}

void appendI32(std::vector<std::uint8_t>& output, std::int32_t value) {
    appendU32(output, static_cast<std::uint32_t>(value));
}

void appendU64(std::vector<std::uint8_t>& output, std::uint64_t value) {
    for (unsigned shift = 0; shift < 64; shift += 8) {
        output.push_back(static_cast<std::uint8_t>((value >> shift) & 0xFF));
    }
}

void appendFloat(std::vector<std::uint8_t>& output, float value) {
    std::uint32_t bits = 0;
    static_assert(sizeof(bits) == sizeof(value), "Protocol requires 32-bit float.");
    std::memcpy(&bits, &value, sizeof(bits));
    appendU32(output, bits);
}

void appendString(std::vector<std::uint8_t>& output, const std::string& value) {
    if (value.size() > 48) throw std::invalid_argument("Protocol strings are limited to 48 bytes.");
    for (unsigned char character : value) {
        if (character < 0x20 || character > 0x7E) {
            throw std::invalid_argument("Protocol strings must be printable ASCII.");
        }
    }
    appendU8(output, static_cast<std::uint8_t>(value.size()));
    output.insert(output.end(), value.begin(), value.end());
}

std::uint64_t readUnsigned(const std::vector<std::uint8_t>& input,
                          std::size_t& offset, std::size_t size) {
    if (size > 8 || input.size() < offset + size) throw std::invalid_argument("Truncated packet.");
    std::uint64_t value = 0;
    for (std::size_t i = 0; i < size; ++i) {
        value |= static_cast<std::uint64_t>(input[offset++]) << (8 * i);
    }
    return value;
}

void validateSamples(const std::vector<AccelerometerSample>& samples, std::uint32_t period) {
    if (samples.empty() || samples.size() > kMaximumAccelerometerSamples || period == 0) {
        throw std::invalid_argument("Accelerometer packet needs 1..12 configured samples.");
    }
    for (std::size_t i = 1; i < samples.size(); ++i) {
        if (samples[i].sequence != samples[i - 1].sequence + 1U
                || samples[i].deviceTimestampMicros <= samples[i - 1].deviceTimestampMicros
                || samples[i].deviceTimestampMicros - samples[i - 1].deviceTimestampMicros != period) {
            throw std::invalid_argument("Accelerometer samples contain a sequence gap or invalid timestamp.");
        }
    }
}

void validateSamples(const std::vector<SemgSample>& samples, std::uint32_t period) {
    if (samples.empty() || samples.size() > kMaximumSemgSamples || period == 0) {
        throw std::invalid_argument("sEMG packet needs 1..36 configured samples.");
    }
    for (std::size_t i = 1; i < samples.size(); ++i) {
        if (samples[i].sequence != samples[i - 1].sequence + 1U
                || samples[i].deviceTimestampMicros <= samples[i - 1].deviceTimestampMicros
                || samples[i].deviceTimestampMicros - samples[i - 1].deviceTimestampMicros != period) {
            throw std::invalid_argument("sEMG samples contain a sequence gap or invalid timestamp.");
        }
    }
}

}  // namespace

bool SensorConfiguration::valid() const {
    return !configurationId.empty() && samplePeriodMicros > 0
            && std::isfinite(rawScaleToSi) && rawScaleToSi > 0.0;
}

bool SemgConfiguration::valid() const {
    return !configurationId.empty() && !analogFrontEndId.empty() && samplePeriodMicros > 0
            && std::isfinite(rawScaleToInputUnits) && rawScaleToInputUnits > 0.0
            && !inputUnit.empty() && batteryPowered && protectedInput
            && externalSafetyReviewComplete;
}

bool DeviceStatus::accelerometerAvailable() const {
    return accelerometer.state == SensorState::Available
            && accelerometer.error == ErrorCode::None && accelerometerConfiguration.valid();
}

bool DeviceStatus::semgAvailable() const {
    return semg.state == SensorState::Available
            && semg.error == ErrorCode::None && semgConfiguration.valid();
}

std::uint16_t PacketCodec::crc16Ccitt(const std::uint8_t* data, std::size_t length) {
    std::uint16_t crc = 0xFFFF;
    for (std::size_t i = 0; i < length; ++i) {
        crc ^= static_cast<std::uint16_t>(data[i]) << 8;
        for (int bit = 0; bit < 8; ++bit) {
            crc = (crc & 0x8000) != 0
                    ? static_cast<std::uint16_t>((crc << 1) ^ 0x1021)
                    : static_cast<std::uint16_t>(crc << 1);
        }
    }
    return crc;
}

std::vector<std::uint8_t> PacketCodec::encode(const Packet& packet) {
    if (packet.version != kProtocolVersion
            || (packet.type != kTypeStatus && packet.type != kTypeError
                && packet.type != kTypeAccelerometerBatch
                && packet.type != kTypeSemgBatch && packet.type != kTypeTimeSyncRequest
                && packet.type != kTypeTimeSyncResponse)
            || packet.payload.size() > std::numeric_limits<std::uint16_t>::max()
            || kHeaderBytes + packet.payload.size() + kCrcBytes > kMaximumPacketBytes) {
        throw std::invalid_argument("Packet fields exceed protocol limits or use an unsupported version/type.");
    }
    std::vector<std::uint8_t> output;
    output.reserve(kHeaderBytes + packet.payload.size() + kCrcBytes);
    appendU8(output, kMagicFirst);
    appendU8(output, kMagicSecond);
    appendU8(output, packet.version);
    appendU8(output, packet.type);
    appendU32(output, packet.sessionId);
    appendU32(output, packet.sequence);
    appendU64(output, packet.deviceTimestampMicros);
    appendU16(output, static_cast<std::uint16_t>(packet.payload.size()));
    output.insert(output.end(), packet.payload.begin(), packet.payload.end());
    const std::uint16_t crc = crc16Ccitt(output.data(), output.size());
    appendU16(output, crc);
    return output;
}

std::optional<Packet> PacketCodec::decode(const std::vector<std::uint8_t>& bytes,
                                         std::string& error) {
    error.clear();
    if (bytes.size() < kHeaderBytes + kCrcBytes || bytes.size() > kMaximumPacketBytes) {
        error = "packet length is outside protocol bounds";
        return std::nullopt;
    }
    if (bytes[0] != kMagicFirst || bytes[1] != kMagicSecond) {
        error = "packet magic is invalid";
        return std::nullopt;
    }
    const std::uint16_t expectedCrc = static_cast<std::uint16_t>(bytes[bytes.size() - 2])
            | static_cast<std::uint16_t>(bytes[bytes.size() - 1] << 8);
    if (crc16Ccitt(bytes.data(), bytes.size() - kCrcBytes) != expectedCrc) {
        error = "packet checksum is invalid";
        return std::nullopt;
    }
    std::size_t offset = 2;
    Packet packet;
    packet.version = static_cast<std::uint8_t>(readUnsigned(bytes, offset, 1));
    packet.type = static_cast<std::uint8_t>(readUnsigned(bytes, offset, 1));
    packet.sessionId = static_cast<std::uint32_t>(readUnsigned(bytes, offset, 4));
    packet.sequence = static_cast<std::uint32_t>(readUnsigned(bytes, offset, 4));
    packet.deviceTimestampMicros = readUnsigned(bytes, offset, 8);
    const auto payloadLength = static_cast<std::size_t>(readUnsigned(bytes, offset, 2));
    if (packet.version != kProtocolVersion) {
        error = "protocol version is unsupported";
        return std::nullopt;
    }
    if (packet.type != kTypeStatus && packet.type != kTypeError
            && packet.type != kTypeAccelerometerBatch
            && packet.type != kTypeSemgBatch && packet.type != kTypeTimeSyncRequest
            && packet.type != kTypeTimeSyncResponse) {
        error = "packet type is unsupported";
        return std::nullopt;
    }
    if (payloadLength != bytes.size() - kHeaderBytes - kCrcBytes) {
        error = "payload length does not match packet size";
        return std::nullopt;
    }
    packet.payload.assign(bytes.begin() + static_cast<std::ptrdiff_t>(offset),
                          bytes.end() - static_cast<std::ptrdiff_t>(kCrcBytes));
    return packet;
}

Packet PacketCodec::statusPacket(std::uint32_t sessionId, std::uint32_t sequence,
                                std::uint64_t timestampMicros, const DeviceStatus& status) {
    std::vector<std::uint8_t> payload;
    std::uint8_t flags = static_cast<std::uint8_t>(status.accelerometerAvailable() ? 0x01 : 0x00);
    if (status.semgAvailable()) flags |= 0x02;
    if (status.semgConfiguration.externalSafetyReviewComplete) flags |= 0x04;
    if (status.semgConfiguration.batteryPowered) flags |= 0x08;
    if (status.semgConfiguration.protectedInput) flags |= 0x10;
    appendU8(payload, flags);
    appendU8(payload, kSiAccelerationUnit);
    appendFloat(payload, static_cast<float>(status.accelerometerConfiguration.rawScaleToSi));
    appendU32(payload, status.accelerometerConfiguration.samplePeriodMicros);
    appendFloat(payload, static_cast<float>(status.semgConfiguration.rawScaleToInputUnits));
    const float semgRate = status.semgConfiguration.samplePeriodMicros == 0 ? 0.0F
            : 1000000.0F / status.semgConfiguration.samplePeriodMicros;
    appendFloat(payload, semgRate);
    appendU8(payload, static_cast<std::uint8_t>(status.accelerometer.error));
    appendU8(payload, static_cast<std::uint8_t>(status.semg.error));
    appendString(payload, status.accelerometerConfiguration.configurationId);
    appendString(payload, status.semgConfiguration.configurationId);
    appendString(payload, status.semgConfiguration.inputUnit);
    appendString(payload, status.semgConfiguration.analogFrontEndId);
    appendString(payload, status.deviceId);
    appendString(payload, status.firmwareVersion);
    return {kProtocolVersion, kTypeStatus, sessionId, sequence, timestampMicros, payload};
}

Packet PacketCodec::errorPacket(std::uint32_t sessionId, std::uint32_t sequence,
                               std::uint64_t timestampMicros, ErrorCode error,
                               std::uint8_t source, const std::string& message) {
    std::vector<std::uint8_t> payload;
    appendU8(payload, static_cast<std::uint8_t>(error));
    appendU8(payload, source);
    appendString(payload, message);
    return {kProtocolVersion, kTypeError, sessionId, sequence, timestampMicros, payload};
}

Packet PacketCodec::accelerometerBatch(std::uint32_t sessionId, std::uint32_t packetSequence,
                                       const SensorConfiguration& configuration,
                                       const std::vector<AccelerometerSample>& samples) {
    if (!configuration.valid()) throw std::invalid_argument("Accelerometer configuration is unavailable.");
    validateSamples(samples, configuration.samplePeriodMicros);
    std::vector<std::uint8_t> payload;
    appendU8(payload, static_cast<std::uint8_t>(samples.size()));
    appendU32(payload, configuration.samplePeriodMicros);
    appendU32(payload, samples.front().sequence);
    appendU8(payload, kSiAccelerationUnit);
    appendFloat(payload, static_cast<float>(configuration.rawScaleToSi));
    for (const auto& sample : samples) {
        appendI32(payload, sample.xRaw);
        appendI32(payload, sample.yRaw);
        appendI32(payload, sample.zRaw);
    }
    return {kProtocolVersion, kTypeAccelerometerBatch, sessionId, packetSequence,
            samples.front().deviceTimestampMicros, payload};
}

Packet PacketCodec::semgBatch(std::uint32_t sessionId, std::uint32_t packetSequence,
                              const SemgConfiguration& configuration,
                              const std::vector<SemgSample>& samples) {
    if (!configuration.valid()) throw std::invalid_argument("sEMG configuration is unavailable or not safety-reviewed.");
    validateSamples(samples, configuration.samplePeriodMicros);
    std::vector<std::uint8_t> payload;
    appendU8(payload, static_cast<std::uint8_t>(samples.size()));
    appendU32(payload, configuration.samplePeriodMicros);
    appendU32(payload, samples.front().sequence);
    appendFloat(payload, static_cast<float>(configuration.rawScaleToInputUnits));
    for (const auto& sample : samples) appendI32(payload, sample.rawValue);
    return {kProtocolVersion, kTypeSemgBatch, sessionId, packetSequence,
            samples.front().deviceTimestampMicros, payload};
}

Packet PacketCodec::timeSyncResponse(const Packet& request, std::uint32_t responseSessionId,
                                    std::uint64_t responseDeviceMicros,
                                    std::uint32_t responseSequence) {
    if (request.type != kTypeTimeSyncRequest || request.payload.size() != 8) {
        throw std::invalid_argument("Time synchronization request is malformed.");
    }
    Packet response{kProtocolVersion, kTypeTimeSyncResponse, responseSessionId,
                    responseSequence, responseDeviceMicros, {}};
    response.payload = request.payload;
    return response;
}

}  // namespace speechbridge
