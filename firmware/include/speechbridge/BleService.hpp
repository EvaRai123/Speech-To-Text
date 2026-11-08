#pragma once

#include "speechbridge/PacketCodec.hpp"

#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace speechbridge {

constexpr const char* kServiceUuid = "7b6e0001-8f2a-4a2d-9c31-535045454348";
constexpr const char* kDataCharacteristicUuid = "7b6e0002-8f2a-4a2d-9c31-535045454348";
constexpr const char* kControlCharacteristicUuid = "7b6e0003-8f2a-4a2d-9c31-535045454348";

enum class BleLinkState {
    Stopped,
    Advertising,
    Connected,
    Disconnected,
    Error
};

class BlePeripheral {
public:
    using ControlHandler = std::function<void(const std::vector<std::uint8_t>&)>;
    using LinkHandler = std::function<void(BleLinkState)>;
    virtual ~BlePeripheral() = default;

    virtual bool start(const std::string& deviceId, ControlHandler controlHandler,
                       LinkHandler linkHandler) = 0;
    virtual void stop() = 0;
    virtual bool connected() const = 0;
    virtual std::size_t maximumNotificationBytes() const = 0;
    virtual bool notify(const std::vector<std::uint8_t>& encodedPacket) = 0;
};

class BleService {
public:
    explicit BleService(BlePeripheral& peripheral);
    bool start(const std::string& deviceId, BlePeripheral::ControlHandler controlHandler,
               BlePeripheral::LinkHandler linkHandler);
    bool publish(const Packet& packet);
    void stop();
    bool connected() const;

private:
    BlePeripheral& peripheral_;
};

}  // namespace speechbridge
