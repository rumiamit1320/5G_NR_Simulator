# 5G_NR_Simulator — 3GPP Verification Matrix

## Verification policy

This matrix separates **standards consistency** from **formal 3GPP conformance**. The simulator is an educational/reference implementation, so passing an automated check does not by itself establish conformance certification.

Pinned standards baseline: **3GPP Release 19 family, as published/maintained by the 3GPP specification portal**. A future release change must update this document and the test vectors together.

| Test ID | Feature | Reference | Automated verification | Classification |
|---|---|---|---|---|
| STD-NR-001 | NR subcarrier spacing | TS 38.211 | 15/30/60 kHz configurations accepted and returned | MODEL CONSISTENCY |
| STD-NR-002 | Downlink/uplink waveform and physical-layer processing | TS 38.211 | V61 end-to-end API smoke path; implementation regression tests | MODEL CONSISTENCY |
| STD-NR-003 | CRC / channel coding / rate matching | TS 38.212 | Existing V61 regression/smoke tests | MODEL CONSISTENCY; vector conformance requires reference vectors |
| STD-NR-004 | Physical-layer procedures for control | TS 38.213 | API/regression coverage where implemented | NOT A FULL CONFORMANCE TEST |
| STD-NR-005 | MCS/modulation and data procedures | TS 38.214 | QPSK/16-QAM/64-QAM/256-QAM paths and MCS-domain checks | MODEL CONSISTENCY; exact table conformance requires pinned vectors |
| STD-NR-006 | CSI/MIMO/beam-related behavior | TS 38.214 | MIMO/CSI UI/API paths and rank bounds | MODEL CONSISTENCY |
| STD-NR-007 | PHY measurements terminology/behavior | TS 38.215 | SINR/RSRP/throughput result presence and finite-value checks | MODEL CONSISTENCY |
| STD-NR-008 | Overall NR architecture terminology | TS 38.300 | V62 → V63 → V61 → V64 pipeline presence and API integration | ARCHITECTURAL CONSISTENCY |
| STD-NR-009 | Scheduler/closed-loop behavior | TS 38.213 / TS 38.214 | PRB conservation, feedback path and closed-loop API checks | SIMULATOR-SPECIFIC; not normative scheduler conformance |
| STD-NR-010 | Mobility/Doppler abstraction | TS 38.300 / TS 38.215 context | velocity parameter exercises and finite radio results | MODEL CONSISTENCY |

## Important limits

- The simulator's configurable **1–106 PRB range is an application constraint**, not a statement that NR universally supports only 1–106 PRBs.
- CQI/MCS/rank range checks are sanity checks. Exact normative conformance requires release-specific 3GPP tables and reference vectors.
- V64 closed-loop scheduling is a simulator feedback model. It must not be represented as a claim of 3GPP scheduler implementation conformance.
- Passing the web/API suite means the deployed application behaves consistently with its implemented model and declared parameter domains; it is not a substitute for a formal conformance test set.

## Required evidence for a future formal conformance layer

1. Pin an exact 3GPP release/version for every specification.
2. Add machine-readable golden vectors for each normative PHY procedure.
3. Compare bit-exact CRC, coding, rate matching, modulation, OFDM and measurement results against those vectors.
4. Record tolerance/quantization rules explicitly for floating-point quantities.
5. Produce a per-clause PASS/FAIL report and retain the vector inputs/outputs as CI artifacts.
