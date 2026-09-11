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

## Web simulator validation and deployment

The web layer is additive: it calls the retained Kotlin reference engine rather than duplicating NR algorithms in JavaScript. The web CI exercises representative end-to-end parameter sets spanning low-SNR QPSK, mid-SNR 64-QAM, high-SNR 256-QAM, and conservative 16-QAM. The checks validate the JSON contract and finite primary metrics rather than asserting artificial monotonic behavior on stochastic channel models.

`tools/validate_parameter_sweep.sh` extends this into a repeatable boundary sweep covering SNR −5…35 dB, PRB/layer/MIMO extremes, MCS 0…27, SCS 15/30/60 kHz, and HARQ on/off. It also drives distinct inputs through the V49–V58 interactive boundaries and verifies their returned results. Deterministic relationships already represented by the retained `Simulator` are checked (notably BER versus SNR); parameters whose legacy model does not affect a metric are validated as configuration inputs rather than given artificial effects.

The V19–V60 laboratory has an automated educational-visualization check. It verifies that the lab page, visualization renderer, explanatory "How to read it" text, V19/V30 visualizations, and V31–V60 extension are present, and that representative V19/V22/V29 API routes remain live. V49–V58 now expose their actual existing Kotlin model parameters through the web adapter while V44/V45/V59/V60 remain canonical reference vectors.

## V61 end-to-end link laboratory

V61 is a separate additive execution path in `NrIntegratedLinkV61.kt`. It does not replace or modify the existing V1–V60 engines. The pipeline is:

`bits → CRC-24C → scrambling → Polar V47 → rate matching → QAM → layer/precoding boundary → OFDM → AWGN → MMSE/ZF equalization → demodulation → rate recovery → Polar decode → descrambling → CRC → BER/EVM/throughput`.

The web endpoint is `/api/link`, and the interactive laboratory is `/link.html`. CI validates QPSK, 16-QAM, 64-QAM and 256-QAM configurations. V61 currently uses the retained V47 Polar decoder for its bit-accurate round trip; the existing V46/V8 LDPC engines remain intact and are not replaced. V61's rate-matching window is deliberately reversible for this hard-decision laboratory path rather than being presented as a normative 38.212 LDPC rate-matching implementation.

The V61 result exposes stage-by-stage diagnostics, coded/transmitted/decoded bit counts, CRC status, BER, EVM, throughput, OFDM occupancy and TX/RX constellation samples. The web layer and CI therefore exercise the new chain without changing the current simulator API or Android architecture.

For deployment, the web server can be packaged with:

```bash
gradle :web-server:installDist --no-daemon
tar -C web-server/build/install -czf 5g-nr-simulator-web.tar.gz web-server
```

A root `Dockerfile` builds the same `:web-server` distribution on JDK 17 and runs it as an unprivileged user on port 8080. CI builds the container, runs the scenario/visualization/parameter-sweep/V61 validations, and publishes the standalone distribution as a workflow artifact. This is deployment-ready packaging; no hosted production service or 3GPP certification claim is implied.
