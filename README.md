# Firefighting Drone Swarm Simulator

A Java control-system simulation that coordinates a swarm of drones responding to fire incidents. The scheduler, drone fleet, and fire-incident generator run as separate processes and communicate over UDP. A Swing dashboard displays incidents, assignments, drone telemetry, faults, and simulation metrics in real time.

This repository is a sanitized portfolio snapshot of a four-person SYSC 3303 course project at Carleton University. Pietro Adamvoski implemented the application represented here; the original team also participated in the course submission, documentation, review, and presentation process. Course reports, student identifiers, archived iterations, and grading material are intentionally excluded.

## Highlights

- Runs the Scheduler, Drone, and Fire Incident subsystems as independent Java processes.
- Registers multiple drones dynamically and assigns queued incidents to available drones.
- Models each mission with state transitions from idle through travel, agent drop, return, and refill.
- Exchanges registration, assignment, telemetry, incident, acknowledgement, and fault messages over UDP.
- Injects simulated faults and reassigns incidents when a drone becomes unavailable.
- Tracks water, battery, fuel, response time, completion time, queue depth, utilization, and fleet distance.
- Includes a Swing dashboard and a launcher for a configurable fleet, defaulting to 20 drones.
- Uses JUnit 5 unit and integration tests for scheduling, state transitions, UDP messages, fault recovery, and complete mission flows.

## Architecture

```text
Fire Incident process ── IncidentReport ──> Scheduler process
                                               │
                                  AssignTask / telemetry
                                               │
                                               v
                                        Drone processes
                                               │
                                      status and faults
                                               │
                                               v
                                      Runtime dashboard
```

The Scheduler owns the incident queue and fleet state. The Fire Incident process reads timestamped scenarios from CSV and sends incidents to the Scheduler. Each Drone process registers itself, receives assignments, advances through its state machine, and reports telemetry or faults. Shared packet builders and parsers define the UDP message contracts.

Important packages:

- `scheduler`: dispatch, queueing, reassignment, fault handling, logging, and metrics
- `drone`: drone registration, mission state machine, resource use, and telemetry
- `fire`: scenario parsing and timed incident delivery
- `net/udp`: UDP endpoints, senders, receivers, and packet serialization
- `messaging`: typed messages exchanged between processes
- `ui`: live simulation dashboard

## Requirements

- Java 17 or later
- Maven 3.8 or later
- A graphical desktop session for the Swing dashboard
- Bash for `run-demo.sh`

## Run the simulation

The launcher compiles the project, starts the Scheduler and dashboard, asks how many drones to launch, and starts the Fire Incident process:

```bash
./run-demo.sh
```

Press `Ctrl+C` to stop the simulation and clean up the Java processes started by the launcher.

To run the processes manually in separate terminals:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="DroneSwarmSim.scheduler.SchedulerMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.fire.FireIncidentMain"
```

Pass a drone ID to start one drone instead of the default fleet:

```bash
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain" -Dexec.args="3"
```

Sample event and zone files are under `src/`. Runtime logs are written to `logs/` and ignored by Git.

## Test

```bash
mvn test
```

The suite covers scheduler selection and queue order, drone state transitions and capacity, fault recovery, CSV parsing, UDP packet round trips, and multi-process mission scenarios. GUI tests cover model-facing behavior; display-dependent behavior still requires manual validation.

## Known limitations

- UDP delivery is intentionally simple and does not provide production-grade reliability or authentication.
- The simulation uses configurable timing and resource constants; measured results are not hard real-time guarantees.
- The launcher uses `pkill` to stop processes whose command line contains this project's main-class names. Run it only in a development environment.
- The dashboard requires a graphical environment and is not designed for a headless server.

## Attribution

Created for Carleton University's SYSC 3303 course with Avery Robertson, Adam Haddadin, and Fareen Lavji. This public snapshot preserves the application and tests while omitting private academic records and submission artifacts.
