package com.example.nrsimulator

/** Canonical slot resource grid: 12 subcarriers per PRB, 14 symbols for normal CP. */
class NrCanonicalResourceGridV123(
    val prbCount: Int,
    val symbolsPerSlot: Int = 14,
    val layers: Int = 1,
    val slotIndex: Int = 0
) {
    data class Key(val slot: Int, val symbol: Int, val prb: Int, val subcarrier: Int, val layer: Int = 0) {
        init {
            require(symbol >= 0 && prb >= 0 && subcarrier in 0..11 && layer >= 0)
        }
        fun absoluteSubcarrier(): Int = prb * 12 + subcarrier
    }
    data class Allocation(val resources: List<Key>, val collisions: Int, val inserted: Int)

    init {
        require(prbCount > 0)
        require(symbolsPerSlot == 14)
        require(layers > 0)
        require(slotIndex >= 0)
    }

    private val used = LinkedHashSet<Key>()

    fun reserve(key: Key): Boolean = used.add(key)

    fun reserve(
        channelSlot: Int,
        symbolStart: Int,
        symbolCount: Int,
        prbStart: Int,
        prbCount: Int,
        layer: Int = 0
    ): Allocation {
        require(channelSlot >= 0)
        require(symbolStart in 0 until symbolsPerSlot)
        require(symbolCount > 0 && symbolStart + symbolCount <= symbolsPerSlot)
        require(prbStart >= 0 && prbCount > 0 && prbStart + prbCount <= prbCountTotal)
        require(layer in 0 until layers)
        val requested = ArrayList<Key>()
        for (l in layer..layer)
            for (s in symbolStart until symbolStart + symbolCount)
                for (p in prbStart until prbStart + prbCount)
                    for (k in 0..11)
                        requested += Key(channelSlot, s, p, k, l)
        val collisions = requested.count { it in used }
        var inserted = 0
        for (r in requested) if (used.add(r)) inserted++
        return Allocation(requested, collisions, inserted)
    }

    private val prbCountTotal: Int get() = prbCount
    fun contains(key: Key): Boolean = key in used
    fun usedCount(): Int = used.size
    fun freeCount(): Int = prbCount * symbolsPerSlot * 12 * layers - used.size
    fun allUsed(): List<Key> = used.toList()
    fun collisions(requested: Collection<Key>): Int = requested.count { it in used }
    fun clear() = used.clear()
}
