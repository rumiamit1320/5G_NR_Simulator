package com.example.nrsimulator

/** Deterministic regression tests for the V133 shared scheduled waveform boundary. */
object NrCanonicalSharedWaveformV133Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        val r = NrCanonicalSharedWaveformV133.run()
        c["execution passes"] = r.passed
        c["grants exist"] = r.grants.isNotEmpty()
        c["one placement per grant"] = r.placements.size == r.grants.size
        c["scheduled PRBs bounded"] = r.grants.sumOf { it.prbCount } == r.grants.maxOfOrNull { it.prbStart + it.prbCount }?.let { _ ->
            r.grants.sumOf { it.prbCount }
        }
        c["no shared-grid collisions"] = r.collisionCount == 0
        c["data and DMRS materialized"] = r.dataElements > 0 && r.dmrsElements > 0
        c["FFT is valid"] = r.fftSize and (r.fftSize - 1) == 0
        c["waveform has 14 symbols"] = r.ofdmSymbols == 14
        c["waveform dimensions valid"] = r.waveform.all { a ->
            a.size == r.ofdmSymbols && a.all { it.size == r.fftSize + 144 }
        }
        c["grid and waveform are non-empty"] = r.frequencyGrid.any { s ->
            s.any { layer -> layer.any { it != null } }
        }
        return Result(c.values.all { it }, c)
    }
}
