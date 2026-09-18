package com.example.nrsimulator

/** Deterministic slot scheduler boundary tying UE demand, MCS and V123/V124 allocations. */
object NrCanonicalSchedulerV126 {
    enum class Direction { DL, UL }
    data class Ue(
        val id: Int,
        val direction: Direction = Direction.DL,
        val bufferBytes: Int = 0,
        val priority: Int = 0,
        val maxLayers: Int = 1
    )
    data class Config(
        val slot: Int = 0,
        val prbCount: Int = 24,
        val symbolsPerSlot: Int = 14,
        val startSymbol: Int = 1,
        val symbolCount: Int = 12,
        val maxPrbsPerUe: Int = 8,
        val layers: Int = 1
    )
    data class Grant(
        val ueId: Int,
        val direction: Direction,
        val prbStart: Int,
        val prbCount: Int,
        val startSymbol: Int,
        val symbolCount: Int,
        val layers: Int,
        val mcs: Int,
        val harqProcess: Int,
        val tbsBytes: Int
    )
    data class Report(val grants: List<Grant>, val scheduledPrbs: Int, val unscheduledPrbs: Int, val totalTbsBytes: Int)

    fun schedule(ues: List<Ue>, config: Config = Config()): Report {
        require(config.prbCount > 0 && config.maxPrbsPerUe > 0 && config.layers > 0)
        val sorted = ues.filter { it.bufferBytes > 0 }.sortedWith(compareByDescending<Ue> { it.priority }.thenBy { it.id })
        val grants = ArrayList<Grant>()
        var cursor = 0
        for ((index, ue) in sorted.withIndex()) {
            if (cursor >= config.prbCount) break
            val prbs = minOf(config.maxPrbsPerUe, config.prbCount - cursor)
            val layers = minOf(config.layers, ue.maxLayers).coerceAtLeast(1)
            val mcs = chooseMcs(ue.bufferBytes, prbs, config.symbolCount, layers)
            val bitsPerRe = when {
                mcs <= 9 -> 2
                mcs <= 16 -> 4
                else -> 6
            }
            val re = prbs * config.symbolCount * 12 * layers
            val tbs = minOf(ue.bufferBytes, (re * bitsPerRe) / 8)
            grants += Grant(ue.id, ue.direction, cursor, prbs, config.startSymbol, config.symbolCount, layers, mcs, index % 16, tbs)
            cursor += prbs
        }
        return Report(grants, cursor, config.prbCount - cursor, grants.sumOf { it.tbsBytes })
    }

    private fun chooseMcs(bytes: Int, prbs: Int, symbols: Int, layers: Int): Int {
        val demand = bytes.toDouble() / (prbs * symbols * 12 * layers).coerceAtLeast(1)
        return when { demand > 0.55 -> 20; demand > 0.25 -> 12; else -> 4 }
    }
}
