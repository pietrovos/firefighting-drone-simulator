# Firefighting Drone Swarm Simulator

A Java simulation of a dispatcher coordinating firefighting drones over UDP. It models incident intake, priority queueing, drone missions, telemetry, injected faults, recovery and reassignment, with a Swing dashboard for runtime monitoring.

This was a four-person Carleton University SYSC 3303 course project by Pietro Adamvoski, Avery Robertson, Adam Haddadin, and Fareen Lavji. This repository is a portfolio snapshot of the team's application, not a sole-authored project.

## Pietro's Contributions

Pietro's substantial direct contributions included:

- the initial Scheduler and Fire Incident subsystem implementations
- task assignment and drone-status coordination
- the runtime GUI and telemetry display
- fault-recovery and incident-reassignment refinements
- scheduler, fault-handling, and queue-behaviour tests
- the foundation for simulation metrics
- the final launcher and default 20-drone workflow

Avery Robertson, Adam Haddadin, and Fareen Lavji were project teammates and contributors to the shared course project. The repository intentionally does not attribute the complete implementation to Pietro or attempt to assign every remaining file to one person.

## Architecture

The simulation runs as three Java processes:

```text
Fire Incident process  -- incident reports -->  Scheduler process
                                                    |
                                              assignments
                                                    |
                                                    v
Drone process (one thread per drone)  -- telemetry/faults --> Scheduler
                                                    |
                                                    v
                                           Swing dashboard
```

- **Scheduler process:** owns the incident queue, tracks fleet state, dispatches work, handles faults and reassignment, records metrics, and hosts the dashboard.
- **Drone process:** `DroneMain` starts one `DroneSubsystem` thread per drone. The default workflow therefore uses one drone process containing 20 drone threads, not 20 independent drone processes.
- **Fire Incident process:** reads timestamped events from CSV and sends them to the scheduler on a compressed clock.
- **UDP messages:** registration, incident, assignment, acknowledgement, telemetry, and fault packets are serialized by the shared `messaging` and `net.udp` packages.

## Requirements

- Java 17 or later
- Maven 3.8 or later
- Bash and a graphical desktop session for the launcher and Swing dashboard

## Run the Demo

```bash
./run-demo.sh
```

The launcher compiles the project, starts the Scheduler, asks for a fleet size, starts the drones, and then starts event playback. Press `Ctrl+C` to stop the processes launched by the script.

The default uses 20 drone threads and `src/Sample_event_file.csv`, a short five-incident fault scenario. Its event timestamps span 1.2 simulated seconds and are dispatched in well under one second on the configured compressed clock; drone missions and fault countdowns continue after the final event is sent.

The full 50-event scenario remains available at `src/Final_event_file_w26.csv`. Its timestamps span 7:51:13 of simulated time, or approximately 9 minutes 26 seconds of event playback at the configured 20 ms per simulated second, plus time for outstanding missions to finish. Run it by passing the file to the Fire Incident process:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="DroneSwarmSim.scheduler.SchedulerMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.fire.FireIncidentMain" -Dexec.args="src/Final_event_file_w26.csv"
```

Run those commands in separate terminals. Passing a drone ID starts only that drone thread instead of the configured fleet:

```bash
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain" -Dexec.args="3"
```

## Tests

```bash
mvn test
```

The suite contains unit and integration tests for scheduler selection and queue order, drone state and capacity changes, fault recovery, CSV parsing, packet contracts, UDP loopback, and mission coordination. The `*IT` tests exercise multiple components through local UDP sockets in one test JVM; they are integration tests, not a production-like end-to-end deployment. GUI tests cover model-facing behavior, while visual behavior still requires manual checking in a desktop session.

## Limitations

- UDP transport has no delivery guarantee, authentication, or production hardening.
- Timing and resource values are simulation constants, not real-time or physical-performance claims.
- The dashboard requires a graphical environment.
- `run-demo.sh` uses `pkill` against this project's main-class names during cleanup and is intended for local development only.

## Reuse

No open-source license or team permission to grant one is documented in this snapshot. The source is available for portfolio review, but no permission to copy, modify, or redistribute it is granted here.
