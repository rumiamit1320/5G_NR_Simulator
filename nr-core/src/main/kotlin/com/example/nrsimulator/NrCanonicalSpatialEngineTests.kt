package com.example.nrsimulator

import kotlin.math.sin
import kotlin.math.cos

/** Deterministic regression tests for the additive spatial/frequency-selective engine. */
object NrCanonicalSpatialEngineTests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val checks = linkedMapOf<String, Boolean>()
        checks["FFT/IFFT round-trip"] = fftRoundTrip()
        checks["TDL delay response"] = tdlDelay()
        checks["Frequency response"] = frequencyResponse()
        checks["LS interpolation"] = lsInterpolation()
        checks["2x2 ZF detection"] = mimoDetection("ZF")
        checks["2x2 MMSE detection"] = mimoDetection("MMSE")
        return Result(checks.values.all { it }, checks)
    }

    private fun fftRoundTrip(): Boolean {
        val x = Array(128) { i -> NrCanonicalSpatialEngine.C(sin(i * 0.13), cos(i * 0.07)) }
        val y = NrCanonicalSpatialEngine.fft(NrCanonicalSpatialEngine.fft(x, true), false)
        val err = y.indices.maxOf { i -> (y[i] - x[i]).abs() }
        return err < 1e-9
    }

    private fun tdlDelay(): Boolean {
        val x = Array(1) { Array(32) { i -> if (i == 3) NrCanonicalSpatialEngine.C(1.0, 0.0) else NrCanonicalSpatialEngine.C(0.0, 0.0) } }
        val tap = NrCanonicalSpatialEngine.Tap(2, arrayOf(arrayOf(NrCanonicalSpatialEngine.C(0.5, -0.25))))
        val r = NrCanonicalSpatialEngine.applyTdl(x, listOf(tap), 100.0, 17).output[0]
        val expected = NrCanonicalSpatialEngine.C(0.5, -0.25)
        return r[5].minus(expected).abs() < 1e-9 && r.withIndex().filter { it.index != 5 }.maxOf { it.value.abs() } < 1e-5
    }

    private fun frequencyResponse(): Boolean {
        val h0 = NrCanonicalSpatialEngine.C(1.0, 0.0)
        val h1 = NrCanonicalSpatialEngine.C(0.5, 0.0)
        val taps = listOf(
            NrCanonicalSpatialEngine.Tap(0, arrayOf(arrayOf(h0))),
            NrCanonicalSpatialEngine.Tap(1, arrayOf(arrayOf(h1)))
        )
        val h = NrCanonicalSpatialEngine.frequencyResponse(taps, 8)
        return (h[0][0][0].re - 1.5).let { kotlin.math.abs(it) } < 1e-9 && kotlin.math.abs(h[4][0][0].re - 0.5) < 1e-9
    }

    private fun lsInterpolation(): Boolean {
        val n = 8
        val trueH = Array(n) { k -> NrCanonicalSpatialEngine.C(1.0 + 0.02 * k, -0.1 + 0.01 * k) }
        val pilots = Array(1) { Array(1) { arrayOf(NrCanonicalSpatialEngine.C(1.0, 0.0), NrCanonicalSpatialEngine.C(1.0, 0.0)) } }
        val received = Array(1) { Array(n) { k -> trueH[k] } }
        val est = NrCanonicalSpatialEngine.estimateFromOrthogonalPilots(received, pilots, intArrayOf(0, 4))
        return (0..4).all { k -> est.h[k][0][0].minus(trueH[k]).abs() < 1e-9 }
    }

    private fun mimoDetection(method: String): Boolean {
        val h = arrayOf(
            arrayOf(NrCanonicalSpatialEngine.C(1.0, 0.0), NrCanonicalSpatialEngine.C(0.2, 0.1)),
            arrayOf(NrCanonicalSpatialEngine.C(-0.1, 0.15), NrCanonicalSpatialEngine.C(0.9, 0.0))
        )
        val x = arrayOf(NrCanonicalSpatialEngine.C(0.7, 0.2), NrCanonicalSpatialEngine.C(-0.4, 0.3))
        val y = Array(2) { r -> Array(1) { h[r][0] * x[0] + h[r][1] * x[1] } }
        val hGrid = Array(1) { h }
        val d = NrCanonicalSpatialEngine.detect(y, hGrid, 1e-9, method)
        return d.symbols[0][0].minus(x[0]).abs() < 1e-5 && d.symbols[1][0].minus(x[1]).abs() < 1e-5 && d.postSinrDb[0].isFinite()
    }
}
