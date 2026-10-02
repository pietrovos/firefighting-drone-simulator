
# FIREFIGHTING DRONE SWARM — Iteration 04
_Enhancement to Simulation — Fault Handling, Shutdown Logic, and Recovery_

Iteration 04 introduces:
- Fault injection through the event CSV fault column
- Fault state machine for drones (soft and hard faults)
- Travel timeout detection (`DRONE_STUCK`)
- Nozzle jam detection (`NOZZLE_STUCK_CLOSED`, `NOZZLE_STUCK_OPEN`)
- Hard-fault drone shutdown (`SHUTDOWN`)
- Scheduler-level reassignment
- GUI overlays for faults and offline drones
- Updated message types for error propagation
## Main files
### Entry points
- `DroneSwarmSim.scheduler.SchedulerMain`
- `DroneSwarmSim.fire.FireIncidentMain`
- `DroneSwarmSim.drone.DroneMain`
### Core subsystem files
- `Scheduler.java`
- `FaultTypes.java`
- `FireIncidentSubsystem.java`
- `DroneSubsystem.java`
### GUI files
- `GUI.java`
- `RuntimeGUI.java`
- `TileTypes.java`
### Models & Messages
- `Zone.java`, `Severity.java`, `Timer.java`
- **New**: `FaultReport.java`
### UDP Support
- `UdpEndpoint.java`, `UdpSender.java`, `UdpReceiver.java`
- `PacketBuilder.java`, `PacketParser.java`
## Input Files
### Updated CSV Format
Iteration 04 introduces a new `FaultType` column enabling scripted fault injection.
#### Column Template
`Time`, `ZoneId`, `EventType`, `Severity`, `FaultType`
#### Example CSV
```
Time,ZoneId,EventType,Severity,FaultType
14:03:15,3,FIRE_DETECTED,High,NOZZLE_STUCK_OPEN
14:07:00,1,FIRE_DETECTED,Moderate,NONE
14:09:22,2,DRONE_REQUEST,Low,DRONE_STUCK
14:10:00,1,DRONE_REQUEST,High,NOZZLE_STUCK_CLOSED
14:12:30,4,FIRE_DETECTED,Low,NONE
```
#### Allowed Values
- EventType: `FIRE_DETECTED`, `DRONE_REQUEST`
- Severity: `Low`, `Moderate`, `High`
- FaultType: `NONE`, `DRONE_STUCK`, `NOZZLE_STUCK_CLOSED`, `NOZZLE_STUCK_OPEN`, `PACKET_LOSS`, `PACKET_CORRUPTION`
## Diagrams
### UML Class Diagram
```mermaid
classDiagram
    direction LR

    %% ENTRY POINTS
    class SchedulerMain {
      +main(args)
    }
    class FireIncidentMain {
      +main(args)
    }
    class DroneMain {
      +main(args)
    }

    %% CORE SUBSYSTEMS
    class Scheduler {
      +run()
      +getZones()
      +processIncident(IncidentReport)
      +registerDrone(DroneRegister)
      +updateDrone(DroneUpdate)
      +handleFault(droneId, faultType)
      +reassign(zoneId)
    }

    class FireIncidentSubsystem {
      +run()
      +parseEventFile(path)
      +dispatchIncident(IncidentReport)
    }

    class DroneSubsystem {
      +run()
      +injectFaultIfAny()
      +applyFault(faultType)
      +moveTowardTarget()
      +simulateDrop()
      +sendTelemetry()
    }

    %% DATA + TRACKING
    class DroneInfo {
      +getState()
      +faultState
      +updateTelemetry(state, xPos, yPos, water)
      +assignZone(zoneId)
      +clearAssignedZone()
      +incrementCompletedAssignments()
    }

    %% GUI
    class GUI {
      +start()
      +showFireIncident(zoneId, severityLabel)
      +showAssignment(droneId, zoneId, severityLabel, remainingWater, status)
      +updateAssignmentProgress(droneId, zoneId, severityLabel, remainingWater, status)
      +showDronePosition(droneId, x, y)
      +clearFireIncident(zoneId)
      +clearAssignment(droneId)
      +showFault(droneId, faultType)
      +showDroneOffline(droneId)
      +logEvent(message)
      +logTelemetry(message)
      +logQueueEvent(message)
      +logAssignmentHistory(message)
    }

    class RuntimeGUI

    %% NETWORKING (UDP)
    class UdpSender {
      +send(data)
    }
    class UdpReceiver {
      +start()
      +close()
    }

    class PacketBuilder {
      +build(IncidentReport)
      +build(AssignTask)
      +build(DroneRegister)
      +build(DroneUpdate)
      +build(FaultReport)
    }

    class PacketParser {
      +getType(data)
      +parseIncidentReport(data)
      +parseAssignTask(data)
      +parseDroneRegister(data)
      +parseDroneUpdate(data)
      +parseFaultReport(data)
    }

    %% MESSAGES / EVENTS
    class IncidentReport {
      +time
      +zoneId
      +eventType
      +severity
      +faultType
    }

    class AssignTask {
      +droneId
      +zoneId
      +zoneXOrigin
      +zoneYOrigin
      +zoneWidth
      +zoneLength
      +severity
    }

    class DroneRegister {
      +droneId
      +host
      +port
    }

    class DroneUpdate {
      +droneId
      +state
      +xPos
      +yPos
      +water
    }

    class FaultReport {
      +droneId
      +faultType
    }

    %% STATIC MODELS
    class Zone {
      +zoneID
      +xOrigin
      +yOrigin
      +width
      +length
    }

    class Severity {
      <<enumeration>>
    }
    class EventType {
      <<enumeration>>
    }
    class DroneState {
      <<enumeration>>
    }
    class SchedulingState {
      <<enumeration>>
    }
    class FaultTypes {
      <<enumeration>>
      NONE
      DRONE_STUCK
      NOZZLE_STUCK_CLOSED
      NOZZLE_STUCK_OPEN
      PACKET_LOSS
      PACKET_CORRUPTION
    }

    %% RELATIONSHIPS
    SchedulerMain --> Scheduler
    SchedulerMain --> RuntimeGUI
    RuntimeGUI --|> GUI

    FireIncidentMain --> FireIncidentSubsystem
    DroneMain --> DroneSubsystem

    Scheduler --> Zone
    Scheduler --> IncidentReport
    Scheduler --> AssignTask
    Scheduler --> DroneInfo
    Scheduler --> GUI
    Scheduler --> UdpReceiver
    Scheduler --> UdpSender
    Scheduler --> PacketParser
    Scheduler --> PacketBuilder
    Scheduler --> FaultTypes

    FireIncidentSubsystem --> IncidentReport
    FireIncidentSubsystem --> Severity
    FireIncidentSubsystem --> EventType
    FireIncidentSubsystem --> UdpSender
    FireIncidentSubsystem --> PacketBuilder

    DroneSubsystem --> AssignTask
    DroneSubsystem --> DroneRegister
    DroneSubsystem --> DroneUpdate
    DroneSubsystem --> FaultReport
    DroneSubsystem --> UdpReceiver
    DroneSubsystem --> UdpSender
    DroneSubsystem --> PacketParser
    DroneSubsystem --> PacketBuilder
    DroneSubsystem --> DroneState
    DroneSubsystem --> FaultTypes

    IncidentReport --> EventType
    IncidentReport --> Severity
    IncidentReport --> FaultTypes

    AssignTask --> Severity
    DroneUpdate --> DroneState
    FaultReport --> FaultTypes

    GUI --> Zone : renders
```
### Sequence Diagrams
#### Messeges SD
```mermaid
sequenceDiagram
    autonumber

    participant FIS as FireIncidentSubsystem
    participant SCH as Scheduler
    participant GUI as RuntimeGUI
    participant D0 as Drone0
    participant D1 as Drone1

    FIS->>SCH: IncidentReport over UDP
    SCH->>GUI: showFireIncident(zoneId, severity)
    D0->>SCH: DroneRegister(droneId=0, port=7000)
    D1->>SCH: DroneRegister(droneId=1, port=7001)

    SCH->>SCH: queue incident and choose best idle drone
    SCH->>GUI: showAssignment(droneId, zoneId, severity, remainingWater, status)
    SCH->>D0: AssignTask over UDP

    D0->>SCH: DroneUpdate(EN_ROUTE, xPos, yPos, water)
    SCH->>GUI: showDronePosition(droneId, xPos, yPos)
    SCH->>GUI: updateAssignmentProgress(...)

    D0->>SCH: DroneUpdate(ARRIVED, xPos, yPos, water)
    SCH->>GUI: updateAssignmentProgress(...)

    D0->>SCH: DroneUpdate(DROPPING_AGENT, xPos, yPos, water)
    SCH->>GUI: updateAssignmentProgress(...)

    D0->>SCH: DroneUpdate(COMPLETED, xPos, yPos, waterAfterDrop)
    alt zone still needs water
        SCH->>GUI: logAssignmentHistory(partial drop)
        SCH->>GUI: logQueueEvent(re-queueing zone)
        SCH->>SCH: put incident back into queue
        SCH->>D1: AssignTask over UDP
    else zone extinguished
        SCH->>GUI: clearFireIncident(zoneId)
        SCH->>GUI: logAssignmentHistory(zone extinguished)
    end

    D0->>SCH: DroneUpdate(RETURNING, xPos, yPos, water)
    SCH->>GUI: updateAssignmentProgress(...)

    D0->>SCH: DroneUpdate(IDLE, 0, 0, 15.0)
    SCH->>GUI: clearAssignment(droneId)
    SCH->>GUI: clearDronePosition(droneId)
```
#### FAULT Handling SD
```mermaid
sequenceDiagram
 autonumber
 participant FIS as FireIncidentSubsystem
 participant SCH as Scheduler
 participant GUI as RuntimeGUI
 participant D0 as Drone0
 participant D1 as Drone1
 FIS->>SCH: IncidentReport
 SCH->>GUI: showFireIncident(...)
 D0->>SCH: DroneRegister(0)
 D1->>SCH: DroneRegister(1)
 SCH->>D0: AssignTask(zoneId)
 D0->>SCH: DroneUpdate(EN_ROUTE)
 alt CSV Fault Injected
     D0->>SCH: FaultReport(NOZZLE_STUCK_CLOSED)
     SCH->>GUI: showFault(...)
     SCH->>SCH: reassign(zoneId)
     SCH->>D1: AssignTask(zoneId)
 else Normal flow
     D0->>SCH: DroneUpdate(ARRIVED)
     D0->>SCH: DroneUpdate(DROPPING_AGENT)
     D0->>SCH: DroneUpdate(COMPLETED)
 end
```
### State Machines
#### Scheduler SM
```mermaid
stateDiagram-v2
 [*] --> WAITING
 WAITING --> ASSIGNING: IncidentReport
 ASSIGNING --> MONITORING: AssignTask
 ASSIGNING --> WAITING: No idle drones
 MONITORING --> MONITORING: DroneUpdate
 MONITORING --> FAULT_HANDLING: FaultReport
 FAULT_HANDLING --> ASSIGNING: Soft fault
 FAULT_HANDLING --> OFFLINE: Hard fault
 OFFLINE --> WAITING
 WAITING --> WAITING: DroneRegister
```
#### Drone SM
```mermaid
stateDiagram-v2
 [*] --> IDLE
 IDLE --> EN_ROUTE
 EN_ROUTE --> ARRIVED
 EN_ROUTE --> FAULTED: DRONE_STUCK (timeout)
 ARRIVED --> DROPPING_AGENT
 DROPPING_AGENT --> COMPLETED
 DROPPING_AGENT --> FAULTED: NOZZLE_STUCK_CLOSED
 DROPPING_AGENT --> FAULTED: NOZZLE_STUCK_OPEN
 COMPLETED --> RETURNING
 RETURNING --> IDLE
 FAULTED --> [*]
```
### Timing Diagrams
#### Travel Timeout → FAULT_STUCK_IN_TRANSIT
```mermaid
sequenceDiagram
 autonumber
 D->>S: DroneUpdate(EN_ROUTE)
 Note over D,S: travel timer starts
 D--xS: timeout reached
 S->>G: showFault(FAULT_STUCK_IN_TRANSIT)
 S->>S: reassign
```
#### Nozzle Closed
```mermaid
sequenceDiagram
 autonumber
 D->>S: DroneUpdate(DROPPING_AGENT)
 D->>S: DroneFaultReport(FAULT_NOZZLE_CLOSED)
 S->>G: showFault(...)
 S->>S: reassign
```
#### Nozzle Open (Hard Fault)
```mermaid
sequenceDiagram
 autonumber
 D->>S: DroneFaultReport(FAULT_NOZZLE_OPEN)
 S->>G: showDroneOffline(...)
 S->>S: reassign + mark SHUTDOWN
```
#### Packet Corruption
```mermaid
sequenceDiagram
 autonumber
 D--xS: Corrupted packet
 S->>G: logEvent(PacketCorruption)
 S->>S: treat as soft fault → reassign
```
## Running the System
### 1. Start the Scheduler
**Run**:
- **Main class**: `DroneSwarmSim.scheduler.SchedulerMain`
- **Optional**: zone file path
### 2. Start Drones
Run **each drone in its own process**:
- **Main class**: `DroneSwarmSim.drone.DroneMain`
- **Argument(s)**: drone ID (0,1,2...)
- **Port mapping**:
  - 0 → 7000
  - 1 → 7001
  - 2 → 7002
