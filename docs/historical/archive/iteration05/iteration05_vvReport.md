# Iteration 05 – V&V Report
_SYSC3303A3 • Winter 2026 • Group 6 • Dr. Sabouni, Rami._<br>
**Project**: Firefighting Drone Swarm Simulation  
**Author**: @fareenlavji  
**Submission**: Iteration 05 (Final)  
**Date**: April, 05 2026

## 1. Overview
This document is the Verification and Validation (V&V) report for Iteration 05 of the Firefighting Drone Swarm Simulation.  It maps every test requirement defined in the V&V specification (and the finalized Metric Requirements from the course discussion forum) to a concrete, executable JUnit test and explains how pass criteria are verified.<br>
The full test suite is invoked with:
```
mvn test
```
**Total tests: 97 | Failures: 0 | Skipped: 0** *(as of the final commit on this branch)*

## 2. New Capabilities Verified in Iteration 05
| Capability                                      | Where implemented                                                   |
|-------------------------------------------------|---------------------------------------------------------------------|
| Battery drain during flight (% per second)      | `DroneSubsystem` + `DroneConfig`                                    |
| Fuel drain per metre travelled                  | `DroneSubsystem` + `DroneConfig`                                    |
| `REFILLING` state resets water / battery / fuel | `DroneSubsystem.performRefill()`                                    |
| Liquid-agent capacity enforcement               | `DroneSubsystem` — agent capped at `AGENT_CAPACITY_LITRES`          |
| `LinkedBlockingQueue` task queue (no task loss) | `DroneSubsystem.taskQueue`                                          |
| Battery + fuel fields in `DroneUpdate`          | `DroneUpdate` 7-param constructor; `PacketBuilder` / `PacketParser` |
| All 5 required + 2 optional performance metrics | `SimulationMetrics` (see §5)                                        |
| File-based logging                              | `SimulationLogger` → `logs/` directory                              |
| `DroneSubsystem` startup race-condition fix     | Receiver started **before** REGISTER is sent                        |
| `SimulationMetrics` idempotent fire recording   | `recordFireDetected` / `recordFireExtinguished` use `putIfAbsent` — duplicate UDP packets cannot inflate counters |
| `FireIncidentSubsystem` malformed-line recovery | Per-line `try/catch` in `parseIncidentReport` — bad CSV rows are logged and skipped; processing continues |
| `DroneInfo` thread-visible fields               | `completedAssignments`, `zonesServiced`, `assignedSeverity`, `targetZoneCenterX/Y`, `currentIncident` declared `volatile` |
| `RuntimeGUI` EDT-safe fault timers              | `startDroneFaultCountdown` / `stopFaultCountdown` execute `Timer`/`HashMap` mutations via `SwingUtilities.invokeLater` |

