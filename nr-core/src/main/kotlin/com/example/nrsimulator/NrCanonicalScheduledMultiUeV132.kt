package com.example.nrsimulator

/** V132: end-to-end scheduled multi-UE execution facade, additive to V117-V131. */
object NrCanonicalScheduledMultiUeV132 {
    data class Config(
        val numerology: NrCanonicalNumerologyV123.Config = NrCanonicalNumerologyV123.Config(),
        val prbCount: Int = 24,
        val ues: List<NrCanonicalSchedulerV126.Ue> = listOf(
            NrCanonicalSchedulerV126.Ue(1, bufferBytes = 2048, priority = 2, maxLayers = 1),
            NrCanonicalSchedulerV126.Ue(2, bufferBytes = 2048, priority = 1, maxLayers = 1)
        ),
        val snrDb: Double = 35.0,
        val dopplerHz: Double = 0.0,
        val seed: Int = 13201
    )
    data class UeResult(val ueId: Int, val prbStart: Int, val prbCount: Int, val phy: NrCanonicalScheduledPhyV131.Report)
    data class Report(
        val passed: Boolean,
        val grants: List<NrCanonicalSchedulerV126.Grant>,
        val ueResults: List<UeResult>,
        val scheduledPrbs: Int,
        val totalDataRe: Int,
        val totalDmrsRe: Int,
        val notes: String
    )
    fun run(config: Config = Config()): Report {
        require(config.prbCount > 0 && config.ues.isNotEmpty())
        val schedule = NrCanonicalSchedulerV126.schedule(
            config.ues,
            NrCanonicalSchedulerV126.Config(prbCount=config.prbCount, slot=config.numerology.slotIndex))
        val results = schedule.grants.mapIndexed { i, g ->
            val phy = NrCanonicalScheduledPhyV131.run(NrCanonicalScheduledPhyV131.Config(
                payloadBits=(g.tbsBytes * 8).coerceIn(64, 512), snrDb=config.snrDb,
                layers=g.layers, txAntennas=g.layers, rxAntennas=g.layers,
                dopplerHz=config.dopplerHz, timeSeconds=config.numerology.slotIndex * 0.001,
                seed=config.seed + i))
            UeResult(g.ueId, g.prbStart, g.prbCount, phy)
        }
        val totalData = schedule.grants.sumOf { g ->
            NrCanonicalMultiSymbolV124.plan(NrCanonicalMultiSymbolV124.Config(
                slot=config.numerology.slotIndex, prbStart=g.prbStart, prbCount=g.prbCount,
                layers=g.layers, direction=if (g.direction==NrCanonicalSchedulerV126.Direction.DL)
                    NrCanonicalMultiSymbolV124.Direction.DOWNLINK else NrCanonicalMultiSymbolV124.Direction.UPLINK)).data.size
        }
        val totalDmrs = schedule.grants.sumOf { g ->
            NrCanonicalMultiSymbolV124.plan(NrCanonicalMultiSymbolV124.Config(
                slot=config.numerology.slotIndex, prbStart=g.prbStart, prbCount=g.prbCount,
                layers=g.layers, direction=if (g.direction==NrCanonicalSchedulerV126.Direction.DL)
                    NrCanonicalMultiSymbolV124.Direction.DOWNLINK else NrCanonicalMultiSymbolV124.Direction.UPLINK)).dmrs.size
        }
        val passed = schedule.scheduledPrbs <= config.prbCount && results.size == schedule.grants.size &&
            results.all { it.phy.phy.payloadBits == (schedule.grants.first { g -> g.ueId == it.ueId }.tbsBytes * 8).coerceIn(64,512) }
        return Report(passed, schedule.grants, results, schedule.scheduledPrbs, totalData, totalDmrs,
            "V132 schedules multiple UEs and invokes the canonical V131/V117 coded PHY; V123-V131 remain additive.")
    }
}
