# 5G NR Simulator — Conformance Status

## Architecture
V1–V45 remain intact. V46–V60 are additive modules and adapters; V65 is an additional network orchestration layer; V66 is an additive integration layer; V67 adds a timing annotation/execution spine; V68 adds a timing-aware HARQ event layer. No established public simulator module was removed, renamed, or replaced.

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

## V61–V68
- V61: integrated laboratory PHY link using the existing coding, modulation, OFDM and CRC boundaries.
- V62: additive multi-UE/multi-cell radio environment with mobility, path loss, shadowing, interference, Doppler, CQI/MCS, rank adaptation and PRB allocation.
- V63: additive bridge from V62 radio conditions into the V61 PHY.
- V64: additive closed-loop execution with PHY ACK/NACK and goodput feedback into proportional-fair history. It accepts an optional external SINR correction map; the default empty map preserves the prior behavior.
- V65: additive network orchestration with explicit gNB topology, UE positions, serving-cell association, handover hysteresis, traffic-demand accounting, cell-load KPIs and network-level fairness.
- V66: additive integrated execution path that composes V65 topology/traffic, V27 mobility, V28 beam selection, V16 TDL/CDL channel characterization, V15 MIMO characterization, V62 radio conditions and V64 closed-loop PHY/scheduler/HARQ.
- V67: additive NR timing spine exposing numerology, slots/subframe, slots/frame, 10 ms frame timing, 1 ms subframes, slot duration and absolute frame/subframe/slot coordinates while preserving V66 radio execution.
- V68: additive slot-aware HARQ event layer. It binds V51 HARQ state/RV behavior to V67 transmission and feedback slots, models configurable ACK delay (`k1`-style abstraction), exposes earliest retransmission opportunity after a NACK, and does not fabricate a retransmitted PHY block when V64/V66 did not simulate one.

## Important limitation
This project is **not 3GPP certified**. ETSI/3GPP normative specifications define substantially more procedure detail and conformance testing than can be established by software round-trip tests alone. Official Release-19 conformance specifications, RF tests, interoperability testing, and applicable certification evidence remain external requirements.

## Architecture / Execution Separation

V1–V67 APIs are preserved. Pure Kotlin NR logic is hosted in `:nr-core` so the same implementation can be consumed by the Android app and standalone JVM verification. `MainActivity` remains Android-only. V65 remains network orchestration, V66 remains the integrated radio path, V67 remains the slot clock, V51 remains the HARQ state/RV primitive, and V68 is an additive timing adapter around them. V68 intentionally reports planned retransmission opportunities rather than claiming that a second PHY transmission has already occurred.