## 3. Unit Tests
### 3.1 Drone Subsystem
| Test ID                   | Class / Method                                           | Scope                                                                                | Pass Criteria                                                                                                              |
|---------------------------|----------------------------------------------------------|--------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| **UT-DRONE-STATE-01**     | `DroneStateMachineTest.testIdleToArrivedTransition`      | IDLE → EN_ROUTE → ARRIVED transition                                                 | `ARRIVED` `DroneUpdate` received; state = `ARRIVED`, drone ID preserved                                                    |
| **UT-DRONE-STATE-02**     | `DroneStateMachineTest.testFullStateMachineCycle`        | Full cycle IDLE→ARRIVED→DROPPING→COMPLETED→REFILLING→IDLE                            | All expected states observed in sequence                                                                                   |
| **UT-DRONE-CAPACITY-01**  | `DroneCapacityRefillTest.testWaterDepletedThenRefilled`  | Agent depletion: water = 0 at COMPLETED; water restored at IDLE after REFILLING      | `COMPLETED` update has `water <= 0`; final `IDLE` update has `water = 15 L`                                                |
| **UT-DRONE-CAPACITY-02**  | `DroneCapacityRefillTest.testMultiMissionCapacityQueue`  | Multiple queued tasks — both complete end-to-end                                     | Two consecutive `IDLE` states observed                                                                                     |
| **UT-DRONE-FAULTFLAG-01** | `DroneFaultRecoveryTest.nozzleStuckClosedFaultFlagTest`  | Nozzle stuck-closed (soft fault): state → FAULTED, fault report sent, drone recovers | `FAULTED` DroneUpdate published; `FaultReport` with `NOZZLE_STUCK_CLOSED` received; drone returns to `IDLE` with full tank |
| **UT-DRONE-FAULTFLAG-02** | `DroneFaultRecoveryTest.nozzleStuckOpenHardFaultTest`    | Nozzle stuck-open (hard fault): drone permanently FAULTED                            | `FAULTED` DroneUpdate published; `FaultReport` received; **no** subsequent `IDLE` update                                   |
| **UT-DRONE-METRICS-01**   | `DroneStatusMetricsTest.droneStartsWithFullConsumables`  | Initial battery=100%, fuel=100%, water=15L                                           | Pure unit assertions; no network                                                                                           |
| **UT-DRONE-METRICS-02**   | `DroneStatusMetricsTest.batteryDepletedDuringFlight`     | Battery & fuel drain after flight to zone 300m away                                  | `ARRIVED` update shows battery < 100% and fuel < 100%                                                                      |
| **UT-DRONE-METRICS-03**   | `DroneStatusMetricsTest.waterDepletedAfterDrop`          | Water = 0 after DROPPING_AGENT                                                       | `COMPLETED` update has `water = 0`                                                                                         |
| **UT-DRONE-METRICS-04**   | `DroneStatusMetricsTest.refillingRestoresAllConsumables` | REFILLING resets battery=100%, fuel=100%, water=15L                                  | Final `IDLE` update after `REFILLING` has all values restored                                                              |
### 3.2 Scheduler Subsystem
| Test ID                | Class / Method                                                | Scope                                                              | Pass Criteria                                                            |
|------------------------|---------------------------------------------------------------|--------------------------------------------------------------------|--------------------------------------------------------------------------|
| **UT-SCHED-QUEUE-01**  | `SchedulerQueueOrderTest.incidentsServedInArrivalOrder`       | Incidents dispatched in FIFO arrival order                         | Three `AssignTask` messages received in zone order 1 → 2 → 3             |
| **UT-SCHED-ASSIGN-01** | `SchedulerLoadBalancingTest.testLoadBalancedAssignment`       | Load-balancing: drone with fewest zones serviced chosen            | Less-loaded drone (zonesServiced=0) receives the second task             |
| **UT-SCHED-ASSIGN-02** | `SchedulerQueueOrderTest.schedulerSelectsLessLoadedDrone`     | Two drones; first completes one mission; second receives next task | Drone 2 (0 zones) receives assignment after drone 1 (1 zone) is idle     |
| **UT-SCHED-FAULT-01**  | `FaultHandlingTest.testDroneStuckTimeoutTriggersReassignment` | Timeout logic: stuck drone reassigned                              | Backup drone receives reassigned task; GUI logs `[TIMEOUT] Drone 1`      |
| **UT-SCHED-FAULT-02**  | `FaultHandlingTest.testFaultReportTriggersReassignment`       | Soft fault → mission requeued and reassigned                       | Drone 2 receives reassigned task with `faultType = NONE`                 |
| **UT-SCHED-FAULT-03**  | `FaultHandlingTest.testHardFaultMarksDroneOffline`            | Hard fault → drone removed from eligible list                      | Healthy drone receives replacement task; GUI logs `[HARD FAULT] Drone 1` |
| **UT-SCHED-FAULT-04**  | `FaultHandlingTest.testFaultTypeForwardedInAssignTask`        | `faultType` column in CSV propagated to `AssignTask`               | `AssignTask.faultType()` matches the injected fault type                 |
| **UT-SCHED-FAULT-05**  | `FaultHandlingTest.testFaultReportPacketRoundTrip`            | `FaultReport` encode → decode preserves all fields                 | `droneId`, `faultType`, `message` identical after round-trip             |
| **UT-SCHED-FAULT-06**  | `FaultHandlingTest.testAssignTaskWithFaultRoundTrip`          | `AssignTask` with fault type survives packet round-trip            | `faultType` field preserved after encode → decode                        |
| **UT-SCHED-FAULT-07**  | `FaultHandlingTest.testIncidentReportWithFaultRoundTrip`      | `IncidentReport` with fault type survives packet round-trip        | `faultType` field preserved after encode → decode                        |
| **UT-SCHED-FAULT-08**  | `FaultHandlingTest.testIncidentReportDefaultsToNoFault`       | `IncidentReport` constructed without fault defaults to `NONE`      | `faultType()` returns `FaultTypes.NONE` when not specified               |
| **UT-SCHED-PATH-01**   | `SchedulerLoadBalancingTest.testPassesThroughZone_onPath`     | Path-through redirect: zone on flight path                         | `passesThroughZone()` returns `true`                                     |
| **UT-SCHED-PATH-02**   | `SchedulerLoadBalancingTest.testPassesThroughZone_offPath`    | Zone off flight path not selected                                  | `passesThroughZone()` returns `false`                                    |
| **UT-SCHED-PATH-03**   | `SchedulerLoadBalancingTest.testPassesThroughZone_behindDrone`| Zone behind the drone not selected                                 | `passesThroughZone()` returns `false`                                    |
| **UT-SCHED-PATH-04**   | `SchedulerLoadBalancingTest.testPassesThroughZone_droneAtTarget` | Drone already at target — no spurious redirect                  | `passesThroughZone()` returns `false` when `distToTarget < 1e-6`         |
### 3.3 Fire Incident Subsystem
| Test ID                 | Class / Method                                                         | Scope                                                             | Pass Criteria                                                                                                   |
|-------------------------|------------------------------------------------------------------------|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| **UT-FIRE-CSV-01**      | `FireIncidentSubsystemTest.testSendsIncidentReportsFromCsv`            | CSV parsing: 2 events → 2 UDP packets with correct fields         | `time`, `zoneId`, `eventType`, `severity` match CSV input                                                       |
| **UT-FIRE-CSV-02**      | `FireIncidentSubsystemTest.testEmptyFileProducesNoPackets`             | Empty file → no packets sent                                      | No `IncidentReport` received within 2 s                                                                         |
| **UT-FIRE-CSV-03**      | `FireIncidentSubsystemTest.testSingleEventFile`                        | Single-row CSV                                                    | Correct zone ID and severity received                                                                           |
| **UT-FIRE-CSV-04**      | `FireIncidentSubsystemTest.testMalformedLineSkippedAndValidLineStillSent` | Malformed CSV row skipped; surrounding valid rows still dispatched | 2 valid `IncidentReport` packets received; malformed row does not abort processing                              |
| **UT-FIRE-ZONEFILE-01** | `ZoneCsvParserTest`                                                    | Zone CSV parsing; valid + invalid rows                            | Zone objects created with correct coordinates; invalid rows skipped cleanly                                     |
### 3.4 UDP / Communication Utilities
| Test ID            | Class / Method                                                       | Scope                                                        | Pass Criteria                                                                |
|--------------------|----------------------------------------------------------------------|--------------------------------------------------------------|------------------------------------------------------------------------------|
| **UT-NET-PKT-01**  | `PacketRoundTripTest.*`                                              | All message types round-trip via PacketBuilder/PacketParser  | All fields preserved exactly after encode → decode                           |
| **UT-NET-PKT-02**  | `DroneStatusMetricsTest.droneUpdateRoundTripIncludesBatteryAndFuel`  | New `DroneUpdate` 7-param constructor round-trip             | `battery` and `fuel` fields preserved                                        |
| **UT-NET-PKT-03**  | `DroneStatusMetricsTest.legacyDroneUpdateDefaultsBatteryFuelTo100`   | Legacy 5-param constructor backward compatibility            | `battery` = 100%, `fuel` = 100% when not specified                           |
| **UT-NET-LOSS-01** | `PacketParserTest.testEmptyBytesReturnUnknownType`                   | Empty payload returns `UNKNOWN` type                         | `getType([])` returns `UNKNOWN`; no exception thrown                         |
| **UT-NET-LOSS-02** | `PacketParserTest.testRandomBytesReturnUnknownType`                  | Random bytes return `UNKNOWN` type                           | Parser does not crash on arbitrary input                                     |
| **UT-NET-LOSS-03** | `PacketParserTest.testPlaintextStringProducesNullType`               | Plain-text payload is handled gracefully                     | `getType()` returns `null` or `UNKNOWN`; no exception                        |
| **UT-NET-LOSS-04** | `PacketParserTest.testInvalidJsonObjectProducesNullType`             | Malformed JSON object handled                                | `getType()` returns `null`; no exception                                     |
| **UT-NET-LOSS-05** | `PacketParserTest.testMissingTypeFieldProducesNullType`              | JSON without `type` field                                    | `getType()` returns `null`; no exception                                     |
| **UT-NET-LOSS-06** | `PacketParserTest.testUnknownTypeValueProducesNullType`              | JSON with unrecognised `type` value                          | `getType()` returns `null`; no exception                                     |
| **UT-NET-LOSS-07** | `PacketParserTest.testParseIncidentReportWithMissingFieldsReturnsNull` | Incomplete `IncidentReport` payload                        | `parseIncidentReport()` returns `null`; no exception                         |
| **UT-NET-LOSS-08** | `PacketParserTest.testParseAssignTaskWithMissingFieldsReturnsNull`   | Incomplete `AssignTask` payload                              | `parseAssignTask()` returns `null`; no exception                             |
| **UT-NET-LOSS-09** | `PacketParserTest.testParseDroneRegisterWithMissingFieldsReturnsNull`| Incomplete `DroneRegister` payload                           | `parseDroneRegister()` returns `null`; no exception                          |
| **UT-NET-LOSS-10** | `PacketParserTest.testParseDroneUpdateWithMissingFieldsReturnsNull`  | Incomplete `DroneUpdate` payload                             | `parseDroneUpdate()` returns `null`; no exception                            |
| **UT-NET-LOSS-11** | `PacketParserTest.testNullByteArrayHandledGracefully`                | `null` byte array input                                      | Parser returns `null` or `UNKNOWN`; no `NullPointerException`                |

