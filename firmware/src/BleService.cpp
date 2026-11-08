#include "speechbridge/BleService.hpp"

namespace speechbridge {

BleService::BleService(BlePeripheral& peripheral) : peripheral_(peripheral) {}

bool BleService::start(const std::string& deviceId,
                       BlePeripheral::ControlHandler controlHandler,
                       BlePeripheral::LinkHandler linkHandler) {
    if (deviceId.empty()) return false;
    return peripheral_.start(deviceId, std::move(controlHandler), std::move(linkHandler));
}

bool BleService::publish(const Packet& packet) {
    if (!peripheral_.connected()) return false;
    std::vector<std::uint8_t> encoded;
    try {
        encoded = PacketCodec::encode(packet);
    } catch (const std::invalid_argument&) {
        return false;
    }
    if (encoded.size() > peripheral_.maximumNotificationBytes()) return false;
    return peripheral_.notify(encoded);
}

void BleService::stop() {
    peripheral_.stop();
}

bool BleService::connected() const {
    return peripheral_.connected();
}

}  // namespace speechbridge
