# Research-Grade 5G NR Simulator Roadmap

This roadmap upgrades the existing simulator without replacing its architecture. V66 remains the system integration spine; later versions deepen existing PHY, MAC, RRC/NAS and core components.

## Gate 1 — Soft PHY / HARQ
Status: **started**

- V73 symbol-derived max-log LLR generation for QPSK/16QAM/64QAM/256QAM.
- RV-aware soft rate recovery with additive LLR accumulation.
- Soft-input Polar successive-cancellation decoder.
- CRC remains decoder-derived; confidence thresholds are not allowed to manufacture ACKs.
- V61 exposes the complete equalized symbol vector additively for research analysis.

Acceptance:
- deterministic repeatability;
- LLR sign correctness against known constellation points;
- AWGN BLER curves;
- HARQ combining gain versus single-shot transmission;
- CRC consistency.

## Gate 2 — Normative NR LDPC

Replace the V46 reference LDPC orchestration with a real QC-LDPC engine aligned to TS 38.212:

1. transport-block CRC24A;
2. base-graph selection;
3. code-block segmentation;
4. code-block CRC24B;
5. lifting-factor selection;
6. BG1/BG2 quasi-cyclic encoding;
7. exact circular-buffer rate matching and RV bit selection;
8. inverse rate recovery with filler handling;
9. layered/min-sum or normalized-min-sum decoding;
10. code-block CRC and TB CRC.

Acceptance:
- parity-check syndrome is zero for every generated codeword;
- encoder/decoder round trips at zero noise;
- deterministic known-answer tests;
- BLER/SNR curves against independent reference results;
- RV0/RV2/RV3/RV1 HARQ combining.

## Gate 3 — Real PDSCH/PUSCH waveform chain

Close the full data-channel loop:

MAC TB → CRC → LDPC → rate matching → scrambling → QAM → layer mapping → precoding → DM-RS/PTRS → resource grid → OFDM → channel → OFDM demodulation → channel estimation → MIMO equalization → soft demodulation → rate recovery → LDPC → CRC → MAC.

Existing V61 remains the compatibility laboratory path until this new chain is validated.

Acceptance:
- PDSCH and PUSCH resource-grid correctness;
- DM-RS channel-estimation error versus SNR;
- ZF/MMSE MIMO performance;
- BLER curves for supported MCS configurations.

## Gate 4 — Control channels and initial access

Implement standards-oriented execution for:

- SS/PBCH block generation/detection;
- SSB beam sweeping;
- PRACH preamble generation/detection;
- RAR;
- Msg3/Msg4;
- PDCCH search spaces;
- DCI encoding/decoding;
- PDSCH scheduling activation.

Acceptance:
- UE can progress from cell search to RRC-connected state without bypassing the radio procedures.

## Gate 5 — Closed-loop MAC/RLC/HARQ

Connect actual PHY measurements and CRC outcomes to:

- CQI/PMI/RI;
- MCS selection;
- TBS calculation;
- proportional-fair and QoS scheduling;
- SR/BSR;
- RLC AM retransmission/status;
- HARQ process persistence;
- latency and queueing metrics.

Acceptance:
- throughput, BLER, latency, fairness and retransmission statistics remain internally consistent.

## Gate 6 — RRC/NAS/5GC system procedures

Turn the existing protocol boundaries into persistent procedures:

UE ↔ gNB RRC
UE ↔ AMF NAS
NGAP
GTP-U
PDU session establishment/release
QoS-flow handling
handover/path-switch procedures

Acceptance:
- UE registration and PDU-session state transitions are persistent and observable;
- user-plane packets traverse UE → gNB → UPF → data network model.

## Gate 7 — Channel/MIMO research fidelity

Deepen existing V15/V16/V27/V28/V62 components rather than creating duplicates:

- calibrated TDL/CDL statistics;
- Doppler and delay spread validation;
- spatial correlation;
- LOS/NLOS;
- blockage;
- beam management and recovery;
- CSI-RS/SRS;
- codebook precoding;
- rank adaptation;
- MMSE/ZF equalization;
- multi-cell interference.

Acceptance:
- statistical outputs match independent reference distributions within documented tolerances.

## Gate 8 — Research validation framework

Build automated experiments for:

- BER/BLER vs SNR;
- throughput vs SNR;
- HARQ gain;
- MCS curves;
- MIMO rank/antenna scaling;
- TDL/CDL channel statistics;
- mobility/handover interruption;
- scheduler fairness/latency;
- multi-cell interference;
- reproducibility by seed.

Each experiment must record configuration, seed, simulator revision, metrics and pass/fail tolerance.

## Non-negotiable architecture rule

Do not create a second mobility engine, second scheduler, second primary PHY, or second network simulator. New versions must either deepen an existing component or provide a thin adapter around it. V66 remains the integration spine.

## Standards target

The primary normative target is a declared 3GPP release. The PHY work is anchored to TS 38.211–38.215, with higher-layer procedure work anchored to the corresponding 38.2xx/38.3xx specifications. Conformance claims must be backed by independent known-answer and performance tests; internal simulator self-consistency is not sufficient.

## Definition of research-grade

The project is considered research-grade only when the simulator can produce reproducible, physically meaningful waveform-level results; maintain persistent protocol state across the stack; support real soft information and decoder-based HARQ; expose controlled experiment configurations; and validate its outputs against independent analytical or reference results. A large number of versioned classes alone does not satisfy this criterion.
