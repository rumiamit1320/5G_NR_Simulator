# V124–V127 additive canonical NR execution

V124 adds a multi-symbol resource planner using the V123 14-symbol/12-subcarrier grid and the existing V101 DM-RS mapper.

V125 adds a channel-processing boundary with deterministic fractional-delay interpolation and AWGN. Existing V118/V121 TDL/Jakes implementations remain the authoritative channel generators and are not replaced.

V126 adds deterministic UE scheduling with DL/UL direction, contiguous PRB grants, layer limits, MCS selection, HARQ-process indexing and TBS estimation.

V127 ties V123–V126 together in a canonical execution facade. It intentionally does not replace V117–V122 waveform execution. The result is a staged migration path rather than a parallel PHY implementation.

The project is not thereby 3GPP-certified. Detailed conformance still requires bit-exact mapping, complete TS 38.211/38.212/38.213/38.214 procedures, reference vectors and broader channel/MIMO validation.
