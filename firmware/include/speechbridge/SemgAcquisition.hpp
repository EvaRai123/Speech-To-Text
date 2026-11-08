#pragma once

#include "speechbridge/SensorAcquisition.hpp"

namespace speechbridge {

class ExternalBiopotentialFrontEnd : public SemgAcquisition {
public:
    ~ExternalBiopotentialFrontEnd() override = default;

    // This interface accepts digitized samples from a separately selected and safety-reviewed AFE.
};

}  // namespace speechbridge
