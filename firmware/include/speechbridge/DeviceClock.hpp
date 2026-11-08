#pragma once

#include <cstdint>

namespace speechbridge {

class MonotonicClock {
public:
    virtual ~MonotonicClock() = default;
    virtual std::uint64_t micros() const = 0;
};

class SessionSequence {
public:
    explicit SessionSequence(std::uint32_t sessionId);
    std::uint32_t sessionId() const;
    std::uint32_t nextPacketSequence();
    std::uint32_t nextAccelerometerSequence();
    std::uint32_t nextSemgSequence();
    void reset(std::uint32_t newSessionId);

private:
    std::uint32_t sessionId_;
    std::uint32_t packetSequence_ = 0;
    std::uint32_t accelerometerSequence_ = 0;
    std::uint32_t semgSequence_ = 0;
};

}  // namespace speechbridge
