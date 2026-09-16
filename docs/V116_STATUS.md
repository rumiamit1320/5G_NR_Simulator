# V116 status

V116 connects the existing V101 DM-RS generator to the canonical OFDM/MIMO waveform and estimates the MIMO channel from received pilots before MMSE detection.

The implementation is additive and preserves historical V1-V115 APIs. V100 remains the validated coding/recovery baseline.

The implementation intentionally supports one and two layers in this milestone because the selected TYPE-1 ports provide deterministic frequency-orthogonal pilots without introducing an incomplete CDM implementation.

Full 3GPP bit-exact multi-layer PDSCH/PUSCH conformance remains a subsequent milestone.