### 3.5 Scheduler Selection & Fairness
| Test ID                   | Class / Method                                                    | Scope                                                          | Pass Criteria                                                                |
|---------------------------|-------------------------------------------------------------------|----------------------------------------------------------------|------------------------------------------------------------------------------|
| **UT-SCHED-SEL-01**       | `SchedulerSelectionTest.testAssignsDroneToIncident`               | Idle drone receives first assignment via UDP                   | `AssignTask` received; droneId, zoneId, severity all correct                 |
| **UT-SCHED-SEL-02**       | `SchedulerSelectionTest.testHighSeverityAssignment`               | High-severity incident dispatched correctly                    | `AssignTask` carries `severity = HIGH` and correct zone ID                   |
| **UT-SCHED-SEL-03**       | `SchedulerSelectionTest.testTwoDronesFairnessForTwoIncidents`     | Two drones, two incidents → no duplicate assignments           | Exactly 2 total assignments (one per drone-incident pair)                    |
| **UT-SCHED-BAL-01**       | `SchedulerTest.balancesAssignmentsAcrossMultipleDrones`           | Load balance + proximity: correct drone assigned per zone      | Drone 1 (near zone 1) gets zone 1; Drone 2 (near zone 2) gets zone 2        |

### 3.6 Simulation Metrics
| Test ID                     | Class / Method                                                          | Scope                                                                    | Pass Criteria                                                                                             |
|-----------------------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| **UT-METRICS-DEDUP-01**     | `SimulationMetricsTest.duplicateFireDetectedDoesNotOverCount`           | Duplicate `recordFireDetected` for same zone → counter stays at 1        | `getSummaryString()` reports `Fires reported: 1` after 3 calls for zone 1                                 |
| **UT-METRICS-DEDUP-02**     | `SimulationMetricsTest.duplicateFireExtinguishedDoesNotOverCount`       | Duplicate `recordFireExtinguished` for same zone → counter stays at 1    | `getSummaryString()` reports `Fires extinguished: 1` after 3 calls for zone 5                             |
| **UT-METRICS-COUNT-01**     | `SimulationMetricsTest.distinctZonesCountedSeparately`                  | Each unique zone contributes exactly one unit to reported/extinguished    | Totals = 3 reported, 2 extinguished for 3 detected / 2 extinguished distinct zones (duplicates ignored)   |

