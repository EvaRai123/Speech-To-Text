# SpeechBridge Phase 3 measurement model

SpeechBridge is an engineering research prototype. Measurements and derived values are not diagnostic, treatment, or clinical-improvement claims. Accelerometer and sEMG processing accepts explicit timestamped input, but this phase does not connect or simulate either sensor in the running app.

## Acquisition and timestamps

`MeasurementRecord` distinguishes a point acquisition timestamp from an aggregate's monotonic window start/end and the app's wall-clock receipt time. The audio capture path does not currently expose a reliable per-frame acquisition clock, so audio records retain a null acquisition timestamp. Camera records use the frame timestamp when CameraX provides one. The accelerometer and sEMG processors use the timestamps supplied by their caller. Receipt time is never substituted for acquisition time.

Unavailable and invalid records have null value and unit. Only an available measurement may carry a numeric value. Unit/type/source compatibility is checked by `MeasurementType` and `MeasurementRecord`.

## Audio quality

`AudioMeasurementEngine` operates on captured PCM16 frames without storing the waveform in progress history. Existing flags identify clipping, low signal-to-noise ratio, very quiet input, absent detected activity, and unavailable pitch. `SignalQualityAssessment.forAudio` uses those flags for quality classification and passes through the pitch estimator's bounded periodicity confidence when it exists. This confidence concerns the estimator, not clinical accuracy. The noise-floor fallback used by the existing engine is an analysis threshold, not a sensor calibration.

## Accelerometer processing

`AccelerometerProcessor` accepts samples in m/s^2 with monotonic nanosecond timestamps. The reported rate is estimated from the median observed interval; if an expected rate is configured, it is checked against that estimate. Long gaps and irregular intervals are counted. The processor removes each axis mean (including static orientation/gravity offset), calculates the vector magnitude of the centered axes, and optionally applies a first-order high-pass filter using the configured cutoff. It returns window RMS, peak-to-peak, and integrated squared magnitude. A Hann-windowed direct DFT dominant-frequency estimate is emitted only for at least 32 samples, at least 0.5 seconds of data, and sufficiently regular intervals. Saturation, gaps, irregular intervals, and the configured noise floor affect source reliability. These are neck-mount mechanical vibration proxies, not vocal-fold motion, voice quality, or phonation success.

## sEMG processing

`SemgProcessor.Config` requires an explicit sample rate, input-unit-to-microvolt scale, high/low-pass cutoffs, envelope window, and optional saturation threshold. It rejects filter cutoffs at or above Nyquist and input windows whose observed rate differs from configuration. The processor estimates offset from the first tenth of the supplied window, applies first-order high-pass and low-pass filters, full-wave rectifies, and calculates a trailing moving RMS envelope. The offset and envelope depend on the configured analog front end and are in uV only when that scale is valid. Reliability factors include missing and irregular samples, saturation, and baseline variation. No ADC range, electrode layout, analog gain, or hardware connection is assumed by the application. An appropriate biopotential front end and electrical protection are required; do not connect electrodes directly to ESP32 GPIO.

## Quality and confidence

Accelerometer and sEMG reliability is a bounded, source-specific engineering estimate, not a universal score. Both begin with continuity `1 - min(1, 2 * missingFraction + irregularIntervalFraction)` and multiply by 0.5 for saturation. Accelerometer reliability multiplies by 0.4 below its configured noise floor; sEMG reliability multiplies by 0.7 for an unstable baseline and by 0.7 for excessive noise when that factor is available. Poor status is assigned for saturation, an accelerometer window below its configured noise floor, at least 20% missing samples, or reliability below 0.45; other observed degradations are limited quality. The component-specific explanation and flags accompany the estimate.

Camera quality is limited when contours are usable because the current provider does not quantify lighting or temporal tracking stability; no numeric camera confidence is emitted. Missing faces or contours produce poor/unavailable records. Audio quality follows the existing audio flags and pitch estimator confidence described above. No cross-source universal score is calculated.

## Personal baselines and comparisons

`PersonalBaseline` keys each profile by local participant, source, task, and exact observed configuration identifier. The Research Lab can create/update profiles from completed saved attempts only when at least two attempts have valid, non-poor measurements and the same explicitly persisted configuration. History records the contributing attempt IDs and updates remain deduplicated. Attempts without an observed configuration ID are reported as unavailable; the app does not infer a microphone, sensor placement, electrode, or camera configuration. The progress preference names and lesson completion keys are unchanged. The current runtime does not yet collect these configuration IDs, so existing production attempts normally remain ineligible.

Descriptive attempt comparisons are restricted to the same lesson and matching source/type/unit, retain quality labels, and are explicitly not window/configuration-compatible ablation. The channel-ablation comparison path requires the same task, source/type/unit, acceptable quality, non-overlapping ordered acquisition windows, and exact persisted configuration IDs. Audio acquisition windows are not currently available from the saved audio path, and production attempts do not yet persist source configuration IDs, so many or all channel comparisons are correctly blocked. Neither comparison labels a change as improvement.

## Fusion and ablation

Fusion validates attempt grouping, measurement status, quality, and timestamped-window alignment. The Research Lab labels eligible channels as eligibility only, not validated fusion. It preserves channel records and reasons for exclusions. Even when windows align, the current measurements represent different constructs and units, and no validated mapping combines them. The engine therefore returns fusion unavailable rather than creating a weighted or universal score. Channel ablation can select audio, camera, accelerometer, sEMG, or combinations but reports a comparison only when equivalence and compatible data are present; it does not claim that a larger subset is superior.

Recommendations use same-lesson saved attempt history and explicitly recorded user comfort when available. They are optional, make no automatic changes, and distinguish missing/poor signal from speech performance. A recommendation is withheld when history is insufficient. Difficulty feedback does not select an allegedly easier/harder lesson because the curriculum has no explicit, defensible difficulty relationship.

## Research export and privacy

CSV export contains saved measurement metadata, attempt participation counts, available source configuration IDs, and escaped text fields. Null values remain empty with their explicit status; genuine zero values remain numeric zero. When an attempt has no accelerometer or sEMG record, export adds an explicit unavailable row with a null value and explanatory status; a camera attempt status without a camera measurement is also exported as a status-only unavailable row. An absent audio row is marked as a missing record with unknown activation/rejection state, not as a fabricated unavailable measurement. These rows are metadata, not sensor samples. Calibration and fusion references are blank because no validated fusion/reference is persisted. The export contains no raw audio or video, and the participant ID supplied by the UI is the app's local pseudonymous identifier.
