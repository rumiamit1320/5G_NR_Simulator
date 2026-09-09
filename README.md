# 5G NR Simulator — V30 Hardened Integration

This project preserves the V1–V30 versioned architecture and adds an integration-only execution layer.

## New hardening layer

- `NrRrcIntegration.kt` — compact RRC state/config integration facade.
- `NrEndToEndV30.kt` — executable slot-level downlink path:
  - application byte buffer
  - PDCP PDU generation
  - RLC segmentation/reassembly
  - multi-UE scheduling
  - DCI generation
  - PDCCH encode/decode reference path
  - PDSCH PHY reference link using the existing V12 engine
  - CRC/LDPC result
  - HARQ RV sequence 0→2→3→1
  - PUCCH/UCI ACK reporting
  - byte-delivery and throughput accounting
- `NrEndToEndV30Tests.kt` — integration regression tests.

The existing V1–V30 modules remain independently callable and were not replaced. The new layer is deliberately additive so individual modules can later be upgraded to exact 3GPP procedures without changing the UI-facing architecture.

## Conformance scope

The integration layer is a simulator/reference path, not a claim of complete 3GPP conformance. In particular, PDCCH coding/scrambling, PUCCH/PUSCH details, RRC signaling, RLC/PDCP procedures, and 5GC signaling retain the reference-level limitations of the underlying versioned modules.

The architecture is aligned with the NR overall description and RRC layering represented by 3GPP TS 38.300 and TS 38.331.

## V31 conformance foundation
V31 is additive and leaves V1-V30 APIs intact. It adds exact low-level NR/security primitives (38.211 Gold sequence, CRC24C, 128-NEA2, 128-NIA2), an RLC AM state foundation, and an RRC transaction/configuration foundation. This is a conformance-enabling layer, not a claim of complete 3GPP certification.


### V32–V45 Conformance Expansion

The project now contains additive V32–V45 modules. They preserve the existing V1–V31 architecture and expose isolated adapters/foundations rather than rewriting the existing simulator. V45 is a certification gate and intentionally remains closed until external normative test evidence is available.

### V46–V60 NR core upgrade

V46–V60 are additive modules layered above the retained V1–V45 implementation. They add stronger transport/LDPC orchestration, polar/PDCCH boundaries, resource-grid and numerology models, HARQ/MAC/RLC/PDCP state, RRC/NAS/5GC user-plane boundaries, UE/gNB split interfaces, a conformance harness, and a Release-19 compliance matrix.

The implementation intentionally distinguishes executable simulator/reference behavior from normative conformance. In particular, V47/V48 and V55 are codec boundaries rather than claims of complete 3GPP Polar/PDCCH or generated TS 38.331 ASN.1 conformance. V45 remains closed and V60 reports the remaining evidence requirements.

The current public Release-19 baseline includes TS 38.211/212/213/214 V19.4.0 and TS 38.331 V19.3.0 in ETSI's 2026 publication/work-programme records.

## Shared JVM NR Core (architecture-preserving)

The V1–V60 implementation is now organized so the existing Android application remains the UI/application module while the 84 pure-Kotlin NR implementation sources are supplied by the new `:nr-core` JVM module. The public package/API remains `com.example.nrsimulator`; this is a source relocation behind the same package boundary, not a rewrite of the simulator architecture.

- `:app` — existing Android/Compose application; `MainActivity.kt` remains in place.
- `:nr-core` — pure Kotlin/JVM implementation used by Android and standalone JVM verification.
- `nr-core/src/test/.../CoreRegressionMain.kt` — standalone regression entry point.

Standalone JVM regression can be run through the Gradle `:nr-core` module when a Gradle installation/wrapper is available. In the current environment, the equivalent JVM compilation was executed directly with `kotlinc` and produced `CORE_REGRESSION=PASS`.
