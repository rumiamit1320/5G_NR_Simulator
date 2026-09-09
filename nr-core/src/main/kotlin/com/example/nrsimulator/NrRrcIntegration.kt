package com.example.nrsimulator

/**
 * Integration-only RRC state/config layer. Existing V1-V30 APIs remain untouched.
 * This is a compact executable state machine, not a byte-level 38.331 implementation.
 */
enum class NrRrcState { IDLE, CONNECTING, CONNECTED, RECONFIGURING }

data class NrRrcCellConfig(
    val cellId: Int = 1,
    val pci: Int = 42,
    val scsKHz: Int = 30,
    val bwpPrbs: Int = 52,
    val tddPattern: String = "DDDDDDUUUU",
    val coresetId: Int = 0,
    val searchSpaceId: Int = 0
)

data class NrRrcUeContext(
    val ueId: Int,
    val state: NrRrcState,
    val cell: NrRrcCellConfig,
    val reconfigurationCount: Int = 0
)

class NrRrcIntegration {
    fun connect(ueId: Int, cell: NrRrcCellConfig = NrRrcCellConfig()): NrRrcUeContext =
        NrRrcUeContext(ueId, NrRrcState.CONNECTED, cell)

    fun reconfigure(ctx: NrRrcUeContext, cell: NrRrcCellConfig): NrRrcUeContext =
        ctx.copy(state = NrRrcState.RECONFIGURING, cell = cell, reconfigurationCount = ctx.reconfigurationCount + 1)
            .copy(state = NrRrcState.CONNECTED)

    fun release(ctx: NrRrcUeContext): NrRrcUeContext = ctx.copy(state = NrRrcState.IDLE)
}
