#pragma once

#include "speechbridge/Types.hpp"

namespace speechbridge {

struct DeviceStatus {
    std::string deviceId;
    std::string firmwareVersion;
    SensorStatus accelerometer;
    SensorStatus semg;
    SensorConfiguration accelerometerConfiguration;
    SemgConfiguration semgConfiguration;

    bool accelerometerAvailable() const;
    bool semgAvailable() const;
};

}  // namespace speechbridge
