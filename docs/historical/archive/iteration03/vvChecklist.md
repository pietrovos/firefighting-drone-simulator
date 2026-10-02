# Iteration 03 — V&V Test Plan Checklist

**Goal:** Verify three independent processes over UDP (Scheduler, FireIncident, N×Drone), multi-drone scheduling (fairness + minimal wait), en-route retargeting, packet content tests, UML name parity, and GUI assignment view.

## 0. Prep
- Packages: `DroneSwarmSim.fire`, `DroneSwarmSim.scheduler`, `DroneSwarmSim.drone`, `DroneSwarmSim.messaging`, `DroneSwarmSim.net.udp`, `DroneSwarmSim.model`, `DroneSwarmSim.ui`, `DroneSwarmSim.config`.
- Entry points: `DroneSwarmSim.scheduler.SchedulerMain`, `DroneSwarmSim.fire.FireIncidentMain`, `DroneSwarmSim.drone.DroneMain` (run N instances).
- IPC: UDP only (`UdpSender`, `UdpReceiver`, `UdpEndpoint`).
- Diagrams: Inter-process sequence; names match code.

## 1. Unit Tests (no sockets)
### Messaging & Packets
- [x] PacketBuilderTest — JSON round-trip for: IncidentReport, AssignTask, DroneRegister, DroneUpdate, Ack (`PacketRoundTripTest.java`, 6 tests).
- [x] PacketParserTest — malformed/missing fields handled cleanly (`PacketParserTest.java`, 11 tests).
- [x] DTO schema tests: IncidentReportDtoTest (`IncidentReportDtoTest.java`, 4 tests); AssignTask/DroneRegister/DroneUpdate/Ack covered by `PacketRoundTripTest` and `MessageContractsIT`.

### Scheduler
- [x] SchedulerSelectionTest — ETA + capacity (Low=10, Moderate=20, High=30), fairness, deterministic tie-breakers (`SchedulerSelectionTest.java`, 3 tests).
- [x] RetargetingPolicyTest — equal/higher severity and small detour → retarget; otherwise don't (`SchedulerLoadBalancingTest.java`, 6 tests including `testPathThroughRedirect`).
- [x] MissionLifecycleTest — QUEUED → ASSIGNED → IN_PROGRESS → DONE (`MissionLifecycleTest.java`, 1 test).

### Drone SM & Models
- [x] DroneStateMachineTest — IDLE → EN_ROUTE → DROPPING → REFILLING → IDLE (`DroneStateMachineTest.java`, 3 tests).
- [x] DroneCapacityRefillTest — depletion → REFILLING → next assignment (`DroneCapacityRefillTest.java`, 3 tests).
- [x] ZoneCsvParserTest — coordinates `(x;y)` semicolon-separated; rejects malformed rows (`ZoneCsvParserTest.java`, 7 tests).
- [x] SeverityMappingTest — Low=10, Moderate=20, High=30 (`SeverityMappingTest.java`, 5 tests).

**Run (unit):**
```bash
mvn -q -Dtest='*Test' test
```

## 2. Integration Tests (UDP loopback)
### UDP Subsystem
- [x] UdpLoopbackIT — echo payload sanity (`UdpLoopbackIT.java`, 4 tests).
- [x] MessageContractsIT — producer encodes, consumer decodes (all message types) (`MessageContractsIT.java`, 5 tests).

### End-to-End Flows
- [x] TwoDronesBasicFlowIT — Scheduler + FireIncident + 2×Drone over UDP loopback; verifies AssignTask delivery and mission completion (`TwoDronesBasicFlowIT.java`, 2 tests).
- [x] MultiIncidentQueueDrainIT — mixed severities (LOW/MODERATE/HIGH); 3 drones, 6 incidents; all assigned and fairly distributed (`MultiIncidentQueueDrainIT.java`, 2 tests).

### Negative/Robustness
- [x] MalformedPacketHandlingIT — invalid/unknown messages return null type and are silently ignored; system continues (`PacketParserTest.java` covers parser robustness; `MessageContractsIT` verifies happy-path contracts).
- [ ] SilentDroneTimeoutIT — missing drone updates → mission re-queued and reassigned. **NOTE:** Scheduler does not yet implement a heartbeat/timeout mechanism; this test is deferred until timeout logic is added to the Scheduler.

**Run (integration):**
```bash
mvn -q -Dtest='*IT' verify
```

## 3. System / Acceptance
- [x] Balanced Load Demo — ≥3 drones, 5+ incidents; missions served roughly balanced (`MultiIncidentQueueDrainIT#testBalancedLoadAcrossThreeDrones`: 3 drones × 6 incidents, each drone receives 1-3 tasks).
- [x] Retargeting Demo — incident on path of en-route drone (equal/higher severity) → reassigned (`SchedulerLoadBalancingTest#testPathThroughRedirect`).
- [x] GUI View Demo — `StubGUI` state-tracking verified: assignment (`showAssignment`), State (`updateDroneState`), Position (`updateDronePosition`), Water (`updateDroneWater`), fire count, event log (`GUITest.java`, 6 tests). Full Swing rendering validated manually in a headed environment.

## 4. Docs & Quality
- [x] Javadoc on DTOs and UDP utilities.
- [ ] UML (Class, Inter-Process Sequence, State) updated; names match code.
- [x] README Iteration 03 — ports, runbook, message contract, Maven commands.

## 5. Maven Quick Commands
```bash
# all (unit + IT)
mvn -q clean verify

# unit only
mvn -q -Dtest='*Test' test

# integration only
mvn -q -Dtest='*IT' verify

# targeted suites
mvn -q -Dtest='DroneSwarmSim.scheduler.*Test' test
mvn -q -Dtest='DroneSwarmSim.drone.*Test' test
mvn -q -Dtest='DroneSwarmSim.model.*Test' test
```

## 6. Submission Evidence
- [x] 76 automated tests pass (`mvn test`); Surefire reports generated under `target/surefire-reports/`.
- [ ] GUI screenshot (headed environment required for Swing rendering).
- [ ] Updated UML exports.
