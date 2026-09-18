# V128-V132 additive canonical NR execution upgrade

V128 materializes the complete multi-symbol V123 slot allocation and reuses the V124 DM-RS-aware planner.

V129 adds a deterministic physical symbol mapper over the scheduled data and DM-RS resource elements.

V130 adds LS DM-RS estimation with deterministic time/frequency nearest-neighbour interpolation across the complete scheduled allocation.

V131 bridges scheduled execution to the existing canonical V117 coded PHY and reuses V121 Jakes-style TDL when Doppler is enabled.

V132 provides a multi-UE scheduled execution facade: V126 scheduling -> V124 resource planning -> V131/V117 coded PHY, while retaining all earlier public APIs.

The implementation is additive. V117-V127 are not replaced or removed. The V128-V132 layer is intentionally a migration bridge; it is not a claim of complete 3GPP bit-exact PDSCH/PUSCH conformance. The standards target remains TS 38.211, 38.212, 38.213 and 38.214, with TR 38.901 for channel modelling. The current 3GPP portal lists these NR specifications under change control. 
