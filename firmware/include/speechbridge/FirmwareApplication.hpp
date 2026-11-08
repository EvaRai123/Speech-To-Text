#pragma once

#include "speechbridge/AccelerometerAcquisition.hpp"
#include "speechbridge/BleService.hpp"
#include "speechbridge/DeviceClock.hpp"
#include "speechbridge/SemgAcquisition.hpp"

#include <vector>

namespace speechbridge {

class FirmwarePlatform {
public:
    virtual ~FirmwarePlatform() = default;
    virtual MonotonicClock& clock() = 0;
    virtual SessionSequence& session() = 0;
    virtual BleService& ble() = 0;
    virtual AccelerometerAcquisition* accelerometer() = 0;
    virtual SemgAcquisition* semg() = 0;
    virtual DeviceStatus& deviceStatus() = 0;
    virtual bool statusChanged() = 0;
    virtual void markStatusChanged() = 0;
    virtual bool waitForAcquisitionCycle() = 0;
    virtual void reportError(ErrorCode error, const char* detail) = 0;
};

int firmwareMain(FirmwarePlatform& platform);

}  // namespace speechbridge
