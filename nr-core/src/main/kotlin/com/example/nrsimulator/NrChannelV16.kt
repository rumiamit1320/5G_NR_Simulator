package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

/**
 * V16: 3GPP TR 38.901-oriented link-level channel layer.
 *
 * This is intentionally additive to V1-V15.  It provides deterministic TDL-A..E
 * profiles, a compact CDL-style clustered model, delay scaling, Doppler, LOS/Ricean
 * behavior, and separable spatial correlation for MIMO.  It is a simulator/reference
 * implementation rather than a claim of full 38.901 coefficient-generation conformance.
 */
data class NrChannelV16Config(
    val model: String = "TDL-C",
    val txAntennas: Int = 2,
    val rxAntennas: Int = 2,
    val scsKHz: Int = 30,
    val prbs: Int = 52,
    val carrierGHz: Double = 3.5,
    val velocityKmh: Double = 30.0,
    val rmsDelayNs: Double = 100.0,
    val spatialCorrelation: Double = 0.35,
    val losKDb: Double = 9.0,
    val snrDb: Double = 20.0,
    val timeIndex: Int = 0,
    val seed: Int = 0x1601
)

data class NrChannelV16Result(
    val model: String,
    val txAntennas: Int,
    val rxAntennas: Int,
    val taps: Int,
    val rmsDelayNs: Double,
    val dopplerHz: Double,
    val spatialCorrelation: Double,
    val los: Boolean,
    val meanPowerDb: Double,
    val frequencySelectivityDb: Double,
    val coherenceTimeUs: Double,
    val pass: Boolean,
    val note: String
)

class NrChannelV16(private val cfg: NrChannelV16Config) {
    private val tx = cfg.txAntennas.coerceIn(1, 8)
    private val rx = cfg.rxAntennas.coerceIn(1, 8)
    private val nSc = (cfg.prbs.coerceIn(1, 275) * 12).coerceAtLeast(12)
    private val rng = Random(cfg.seed)
    private val model = cfg.model.uppercase()
    private val base = profile(model)
    private val scale = cfg.rmsDelayNs.coerceAtLeast(1.0) / rms(base.delays)
    private val delaysNs = base.delays.map { it * scale }.toDoubleArray()
    private val powers = base.powersDb.map { 10.0.pow(it / 10.0) }.toDoubleArray()
    private val norm = sqrt(powers.sum().coerceAtLeast(1e-12))
    private val delaysSec = delaysNs.map { it * 1e-9 }.toDoubleArray()
    private val fd = cfg.velocityKmh.coerceAtLeast(0.0) / 3.6 / 299792458.0 * cfg.carrierGHz * 1e9
    private val rho = cfg.spatialCorrelation.coerceIn(0.0, 0.99)
    private val txL = chol(expCorr(tx, rho))
    private val rxL = chol(expCorr(rx, rho))
    private val taps = Array(rx) { Array(tx) { Array(delaysSec.size) { Complex(0.0, 0.0) } } }
    private val los = model == "TDL-D" || model == "TDL-E" || model == "CDL-D" || model == "CDL-E"

    init {
        val iid = Array(rx) { Array(tx) { Array(delaysSec.size) { Complex(0.0, 0.0) } } }
        for (r in 0 until rx) for (t in 0 until tx) for (p in delaysSec.indices) {
            val s = sqrt(powers[p]) / norm / sqrt(2.0)
            iid[r][t][p] = Complex(g() * s, g() * s)
        }
        for (p in delaysSec.indices) {
            val spatial = applyKronecker(iid, p)
            for (r in 0 until rx) for (t in 0 until tx) taps[r][t][p] = spatial[r][t]
        }
        if (los) addLosComponent()
    }

    fun frequencyResponse(subcarrier: Int, totalSubcarriers: Int = nSc, timeIndex: Int = cfg.timeIndex): Array<Array<Complex>> {
        val out = Array(rx) { Array(tx) { Complex(0.0, 0.0) } }
        val centered = subcarrier - totalSubcarriers / 2.0
        val df = cfg.scsKHz * 1000.0
        for (r in 0 until rx) for (t in 0 until tx) {
            var h = Complex(0.0, 0.0)
            for (p in delaysSec.indices) {
                val phaseDelay = -2.0 * PI * centered * df * delaysSec[p]
                val phaseDoppler = 2.0 * PI * fd * timeIndex / (cfg.scsKHz * 1000.0)
                val ph = phaseDelay + phaseDoppler * (1.0 + 0.13 * p)
                h += taps[r][t][p] * Complex(cos(ph), sin(ph))
            }
            out[r][t] = h
        }
        return out
    }

