# Canonical Spatial/Frequency-Selective PHY Engine

This additive layer extends the canonical NR simulator without deleting or changing V1-V110 implementations.

## Implemented

- Radix-2 FFT/IFFT for power-of-two waveform sizes.
- Complex MIMO tapped-delay-line (TDL) convolution:
  \[
  y_r[n]=\sum_t\sum_p h_{r,t,p}x_t[n-\tau_p]+w_r[n]
  \]
- Frequency response per subcarrier:
  \[
  H_{r,t}[k]=\sum_p h_{r,t,p}e^{-j2\pi k\tau_p/N}
  \]
- Pilot-aided LS channel estimation with linear frequency interpolation.
- General MIMO ZF and MMSE detection using
  \[
  W_{ZF}=(H^HH)^{-1}H^H
  \]
  and
  \[
  W_{MMSE}=(H^HH+\sigma_n^2I)^{-1}H^H.
  \]
- Deterministic regression tests for FFT round-trip, TDL delay response, frequency response, LS interpolation, 2x2 ZF and 2x2 MMSE.

## Compatibility

The layer is additive. Existing V1-V110 classes, APIs, web routes and Android interfaces are retained.

The existing V100 end-to-end execution remains the compatibility/default single-layer path. The new engine is exposed through the canonical component catalog and core regression suite so that its numerical behavior can be validated before replacing historical implementations in production paths.

## Next integration boundary

The next safe integration step is to feed a true multi-port NR DM-RS resource grid into this engine, estimate the full \(H[k,l]\) tensor, and use the resulting per-RE detector for PDSCH/PUSCH. That integration should be gated by 3GPP reference vectors before changing the default V100 execution semantics.
