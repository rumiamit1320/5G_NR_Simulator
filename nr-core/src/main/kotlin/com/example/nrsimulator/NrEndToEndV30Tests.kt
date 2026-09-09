package com.example.nrsimulator

/** Regression tests for the hardened V30 end-to-end integration layer. */
object NrEndToEndV30Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val r = NrEndToEndV30().run(NrEndToEndConfigV30(ueCount = 2, prbs = 24, snrDb = 20.0, frames = 1, slotsPerFrame = 4, payloadBytesPerUe = 1200))
        check(r.rrcConnectedUes == 2)
        out += "RRC connected UEs: PASS"
        check(r.pdcpPass && r.rlcPass)
        out += "PDCP -> RLC buffers: PASS"
        check(r.downlinkGrants > 0 && r.pdcchDecodes == r.downlinkGrants)
        out += "Scheduler -> DCI -> PDCCH: PASS"
        check(r.pdschAcks + r.pdschNacks == r.downlinkGrants)
        out += "PDSCH -> CRC/HARQ: PASS"
        check(r.uciAcks >= r.pdschAcks)
        out += "HARQ -> PUCCH/UCI: PASS"
        check(r.bytesDelivered in 0..r.bytesOffered)
        out += "Application byte delivery accounting: PASS"
        check(r.throughputMbps >= 0.0)
        out += "End-to-end throughput accounting: PASS"
        check(r.endToEndPass)
        out += "V30 hardened end-to-end path: PASS"
        return out
    }
}
