#include "speechbridge/FirmwareApplication.hpp"

namespace speechbridge {

int firmwareMain(FirmwarePlatform& platform) {
    auto& deviceStatus = platform.deviceStatus();
    if (platform.accelerometer() != nullptr && deviceStatus.accelerometerConfiguration.valid()) {
        deviceStatus.accelerometer =
                platform.accelerometer()->initialize(deviceStatus.accelerometerConfiguration);
    } else {
        deviceStatus.accelerometer = {SensorState::NotConfigured, ErrorCode::NotConfigured, ""};
    }
    if (platform.semg() != nullptr && deviceStatus.semgConfiguration.valid()) {
        deviceStatus.semg = platform.semg()->initialize(deviceStatus.semgConfiguration);
    } else {
        deviceStatus.semg = {SensorState::NotConfigured, ErrorCode::NotConfigured, ""};
    }
    platform.markStatusChanged();

    const auto sendError = [&platform](ErrorCode error, std::uint8_t source, const char* detail) {
        platform.reportError(error, detail);
        if (platform.ble().connected()) {
            const auto packet = PacketCodec::errorPacket(platform.session().sessionId(),
                    platform.session().nextPacketSequence(), platform.clock().micros(),
                    error, source, detail);
            platform.ble().publish(packet);
        }
    };
    const auto controlHandler = [&platform, &sendError](const std::vector<std::uint8_t>& bytes) {
        std::string error;
        const auto request = PacketCodec::decode(bytes, error);
        if (!request.has_value()) {
            sendError(ErrorCode::InvalidPacket, 0, "Invalid BLE control packet.");
            return;
        }
        if (request->type != kTypeTimeSyncRequest || request->payload.size() != 8) {
            sendError(ErrorCode::UnsupportedProtocol, 0, "Unsupported BLE control message.");
            return;
        }
        const Packet response = PacketCodec::timeSyncResponse(request.value(),
                platform.session().sessionId(), platform.clock().micros(),
                platform.session().nextPacketSequence());
        platform.ble().publish(response);
    };
    const auto linkHandler = [&sendError](BleLinkState linkState) {
        if (linkState == BleLinkState::Error) {
            sendError(ErrorCode::ReadFailed, 0, "BLE peripheral connection error.");
        }
    };
    if (!platform.ble().start(deviceStatus.deviceId, controlHandler, linkHandler)) {
        sendError(ErrorCode::NotConfigured, 0,
                  "BLE service start failed.");
        return -1;
    }
    bool statusSent = false;
    bool wasConnected = false;
    while (platform.waitForAcquisitionCycle()) {
        const bool connected = platform.ble().connected();
        if (!connected) {
            wasConnected = false;
            continue;
        }
        if (!wasConnected || !statusSent || platform.statusChanged()) {
            const auto statusPacket = PacketCodec::statusPacket(platform.session().sessionId(),
                    platform.session().nextPacketSequence(), platform.clock().micros(),
                    deviceStatus);
            platform.ble().publish(statusPacket);
            statusSent = true;
        }
        wasConnected = true;
        const auto sessionId = platform.session().sessionId();

        if (platform.accelerometer() != nullptr
                && deviceStatus.accelerometerAvailable()) {
            std::vector<AccelerometerSample> samples;
            samples.reserve(12);
            for (std::size_t i = 0; i < 12; ++i) {
                const auto sampleSequence = platform.session().nextAccelerometerSequence();
                auto result = platform.accelerometer()->read();
                if (!result.sample.has_value()) {
                    const auto error = result.error == ErrorCode::None
                            ? ErrorCode::MissingSamples : result.error;
                    sendError(error, 1, "Accelerometer read failed; sample omitted.");
                    deviceStatus.accelerometer = {SensorState::ReadFailed, error,
                            deviceStatus.accelerometerConfiguration.configurationId};
                    platform.markStatusChanged();
                    break;
                }
                auto sample = result.sample.value();
                sample.sequence = sampleSequence;
                samples.push_back(sample);
            }
            if (!samples.empty()) {
                try {
                    platform.ble().publish(PacketCodec::accelerometerBatch(sessionId,
                            platform.session().nextPacketSequence(),
                            deviceStatus.accelerometerConfiguration, samples));
                } catch (const std::invalid_argument&) {
                    sendError(ErrorCode::InvalidSample, 1,
                            "Invalid accelerometer batch; samples omitted.");
                }
            }
        }

        if (platform.semg() != nullptr && platform.deviceStatus().semgAvailable()) {
            std::vector<SemgSample> samples;
            samples.reserve(36);
            for (std::size_t i = 0; i < 36; ++i) {
                const auto sampleSequence = platform.session().nextSemgSequence();
                auto result = platform.semg()->read();
                if (!result.sample.has_value()) {
                    const auto error = result.error == ErrorCode::None
                            ? ErrorCode::MissingSamples : result.error;
                    sendError(error, 2, "sEMG read failed; sample omitted.");
                    deviceStatus.semg = {SensorState::ReadFailed, error,
                            deviceStatus.semgConfiguration.configurationId};
                    platform.markStatusChanged();
                    break;
                }
                auto sample = result.sample.value();
                sample.sequence = sampleSequence;
                samples.push_back(sample);
            }
            if (!samples.empty()) {
                try {
                    platform.ble().publish(PacketCodec::semgBatch(sessionId,
                            platform.session().nextPacketSequence(),
                            deviceStatus.semgConfiguration, samples));
                } catch (const std::invalid_argument&) {
                    sendError(ErrorCode::InvalidSample, 2,
                            "Invalid sEMG batch; samples omitted.");
                }
            }
        }
    }
    platform.ble().stop();
    return 0;
}

}  // namespace speechbridge
