cmake_minimum_required(VERSION 3.16)
project(speechbridge_firmware_core LANGUAGES CXX)
enable_testing()

add_library(speechbridge_firmware_core
    src/BleService.cpp
    src/DeviceClock.cpp
    src/FirmwareApplication.cpp
    src/PacketCodec.cpp
    src/main.cpp
)

target_include_directories(speechbridge_firmware_core PUBLIC include)
target_compile_features(speechbridge_firmware_core PUBLIC cxx_std_17)

add_executable(speechbridge_packet_codec_test tests/PacketCodecTest.cpp)
target_link_libraries(speechbridge_packet_codec_test PRIVATE speechbridge_firmware_core)
add_test(NAME speechbridge_packet_codec_test COMMAND speechbridge_packet_codec_test)
