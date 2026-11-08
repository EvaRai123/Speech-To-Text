#pragma once

#include "speechbridge/SensorAcquisition.hpp"

namespace speechbridge {

class ThreeAxisAccelerometerDriver : public AccelerometerAcquisition {
public:
    ~ThreeAxisAccelerometerDriver() override = default;

    // Board-specific bus and sensor drivers implement these methods after a part is selected.
};

}  // namespace speechbridge
