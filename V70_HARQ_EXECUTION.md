# V70 Additive HARQ Execution

V70 extends the existing V51/V64/V66/V67 stack without replacing any established API.

## Execution path

`V66 PHY -> V67 slot timing -> V70 persistent HARQ process scheduler -> V51 state/RV/soft bookkeeping`

V70 runs the existing V66 radio execution over an extended slot horizon. A NACKed transport block is retained by `(UE, HARQ process)` and assigned a stable `tbId`. After the configured ACK delay, the same process receives a retransmission opportunity. The CRC result for that retransmission comes from the existing V66 slot result; V70 does not synthesize a CRC outcome.

## HARQ behavior

- persistent per-UE/process state
- stable transport-block identity across retransmissions
- ACK/NACK feedback delay
- maximum four transmissions
- RV sequence `0 -> 2 -> 3 -> 1`
- abstract soft-buffer accumulation through the existing V51 primitive
- retransmission priority over a new TB on the same process
- retransmission slots may extend beyond the original requested simulation horizon

## Architecture boundary

V70 is an additive execution adapter. It does **not** replace V51, V64, V66, or V67 and does not implement a second OFDM/channel/PHY chain.

The current boundary is intentionally execution-level: V66 produces the PHY/CRC observation, while V70 supplies HARQ process identity, timing, RV and state transitions around that observation. A future deeper PHY enhancement can pass RV-specific rate matching and true soft LLR buffers through the same adapter without changing the established layers.
