# 5G NR Simulator — Conformance Status

## Architecture
V1–V45 remain intact. V46–V60 are additive modules and adapters; V65 is an additional network orchestration layer. No existing public simulator module was removed or renamed.

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

## V61–V65
- V61: integrated laboratory PHY link using the existing coding, modulation, OFDM and CRC boundaries.
- V62: additive multi-UE/multi-cell radio environment with mobility, path loss, shadowing, interference, Doppler, CQI/MCS, rank adaptation and PRB allocation.
- V63: additive bridge from V62 radio conditions into the V61 PHY.
- V64: additive closed-loop execution with PHY ACK/NACK and goodput feedback into proportional-fair history.
- V65: additive network orchestration with explicit gNB topology, UE positions, serving-cell association, handover hysteresis, traffic-demand accounting, cell-load KPIs and network-level fairness.

## Important limitation
This project is **not 3GPP certified**. ETSI/3GPP normative specifications define substantially more procedure detail and conformance testing than can be established by software round-trip tests alone. Official Release-19 conformance specifications, RF tests, interoperability testing, and applicable certification evidence remain external requirements.

## Architecture / Execution Separation

V1–V65 APIs are preserved. Pure Kotlin NR logic is hosted in `:nr-core` so the same implementation can be consumed by the Android app and standalone JVM verification. `MainActivity` remains Android-only. V65 is an orchestration layer over V62/V64; it does not replace their radio or PHY implementations. This separation does not imply 3GPP certification; it makes network-level experimentation reusable without coupling the existing Android UI to the new simulation layer.
