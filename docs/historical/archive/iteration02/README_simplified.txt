FIREFIGHTING DRONE SWARM (RTCS-SIM) — CLEAN TEXT README

SYSC3303 — A3G6
Winter 2026

1. OVERVIEW
This Real-Time Control System (RTCS) simulates a firefighting drone swarm composed of three subsystems: Scheduler, Drone Subsystem, and Fire Incident Subsystem.
Each subsystem operates independently and coordinates real-time fire events, drone tasking, state-machine behavior, and agent usage.

2. FEATURES
Iteration 1: Basic message passing between subsystems using threads.
Iteration 2: Core scheduling logic and complete drone state machine implemented.
Iteration 3: Multi-drone support and UDP-based distributed execution (planned).
Iteration 4: Fault injection, detection, and fault handling (planned).
Iteration 5: Real-time visualization, capacity limits, and performance metrics (planned).

3. SYSTEM ARCHITECTURE
Current code layout (Iteration 2):
- All Java sources are under src/main/java/org/example.
- Main.java (in org.example) is the system entry point.

Planned package refactor (Iteration 3+ — not yet implemented):
- fire/ — Fire Incident Subsystem (FireIncident, FireIncidentSubsystem, EventType)
- scheduler/ — Scheduler and state machine (Scheduler, SchedulingState, FaultTypes)
- drone/ — Drone Subsystem and drone logic (DroneSubsystem, DroneState, DroneTask, DroneStatusUpdate, TileTypes)
- channels/ — Communication channels (IncidentReportChannel, DroneCommChannel, DroneStatusChannel)
- model/ — Shared types (Severity, Zone, Timer)
- ui/ — GUI components
Design Summary (Iteration 2):
- Scheduler receives incidents, assigns drone tasks, and handles faults.
- Drone Subsystem follows a state machine to navigate, drop agent, refill, and report completion.
- Fire Incident Subsystem streams time-based fire events and receives acknowledgements.
- Communication is fully decoupled through channel classes.
- This architecture prepares for multi-drone coordination and distributed execution in later iterations.

4. INSTALLATION AND SETUP
Requirements: Java 17 or later, IntelliJ IDEA recommended.

Getting the project:
Clone or download the repository and open the project folder.

Opening in IntelliJ:
Open IntelliJ -> File -> Open -> Select project root.

Building:
Via IntelliJ or via terminal using Maven.

Running:
Run the Main class in src/main/java/org/example.
Load event and zone input files when prompted.

5. INPUT FILES
Event File (CSV): time, zoneId, eventType, severity. Example: 14:03:15.000,3,FIRE_DETECTED,High
Zone File (CSV): zoneId, (x1,y1), (x2,y2). Example: 3,(700,600),(1200,1200)
Severity maps to required extinguishing agent.

6. CONFIGURATION
Configurable parameters include number of drones, zones, travel time, drop time, nozzle open/close time, initial capacity, and input paths.
Future: UDP ports, per-drone sockets, fault injection options.

7. SYSTEM BEHAVIOR
Fire Incident Subsystem: reads events, sends to Scheduler, receives acknowledgements.
Scheduler: manages queue, assigns tasks, forwards messages, handles faults.
Drone Subsystem: state machine Idle -> Flying -> Drop Water -> Inform Scheduler -> Refill -> Idle; reports status.

8. CURRENT ITERATION STATUS
Iteration 0 -> Gathered parameters and timing assumptions.
Iteration 1 -> Implemented basic threaded communication and end-to-end message flow.
Iteration 2 -> Implemented scheduling, drone state machine, real-time behavior, refined interactions, and GUI updates.

9. VERIFICATION AND VALIDATION (V&V)
Current automated tests: placeholder GUI test class (GUItests.java) under src/test/java.
Current manual validation: running end-to-end scenarios using representative event and zone files.
Recommended unit tests: GUI behavior, channel logic, parsing, state transitions, FIFO scheduling, agent depletion/refill, deterministic timing.
Recommended integration/system tests: channel tests, subsystem tests (FireIncident, Drone, Scheduler), full incident->scheduler->drone->scheduler->ack flow, multiple queued incidents, GUI updates, stub GUI, larger sample-input regression suite.

10. TEAM RESPONSIBILITIES
Pietro Adamvoski: Fire Incident and Scheduler
Avery Robertson: GUI, Drone, communication, diagrams
Adam Haddadin: Iteration 1 UMLs, README, initial tests, fixes
Fareen Lavji: V&V requirements, test suite, Iteration 3 architecture, Iteration 2 README, Iteration 3 diagrams
