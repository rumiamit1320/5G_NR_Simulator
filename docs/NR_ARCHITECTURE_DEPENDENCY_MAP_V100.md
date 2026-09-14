# NR Architecture Dependency Map — V1–V100

## Scope

Static dependency audit of the V92–V100 additive project state. The purpose is to identify live paths, compatibility layers, and consolidation candidates **without deleting or replacing existing V1–V91 implementations**.

## Module topology

```text
:app
  MainActivity.kt
      |
      +--> legacy/runtime NR components (V3/V4/V11–V13, V15/V16, V5/V8, V6/V9, ...)
      |
      +--> Android UI / simulation controls

:nr-core
  |
  +--> V1–V30 foundational PHY/protocol implementations
  +--> V31–V45 conformance / protocol extensions
  +--> V46–V60 NR coding + protocol stack
  +--> V61–V67 integrated link/network/timing
  +--> V68–V73 HARQ / soft-PHY
  +--> V74–V81 LDPC / waveform / MIMO / CSI / state
  +--> V82–V84 LDPC transport + rate matching
  +--> V85–V91 exact-table LDPC + PHY mapping / DMRS / grid
  +--> V92–V100 additive PHY expansion
  |
  +--> CoreRegressionMain.kt

:web-server
  |
  +--> WebServer.kt
  +--> LabApi.kt
  |     |
  |     +--> V19–V63 legacy API dispatch
  |     +--> V85–V91 dispatch
  |
  +--> NrV85V91WebAdapter.kt
  +--> NrV92V100WebAdapter.kt   <-- present, but not yet dispatched by LabApi
```

## Primary dependency chains

### Legacy PHY chain

```text
NrPhyV3 / NrPhyV4
        |
        +--> older DSP/channel primitives

NrPhyV11
        +--> Dsp
        +--> NrChannelV11
        +--> NrDmrsV11

NrPhyV12
        +--> NrPhyV11-era primitives
        +--> NrLdpcV8 / NrLdpcV8Tables
        +--> NrChannelV11
        +--> NrDmrsV11

NrPhyV13
        +--> same V11/V12-era foundation
```

**Consolidation candidate:** preserve V3/V4/V11/V12/V13 as compatibility façades, but establish one canonical PHY primitive layer underneath them. Do not delete them during the first consolidation pass.

### LDPC evolution

```text
NrLdpcV8
    +--> NrLdpcV8Tables

NrLdpcV74
    +--> NrLdpcNrTablesV74
    +--> NrLdpcV74ReferenceVectors

NrLdpcV82
    +--> NrLdpcV74

NrLdpcV85
    +--> NrLdpcV82

NrLdpcCodecV86
    +--> NrLdpcV74
    +--> NrLdpcV85

NrCodingChainV87
    +--> NrTransportV83
    +--> NrLdpcV82 / V85
    +--> NrRateMatchingV84
    +--> NrLdpcCodecV86
```

**Canonical candidate:** the V85/V86/V87 path is the strongest current coding-chain foundation. V8/V34/V46/V74 remain required by older tests/APIs and should initially become compatibility branches around a canonical codec interface rather than being removed.

### Transport / coding

```text
NrTransportV9 --> NrLdpcV8

NrTransportV75 --> V74-era system path

NrTransportV83 --> NrLdpcV82
                 |
NrRateMatchingV84 -+
                 |
NrCodingChainV87 --> NrLdpcCodecV86 --> NrLdpcV85
```

**Canonical candidate:** V83 → V84 → V86 → V87 is the current forward-looking transport/coding path.

### V61–V81 integrated radio path

```text
NrIntegratedLinkV61
        |
NrIntegratedSystemV63
        |
NrClosedLoopV64
        |
NrNetworkSimulationV65
        |
NrIntegratedNetworkV66
        +--> NrMimoV15
        +--> NrChannelV16
        +--> NrMobilityV27 / NrBeamV28

Timing/HARQ:
NrTimingV67
   |
   +--> NrHarqTimingV68 --> NrHarqV51
   +--> NrHarqExecutionV69 --> NrHarqV51
   +--> NrHarqExecutionV70 --> NrHarqV51 / NrIntegratedNetworkV66
   +--> NrHarqPhyIntegrationV71
   +--> NrHarqSoftBufferV72
   +--> NrHarqSoftPhyV73

V74–V81:
NrTransportV75
NrWaveformV76
NrMimoReceiverV77
NrControlV78
NrRachV79
NrCsiV80
NrSystemStateV81
```

