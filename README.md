# Firefighting Drone Swarm Simulator

I worked on this Java simulator to coordinate a fleet of firefighting drones
over UDP. Fire incidents enter a priority queue, and the scheduler assigns an
available drone to each mission. Drones send back their position and state as
they work. The simulation can inject communication failures, stuck drones, and
nozzle faults to test recovery and incident reassignment. You can watch the
simulation through a Swing dashboard.

## Team and contributions

I built this with Avery Robertson, Adam Haddadin, and Fareen Lavji for SYSC 3303
at Carleton University. This is my portfolio copy of our team's application,
based on the final submission with an updated mission-control dashboard. The
CSV inputs, Maven build file, and launcher match what we submitted. I also kept
the submitted tests, with corrections to the metrics API calls and queued-task
test timing.

I wrote the initial Scheduler and Fire Incident subsystems. Later, I worked on
task and status coordination, the runtime dashboard and telemetry display,
fault recovery, and incident reassignment. I also wrote scheduler and queue
tests, the first metrics implementation, and the final launcher we used for
the 20-drone demonstration.

My teammates contributed substantial parts of the drone subsystem, UDP
architecture, fault infrastructure, tests, documentation, logging, and resource
simulation.

## Processes and messages

We split the simulation into three Java processes:

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
20-drone demo uses a single drone process. `FireIncidentMain` reads the CSV
event file and sends incidents on a compressed clock.

Our shared messaging code handles registration, incidents, assignments,
acknowledgements, telemetry, and fault reports.

## Requirements

- Java 17 or later
- Maven 3.8 or later
- Bash and a graphical desktop session

## Running the demo

I use the launcher to start the simulation:

```bash
./run-demo.sh
```

It asks how many drones to run, compiles the project, and starts the scheduler,
drones, and event playback. It defaults to 20 drone threads using
`src/Final_event_file_w26.csv` and `src/Final_zone_file_w26.csv`. If you have
Zenity or KDialog installed, the drone-count prompt opens in a dialog.
Otherwise, it appears in the terminal. Press `Ctrl+C` to stop the processes
started by the script.

Our final scenario has 50 events. Playback takes about 9 minutes and 26 seconds,
and drones finish any remaining missions afterward. You can also start the
processes manually. Compile once, then run each of the three Java commands in
a separate terminal:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="DroneSwarmSim.scheduler.SchedulerMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain"
mvn exec:java -Dexec.mainClass="DroneSwarmSim.fire.FireIncidentMain" -Dexec.args="src/Final_event_file_w26.csv"
```

For a shorter run, use `-Dexec.args="src/Sample_event_file.csv"` with
`FireIncidentMain`. That file has five incidents, all sent in under a second
on the compressed clock. Missions and fault countdowns continue afterward.

Passing an ID to `DroneMain` starts one drone instead of the configured fleet:

```bash
mvn exec:java -Dexec.mainClass="DroneSwarmSim.drone.DroneMain" -Dexec.args="3"
```

## Dashboard

I use the dashboard to follow the response map and inspect the fleet. The map
fills its panel when you resize the window or move the dividers. Each cell
represents 100 × 100 metres, though its displayed proportions depend on the
panel's size. The fleet tabs sit beside the map, which starts square when the
window has enough width. Logs start hidden. Use Show logs to open them below
the map and Hide logs to close them.

- Select a fleet row, a drone on the map, or an entry in Inspect drone to
  see its state, position, and water, battery, and fuel indicators.
- Filter the fleet by drone number or state, and click a column heading to
  sort it. Resource bars turn amber or red as supplies run low.
- Open Missions & faults for active assignments and fault countdowns.
  Map legend explains the map's colors and symbols.
- Grey building tiles beside HQ represent a civilian area outside the response
  zones. Water splashes highlight only the fire cells the current drop can clear;
  the remaining cells continue burning until a follow-up drop.
- Drag the dividers to give the map, fleet, or logs more space.
- Logs have Follow latest, Copy, Save, and Clear view controls.
  Scrolling upward keeps your reading position. Clearing the view does not
  delete the recorded files. Each log view retains about 200,000 characters
  so extended telemetry runs do not grow the display indefinitely.

## Tests

I run the tests with Maven:

```bash
mvn test
```

Our tests cover scheduler selection and queue order, drone state and water
capacity, fault recovery, CSV parsing, packet validation, UDP loopback, and
mission coordination. Tests ending in `IT` run several components through local
UDP sockets in one JVM. Their scope is component integration; the full
three-process launcher needs a separate run. The GUI tests check the dashboard's
state handling. I check the rendered dashboard in a desktop session.

## Demo, reports, and design documents

I collected our final demo video, project and verification reports, architecture
diagrams, iteration notes, and recorded logs in the
[supporting material index](docs/README.md). The code here is based on our final
submission. The earlier iteration documents describe the designs we used
along the way.

- [Watch the final demo](docs/demo/final_demo_video.mp4)
- [Read the project report](docs/reports/final_Report.pdf)
- [Read the verification report](docs/reports/final_vv_report.pdf)
- [Browse the original architecture and results](docs/historical/group-README.md)

## Limits and reuse

This is a simulation. UDP delivery is not guaranteed, and we did not add
authentication or production hardening to the protocol. Distances, timing,
battery, and fuel are simulated values rather than measurements of real
drones. You need a graphical environment for the dashboard. The launcher is
intended for local use because its cleanup calls `pkill` with the project's
main-class names.

We did not document an open-source license or permission for one team member
to license everyone's work. I keep this repository available for portfolio
review, but it does not grant permission to copy, modify, or redistribute the
code.