## 4. Integration Tests
| Test ID   | Class                                                   | Description                               | Pass Criteria                                                                                                       |
|-----------|---------------------------------------------------------|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| **IT-01** | `MissionLifecycleTest`                                  | Single drone, no faults — full lifecycle  | State sequence IDLE→EN_ROUTE→ARRIVED→DROPPING→COMPLETED→REFILLING→IDLE observed; final agent level matches severity |
| **IT-02** | `MultiIncidentQueueDrainIT`                             | Multiple queued incidents, single drone   | All incidents serviced in arrival order; no incident lost                                                           |
| **IT-03** | `MultiIncidentQueueDrainIT.testLoadBalanced`            | 3 drones, multiple zones — load balancing | Work distributed across drones; no single drone handles all tasks                                                   |
| **IT-04** | `FaultHandlingTest.testFaultReportTriggersReassignment` | Fault injection via CSV fault column      | Fault injected; scheduler detects, marks drone, reassigns mission                                                   |
| **IT-05** | `UdpLoopbackIT`, `MessageContractsIT`                   | UDP cross-process message exchange        | Messages successfully exchanged; packet contents verified; unknown message types handled gracefully                 |
| **IT-06** | `TwoDronesBasicFlowIT`                                  | Two drones, basic end-to-end flow         | Both drones receive tasks; both complete missions; scheduler balances assignments                                   |
| **IT-07** | `DroneStatusMetricsTest.droneQueuesMultipleTasks`       | Task queue: two tasks queued while busy   | Both tasks completed end-to-end (2× COMPLETED states)                                                               |
| **IT-08** | `SchedulerLoadBalancingTest.testLoadBalancingPrefersLeastLoadedDrone` | Least-loaded idle drone selected for second incident | Drone with `zonesServiced=0` receives second task over drone with `zonesServiced=1` |
| **IT-09** | `SchedulerLoadBalancingTest.testPathThroughRedirect`    | EN_ROUTE drone redirected to same-severity zone on its path | Second `AssignTask` to drone targets the pass-through zone; original zone re-queued |

