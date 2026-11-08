#include "speechbridge/DeviceClock.hpp"

namespace speechbridge {

SessionSequence::SessionSequence(std::uint32_t sessionId) : sessionId_(sessionId) {}

std::uint32_t SessionSequence::sessionId() const {
    return sessionId_;
}

std::uint32_t SessionSequence::nextPacketSequence() {
    return packetSequence_++;
}

std::uint32_t SessionSequence::nextAccelerometerSequence() {
    return accelerometerSequence_++;
}

std::uint32_t SessionSequence::nextSemgSequence() {
    return semgSequence_++;
}

void SessionSequence::reset(std::uint32_t newSessionId) {
    sessionId_ = newSessionId;
    packetSequence_ = 0;
    accelerometerSequence_ = 0;
    semgSequence_ = 0;
}

}  // namespace speechbridge
