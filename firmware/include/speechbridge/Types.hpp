#pragma once

#include <cstdint>
#include <string>

namespace speechbridge {

enum class SensorState : std::uint8_t {
    NotConfigured = 0,
    Initializing = 1,
    Available = 2,
    Unavailable = 3,
    InitializationFailed = 4,
    ReadFailed = 5
};

enum class ErrorCode : std::uint8_t {
    None = 0,
    NotConfigured = 1,
    InitializationFailed = 2,
    ReadFailed = 3,
    InvalidSample = 4,
    MissingSamples = 5,
    UnsupportedProtocol = 6,
    InvalidPacket = 7
};

struct AccelerometerSample {
    std::uint32_t sequence;
    std::uint64_t deviceTimestampMicros;
    std::int32_t xRaw;
    std::int32_t yRaw;
    std::int32_t zRaw;
};

struct SemgSample {
    std::uint32_t sequence;
    std::uint64_t deviceTimestampMicros;
    std::int32_t rawValue;
};

struct SensorConfiguration {
    std::string configurationId;
    std::uint32_t samplePeriodMicros = 0;
    double rawScaleToSi = 0.0;

    bool valid() const;
};

struct SemgConfiguration {
    std::string configurationId;
    std::string analogFrontEndId;
    std::uint32_t samplePeriodMicros = 0;
    double rawScaleToInputUnits = 0.0;
    std::string inputUnit;
    bool batteryPowered = false;
    bool protectedInput = false;
    bool externalSafetyReviewComplete = false;

    bool valid() const;
};

struct SensorStatus {
    SensorState state = SensorState::NotConfigured;
    ErrorCode error = ErrorCode::None;
    std::string configurationId;
};

}  // namespace speechbridge
