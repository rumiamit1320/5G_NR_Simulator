package com.example.nrsimulator

/** Deterministic V18 tests for DCI packing, CRC/RNTI masking and PDCCH candidates. */
object NrV18Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val dci = NrDciV18(frequencyDomainAssignment = 17, timeDomainAssignment = 2, mcs = 23, rv = 2, harqProcess = 5, layers = 2)
        val bits = dci.toBits(52)
        val round = NrDciV18.fromBits(bits, 52)
        check(round.mcs == dci.mcs && round.rv == dci.rv && round.harqProcess == dci.harqProcess && round.layers == dci.layers)
        out += "DCI 1_0 field pack/unpack: PASS"

        val masked = NrCrc24C.appendAndMask(bits, 0x1234)
        val (ok, recovered) = NrCrc24C.checkAndUnmask(masked, 0x1234)
        check(ok && recovered.contentEquals(bits))
        check(!NrCrc24C.checkAndUnmask(masked, 0x4321).first)
        out += "CRC24C + RNTI masking: PASS"

        val p = NrPdcchV18()
        val r = p.run(rnti = 0x1234, bwpPrbs = 52, dci = dci, aggregationLevel = 4)
        check(r.crcOk && r.decodeOk && r.pass)
        check(r.selectedCandidate.aggregationLevel == 4)
        out += "PDCCH encode/decode + CCE candidate: PASS"

        val ss = NrSearchSpaceV18Config(monitoringPeriodSlots = 2, monitoringOffset = 0)
        check(p.buildCandidates(24, ss, 0).isNotEmpty())
        check(p.buildCandidates(24, ss, 1).isEmpty())
        out += "SearchSpace monitoring periodicity: PASS"
        return out
    }
}
