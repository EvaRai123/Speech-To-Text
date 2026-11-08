# SpeechBridge

Experimental wearable-assisted speech-practice and measurement platform for Android.

[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html)
[![Status](https://img.shields.io/badge/status-engineering%20prototype-orange)](#project-status)

SpeechBridge is a student engineering project exploring how structured speech practice could be paired with several kinds of feedback. Dysarthria can affect how clearly a person speaks, including after a stroke. The app is designed to make practice easier to follow while recording carefully described, quality-aware measurements when a supported input is available.

This is an experimental platform. It does not diagnose, treat or cure dysarthria, and it is not a replacement for a speech-language pathologist.

## Why I built it

Speech is a complex physical process. A microphone captures the sound that reaches the phone, but it cannot tell the whole story of how that sound was produced. A neck-mounted accelerometer could describe mechanical vibration, surface EMG could describe electrical activity under electrodes, and a front camera could describe visible lip and jaw movement. Each channel is incomplete on its own, and each can be affected by placement, noise and the person using it.

The research question behind SpeechBridge is:

> Can synchronized audio, neck-vibration, surface-EMG and visual mouth-movement measurements provide more informative feedback during structured speech practice than individual measurement channels alone?

The Android application is therefore built around two related goals: give a person a clear, respectful practice flow today, and keep the measurement contracts explicit enough for later hardware and research work.

## How SpeechBridge works

| Channel | What it can describe | Current status |
| --- | --- | --- |
| Microphone | Speech audio and supported voice features such as relative level, duration and signal quality | Implemented for local practice and microphone-only research condition |
| Neck accelerometer | Mechanical vibration near the neck | Protocol and processing foundations exist; external wearable hardware is not connected in this build |
| Surface EMG | Electrical activity measured beneath skin electrodes | Data contracts and processing foundations exist; no biopotential front end is connected |
| Front camera | Visible lip and jaw movement proxies | Optional CameraX/ML Kit path with contour-derived proxies; it cannot see tongue placement or internal mouth structures |

The Android phone hosts the lesson catalogue, practice screens, microphone capture, camera analysis, storage, comparisons and CSV export. A future ESP32-S3 wearable is intended to acquire neck and EMG signals and send timestamped packets over Bluetooth Low Energy. The current `firmware/` directory contains a portable C++17 protocol core and a packet-codec test target; it is not evidence that a physical board has been assembled or validated.

```text
                 +--------------------------+
                 | Android app               |
                 | lessons, practice,       |
                 | camera, audio, history    |
                 +------------+-------------+
                              |
                 +------------+-------------+
                 | measurement contracts    |
                 | quality, timing, baseline |
                 +------+--------------------+
                        | BLE protocol foundation
                        v
                 +--------------------------+
                 | Future wearable          |
                 | ESP32-S3 + accelerometer |
                 | + sEMG front end         |
                 +--------------------------+
```

## Main features

### Guided learning and practice

The curriculum contains nine areas and 60 structured lessons covering breathing, voice, pitch and expression, loudness, clear sounds, syllables and words, pacing, connected speech, and mouth movement and articulation. Lessons contain short objectives, plain-language instructions, prompts, optional measurements, and a participation policy that does not turn an unavailable sensor into a false score.

The app also includes a short educational section about dysarthria, a glossary, frequently asked questions, safety information, references, sensor explanations and an explanation of the SpeechBridge architecture. These screens are intended to explain the prototype without presenting medical information as a diagnosis.

The guided microphone exercises capture PCM audio locally while the user practices. The app reports whether a usable recording was obtained and shows details such as duration, relative level and signal quality. It does not claim that a phone microphone reading is an absolute room loudness measurement.

The navigation is intentionally organized around a small next step: learn what an exercise means, choose a lesson, practice, and then review what the app could actually observe. A user can continue without the optional wearable, while the research screens make unavailable channels visible instead of presenting a misleading combined result. This is especially important for a prototype that may be used on phones with different microphones, cameras, Android versions and Bluetooth capabilities. The app also keeps educational content separate from measurement output so that a simple practice prompt is not confused with a medical conclusion.

### Measurement-aware history

Practice attempts retain source status and measurement quality rather than silently filling missing channels with zeros. Personal baselines and confidence-aware comparisons are represented in the application model. Review and progress screens show what was recorded, what was unavailable, and when a comparison should be treated cautiously.

The Research Lab provides microphone-only condition A recordings, a framework for additional ablation conditions, channel availability summaries, session history and CSV export. Accelerometer and sEMG conditions remain blocked until the corresponding hardware is actually connected and initialized. This distinction is deliberate: an interface or protocol foundation is not the same as a validated sensor measurement.

### Camera-based visible movement proxies

The optional camera path uses CameraX and ML Kit face detection to derive visible geometry proxies around the mouth. These values can support timing and movement exploration, but they are not measurements of tongue position, vocal-fold movement or clinical articulation accuracy. The lesson illustrations are prompts for the exercise, not anatomical scans or instrument readings.

## Technical architecture

The Android app is a Java application using AndroidX, Material Components, ConstraintLayout, CameraX and ML Kit. The main pieces are organized by responsibility:

```text
app/src/main/java/com/example/speechpractice/
├── *Activity.java                 UI screens and navigation
├── LessonCatalog.java             nine areas and 60 lesson definitions
├── LessonActivity.java             guided lesson flow
├── PcmPracticePipeline.java        microphone capture and PCM processing
├── AudioMeasurementEngine.java     audio summaries and quality checks
├── CameraMeasurement*.java         camera buffering and gating
├── CameraMouthGeometry.java        visible geometry calculations
├── Wearable*.java                  BLE, packets, sequence and status models
├── *Baseline*.java                 personal baseline persistence/comparison
├── *Measurement*.java              source contracts and quality status
├── *Research*.java                 sessions, ablation and export
└── VisualContent.java              lesson illustration mapping
```

The app uses small data classes and explicit enums for measurement type, source, status and quality. That makes it possible to distinguish “not available”, “not required”, “invalid”, “synthetic”, and “measured” instead of collapsing them into one number. `CourseProgressStore` and the review stores persist progress and summaries locally. Raw audio and video are not automatically treated as research data by the dashboard.

The BLE layer defines packet encoding/decoding, device status, sequence tracking and clock synchronization hooks. It reports packet gaps and session changes instead of zero-filling missing samples. `DeviceClockSynchronizer` provides a mapping estimate between device and Android clocks, but its accuracy has not been experimentally established.

The firmware core is intentionally separate from Android. `firmware/CMakeLists.txt` builds a C++17 library from the packet, clock, BLE and application-core sources, then builds a packet-codec test executable. Board-specific pin assignments, sensor drivers, power design and production firmware integration are still future work.

The architecture favors inspectable intermediate values over a single opaque score. For example, an attempt can retain the source status, timing information, quality label and summary values that contributed to a comparison. This makes a later review more useful: an engineer can ask whether a result came from a real input, whether a packet gap occurred, and whether the comparison had enough confidence. It also gives the project a safer foundation for future experiments because missing data remains missing data.

## Research and validation

The project is structured for a future multimodal ablation study. A possible study would compare microphone-only practice with combinations such as microphone plus camera, microphone plus neck vibration, microphone plus sEMG, and all available channels. Each condition would need synchronized timestamps, documented hardware placement, repeated trials, signal-quality checks and participant-specific baselines.

What is currently testable in software includes lesson count and mapping, camera geometry and gating, audio processing, measurement contracts, session persistence, recommendation logic, CSV export, signal comparison and wearable packet handling. These are unit or integration-level checks. Synthetic samples and protocol tests can show that code behaves consistently for supplied inputs; they cannot establish clinical usefulness.

Hardware repeatability, sensor placement, BLE performance under real conditions, timing accuracy, human-subject data collection and clinical effectiveness have not been established. Any future human-subject study would require professional supervision, informed consent, privacy safeguards and applicable ethics approval.

## Installation and running

### Android application

Prerequisites:

- Android Studio with an Android SDK that includes API 35.
- JDK 11 or a compatible Android Studio-managed JDK.
- A device or emulator running Android 8.0/API 26 or newer for the minimum supported version.

Open the repository in Android Studio and let Gradle sync. From the repository root, the configured wrapper commands are:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

To run both checks as a release gate:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug --rerun-tasks
```

Microphone and camera features require runtime permissions. BLE behavior also depends on Android version, device support and the availability of a compatible external device. A successful Android build does not imply that an ESP32-S3 board is connected.

### Firmware core

The firmware directory is a standalone CMake project and requires a C++17 compiler and CMake 3.16 or newer:

```powershell
cmake -S firmware -B firmware/build
cmake --build firmware/build
ctest --test-dir firmware/build --output-on-failure
```

These commands exercise the portable core and packet-codec test. They do not flash a board. An ESP32-S3 SDK/toolchain, board configuration and sensor hardware are still required for a device build.

## Limitations and safety

SpeechBridge is an engineering research prototype. It is not a diagnostic tool and does not replace speech-language therapy or other qualified clinical care. Stop an exercise if it causes discomfort, fatigue, pain or strain, and seek appropriate professional advice for health concerns.

The camera sees only visible movement. It cannot see tongue placement inside the mouth. Neck vibration is not a direct measurement of vocal-fold movement. Surface EMG does not directly measure intrinsic vocal-fold activity. Microphone features are affected by the phone, environment, distance and input route. All wearable measurements depend on hardware quality, electrode placement, calibration, configuration and synchronization.

The current repository includes software foundations and synthetic or local tests, not physical wearable validation, clinical validation or evidence of improved speech outcomes. Research exports may contain sensitive information if a user chooses to create them; they should be handled as private data and shared only with appropriate safeguards.

## Roadmap

1. Select and assemble the accelerometer, EMG front end and safe wearable enclosure.
2. Implement ESP32-S3 sensor acquisition and board-specific BLE integration.
3. Connect the Android BLE path to real accelerometer and sEMG samples.
4. Validate timestamps, clock mapping, packet loss handling and synchronization.
5. Measure signal repeatability across placement and session changes.
6. Run a supervised multimodal ablation study with appropriate approvals.
7. Complete external technical review and research documentation.

## Project status

The current state is an active engineering prototype. The Android learning flow, microphone practice path, local history models, camera proxy path, measurement contracts, research screens, export path and wearable protocol foundations are present in source. Full hardware integration and experimental validation remain future work.

Key technologies include Java 11, AndroidX, Material Components, CameraX, ML Kit Face Detection, Vosk integration hooks, JUnit, CMake and C++17. The exact dependency versions are maintained in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and [`app/build.gradle.kts`](app/build.gradle.kts).

The project is intended for learning, engineering review and future research planning. It should not be presented as clinically validated software or as a medical product.
