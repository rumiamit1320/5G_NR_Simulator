# Canonical PHY Migration — V100

This document defines the additive migration boundary for the V92–V100 PHY expansion.

## Rules

1. V1–V91 implementations remain source-compatible.
2. No historical versioned class is deleted in this migration step.
3. `NrCanonicalPhy` is the registry/composition boundary, not a replacement implementation.
4. New integrations should depend on canonical stage contracts rather than directly creating additional `Vxx` engines.
5. Normative 3GPP conformance is not implied by this abstraction.

## Canonical forward path

`TB -> CRC/segmentation -> LDPC -> rate matching -> scrambling/QAM -> layer mapping -> DMRS -> resource mapping -> OFDM -> channel -> channel estimation -> MIMO equalization -> demapping -> demodulation -> rate recovery -> LDPC decode -> CRC`

## Ownership

| Stage | Current implementation | Migration status |
|---|---|---|
| Transport/CRC | `NrTransportV83`, `NrCodingChainV87` | canonical candidate |
| LDPC geometry/tables | `NrLdpcV85` | canonical candidate |
| LDPC codec | `NrLdpcCodecV86` | canonical candidate |
| Rate matching | `NrCodingChainV87` | canonical candidate |
| QAM/layer mapping | `NrPhyMappingV89` | canonical candidate |
| DMRS/estimation | `NrDmrsMimoV90`, `NrDmrsV92` | consolidate after validation |
| PDSCH/PUSCH mapping | `NrPdschPuschV91` | canonical candidate |
| OFDM | `NrOfdmV93` | canonical candidate |
| MIMO | `NrMimoV94` | canonical candidate |
| Channel | `NrChannelV95` | canonical candidate |
| Link adaptation | `NrLinkAdaptationV96` | canonical candidate |
| HARQ/CSI | `NrHarqCsiV97` | canonical candidate |
| PRACH | `NrRachV98` | canonical candidate |
| Reference validation | `NrRefVectorsV99`, `NrResearchGradeV100` | regression layer |

## Integration boundary

`LabApiV92V100` is deliberately separate from the historical `LabApi` implementation. The main dispatcher can route V92–V100 to this facade without changing V19–V91 behavior.

## Next consolidation gate

Before deleting or merging any Vxx implementation:

- Android compile passes.
- JVM/core tests pass.
- Web build passes.
- V19–V91 API verification passes.
- V92–V100 regression passes.
- Live `/api/lab` checks pass for V85–V100.
- A dependency scan confirms no compatibility/test/reference-vector consumer is lost.
