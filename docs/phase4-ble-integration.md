# SpeechBridge Phase 4 wearable communication foundation

## Status and scope

This phase adds a versioned BLE packet contract, hardware-neutral C++ firmware interfaces and packet codec, and an Android BLE GATT client. It does **not** implement a selected-board ESP-IDF adapter, sensor driver, or analog front end. No ESP32-S3, accelerometer, sEMG hardware, electrodes, or wearable connection is claimed to exist. The firmware core is not built into the Android Gradle project.

The Android client scans only after the user taps **Scan for SpeechBridge wearable**. It requests Android's version-appropriate BLE permissions and reports denial explicitly. A GATT connection alone does not make a channel available; the decoded device status must report the channel initialized and provide compatible configuration metadata. Samples are rejected until valid device status and a usable clock mapping exist. Processing settings remain caller supplied; the Research Lab does not invent them. Processed windows are currently shown as session-only output and are not persisted to a lesson attempt.

## Firmware tree and architecture

```text
firmware/
  CMakeLists.txt
  include/speechbridge/
    AccelerometerAcquisition.hpp
    BleService.hpp
    DeviceClock.hpp
    DeviceStatus.hpp
    FirmwareApplication.hpp
    PacketCodec.hpp
    SemgAcquisition.hpp
    SensorAcquisition.hpp
    Types.hpp
  src/
    BleService.cpp
    DeviceClock.cpp
    FirmwareApplication.cpp
    PacketCodec.cpp
    main.cpp
  tests/
    PacketCodecTest.cpp
```

`firmwareMain(FirmwarePlatform&)` is the application entry seam. The selected board package must provide `FirmwarePlatform`, a monotonic clock, a unique session ID, a BLE peripheral adapter, and concrete driver adapters. The sensor interfaces return optional samples plus explicit errors; a failed read is never converted to zero. Sensor state and error codes are published, and channel acquisition stops after initialization/read failure until the platform reinitializes it.

`ThreeAxisAccelerometerDriver` is an interface only; it contains no guessed model, register map, or bus setup. `ExternalBiopotentialFrontEnd` accepts already digitized samples from a separately selected analog front end. Its configuration is rejected unless input scaling, units, sample period, AFE identity, battery-power, protected-input, and external safety-review metadata are explicitly provided. These flags are an integration gate, not evidence that a circuit is safe.

The CMake target builds the portable firmware core and codec test when a C++17 toolchain is installed. It is not a board firmware target and is not included in Android Gradle. No firmware compiler or CMake executable was available in the current environment, so this target was not compiled here.

## BLE service and characteristics

UUIDs are stable protocol identifiers, not hardware identifiers:

| GATT item | UUID | Properties / purpose |
|---|---|---|
| SpeechBridge service | `7b6e0001-8f2a-4a2d-9c31-535045454348` | Advertised service used for scanning |
| Data characteristic | `7b6e0002-8f2a-4a2d-9c31-535045454348` | Notify or indicate complete protocol packets |
| Control characteristic | `7b6e0003-8f2a-4a2d-9c31-535045454348` | Write protocol control packets, currently clock synchronization |
| CCCD | Bluetooth standard `00002902-0000-1000-8000-00805f9b34fb` | Android subscription descriptor |

The firmware BLE stack adapter must handle advertising, GATT connection/disconnection, control writes, reconnects, and notification-size limits. Android retries a lost connection up to three times with increasing delays; after retries stop, the user may start another scan. Device session changes reset Android packet/sample sequence and clock state.

## Protocol v1 wire format

All multibyte integers and IEEE-754 float32 values are **little-endian**. A complete packet is limited to 182 bytes, chosen to fit a negotiated ATT MTU of 185 bytes (MTU minus the 3-byte notification overhead). The Android client requests MTU 185. A board adapter must not split a packet across notifications; if its negotiated notification limit is too small, it must reduce a batch or reject transmission and report the condition.

| Offset | Size | Field |
|---:|---:|---|
| 0 | 2 | Magic bytes `0x53 0x42` (`SB`) |
| 2 | 1 | Protocol version (`1`) |
| 3 | 1 | Packet type |
| 4 | 4 | Device boot/clock session ID, unsigned |
| 8 | 4 | Device packet sequence, unsigned modulo 2^32 |
| 12 | 8 | Device monotonic acquisition timestamp in microseconds |
| 20 | 2 | Payload byte count |
| 22 | variable | Payload |
| final 2 | 2 | CRC-16/CCITT-FALSE over header and payload; polynomial `0x1021`, init `0xFFFF`, little-endian CRC field |

