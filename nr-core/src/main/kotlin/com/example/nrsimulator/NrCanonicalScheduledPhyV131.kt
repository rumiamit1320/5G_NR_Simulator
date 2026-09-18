package com.example.nrsimulator

/** V131: scheduled coded-PHY bridge. Reuses the canonical V117 waveform and V121 time-varying TDL. */
object NrCanonicalScheduledPhyV131 {
    data class Config(
        val payloadBits: Int = 256,
        val snrDb: Double = 35.0,
        val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK,
        val layers: Int = 1,
        val txAntennas: Int = layers,
        val rxAntennas: Int = layers,
        val tdlProfile: NrCanonicalTdlV118.Profile = NrCanonicalTdlV118.Profile.TDL_A,
        val dopplerHz: Double = 0.0,
        val timeSeconds: Double = 0.0,
        val seed: Int = 13101
    )
    data class Report(val passed: Boolean, val phy: NrCanonicalPhyV117.Report, val tdl: NrCanonicalTdlV118.Profile, val timeSeconds: Double)
    fun run(config: Config = Config()): Report {
        val phy = NrCanonicalPhyV117.run(NrCanonicalPhyV117.Config(
            payloadBits=config.payloadBits, snrDb=config.snrDb, modulation=config.modulation,
            layers=config.layers, txAntennas=config.txAntennas, rxAntennas=config.rxAntennas,
            seed=config.seed, tdlProfile=config.tdlProfile, timeVaryingTdl=config.dopplerHz > 0.0,
            tdlDopplerHz=config.dopplerHz, tdlTimeSeconds=config.timeSeconds))
        return Report(phy.passed, phy, config.tdlProfile, config.timeSeconds)
    }
}
