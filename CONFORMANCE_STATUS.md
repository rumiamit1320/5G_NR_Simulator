# 5G NR Simulator — Conformance Status

## Architecture
V1–V45 remain intact. V46–V60 are additive modules and adapters; V65 is an additional network orchestration layer; V66 is an additive integration layer; V67 adds a timing annotation/execution spine; V68 adds a timing-aware HARQ event layer; V69/V70 add persistent HARQ execution; V71 adds a PHY-facing HARQ RV execution adapter; V72 adds an explicit transport-bit LLR soft-buffer boundary. No established public simulator module was removed, renamed, or replaced.

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

## V61–V72
- V61: integrated laboratory PHY link using the existing coding, modulation, OFDM and CRC boundaries.
- V62: additive multi-UE/multi-cell radio environment with mobility, path loss, shadowing, interference, Doppler, CQI/MCS, rank adaptation and PRB allocation.
- V63: additive bridge from V62 radio conditions into the V61 PHY.
- V64: additive closed-loop execution with PHY ACK/NACK and goodput feedback into proportional-fair history. It accepts an optional external SINR correction map; the default empty map preserves prior behavior.
- V65: additive network orchestration with explicit gNB topology, UE positions, serving-cell association, handover hysteresis, traffic-demand accounting, cell-load KPIs and network-level fairness.
- V66: additive integrated execution path composing V65 topology/traffic, V27 mobility, V28 beam selection, V16 channel characterization, V15 MIMO characterization, V62 radio conditions and V64 closed-loop PHY/scheduler/HARQ.
- V67: additive NR timing spine exposing numerology, slots/subframe, slots/frame, 10 ms frame timing, 1 ms subframes, slot duration and absolute frame/subframe/slot coordinates while preserving V66 radio execution.
- V68: additive slot-aware HARQ event layer binding V51 HARQ state/RV behavior to V67 transmission and feedback slots.
- V69: additive persistent HARQ process/event state with stable transport-block identity, transmission number, RV progression and retransmission opportunities.
- V70: additive execution adapter that consumes CRC outcomes from the existing V66 timeline for both initial and retransmission opportunities, maintaining persistent process state and V51 soft-buffer bookkeeping.
- V71: additive PHY-facing HARQ adapter that invokes the existing V61 integrated link for successive RVs instead of implementing a second PHY. It exposes per-transmission CRC/BER/EVM results and a deterministic confidence accumulation boundary.
- V72: additive LLR soft-buffer primitive for repeated transport-bit observations. It provides signed reliability accumulation and hard-decision inspection without changing V61/V64/V66 decoder APIs.

## HARQ integration boundary
The current HARQ stack now has distinct layers rather than conflating timing, scheduling, PHY execution and soft combining: V51 supplies process/RV primitives; V67 supplies the slot clock; V68/V69/V70 supply event and persistent execution state; V71 reuses the existing V61 coded PHY for RV-specific transmissions; V72 supplies the explicit LLR accumulation boundary. A full 3GPP NR LDPC/PDSCH/PUSCH implementation with exact bit-selection circular-buffer semantics, modulation-specific LLR demapping, layer/antenna-aware equalization and decoder-internal soft combining remains a deeper PHY enhancement and is not claimed by V71/V72.

## Important limitation
This project is **not 3GPP certified**. ETSI/3GPP normative specifications define substantially more procedure detail and conformance testing than can be established by software round-trip tests alone. Official Release-19 conformance specifications, RF tests, interoperability testing, and applicable certification evidence remain external requirements.

## Architecture / Execution Separation
V1–V72 APIs are preserved. Pure Kotlin NR logic is hosted in `:nr-core` so the same implementation can be consumed by the Android app and standalone JVM verification. `MainActivity` remains Android-only. V65 remains network orchestration, V66 remains the integrated radio path, V67 remains the slot clock, V51 remains the HARQ state/RV primitive, V68/V69/V70 remain additive HARQ timing/execution adapters, V71 reuses V61 for PHY execution, and V72 provides the soft-buffer boundary. No duplicate mobility, propagation, MIMO or primary PHY engine was introduced.
