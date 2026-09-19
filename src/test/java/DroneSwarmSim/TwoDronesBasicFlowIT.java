package DroneSwarmSim;

import DroneSwarmSim.drone.DroneSubsystem;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.fire.FireIncidentSubsystem;
import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import DroneSwarmSim.net.udp.UdpSender;
import DroneSwarmSim.scheduler.Scheduler;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Integration test class for validating functionality of the scheduler and drone subsystems
 * using a basic scenario involving two drones.
 * <p>
 * This test class focuses on verifying tasks such as assignment of incidents to drones,
 * registration of drones with the scheduler, and the integrated flow of handling an incident
 * until mission completion.
 */
@DisplayName("Two-Drones Basic Flow Integration Test")
class TwoDronesBasicFlowIT {

    private static final int SCHEDULER_PORT = 15601;
    private static final int DRONE1_PORT    = 15651;
    private static final int DRONE2_PORT    = 15652;

    /**
     * Creates a temporary CSV zone file with the given data lines.
     * The file includes a predefined header row: "Zone ID, Zone Start, Zone End".
     * Each line provided in the dataLines parameter will be written as a new row in the file.
     * The file is scheduled for deletion when the JVM exits.
     *
     * @return a {@code File} object representing the created temporary zone file.
     * @throws Exception if an error occurs while creating or writing to the file.
     */
    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("it_zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Creates a temporary CSV event file with the given data lines.
     * The file includes a predefined header row: "Time, Zone ID, Event type,Severity".
     * Each line provided in the dataLines parameter will be written as a new row in the file.
     * The file is scheduled for deletion when the JVM exits.
     *
     * @param dataLines variable number of strings, each representing a row of event data to be included in the file.
     *                  Each string should adhere to the format expected by the predefined header.
     * @return a {@code File} object representing the created temporary event file.
     * @throws Exception if an error occurs while creating or writing to the file.
     */
    private File createEventFile(String... dataLines) throws Exception {
        File f = File.createTempFile("it_events", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Time,Zone ID,Event type,Severity");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Verifies the behaviour of the scheduler when a single incident is reported
     * and two drones are registered to handle tasks. Ensures that exactly one drone
     * is assigned the task and that only a single assignment message is sent.
     * <p>
     * This test performs the following steps:
     * 1. Creates a temporary zone file for the scheduler configuration.
     * 2. Mocks two drones using UDP receivers and listens for task assignment messages.
     * 3. Starts the scheduler and registers both drones.
     * 4. Sends an incident report to the scheduler.
     * 5. Validates that only one drone receives the task assignment message.
     * <p>
     * The test ensures:
     * - Only one assignment message is sent for the incident.
     * - Exactly one drone responds to the task assignment.
     * <p>
     * @throws Exception if an error occurs during test execution.
     */
    @Test
    @Timeout(15)
    void testOneIncidentAssignedToOneDrone() throws Exception {
        File zoneFile = createZoneFile("1,(0;0),(100;100)");

        List<byte[]> d1Tasks = new CopyOnWriteArrayList<>();
        List<byte[]> d2Tasks = new CopyOnWriteArrayList<>();
        CountDownLatch assignLatch = new CountDownLatch(1);

        // Verifies a single incident assigns to exactly one drone using mock receivers
        try (UdpReceiver mockDrone1 = new UdpReceiver(DRONE1_PORT, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) { d1Tasks.add(data); assignLatch.countDown(); }
             });
             UdpReceiver mockDrone2 = new UdpReceiver(DRONE2_PORT, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) { d2Tasks.add(data); assignLatch.countDown(); }
             })) {
            mockDrone1.start();
            mockDrone2.start();

            Scheduler scheduler = new Scheduler(SCHEDULER_PORT, zoneFile.getAbsolutePath());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            Thread.sleep(150);

            // Sends drone registrations and fire incident report to scheduler
            try (UdpSender sender = new UdpSender("localhost", SCHEDULER_PORT)) {
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", DRONE1_PORT)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", DRONE2_PORT)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.LOW)));
            }

            assertTrue(assignLatch.await(5, TimeUnit.SECONDS),
                    "Exactly one drone should receive an AssignTask");

            int total = d1Tasks.size() + d2Tasks.size();
            assertEquals(1, total, "Only one assignment should be sent for one incident");

            schedulerThread.interrupt();
        }
    }

    /**
     * Tests the full workflow of the system using real drone subsystems, a scheduler, and a fire incident subsystem.
     * The test simulates a fire incident, assigns the appropriate task to a drone, monitors the process,
     * and verifies that the mission is completed as expected.
     * <p>
     * The test performs the following steps:
     * 1. Creates temporary files for the zone and fire event configurations.
     * 2. Initializes the scheduler, two drone subsystems, and the fire incident subsystem.
     * 3. Starts the scheduler, drones, and fire incident subsystem as separate daemon threads.
     * 4. Simulates the handling of a low-severity fire incident.
     * 5. Confirms the following:
     *    - At least one drone starts a mission (leaving the IDLE state).
     *    - The drone completes the mission and returns to the IDLE state.
     * <p>
     * Assertions:
     * - Ensures that a mission starts within a specified timeout period.
     * - Ensures that the assigned mission is completed within a specified timeout period.
     * <p>
     * @throws Exception if any error occurs during the creation of resources, initialization,
     *                   thread execution, or state verification.
     */
    /**
     * Creates a zone file that is guaranteed to contain at least one zone entry
     * with zoneId = 1, so that incidents referencing zone 1 can be assigned
     * by the Scheduler during this test.
     */
    private File createZoneFileWithSingleZone() throws Exception {
        return createZoneFile("1,(0;0),(100;100)");
    }

    @Test
    @Timeout(40)
    void testFullFlowWithRealDrones() throws Exception {
        int sPort  = 15602;
        int d1Port = 15653;
        int d2Port = 15654;

        File zoneFile  = createZoneFileWithSingleZone();
        File eventFile = createEventFile("10:00:00,1,FIRE_DETECTED,LOW");

        Scheduler scheduler = new Scheduler(sPort, zoneFile.getAbsolutePath());
        DroneSubsystem drone1 = new DroneSubsystem(1, "localhost", sPort, d1Port);
        DroneSubsystem drone2 = new DroneSubsystem(2, "localhost", sPort, d2Port);
        FireIncidentSubsystem fis = new FireIncidentSubsystem(eventFile.getAbsolutePath(), "localhost", sPort);

        Thread st  = new Thread(scheduler); st.setDaemon(true);
        Thread dt1 = new Thread(drone1);    dt1.setDaemon(true);
        Thread dt2 = new Thread(drone2);    dt2.setDaemon(true);
        Thread ft  = new Thread(fis);       ft.setDaemon(true);

        st.start();
        dt1.start();
        dt2.start();
        Thread.sleep(300);
        ft.start();

        // Phase 1: wait until at least one drone LEAVES idle (assignment in progress)
        long deadline = System.currentTimeMillis() + 15_000;
        boolean missionStarted = false;
        // Verifies at least one drone starts a mission within deadline
        while (System.currentTimeMillis() < deadline) {
            if (drone1.getState() != DroneSwarmSim.drone.DroneState.IDLE
                    || drone2.getState() != DroneSwarmSim.drone.DroneState.IDLE) {
                missionStarted = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(missionStarted, "At least one drone should have started a mission");

        // Phase 2: wait until that drone returns to IDLE (mission complete)
        deadline = System.currentTimeMillis() + 20_000;
        boolean missionCompleted = false;
        while (System.currentTimeMillis() < deadline) {
            // Both drones IDLE again means the assigned drone finished
            if (drone1.getState() == DroneSwarmSim.drone.DroneState.IDLE
                    && drone2.getState() == DroneSwarmSim.drone.DroneState.IDLE) {
                missionCompleted = true;
                break;
            }
            Thread.sleep(200);
        }

        assertTrue(missionCompleted,
                "The assigned drone should have returned to IDLE after completing the mission");

        st.interrupt();  dt1.interrupt();  dt2.interrupt();
    }
}
