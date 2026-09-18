package com.example.nrsimulator

/** V127 additive integration facade: numerology -> scheduler -> multi-symbol PHY plan -> channel boundary. */
object NrCanonicalExecutionV127 {
    data class Config(
        val numerology: NrCanonicalNumerologyV123.Config = NrCanonicalNumerologyV123.Config(),
        val prbCount: Int = 24,
        val ues: List<NrCanonicalSchedulerV126.Ue> = listOf(
            NrCanonicalSchedulerV126.Ue(1, bufferBytes = 4096, priority = 2),
            NrCanonicalSchedulerV126.Ue(2, bufferBytes = 2048, priority = 1)
        ),
        val scheduler: NrCanonicalSchedulerV126.Config = NrCanonicalSchedulerV126.Config(prbCount = 24),
        val pdschSymbolStart: Int = 1,
        val pdschSymbolCount: Int = 12
    )
    data class Result(
        val passed: Boolean,
        val timing: NrCanonicalNumerologyV123.Timing,
        val schedule: NrCanonicalSchedulerV126.Report,
        val plans: List<NrCanonicalMultiSymbolV124.Plan>,
        val totalDataRe: Int,
        val totalDmrsRe: Int,
        val notes: String
    )

    fun run(config: Config = Config()): Result {
        require(config.prbCount > 0)
        val timing = NrCanonicalNumerologyV123.timing(config.numerology)
        val schedule = NrCanonicalSchedulerV126.schedule(config.ues, config.scheduler.copy(slot = config.numerology.slotIndex))
        val plans = schedule.grants.map { grant ->
            NrCanonicalMultiSymbolV124.plan(
                NrCanonicalMultiSymbolV124.Config(
                    slot = config.numerology.slotIndex,
                    prbStart = grant.prbStart,
                    prbCount = grant.prbCount,
                    startSymbol = config.pdschSymbolStart,
                    symbolCount = config.pdschSymbolCount,
                    layers = grant.layers,
                    direction = if (grant.direction == NrCanonicalSchedulerV126.Direction.DL)
                        NrCanonicalMultiSymbolV124.Direction.DOWNLINK else NrCanonicalMultiSymbolV124.Direction.UPLINK
                )
            )
        }
        val passed = timing.symbolsPerSlot == 14 &&
            schedule.scheduledPrbs <= config.prbCount &&
            plans.size == schedule.grants.size &&
            plans.all { it.data.isNotEmpty() && it.dmrs.isNotEmpty() }
        return Result(passed, timing, schedule, plans, plans.sumOf { it.data.size }, plans.sumOf { it.dmrs.size },
            "V127 preserves V1-V126 and provides an additive execution boundary for numerology, multi-symbol resources, scheduling, DM-RS-aware mapping and the V125 channel interface.")
    }
}
