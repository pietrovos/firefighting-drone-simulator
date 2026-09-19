# Firefighting Drone Swarm Simulator

This Java simulation dispatches a fleet of firefighting drones over UDP. Fire
incidents enter a priority queue, the scheduler assigns an available drone, and
the drone reports its position and state while completing the mission. The
simulation also injects communication, stuck-drone, and nozzle faults so the
scheduler can recover or reassign the incident. A Swing dashboard shows the
system while it runs.

## Team and contributions

The simulator was a four-person Carleton University SYSC 3303 project by Pietro
Adamvoski, Avery Robertson, Adam Haddadin, and Fareen Lavji. This repository is a
portfolio copy of the team's application.

Pietro wrote the initial Scheduler and Fire Incident subsystem implementations.
His later work included task and status coordination, the runtime dashboard,
telemetry display, fault recovery, incident reassignment, scheduler and queue
tests, the first metrics implementation, and the final launcher used for the
20-drone demonstration.

The other team members contributed substantial parts of the drone subsystem,
UDP architecture, fault infrastructure, tests, documentation, logging, and
resource simulation.

## Processes and messages

The demo starts three Java processes:

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

`SchedulerMain` owns the incident queue and fleet state. It dispatches work,
handles timeouts and reassignment, records metrics, and runs the dashboard.
`DroneMain` starts one `DroneSubsystem` thread for each drone, so the default
20-drone demo still uses a single drone process. `FireIncidentMain` reads the CSV
event file and sends incidents on a compressed clock.

The shared messaging code covers registration, incidents, assignments,
acknowledgements, telemetry, and fault reports.

## Requirements

- Java 17 or later
- Maven 3.8 or later
- Bash and a graphical desktop session

## Running the demo

```bash
./run-demo.sh
```

The launcher compiles the project, starts the scheduler, asks how many drones to
run, and begins event playback. The default is 20 drone threads using
`src/Sample_event_file.csv`. Its five incidents are sent in under a second on
the compressed clock, although missions and fault countdowns continue after the
last event. Press `Ctrl+C` to stop the processes started by the script.

The longer scenario in `src/Final_event_file_w26.csv` contains 50 events. Event
playback takes about 9 minutes and 26 seconds, followed by any missions still in
progress. Run the three commands below in separate terminals:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="DroneSwarmSim.scheduler.SchedulerMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.fire.FireIncidentMain" -Dexec.args="src/Final_event_file_w26.csv"
```

Passing an ID to `DroneMain` starts one drone instead of the configured fleet:

```bash
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain" -Dexec.args="3"
```

## Tests

```bash
mvn test
```

The suite covers scheduler selection and queue order, drone state and water
capacity, fault recovery, CSV parsing, packet validation, UDP loopback, and
mission coordination. Tests ending in `IT` run several components through local
UDP sockets in one JVM. They test integration between components, not the full
three-process launcher. The GUI tests exercise its model-facing behavior;
checking the rendered dashboard still requires a desktop session.

## Limits and reuse

UDP delivery is not guaranteed, and the protocol has no authentication or
production hardening. Distances, timing, battery, and fuel are simulation values,
not physical performance claims. The dashboard requires a graphical environment.
During cleanup, `run-demo.sh` uses `pkill` with this project's main-class names,
so it is intended for local use.

The team did not document an open-source license or permission for one member to
grant a license on everyone's behalf. The code is available for portfolio
review, but this repository does not grant permission to copy, modify, or
redistribute it.
