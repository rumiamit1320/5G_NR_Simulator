# 5G NR Simulator — Conformance Status

## Architecture
V1–V45 remain intact. V46–V60 are additive modules and adapters; V65 is an additional network orchestration layer; V66 is an additive integration layer; V67 adds a timing annotation/execution spine; V68 adds a timing-aware HARQ event layer; V69/V70 add persistent HARQ execution; V71 adds a PHY-facing HARQ RV execution adapter; V72 adds an explicit transport-bit LLR soft-buffer boundary; V73 adds a symbol-derived soft-PHY/HARQ combining path. No established public simulator module was removed, renamed, or replaced.

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

## V61–V73
- V61: integrated laboratory PHY link using the existing coding, modulation, OFDM and CRC boundaries. Its result now exposes the full equalized QAM-symbol vector additively; the existing diagnostic constellation view remains unchanged.
- V62: additive multi-UE/multi-cell radio environment with mobility, path loss, shadowing, interference, Doppler, CQI/MCS, rank adaptation and PRB allocation.
- V63: additive bridge from V62 radio conditions into the V61 PHY.
- V64: additive closed-loop execution with PHY ACK/NACK and goodput feedback into proportional-fair history. It accepts an optional external SINR correction map; the default empty map preserves prior behavior.
- V65: additive network orchestration with explicit gNB topology, UE positions, serving-cell association, handover hysteresis, traffic-demand accounting, cell-load KPIs and network-level fairness.
- V66: additive integrated execution path composing V65 topology/traffic, V27 mobility, V28 beam selection, V16 channel characterization, V15 MIMO characterization, V62 radio conditions and V64 closed-loop PHY/scheduler/HARQ.
- V67: additive NR timing spine exposing numerology, slots/subframe, slots/frame, 10 ms frame timing, 1 ms subframes, slot duration and absolute frame/subframe/slot coordinates while preserving V66 radio execution.
- V68: additive slot-aware HARQ event layer binding V51 HARQ state/RV behavior to V67 transmission and feedback slots.
- V69: additive persistent HARQ process/event state with stable transport-block identity, transmission number, RV progression and retransmission opportunities.
- V70: additive execution adapter that consumes CRC outcomes from the existing V66 timeline for both initial and retransmission opportunities, maintaining persistent process state and V51 soft-buffer bookkeeping.
- V71: additive PHY-facing HARQ adapter that invokes the existing V61 integrated link for successive RVs. Its confidence metric is diagnostic only; CRC truth is never fabricated from a threshold.
- V72: additive LLR soft-buffer primitive for repeated transport-bit observations. It remains useful as a standalone accumulation boundary.
- V73: additive symbol-derived soft PHY. It consumes the full V61 equalized QAM symbols, computes modulation-aware max-log LLRs for QPSK/16QAM/64QAM/256QAM, performs RV-aware soft rate recovery with accumulation, and applies a soft-input Polar successive-cancellation decoder before CRC evaluation.

## HARQ / soft-PHY integration boundary
The HARQ stack now has distinct layers rather than conflating timing, scheduling, PHY execution and soft combining: V51 supplies process/RV primitives; V67 supplies the slot clock; V68/V69/V70 supply event and persistent execution state; V71 preserves the earlier V61-based compatibility path; V72 provides the explicit LLR buffer primitive; V73 provides actual symbol-derived LLR generation and RV-aware combining. V73 is a materially stronger PHY model than the V71 confidence heuristic because CRC remains decoder-derived and the combined state is represented as accumulated soft information.

## Standards boundary
The architecture is being aligned against the 3GPP NR physical-layer specification family, particularly TS 38.211 (physical channels/modulation), TS 38.212 (multiplexing/channel coding), TS 38.213 (control procedures), TS 38.214 (data procedures), and TS 38.215 (measurements). The target release must be declared for any normative conformance claim because these specifications continue to evolve.

## Important limitation
This project is **not yet 3GPP certified and V73 does not claim full NR channel-coding compliance**. The current V47 Polar construction is a deterministic project codec foundation rather than a complete normative NR Polar implementation, and the project still needs a genuine 38.212 LDPC implementation, exact NR transport-block/code-block processing, standards-accurate rate matching/bit selection, PDSCH/PUSCH resource mapping, DM-RS-driven channel estimation/equalization, layer/antenna-aware MIMO waveform processing, control-channel procedures, RACH/initial access, persistent RRC/NAS procedures, and a deeper 3GPP reference-test suite. Official conformance specifications such as the 38.521 family define performance requirements and test configurations that must ultimately be used for validation rather than inferred from internal round-trip tests.

## Architecture / Execution Separation
V1–V73 APIs are preserved except for the additive V61 result field exposing the full equalized constellation. Pure Kotlin NR logic is hosted in `:nr-core` so the same implementation can be consumed by the Android app and standalone JVM verification. `MainActivity` remains Android-only. V65 remains network orchestration, V66 remains the integrated radio path, V67 remains the slot clock, V51 remains the HARQ state/RV primitive, V68/V69/V70 remain additive HARQ timing/execution adapters, V71 remains the compatibility PHY-HARQ adapter, V72 remains the generic soft-buffer primitive, and V73 is the first actual symbol-to-LLR HARQ path. No duplicate mobility, propagation, MIMO or primary network execution engine was introduced.