## 5. Performance Metrics Tests
All seven metrics specified by Dr. Sabouni are computed by `SimulationMetrics` and verified by unit tests.
| Metric # | Metric Name                                        | Method                             | Test                                                                                                 |
|----------|----------------------------------------------------|------------------------------------|------------------------------------------------------------------------------------------------------|
| 1        | Avg Event Response Time (created → ARRIVED)        | `getAverageResponseTimeMs()`       | `SimulationMetrics` populated by Scheduler `handleDroneUpdate()`; verified in `MissionLifecycleTest` |
| 2        | Max Event Response Time                            | `getMaxResponseTimeMs()`           | Same test — max ≥ avg always                                                                         |
| 3        | Avg Event Completion Time (created → extinguished) | `getAverageExtinguishTimeMs()`     | Extinguish time recorded on COMPLETED; `SimulationMetrics` getter verified                           |
| 4        | Max Event Completion Time                          | `getMaxExtinguishTimeMs()`         | Same                                                                                                 |
| 5        | Drone Utilization % per drone                      | `DroneMetrics.getUtilizationPct()` | Flight/idle timer accumulation verified; utilization in (0, 100] after flight                        |
| 6        | Avg Queue Length                                   | `getAverageQueueLength()`          | Sampled at every `offer`/`take` in `Scheduler`; mean = sum/count                                     |
| 7        | Max Queue Length                                   | `getMaxQueueLength()`              | Atomic high-water mark updated on every offer                                                        |

The complete summary (all seven metrics, per-fire breakdown, and per-drone utilization) is printed to stdout by `Scheduler.printMetricsSummary()` and simultaneously written to `logs/metrics.log` via `SimulationLogger`.

## 3a. Additional Unit Tests (Model, DTO, GUI)
These tests do not map to a numbered V&V requirement but provide important regression coverage for shared model and infrastructure classes.