    fun apply(txGrid: Array<Array<Array<Complex>>>, snrDb: Double = cfg.snrDb, timeIndex: Int = cfg.timeIndex): Array<Array<Array<Complex>>> {
        val symbols = txGrid[0].size
        val sub = txGrid[0][0].size
        val random = Random(cfg.seed xor timeIndex xor 0x5A5A)
        val out = Array(rx) { Array(symbols) { Array(sub) { Complex(0.0, 0.0) } } }
        for (r in 0 until rx) for (l in 0 until symbols) for (k in 0 until sub) {
            val h = frequencyResponse(k, sub, timeIndex + l)
            var y = Complex(0.0, 0.0)
            for (t in 0 until tx) y += h[r][t] * txGrid[t][l][k]
            val p = txGrid.sumOf { it[l][k].abs2() }.coerceAtLeast(1e-12) / tx
            val np = p / 10.0.pow(snrDb / 10.0)
            val s = sqrt(np / 2.0)
            out[r][l][k] = y + Complex(g(random) * s, g(random) * s)
        }
        return out
    }

    fun summary(): NrChannelV16Result {
        var power = 0.0
        var maxP = 0.0
        var minP = Double.POSITIVE_INFINITY
        var count = 0
        for (k in 0 until nSc) {
            val h = frequencyResponse(k, nSc)
            for (r in 0 until rx) for (t in 0 until tx) {
                val q = h[r][t].abs2()
                power += q; maxP = max(maxP, q); minP = min(minP, q); count++
            }
        }
        val mean = power / count.coerceAtLeast(1)
        val coh = if (fd > 0.0) 0.423 / fd * 1e6 else Double.POSITIVE_INFINITY
        return NrChannelV16Result(
            model, tx, rx, delaysSec.size, rms(delaysNs), fd, rho, los,
            10.0 * log10(mean.coerceAtLeast(1e-12)),
            10.0 * log10(maxP.coerceAtLeast(1e-12) / minP.coerceAtLeast(1e-12)),
            coh, mean.isFinite() && fd.isFinite(),
            "V16 TR 38.901-oriented TDL/CDL channel: delay scaling, Doppler, LOS/Ricean behavior and separable Tx/Rx spatial correlation. TDL A-C are NLOS-style; D-E are LOS-style."
        )
    }

    private data class Profile(val delays: DoubleArray, val powersDb: DoubleArray)

