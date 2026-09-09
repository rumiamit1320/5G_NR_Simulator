package com.example.nrsimulator

object NrV16Tests {
    @JvmStatic fun main(args: Array<String>) {
        val models = listOf("TDL-A", "TDL-B", "TDL-C", "TDL-D", "TDL-E", "CDL-A", "CDL-C", "CDL-D")
        for ((i, m) in models.withIndex()) {
            val r = NrChannelV16(NrChannelV16Config(model=m, txAntennas=2, rxAntennas=2, prbs=52, scsKHz=30, velocityKmh=60.0, rmsDelayNs=100.0, spatialCorrelation=0.35, seed=0x1600+i)).summary()
            check(r.taps > 0 && r.meanPowerDb.isFinite() && r.dopplerHz > 0.0) { "V16 $m failed" }
            println("$m: taps=${r.taps} RMS=${"%.2f".format(r.rmsDelayNs)} ns fd=${"%.2f".format(r.dopplerHz)} Hz corr=${"%.2f".format(r.spatialCorrelation)} selectivity=${"%.2f".format(r.frequencySelectivityDb)} dB PASS=${r.pass}")
        }
        val h = NrChannelV16(NrChannelV16Config(model="TDL-C", txAntennas=4, rxAntennas=4, velocityKmh=120.0, spatialCorrelation=0.7)).frequencyResponse(100, 624)
        check(h.size == 4 && h[0].size == 4)
        println("4x4 response: PASS")
        val tx = Array(2) { Array(14) { Array(624) { Complex(1.0, 0.0) } } }
        val y = NrChannelV16(NrChannelV16Config(model="TDL-D", txAntennas=2, rxAntennas=2, prbs=52, snrDb=30.0)).apply(tx)
        check(y.size == 2 && y[0].size == 14 && y[0][0].size == 624)
        println("TDL apply path: PASS")
    }
}
