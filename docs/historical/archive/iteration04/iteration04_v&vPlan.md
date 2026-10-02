
# Iteration 04 – V&V Test Plan Checklist
**Goal**: Verify system behaviour under **injected drone faults**, **timing‑based fault detection**, **hard vs. soft fault classification**, **mission reassignment**, **UDP communication robustness**, and GUI **visual fault indicators**.
## 0. Prep
- [ ] Packages present:
    - [ ] `DroneSwarmSim.fire`, `DroneSwarmSim.scheduler`, `DroneSwarmSim.drone`,
    - [ ] `DroneSwarmSim.messaging`, `DroneSwarmSim.net.udp`,
    - [ ] `DroneSwarmSim.model`, `DroneSwarmSim.ui`, `DroneSwarmSim.config`
- [ ] Entry points exist: `SchedulerMain`, `FireIncidentMain`, `DroneMain`
- [ ] IPC uses UDP only (`UdpSender`, `UdpReceiver`, `UdpEndpoint`)
- [ ] **Updated diagrams**: scheduler + drone state machines, fault‑handling timing diagrams
- [ ] **Naming consistency**: only constants, enum/ record values use ALL CAPS (e.g., `IDLE`, `EN_ROUTE`, `FAULT_STUCK`)
## 1. Unit Tests (no sockets)
### Messaging & Packets
- [ ] `PacketFaultRoundTripTest` covers JSON round‑trip for: `FaultInjectionEvent`, `DroneFaultReport`, `FaultAck`
- [ ] `PacketParserFaultRobustnessTest` handles corrupted JSON, missing fields, unknown fault identifiers
- [ ] `FaultDtoSchemaTest` validates fields: `faultType`, `droneId`, `source`, `metadata` (field names remain camelCase)
### Scheduler
- [ ] `SchedulerFaultClassificationTest` checks:
    - [ ] `NOZZLE_OPEN` → hard fault;
    - [ ] `NOZZLE_CLOSED` / `STUCK_IN_TRANSIT` → soft fault
- [ ] `SchedulerFaultReassignmentTest` requeues tasks and reassigns fairly
- [ ] `TimeoutDetectorTest` detects travel timeout and triggers `STUCK_IN_TRANSIT`
### Drone SM & Models
- [ ] `DroneFaultStateMachineTest` verifies transitions:
    - [ ] `EN_ROUTE → FAULT_STUCK`
    - [ ] `DROPPING → FAULT_NOZZLE_CLOSED`
    - [ ] `DROPPING → FAULT_NOZZLE_OPEN → SHUTDOWN`
- [ ] `DroneHardFaultShutdownTest` confirms `FAULT_NOZZLE_OPEN` produces permanent `SHUTDOWN`
- [ ] `FaultColumnParserTest` parses CSV fault column; rejects invalid fault types

**Run (unit)**:
```
mvn -q -Dtest='*Test' test
```
## 2. Integration Tests (UDP loopback)
### UDP Subsystem
- [ ] `UdpLoopbackFaultIT` simulates packet loss and corruption
- [ ] `MessageContractsFaultIT` validates fault‑related message encoding/decoding
### End‑to‑End Flows
- [ ] `StuckInTransitTimeoutIT` verifies timeout → `FAULT_STUCK` → mission reassignment
- [ ] `NozzleClosedFaultFlowIT` verifies `FAULT_NOZZLE_CLOSED` → reassignment
- [ ] `NozzleOpenHardFaultIT` verifies `FAULT_NOZZLE_OPEN` → drone `SHUTDOWN` → reassignment
- [ ] `PacketCorruptionFlowIT` verifies corrupted packet → fault detected → fallback logic
### Negative / Robustness
- [ ] `SilentFaultyDroneIT` missing updates → timeout → `STUCK_IN_TRANSIT` → reassignment
- [ ] `MultiFaultCascadeIT` simultaneous faults → system remains stable
**Run (integration)**:
```
mvn -q -Dtest='*IT' verify
```
## 3. System / Acceptance
- [ ] Stuck mid‑flight demo: timeout → `FAULT_STUCK`; GUI shows fault indicator
- [ ] Nozzle‑closed demo: `FAULT_NOZZLE_CLOSED`; GUI + logs show reassignment
- [ ] Nozzle‑open demo: `FAULT_NOZZLE_OPEN`; drone enters `SHUTDOWN`; GUI shows offline
- [ ] Communication‑fault demo: packet loss/corruption triggers fallback + reassignment
- [ ] GUI fault view demo validates:
    - [ ] `showFault`, `showDroneOffline`,
    - [ ] colour‑coded markers,
    - [ ] event log entries (`DISPATCH → FAULT_DETECTED → SHUTDOWN/REASSIGN`)
**Evidence required**:
- [ ] GUI screenshots for each fault
- [ ] Timing diagrams for each scenario
- [ ] Logs showing: `DISPATCH`, `FAULT_DETECTED`, `SHUTDOWN`, `REASSIGNMENT`
## 4. Docs & Quality
- [ ] **Javadoc updated** for new fault DTOs and utilities
- [ ] **UMLs updated**: drone + scheduler state machines, fault sequences, timing diagrams
- [ ] **README updated**: fault types, CSV fault column format, GUI injection usage, run instructions
## 5. Maven Quick Commands
```
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
- [ ] All automated tests pass (`mvn test`)
- [ ] Surefire XML reports included
- [ ] Fault‑injection CSV included
- [ ] GUI screenshots included
- [ ] Updated UML and timing diagrams included