### 3. Start the Fire Incident Subsystem
- **Main class**: `DroneSwarmSim.fire.FireIncidentMain`
- **Optional**: event CSV path using the standard event format with an optional `Fault Type` column.
### Suggested Setups
#### Single-drone fault demo
1. Scheduler
2. DroneMain 0
3. FireIncidentMain with a regular event CSV whose `Fault Type` column injects the desired fault
#### Multi-drone demo
1. Scheduler
2. DroneMain 0, DroneMain 1
3. FireIncidentMain
## Team Contributions
| Team Member | Iteration 1–2 | Iteration 3 | Iteration 4 |
|-------------|----------------|-------------|-------------|
| **Pietro Adamvoski** | Fire Incident and Scheduler Subsystem. | Setup workflow, documentation, diagrams | Fault handling integration in the scheduler, Iteration 4 setup notes, and timing-diagram cleanup. |
| **Avery Robertson** | GUI, Drone Subsystem, communication between threads, and Iteration 02 UML and state machine diagrams. | Assignment/ Status UI, map + log views | GUI fault overlays, offline indicators, logs |
| **Adam Haddadin** | Iteration 01 UMLs, `README.txt`, initial test suites, and bug fixes. | UDP integration, test cleanup/ updates | Fault-handling test updates, packet parsing support, and verification pass on reassignment flows. |
| **Fareen Lavji** | V&V requirements + test suite packages + source code packages for Iteration 03, Iteration 02 `README.md`, and Iteration 03 UML and state machine diagrams. | Architecture Refactor + V&V Implementation, UDP upgrades/ integrations + full code sweep for javadocs/ test updates, `README.md` with mermaid diagrams + `vvChecklist.md` + `refactorPlan.md`, issue(s) tracking + bugs + merge conflicts → devops CI/CD pipeline on GitHub. | `iteration04_v&vPlan.md` + `README.md` with updates and required diagrams in mermaid script, issue tracking with TA's feedback from iteration 03 + outlined requirements for iteration 04 → GitHub issues for traceability, derived fault CSV specifications + fault logic implement. |
# Other Documentation
- `docs/iteration04/iteration04_v&vPlan.md`
- `docs/iteration03/README.md`
