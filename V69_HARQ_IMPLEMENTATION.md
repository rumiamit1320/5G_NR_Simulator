# V69 Persistent HARQ Execution

V69 is an additive execution/state layer above the retained V51 HARQ primitive and V67 timing spine.

## Scope

- Keeps V51 as the HARQ state/RV primitive.
- Keeps V67 as the authoritative NR slot clock.
- Keeps V64/V66 as the existing PHY and integrated radio execution paths.
- Tracks per-UE/process transmission number and RV progression.
- Associates ACK/NACK with a configurable feedback slot.
- Exposes the next retransmission opportunity after a NACK.
- Bounds transmissions to the configured maximum of four.

## Physical execution boundary

V69 does not invent a retransmitted PHY result. The existing V64/V66 PHY must be invoked by a later scheduler/execution adapter at the exposed retransmission slot so that the retry can produce a new CRC/ACK/NACK result. This separation prevents duplicate PHY implementations and preserves the established architecture.