### 3a.1 Domain Model
| Test ID               | Class / Method                                            | Scope                                                       | Pass Criteria                                                            |
|-----------------------|-----------------------------------------------------------|-------------------------------------------------------------|--------------------------------------------------------------------------|
| **UT-MODEL-SEV-01**   | `SeverityMappingTest.testLowRequiresTenLitres`            | `LOW` severity requires 10 L agent                          | `getReqLitresOfWater()` = 10                                             |
| **UT-MODEL-SEV-02**   | `SeverityMappingTest.testModerateRequiresTwentyLitres`    | `MODERATE` severity requires 20 L agent                     | `getReqLitresOfWater()` = 20                                             |
| **UT-MODEL-SEV-03**   | `SeverityMappingTest.testHighRequiresThirtyLitres`        | `HIGH` severity requires 30 L agent                         | `getReqLitresOfWater()` = 30                                             |
| **UT-MODEL-SEV-04**   | `SeverityMappingTest.testAllSeverityValuesPresent`        | Enum covers all three expected values                       | `values()` contains `LOW`, `MODERATE`, `HIGH`                            |
| **UT-MODEL-SEV-05**   | `SeverityMappingTest.testRequiredLitresAreStrictlyOrdered`| Volume requirements increase with severity                  | LOW < MODERATE < HIGH in litres                                          |
| **UT-MODEL-TIMER-01** | `TimerTest.testOneShotTimer`                              | One-shot `Timer` fires callback exactly once                | Callback invoked once within deadline; no second invocation              |
| **UT-MODEL-TIMER-02** | `TimerTest.testPeriodicTimer`                             | Periodic `Timer` fires callback at regular intervals        | Callback invoked ≥ 2 times within observation window                     |
| **UT-MODEL-TIMER-03** | `TimerTest.testDefaultConstructorAndRun`                  | `Timer` with default constructor runs without error         | No exception thrown on `run()`                                           |

### 3a.2 Messaging DTOs
| Test ID              | Class / Method                                              | Scope                                                           | Pass Criteria                                                         |
|----------------------|-------------------------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|
| **UT-DTO-IR-01**     | `IncidentReportDtoTest.testAllFieldsPreservedLowSeverity`   | `IncidentReport` round-trip with LOW severity                   | All fields (`time`, `zoneId`, `eventType`, `severity`, `faultType`) preserved |
| **UT-DTO-IR-02**     | `IncidentReportDtoTest.testAllFieldsPreservedHighSeverity`  | `IncidentReport` round-trip with HIGH severity                  | All fields preserved                                                  |
| **UT-DTO-IR-03**     | `IncidentReportDtoTest.testAllFieldsPreservedModerateSeverity` | `IncidentReport` round-trip with MODERATE severity           | All fields preserved                                                  |
| **UT-DTO-IR-04**     | `IncidentReportDtoTest.testPacketTypeIsIncidentReport`      | Packet header identifies `INCIDENT_REPORT` type                 | `PacketParser.getType()` returns `INCIDENT_REPORT`                    |

### 3a.3 GUI State Tracking
| Test ID              | Class / Method                                              | Scope                                                           | Pass Criteria                                                         |
|----------------------|-------------------------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|
| **UT-GUI-01**        | `GUITest.testDroneStateTracking`                            | GUI tracks drone state through a mission cycle                  | `lastDroneState` reflects each dispatched state update                |
| **UT-GUI-02**        | `GUITest.testDronePositionTracking`                         | GUI tracks drone position updates                               | `lastX`, `lastY` reflect each position update                         |
| **UT-GUI-03**        | `GUITest.testDroneWaterTracking`                            | GUI tracks drone water level                                    | `lastWater` reflects each water update                                |
| **UT-GUI-04**        | `GUITest.testActiveFireCountTracking`                       | GUI tracks active fire count (increment/decrement)              | `activeFireCount` increments and decrements correctly                 |
| **UT-GUI-05**        | `GUITest.testEventLogging`                                  | GUI accumulates event log entries                               | `allLogs` contains each logged message                                |
| **UT-GUI-06**        | `GUITest.testClearFireTile`                                 | GUI tracks fire-tile clear events                               | `clearFireCount` increments on each `clearFireIncident` call          |