**Canonical candidate:** retain the V64–V81 integrated orchestration as the current closed-loop system layer while gradually redirecting its radio primitives to the V85+ PHY path.

### V85–V91 path

```text
Transport Block
      |
NrTransportV83
      |
NrRateMatchingV84
      |
NrLdpcV85 / NrLdpcCodecV86
      |
NrCodingChainV87
      |
NrPhyMappingV89
      |
NrDmrsMimoV90
      |
NrPdschPuschV91
      |
Web adapter / regression tests
```

This is currently the strongest **data-PHY forward path** in the repository.

### V92–V100 path

```text
NrDmrsV92
NrOfdmV93
NrMimoV94
NrChannelV95
NrLinkAdaptationV96
NrHarqCsiV97
NrRachV98
NrRefVectorsV99
NrResearchGradeV100
```

`NrV92V100Additive.kt` groups several of these primitives and currently depends on the V89/V90 complex-symbol infrastructure for parts of the MIMO path.

`NrV92V100WebAdapter.kt` exposes V92–V100 individually, but **LabApi.kt does not yet dispatch these versions**. This is a known integration gap, not an orphaned implementation.

## Runtime reachability summary

### Android

`MainActivity.kt` directly references multiple generations, including V3/V4/V11–V13, V15/V16, V5/V8 and V6/V9. Therefore the claim that Android stops at V30 is not supported by the source-level reference graph.

The exact runtime call graph still requires execution tracing because source references do not prove every branch is active.

### Web

`WebServer.kt` and `LabApi.kt` retain legacy API paths. `NrV85V91WebAdapter.kt` is integrated into the V85–V91 LabApi dispatch.

`NrV92V100WebAdapter.kt` exists but is not yet connected to the central LabApi dispatch.

### Regression

`CoreRegressionMain.kt` is a major reachability root. It executes the V61–V91 regression families, including V68–V73 HARQ/soft-PHY, V74–V81, V82–V84 and V85–V91 tests.

This means many apparently old classes are **test-required** even when they are not the preferred forward runtime implementation.

## Consolidation classification

| Area | Current preferred path | Older implementations | Action |
|---|---|---|---|
| PHY | V85–V91 + V92+ primitives | V3/V4/V11/V12/V13/V35 | Wrap, don't delete |
| LDPC | V85/V86/V87 | V5/V8/V34/V46/V74 | Canonical interface + compatibility |
| Transport | V83/V87 | V6/V9/V46/V75 | Preserve adapters |
| Rate matching | V84 | older embedded variants | Canonicalize behind interface |
| DMRS | V90 + V92 | V11 | Unify sequence/resource abstraction later |
| MIMO | V90 + V94 | V15/V77 | Canonical complex/MIMO API |
| Channel | V95 target | V11/V16/V35/V49 | Canonical channel model interface |
| OFDM | V93 target | V76/older DSP | Canonical waveform API |
| HARQ | V68–V73 orchestration + V97 CSI/soft combine | V51/V69/V70 | Unify process/state interface |
| RACH | V79 + V98 | earlier RACH primitives | Canonical PRACH/initial-access interface |
| Link adaptation | V17 + V96 | heuristic/older variants | Separate standards MCS policy from simulator policy |
| Grid | V91 | V49-era resource grid | Canonical grid API |

## Safe consolidation order

1. **Freeze current V1–V100 APIs.** No deletion.
2. Introduce small canonical interfaces/data types for `Complex`, resource grids, modulation, coding, channel, MIMO and HARQ.
3. Move implementation ownership to the V85–V100 forward path.
4. Convert old V classes into thin adapters where practical.
5. Redirect Android/Web integrations to canonical APIs one subsystem at a time.
6. Run the complete regression suite after every subsystem migration.
7. Only after static + runtime verification, mark genuinely unreachable compatibility code for removal.

## Known non-conformance boundaries

V92–V100 should continue to be labelled as simulator/reference primitives, not certified 3GPP implementations. In particular, the current V92 DMRS, V95 channel, V96 link adaptation, V98 RACH and V99 reference vectors are not sufficient to claim full bit-exact 38.211/38.212/38.213/38.214/38.215 conformance.

## Immediate next implementation step

The next safe code change is to introduce a **canonical PHY abstraction layer** and route V85–V100 through it, while leaving every V1–V91 class and public API intact. V92–V100 should then be wired into `LabApi.kt` as a separate additive dispatch before any legacy code is removed.
