# Canonical V116 DM-RS/MIMO boundary

V116 advances the canonical PHY from a known-channel MIMO validation boundary to a pilot-aided waveform path.

## Execution

1. Generate V101 DM-RS resources using the existing exact DM-RS implementation.
2. Map orthogonal TYPE-1 DM-RS ports 1000/1002 for one or two layers.
3. Build frequency-domain resource elements and transform them with the existing canonical FFT/IFFT.
4. Pass the waveform through the existing deterministic canonical MIMO channel.
5. FFT the received waveform.
6. Estimate each transmit-antenna channel from its received DM-RS pilots, with linear interpolation across the occupied spectrum.
7. Feed the estimated channel—not the injected channel—into the existing canonical MMSE detector.
8. Measure recovered-layer EVM, post-SINR, and channel-estimation MSE.

## Scope

This is an additive integration milestone. V1-V115 APIs are preserved. The validated V100 transport/coding/TB-CRC path remains the coding baseline in `NrCanonicalExecution`.

This milestone does not claim full 3GPP bit-exact PDSCH/PUSCH multi-layer conformance. Remaining work includes coupling the actual V100 rate-matched codeword to the V116 resource grid, layer-aware scrambling/modulation, full DM-RS CDM processing for all supported ports, frequency-selective/multi-tap estimation, and feeding estimated-channel soft LLRs into the LDPC decoder.