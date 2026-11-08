#include "speechbridge/PacketCodec.hpp"

#include <cassert>
#include <stdexcept>
#include <string>
#include <vector>

using namespace speechbridge;

int main() {
    Packet original{kProtocolVersion, kTypeTimeSyncRequest, 7, 9, 0,
                    {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}};
    const auto encoded = PacketCodec::encode(original);
    std::string error;
    const auto decoded = PacketCodec::decode(encoded, error);
    assert(decoded.has_value());
    assert(decoded->sessionId == 7);
    assert(decoded->sequence == 9);
    assert(decoded->payload == original.payload);

    auto corrupted = encoded;
    corrupted[24] ^= 0x01;
    assert(!PacketCodec::decode(corrupted, error).has_value());
    assert(error == "packet checksum is invalid");

    SensorConfiguration accelerometerConfig{"fixture-accel-config", 10000, 0.001};
    std::vector<AccelerometerSample> acceleration{
            {0, 1000000, 100, -100, 20},
            {1, 1010000, 110, -90, 30}};
    const auto accelPacket = PacketCodec::accelerometerBatch(
            7, 10, accelerometerConfig, acceleration);
    assert(accelPacket.type == kTypeAccelerometerBatch);
    assert(accelPacket.deviceTimestampMicros == 1000000);

    acceleration[1].sequence = 2;
    bool gapRejected = false;
    try {
        PacketCodec::accelerometerBatch(7, 10, accelerometerConfig, acceleration);
    } catch (const std::invalid_argument&) {
        gapRejected = true;
    }
    assert(gapRejected);

    SemgConfiguration semgConfig{"afe-config", "afe-part-id", 1000, 0.25,
                                 "adc-count", true, true, true};
    std::vector<SemgSample> semg{{0, 2000000, 13}, {1, 2001000, 17}};
    assert(PacketCodec::semgBatch(7, 11, semgConfig, semg).type == kTypeSemgBatch);
    semgConfig.protectedInput = false;
    bool unsafeConfigurationRejected = false;
    try {
        PacketCodec::semgBatch(7, 11, semgConfig, semg);
    } catch (const std::invalid_argument&) {
        unsafeConfigurationRejected = true;
    }
    assert(unsafeConfigurationRejected);

    const auto syncResponse = PacketCodec::timeSyncResponse(original, 7, 3000000, 12);
    assert(syncResponse.type == kTypeTimeSyncResponse);
    assert(syncResponse.sessionId == 7);
    assert(syncResponse.payload == original.payload);
    return 0;
}
