L4-R4 LOG READINESS

Overall: READY

Recorder instantiated:
YES

Recorder runtime call site:
YES

Start control:
YES

Stop control:
YES

Finalize control:
YES

Reset control:
YES

Experiment ID:
YES

Run ID:
YES

Ground-truth distance:
YES

Ground-truth angle:
YES

BLE RSSI:
YES

Filtered RSSI:
YES

Sensor snapshots:
YES

Distance estimator:
YES (nullable; current debug workflow records UNKNOWN unless a validated calibration window is supplied)

Direction estimator:
YES (nullable; current output remains UNKNOWN)

Confidence:
YES

Uncertainty:
YES (nullable)

Incremental persistence:
YES (atomic checkpoint per sample using temporary file and rename)

JSON extraction:
YES

Dedicated validation logs:
YES (`VOKIE_VALIDATION`; compact event logging)

Production communication:
UNCHANGED

Real physical measurements:
0

Physical validation:
NOT PERFORMED

Build:
PASS

Tests:
PASS

Files changed:
- app/src/debug/java/com/vokie/ranging/PhysicalValidationRecorder.kt
- app/src/debug/java/com/vokie/ranging/RangingLabActivity.kt
- app/src/test/java/com/vokie/ranging/PhysicalValidationRecorderTest.kt
- model-lab/ranging/validation/README.md
- model-lab/ranging/validation/L4-R4-log-readiness.md
- model-lab/ranging/validation/distance_validation_measurements.json
- model-lab/ranging/validation/direction_validation_measurements.json
- model-lab/ranging/validation/validation_summary.json

Runtime path:
PhysicalValidationRecorder -> RangingLabActivity -> live RelativePeerLocalizationEngine state -> ValidationMeasurement -> incremental JSON files.

Notes:
- Embedded DEBUG BLE scanner records real ScanResult RSSI values into the localization engine.
- The advertiser only advertises; it does not produce RSSI observations.
- `Record Sample Now` is available as a manual fallback; automatic recording begins after `Start Run`.
- If no peer/RSSI state exists, no sample is invented and the manual button has nothing to record.
- Ground-truth fields are user-entered and remain separate from estimator fields.