    private fun profile(name: String): Profile {
        val a = doubleArrayOf(0.0,.3819,.4025,.5868,.4610,.5375,.6708,.5750,.7618,1.5375,1.8978,2.2242,2.1718,2.4942,2.5119,3.0582,4.0810,4.4579,4.5695,4.7966,5.0066,5.3043,9.6586)
        val ap = doubleArrayOf(-13.4,0.0,-2.2,-4.0,-6.0,-8.2,-9.9,-10.5,-7.5,-15.9,-6.6,-16.7,-12.4,-15.2,-10.8,-11.3,-12.7,-16.2,-18.3,-18.9,-16.6,-19.9,-29.7)
        val b = doubleArrayOf(0.0,.1072,.2155,.2095,.2870,.2344,.5967,.3176,.4610,.4710,.5375,.5975,.8050,.8300,1.2230,1.5740,1.7000,1.8700,2.1250,2.4520,2.8770,3.3480,4.0880,4.7990,5.2370,5.7750,6.3520,6.7920,7.3560,7.9100,8.4980,9.1200,9.6580)
        val bp = doubleArrayOf(-2.2,-1.2,-3.3,-5.0,-2.0,-4.0,-6.0,-7.8,-8.0,-8.8,-10.0,-10.0,-11.0,-12.0,-12.0,-13.0,-14.0,-15.0,-15.5,-16.0,-16.5,-17.0,-17.5,-18.0,-18.5,-19.0,-19.5,-20.0,-20.5,-21.0,-21.5,-22.0,-23.0)
        val c = doubleArrayOf(0.0,.2099,.2219,.2329,.2176,.6366,.6448,.6560,.6584,.7934,.8213,1.0465,1.1727,1.3083,1.3825,1.5048,1.5242,1.6290,1.8233,1.8827,2.0155,2.1070,2.1408,2.1770,2.2607,2.2609,2.4099,2.5374,2.6458,2.7526,2.8628,3.0213,3.1400,3.4000,3.8000,4.2000,4.6000,5.0000,5.4000,5.8000,6.2000,6.6000,7.0000,7.4000,7.8000,8.2000,8.6000,9.0000)
        val cp = doubleArrayOf(-4.4,-1.2,-3.0,-5.2,-2.0,-4.0,-6.0,-8.0,-7.0,-9.0,-10.0,-9.5,-11.0,-10.5,-12.0,-12.5,-13.0,-13.5,-14.0,-14.5,-15.0,-15.5,-16.0,-16.5,-17.0,-17.5,-18.0,-18.5,-19.0,-19.5,-20.0,-20.5,-21.0,-21.5,-22.0,-22.5,-23.0,-23.5,-24.0,-24.5,-25.0,-25.5,-26.0,-26.5,-27.0,-27.5,-28.0,-29.0)
        val d = doubleArrayOf(0.0,.035,.612,.630,.660,.730,1.120,1.480,1.520,2.120,2.170,2.750,3.040)
        val dp = doubleArrayOf(-0.2,-13.0,-1.0,-6.0,-11.0,-16.0,-17.0,-20.0,-24.0,-26.0,-27.0,-29.0,-30.0)
        val e = doubleArrayOf(0.0,.513,.544,.563,.544,.739,1.040,1.330,1.540,1.770,2.020,2.330,2.770)
        val ep = doubleArrayOf(-0.1,-6.0,-7.0,-9.0,-12.0,-14.0,-16.0,-18.0,-20.0,-22.0,-24.0,-26.0,-28.0)
        return when (name) {
            "TDL-A" -> Profile(a, ap)
            "TDL-B" -> Profile(b, bp)
            "TDL-C" -> Profile(c, cp)
            "TDL-D", "CDL-D" -> Profile(d, dp)
            "TDL-E", "CDL-E" -> Profile(e, ep)
            "CDL-A" -> Profile(a.copyOfRange(0, min(12, a.size)), ap.copyOfRange(0, min(12, ap.size)))
            "CDL-B" -> Profile(b.copyOfRange(0, min(16, b.size)), bp.copyOfRange(0, min(16, bp.size)))
            "CDL-C" -> Profile(c.copyOfRange(0, min(18, c.size)), cp.copyOfRange(0, min(18, cp.size)))
            else -> Profile(c, cp)
        }
    }

    private fun applyKronecker(iid: Array<Array<Array<Complex>>>, p: Int): Array<Array<Complex>> {
        val temp = Array(rx) { Array(tx) { Complex(0.0, 0.0) } }
        for (r in 0 until rx) for (t in 0 until tx) {
            var s = Complex(0.0, 0.0)
            for (rr in 0..r) for (tt in 0..t) s += iid[rr][tt][p] * rxL[r][rr] * txL[t][tt]
            temp[r][t] = s
        }
        return temp
    }

    private fun addLosComponent() {
        val k = 10.0.pow(cfg.losKDb / 10.0)
        val a = sqrt(k / (k + 1.0))
        val b = sqrt(1.0 / (k + 1.0))
        for (r in 0 until rx) for (t in 0 until tx) {
            val phase = 2.0 * PI * (r + t) / max(rx + tx, 1)
            taps[r][t][0] = taps[r][t][0] * b + Complex(a * cos(phase), a * sin(phase))
        }
    }

    private fun expCorr(n: Int, r: Double): Array<DoubleArray> = Array(n) { i -> DoubleArray(n) { j -> r.pow(abs(i - j).toDouble()) } }

    private fun chol(a: Array<DoubleArray>): Array<DoubleArray> {
        val n = a.size; val l = Array(n) { DoubleArray(n) }
        for (i in 0 until n) for (j in 0..i) {
            var s = a[i][j]
            for (k in 0 until j) s -= l[i][k] * l[j][k]
            l[i][j] = if (i == j) sqrt(s.coerceAtLeast(1e-12)) else s / l[j][j].coerceAtLeast(1e-12)
        }
        return l
    }

    private fun rms(x: DoubleArray): Double {
        if (x.isEmpty()) return 0.0
        val w = x.indices.sumOf { x[it] * x[it] }.coerceAtLeast(1e-12)
        return sqrt(w / x.size)
    }

    private fun g(): Double = g(rng)
    private fun g(r: Random): Double { var u = 0.0; while (u == 0.0) u = r.nextDouble(); val v = r.nextDouble(); return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * v) }
}
