#pragma once

#include "speechbridge/Types.hpp"

#include <optional>

namespace speechbridge {

template <typename Sample>
struct ReadResult {
    std::optional<Sample> sample;
    ErrorCode error = ErrorCode::None;

    static ReadResult success(const Sample& value) {
        return {value, ErrorCode::None};
    }

    static ReadResult failure(ErrorCode code) {
        return {std::nullopt, code};
    }
};

class AccelerometerAcquisition {
public:
    virtual ~AccelerometerAcquisition() = default;
    virtual SensorStatus initialize(const SensorConfiguration& configuration) = 0;
    virtual ReadResult<AccelerometerSample> read() = 0;
    virtual SensorStatus status() const = 0;
};

class SemgAcquisition {
public:
    virtual ~SemgAcquisition() = default;
    virtual SensorStatus initialize(const SemgConfiguration& configuration) = 0;
    virtual ReadResult<SemgSample> read() = 0;
    virtual SensorStatus status() const = 0;
};

}  // namespace speechbridge
