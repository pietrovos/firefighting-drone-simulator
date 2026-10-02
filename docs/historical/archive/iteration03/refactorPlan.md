# Iteration 03 — Proposed Architecture Refactor (Package Layout)
    src/main/java/DroneSwarmSim/
      fire/
        FireIncidentMain.java            # process entry point
        FireIncidentSubsystem.java
        FireIncident.java
        EventType.java

      scheduler/
        SchedulerMain.java               # process entry point
        Scheduler.java
        SchedulingState.java
        FaultTypes.java

      drone/
        DroneMain.java                   # process entry point (run N instances)
        DroneSubsystem.java
        DroneState.java
        DroneTask.java
        DroneInfo.java
        DroneStatusUpdate.java

      messaging/                          # new: shared message DTOs
        MessageType.java
        IncidentReport.java
        AssignTask.java
        DroneRegister.java
        DroneUpdate.java
        Ack.java

      net/udp/                            # new: transport adapters
        UdpEndpoint.java
        UdpSender.java
        UdpReceiver.java
        PacketBuilder.java               # JSON encode
        PacketParser.java                # JSON decode

      model/
        Severity.java
        Zone.java
        Timer.java

      ui/
        GUI.java

      config/
        SchedulerConfig.java
        FireIncidentConfig.java
        DroneConfig.java

# Migration & Changes Table (Old → New)
Below is the table mapping **current directories/files** (from the 3 branch trees) to the new **Iteration 03‑compliant architecture**.
| Existing File (Current Trees) | New Location / Action                                                     | Notes                                         |
| ----------------------------- | ------------------------------------------------------------------------- | --------------------------------------------- |
| `Main.java`                   | **Remove** → replaced by `SchedulerMain`, `FireIncidentMain`, `DroneMain` | Required to satisfy “3 independent processes” |
| `IncidentReportChannel.java`  | **Delete** → Replaced by `messaging/IncidentReport` + `net/udp/*`         | Channels were thread‑based; no longer valid   |
| `DroneCommChannel.java`       | **Delete** → Replaced by `messaging/AssignTask` + UDP                     | Same as above                                 |
| `DroneStatusChannel.java`     | **Delete** → Replaced by `messaging/DroneUpdate` + UDP                    | Same as above                                 |
| `FireIncidentSubsystem.java`  | Move → `fire/FireIncidentSubsystem.java`                                  | Domain logic unchanged                        |
| `FireIncident.java`           | Move → `fire/FireIncident.java`                                           | Domain model                                  |
| `EventType.java`              | Move → `fire/EventType.java`                                              | Domain enum                                   |
| `Scheduler.java`              | Move → `scheduler/Scheduler.java`                                         | Add multi‑drone registry + assignment         |
| `SchedulingState.java`        | Move → `scheduler/SchedulingState.java`                                   | OK                                            |
| `FaultTypes.java`             | Move → `scheduler/FaultTypes.java`                                        | OK                                            |
| `DroneSubsystem.java`         | Move → `drone/DroneSubsystem.java`                                        | Domain logic stays here                       |
| `DroneState.java`             | Move → `drone/DroneState.java`                                            | Domain enum                                   |
| `DroneTask.java`              | Move → `drone/DroneTask.java`                                             | Domain model                                  |
| `DroneInfo.java`              | Move → `drone/DroneInfo.java`                                             | From Adam’s branch                            |
| `DroneStatusUpdate.java`      | Move → `drone/DroneStatusUpdate.java`                                     | Existing in Adam’s branch                     |
| `UDPMessage.java`             | **Split** → `messaging/*.java`                                            | Break monolith into strongly‑typed DTOs       |
| `NetworkConfig.java`          | Move → `config/*.java`                                                    | Becomes typed subsystem configs               |
| `Severity.java`               | Move → `model/Severity.java`                                              | Domain model                                  |
| `Zone.java`                   | Move → `model/Zone.java`                                                  | Domain model                                  |
| `Timer.java`                  | Move → `model/Timer.java`                                                 | Domain model                                  |
| Tests referencing channels    | **Rewrite** to test DTOs + PacketBuilder/Parser + UDP loopback            | Replace channel mocks with packet tests       |
| `GUI.java`                    | Move → `ui/GUI.java`                                                      | Add Assignment panel for Iteration 03         |