The unsigned 64-bit device timestamp is monotonic microseconds from the platform clock. The selected board adapter must extend any narrower hardware timer into this representation. A device reboot or clock/session reset must issue a new session ID; Android then discards old clock estimates and sequence state. Packet and sample sequence counters wrap modulo 2^32; receivers distinguish forward wrap from duplicate/out-of-order traffic.

### Packet types and payloads

| Type | Payload |
|---:|---|
| `0x01` status | Fixed 20-byte prefix: availability flags (bit 0 accelerometer, bit 1 sEMG, bit 2 external sEMG safety review reported complete, bit 3 sEMG battery-powered, bit 4 sEMG protected-input), accelerometer unit code (`1` = m/s²), float32 raw-to-m/s² scale, uint32 accelerometer sample period in µs, float32 sEMG raw-to-configured-input-unit scale, float32 sEMG rate in Hz, accelerometer init/error code, sEMG init/error code. Then six length-prefixed ASCII strings (one-byte length, max 48 bytes each): accelerometer configuration ID, sEMG configuration ID, sEMG input unit, sEMG AFE ID, device ID, firmware version. Flags are accepted only with successful initialization and valid corresponding metadata. Android requires all three device-reported sEMG safety/configuration bits before marking sEMG available. These are unverified device assertions, not a physical safety certification. |
| `0x02` error | Error code byte, source byte (`0` system, `1` accelerometer, `2` sEMG), then one length-prefixed printable-ASCII message (max 48 bytes). |
| `0x10` accelerometer batch | Count byte (1–12), uint32 sample period µs, uint32 first sample sequence, unit code byte (`1` = m/s²), float32 scale from signed raw int32 counts to m/s², then `count` repetitions of signed int32 X/Y/Z raw axes. Header timestamp is the first sample time. |
| `0x11` sEMG batch | Count byte (1–36), uint32 sample period µs, uint32 first sample sequence, float32 scale from signed raw int32 to the declared configured input unit, then `count` signed int32 samples. Header timestamp is the first sample time. |
| `0x20` time-sync request | Signed-range-checked Android monotonic send time in nanoseconds stored as an int64 payload. Request header session and device timestamp are zero; request sequence is Android-local. |
| `0x21` time-sync response | Echoed 8-byte Android send timestamp; header has current device session, device packet sequence, and device monotonic microsecond response timestamp. |

Status configuration IDs are emitted only when actually known. Empty/uninitialized channels remain unavailable. A missing or invalid read is represented by a gap/error/status change, not by a numeric zero. Invalid CRC, lengths, types, protocol versions, units, scales, counts, timestamps, or configuration metadata are rejected. There is no implicit fallback channel.

The 182-byte ceiling allows 12 three-axis accelerometer samples: 24 bytes frame overhead + 14 batch metadata bytes + 144 sample bytes = 182. At 100 Hz this is up to 120 ms of data per notification (about 8.3 notifications/s). sEMG batches allow up to 36 samples; at 1 kHz that is about 36 ms per notification (about 27.8 notifications/s). These are packet-size examples, not selected sensor sample rates. Actual rates and batch sizes must be chosen from the selected sensors, AFE, BLE throughput, latency needs, and measured loss behavior.

## Android BLE and existing measurement contracts

`WearableBleManager` owns permission checks, user-started scanning, GATT connection/service discovery, CCCD subscription, limited reconnect attempts, packet validation, status, sequence/gap detection, and sync requests. API 31+ requests `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`; API 26–30 uses location permission for BLE scanning. BLE is declared optional for installation. Permission denial and Bluetooth-off states do not crash or pretend data exists.

`WearablePacketDecoder` and `WearablePacketEncoder` implement the packet framing and CRC. `WearableSequenceTracker` covers device packet sequence and session changes; `WearableSampleSequenceTracker` detects per-channel sample gaps and duplicate/out-of-order batches. Gaps are reported; they do not get zero-filled. Device status is separate from connection status.

The listener receives each `WearablePacket` with Android monotonic receipt time separately from the packet's device acquisition time. `DeviceClockSynchronizer` uses the request timestamp and Android send/receipt midpoint to fit an offset and drift estimate over multiple exchanges. Converted sample timestamps are used only after two observations exist. This is a software estimate only; no synchronization accuracy or drift bound has been measured. A new device session invalidates the estimate.

