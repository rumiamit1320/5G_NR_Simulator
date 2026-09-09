# 5G NR Simulator — Conformance Status

## Architecture
V1–V45 remain intact. V46–V60 are additive modules and adapters; no existing public simulator module was removed or renamed.

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

## Important limitation
This project is **not 3GPP certified**. ETSI/3GPP normative specifications define substantially more procedure detail and conformance testing than can be established by software round-trip tests alone. Official Release-19 conformance specifications, RF tests, interoperability testing, and applicable certification evidence remain external requirements.

## Architecture / Execution Separation

V1–V60 APIs are preserved. Pure Kotlin NR logic is now hosted in `:nr-core` so the same implementation can be consumed by the Android app and a standalone JVM verification runner. `MainActivity` remains Android-only. This separation does not imply 3GPP certification; it only makes verification and reuse independent of the Android UI.
