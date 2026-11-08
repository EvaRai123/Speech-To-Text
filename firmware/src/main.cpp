#include "speechbridge/FirmwareApplication.hpp"

namespace speechbridge {

// The selected board package must provide the platform adapter and invoke this entry.
int applicationEntry(FirmwarePlatform& platform) {
    return firmwareMain(platform);
}

}  // namespace speechbridge
