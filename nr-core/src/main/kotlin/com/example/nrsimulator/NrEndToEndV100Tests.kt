package com.example.nrsimulator

/** Additive V100 end-to-end acceptance checks. */
object NrEndToEndV100Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>, val report: NrEndToEndV100.Report)

    fun run(): Result {
        val report = NrEndToEndV100.run()
        val checks = linkedMapOf(
            "V100 overall" to report.passed,
            "transport length" to (report.payloadBits == 512),
            "recovered length" to (report.recoveredBits > 0),
            "LDPC" to report.ldpcPassed,
            "CRC" to report.crcPassed,
            "OFDM EVM finite" to report.evm.isFinite(),
            "OFDM symbols" to (report.ofdmSymbols > 0),
            "FFT size" to (report.fftSize >= 16),
            "rate matched bits" to (report.rateMatchedBits > 0)
        )
        return Result(checks.values.all { it }, checks, report)
    }
}