`WearableProcessingBridge` maps validated accelerometer samples into `AccelerometerSample` in m/s² and sEMG samples into `SemgSample` only when device status, source configuration, scale, sampling rate, and caller-supplied processing configuration agree. The existing `AccelerometerProcessor` and `SemgProcessor` produce the existing `ProcessedSignalWindow` and `MeasurementRecord` contracts. sEMG processing configuration must additionally name an input unit matching device status. The app does not set filtering, saturation, noise, electrode, or calibration settings by default. In particular, sEMG remains unavailable until a selected, protected and externally reviewed front end is configured.

The Research Workspace does not currently supply a processing configuration to `WearableBleManager`; therefore it cannot produce processed windows in the shipped UI. If an explicitly configured caller is added later, processed BLE windows still are not attached to lesson attempts or Research Lab CSV export. Device acquisition timestamps, Android monotonic receipt timestamps, and estimated aligned timestamps must remain distinct; BLE arrival time must never be substituted for acquisition time. Phone microphone data is not used as a fallback wearable source. The manager is disconnected when the Research Workspace pauses, so its scan/connection does not continue as background capture. Existing audio/camera acquisition remains unchanged.

Lesson attempt persistence additively records `endedAt` and per-source status/detail fields. Older attempts without these fields continue to load with unavailable end-time/source-status metadata. These additions do not make wearable measurements part of lesson attempts: accelerometer and sEMG are explicitly marked not configured for guided lesson attempts until configuration and provenance can be safely connected.

## Android permissions and dependencies

The manifest adds legacy Bluetooth/admin and fine-location permissions capped at API 30, Android 12+ scan/connect permissions, and an optional BLE hardware feature. No new Gradle dependency was required; the Android platform Bluetooth APIs are used. `BLUETOOTH_SCAN` declares `neverForLocation`; the app does not use scan results to infer location.

## Safety and components still to select

No electrode circuit is designed here. Do not connect electrodes directly to ESP32 GPIO. Any future sEMG acquisition must use a suitable external biopotential AFE and a battery-powered, protected, medically informed design reviewed for the intended use; this software is not an electrical safety review.

Before procurement or hardware validation, select and document:

1. ESP32-S3 module/development board, antenna arrangement, power/charging design, enclosure, and supported ESP-IDF/BLE stack version.
2. Exact three-axis accelerometer and breakout/module; voltage range, interface/bus, raw resolution, configured range, scale, sample-rate options, and mounting.
3. Exact external sEMG/biopotential AFE and protected isolated/battery-powered acquisition assembly; output interface, sample format/rate, analog gain, filters, saturation behavior, scale, and actual configuration identifier.
4. Electrode/lead system and clinically/ethically reviewed placement and operating protocol; no placement is prescribed by this project.
5. Board-specific driver, clock-extension/session-ID implementation, GATT adapter, BLE throughput/loss tests, and versioned firmware build/release process.
6. Explicit, reviewed Android processing configuration for each selected sensor/AFE, including filter/noise/saturation settings and source-configuration identity.

## Tests and validation boundary

Android unit tests use synthetic, explicitly test-only packets. They cover packet fields/CRC, unsupported versions, malformed batches, sequence wrap/gaps/duplicates/session reset, sample sequence gaps, timestamp conversion and drift estimation, channel availability, and mapping validated samples into existing processor measurement types/units. These tests do not establish BLE interoperability or physical sensing accuracy.

The portable C++ packet codec has a CMake test target covering encode/decode, CRC rejection, batch validation, AFE safety metadata gating, and time-sync echo. The local environment had no `cmake`, `g++`, `clang++`, `idf.py`, or PlatformIO executable, so the C++ test target was not built or run. No board-specific firmware build target exists until a board/toolchain is selected.

Physical tests still required: Android scan/connect/permission-denial behavior on supported OS versions; GATT discovery/subscription and MTU negotiation; disconnect/reconnect; packet corruption, drops, duplicates, reordering and notification throughput; sensor initialization/read failures; accelerometer units, scale, timing, clipping, mounting and calibration; sEMG AFE units/rate/scale/saturation and protected battery-powered operation; device clock rollover/session reset and Android offset/drift error against an independent reference; and end-to-end attempt persistence once real configuration and storage integration are designed.