## 6. Logging
`SimulationLogger` (`DroneSwarmSim.scheduler.SimulationLogger`) is initialized by `Scheduler` and writes five persistent log files to the `logs/` directory:
| File              | Contents                                                                                      |
|-------------------|-----------------------------------------------------------------------------------------------|
| `events.log`      | High-level scheduler events: fire detected, drone dispatched, fault, reassignment, timeout    |
| `telemetry.log`   | Per-step drone status updates (position, state, battery %, fuel %)                            |
| `assignments.log` | Task assignment + completion history per drone and zone                                       |
| `queue.log`       | Incident-queue depth changes (offer / take with queue size)                                   |
| `metrics.log`     | Final `SimulationMetrics.getSummaryString()` output including all 7 required/optional metrics |

Every line is prefixed with a wall-clock timestamp (`HH:mm:ss.SSS`).  Files are opened in **append mode** so successive simulation runs accumulate history.

## 7. System & Acceptance Test Status
| Test ID   | Description                                                | Status                                                                                          |
|-----------|------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| **ST-01** | Nominal full-scale (10 drones, 5 zones, heavy CSV)         | **Manual** — all automated unit/IT tests pass; manual run confirms GUI updates and no deadlocks |
| **ST-02** | Fault-heavy scenario (stuck, nozzle jams, sensor failures) | **Covered by** `FaultHandlingTest` (IT-04) + `DroneFaultRecoveryTest`                           |
| **ST-03** | Overload & overflow (CSV exceeding drone capacity)         | **Covered by** `MultiIncidentQueueDrainIT` — queue drains gracefully; no incidents lost         |
| **ST-04** | Timing & performance instrumentation                       | **Covered by** Metrics §5 — `SimulationMetrics` + `logs/metrics.log`                            |
| **ST-05** | GUI & usability — real-time map, fault colours             | **Manual** — `RuntimeGUI` renders battery/fuel cards; fault states rendered in red              |

## 8. Port Registry (test isolation)
To prevent test interference, all integration tests use statically assigned ports outside the production range (6000).
| Port Range    | Assigned To                                                                        |
|---------------|------------------------------------------------------------------------------------|
| 15201 – 15212 | `DroneStateMachineTest`, `DroneCapacityRefillTest`                                 |
| 15301 – 15312 | Drone listen ports for above                                                       |
| 15401 – 15404 | `FireIncidentSubsystemTest`                                                        |
| 15501 – 15503 | `SchedulerSelectionTest` (scheduler side)                                          |
| 15551 – 15554 | `SchedulerSelectionTest` (drone side)                                              |
| 15600         | `SchedulerTest`                                                                    |
| 15700 – 15705 | `SchedulerLoadBalancingTest` (scheduler side)                                      |
| 15754 – 15756 | `SchedulerLoadBalancingTest` (drone side)                                          |
| 15800 – 15850 | `MissionLifecycleTest`                                                             |
| 15900 – 15955 | `MultiIncidentQueueDrainIT`                                                        |
| 16100 – 16452 | `FaultHandlingTest`                                                                |
| 16500 – 16501 | `DroneFaultRecoveryTest` (scheduler side)                                          |
| 16550 – 16560 | `DroneFaultRecoveryTest` (drone side)                                              |
| 16551 – 16554 | `DroneStatusMetricsTest` (drone listen; scheduler uses ephemeral port 0)           |
| 16600 – 16650 | `SchedulerQueueOrderTest.incidentsServedInArrivalOrder`                            |
| 16700 – 16752 | `SchedulerQueueOrderTest.schedulerSelectsLessLoadedDrone`                          |

## 9. Known Limitations
- **System tests ST-01 and ST-05** require a display; they are validated manually since CI runs headless.
- **`DroneStatusMetricsTest` integration tests** (tests 2–5) rely on the OS scheduler for UDP delivery; they are stable in isolation and in the full suite because the drone's `UdpReceiver` now starts before the REGISTER packet is sent (race-condition fix in `DroneSubsystem.run()`).
- The 2-second inter-event sleep in `FireIncidentSubsystem` means the sleep dominates the "mean time per event" metric; this is documented in `SimulationMetrics.printSummary()`.
- **`SchedulerTest.balancesAssignmentsAcrossMultipleDrones`** is occasionally flaky under heavy system load due to UDP delivery ordering. The test uses a 4-second socket timeout with up to 5 retry attempts to mitigate this; it passes reliably in isolation.