# 5G NR Simulator — Conformance Status

## Architecture
V1–V45 remain intact. V46–V60 are additive modules and adapters; V65 is an additional network orchestration layer; V66 is an additive integration layer; V67 adds a timing annotation/execution spine. No existing public simulator module was removed or renamed.

## V46–V60
- V46: transport/LDPC segmentation and rate-matching orchestration.
- V47: reusable polar transform/rate-matching codec boundary.
- V48: PDCCH DCI/CRC/polar/QPSK boundary.
- V49: resource-grid allocation and collision model.
- V50: NR numerology/BWP/slot timing model.
- V51: HARQ process state and RV/soft-combining model.
- V52: MAC multiplexing/CE/BSR model.
- V53: RLC AM state/timer/STATUS foundation.
- V54: PDCP COUNT/security boundary.
- V55: typed RRC codec boundary.
- V56: NAS registration/security/session state machine foundation.
- V57: GTP-U tunnel packet codec.
- V58: UE/gNB/core split-plane interfaces.
- V59: deterministic conformance status harness.
- V60: Release-19-oriented compliance matrix.

## V61–V67
- V61: integrated laboratory PHY link using the existing coding, modulation, OFDM and CRC boundaries.
- V62: additive multi-UE/multi-cell radio environment with mobility, path loss, shadowing, interference, Doppler, CQI/MCS, rank adaptation and PRB allocation.
- V63: additive bridge from V62 radio conditions into the V61 PHY.
- V64: additive closed-loop execution with PHY ACK/NACK and goodput feedback into proportional-fair history. It accepts an optional external SINR correction map; the default empty map preserves the prior behavior.
- V65: additive network orchestration with explicit gNB topology, UE positions, serving-cell association, handover hysteresis, traffic-demand accounting, cell-load KPIs and network-level fairness.
- V66: additive integrated execution path that composes V65 topology/traffic, V27 mobility decisions, V28 beam selection, V16 TDL/CDL channel characterization, V15 MIMO characterization, V62 radio conditions and V64 closed-loop PHY/scheduler/HARQ. The richer link models contribute through one bounded SINR correction rather than applying a second independent fading/noise chain.
- V67: additive NR timing spine. It exposes numerology, slots/subframe, slots/frame, 10 ms frame timing, 1 ms subframes, slot duration and absolute frame/subframe/slot coordinates while preserving V66 radio execution and all V1–V66 APIs.

## Important limitation
This project is **not 3GPP certified**. ETSI/3GPP normative specifications define substantially more procedure detail and conformance testing than can be established by software round-trip tests alone. Official Release-19 conformance specifications, RF tests, interoperability testing, and applicable certification evidence remain external requirements.

## Architecture / Execution Separation

V1–V66 APIs are preserved. Pure Kotlin NR logic is hosted in `:nr-core` so the same implementation can be consumed by the Android app and standalone JVM verification. `MainActivity` remains Android-only. V65 remains the network orchestration layer, V66 remains the integrated radio path, and V67 adds timing metadata without replacing either. V66 also preserves V64's legacy behavior when no external SINR corrections are supplied. The timing layer makes existing slot-indexed simulation results addressable on an NR frame timeline without coupling the Android UI to the simulation core.
