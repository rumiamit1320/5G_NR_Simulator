package com.example.nrsimulator

/**
 * V82 additive standards-oriented NR LDPC planning layer.
 * Existing NrLdpcV74 remains untouched.
 */
object NrLdpcV82 {
    enum class BaseGraph { BG1, BG2 }

    private val liftingSets = arrayOf(
        intArrayOf(2, 4, 8, 16, 32, 64, 128, 256),
        intArrayOf(3, 6, 12, 24, 48, 96, 192, 384),
        intArrayOf(5, 10, 20, 40, 80, 160, 320),
        intArrayOf(7, 14, 28, 56, 112, 224),
        intArrayOf(9, 18, 36, 72, 144, 288),
        intArrayOf(11, 22, 44, 88, 176, 352),
        intArrayOf(13, 26, 52, 104, 208),
        intArrayOf(15, 30, 60, 120, 240)
    )

    fun liftingSet(z: Int): Int {
        for (i in liftingSets.indices) if (z in liftingSets[i]) return i
        throw IllegalArgumentException("Unsupported NR LDPC lifting size Z=$z")
    }

    fun allowedLiftingSizes(): IntArray = liftingSets.flatMap { it.asIterable() }.toIntArray()

    fun selectBaseGraph(tbsBits: Int, targetCodeRate: Double): BaseGraph {
        require(tbsBits >= 0 && targetCodeRate > 0.0)
        return if (tbsBits <= 292 || targetCodeRate <= 0.25 ||
            (tbsBits <= 3824 && targetCodeRate <= 0.67)) BaseGraph.BG2 else BaseGraph.BG1
    }

    data class Geometry(val rows: Int, val columns: Int, val informationColumns: Int, val codeColumns: Int)

    fun geometry(bg: BaseGraph): Geometry = when (bg) {
        BaseGraph.BG1 -> Geometry(46, 68, 22, 66)
        BaseGraph.BG2 -> Geometry(42, 52, 10, 50)
    }

    /** Select the smallest standardized Z for the supplied Kb. */
    fun selectLiftingSize(bg: BaseGraph, kPrime: Int, kb: Int = geometry(bg).informationColumns): Int {
        require(kPrime > 0 && kb > 0)
        return allowedLiftingSizes().filter { kb * it >= kPrime }.minOrNull()
            ?: throw IllegalArgumentException("No NR LDPC lifting size can carry K'=$kPrime with Kb=$kb")
    }

    fun parityCheckSize(bg: BaseGraph, z: Int): Int = geometry(bg).rows * z
    fun encodedSize(bg: BaseGraph, z: Int): Int = geometry(bg).codeColumns * z
}
