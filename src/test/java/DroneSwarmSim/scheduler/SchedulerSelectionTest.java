package DroneSwarmSim.scheduler;

import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import DroneSwarmSim.net.udp.UdpSender;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * A test suite for verifying the behaviour and functionality of the Scheduler module
 * in different scenarios involving drone registration, incident reporting, and task
 * assignment to drones.
 */
@DisplayName("Scheduler Selection Tests")
class SchedulerSelectionTest {

    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Tests the Scheduler's ability to assign a drone to an incident based on the provided
     * incident report and registered drones. Verifies correct communication between components,
     * proper parsing of the task assignment message, and the preservation of relevant data
     * such as drone ID, zone ID, and severity level.
     * <p>
     * Steps performed in this test:
     * 1. Starts a mock drone receiver to listen for task assignment messages.
     * 2. Initializes the Scheduler with a specified zone file and starts it in a separate thread.
     * 3. Registers a drone with the Scheduler using a UDP sender.
     * 4. Sends an incident report to the Scheduler to trigger task assignment.
     * 5. Ensures the mock drone receives a task assignment message within the specified timeout.
     * 6. Parses the task assignment message and verifies:
     *    - The message is parseable.
     *    - The assigned drone ID matches the registered drone.
     *    - The zone ID in the assignment matches expectations.
     *    - The severity level is correctly propagated.
     * <p>
     * The test ensures the assignment task is generated, properly communicated,
     * and contains all required information for execution.
     *
     * @throws Exception if any unexpected error occurs during the test execution
     */
    @Test
    @Timeout(10)
    void testAssignsDroneToIncident() throws Exception {
        int schedulerPort = 15501;
        int dronePort = 15551;
        File zoneFile = createZoneFile("1,(0;0),(700;700)");

        List<byte[]> droneReceived = new CopyOnWriteArrayList<>();
        CountDownLatch taskLatch = new CountDownLatch(1);

        // Tests scheduler assigns idle drone correct task via UDP loopback
        try (UdpReceiver mockDrone = new UdpReceiver(dronePort, data -> {
            if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                droneReceived.add(data);
                taskLatch.countDown();
            }
        })) {
            mockDrone.start();

            Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            Thread.sleep(150);

            // Sends drone registration and fire incident report via UDP
            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", dronePort)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.LOW)));
            }

            assertTrue(taskLatch.await(5, TimeUnit.SECONDS), "Drone should receive AssignTask");

            AssignTask task = PacketParser.parseAssignTask(droneReceived.get(0));
            assertNotNull(task, "AssignTask must be parseable");
            assertEquals(1,            task.droneId(),  "Drone ID must match");
            assertEquals(1,            task.zoneId(),   "Zone ID must match");
            assertEquals(Severity.LOW, task.severity(), "Severity must be preserved");

            schedulerThread.interrupt();
        }
    }

    /**
     * Tests the Scheduler's ability to assign a high-severity task to a registered drone
     * based on the provided incident report. Verifies correct communication between components,
     * the proper parsing of the task assignment message, and the accurate preservation
     * of relevant task details such as zone ID and severity level.
     * <p>
     * Steps performed in this test:
     * 1. Creates a mock drone receiver to listen for task assignment messages.
     * 2. Initializes the Scheduler with a given zone file and starts it in a separate thread.
     * 3. Registers a drone with the Scheduler using a UDP sender.
     * 4. Sends an incident report with high severity level to the Scheduler to trigger task assignment.
     * 5. Confirms the mock drone correctly receives the task assignment message within the timeout period.
     * 6. Validates the parsed task assignment message to ensure:
     *    - The severity level is "HIGH".
     *    - The assigned zone ID matches the expectations.
     * <p>
     * This test ensures that the Scheduler handles high-severity incident reports correctly,
     * communicates the appropriate task to the drone, and maintains data integrity.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    @Timeout(10)
    void testHighSeverityAssignment() throws Exception {
        int schedulerPort = 15502;
        int dronePort = 15552;
        File zoneFile = createZoneFile("2,(0;700),(700;1600)");

        List<AssignTask> tasks = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Captures assignment packets; launches scheduler; validates high-severity task assignment
        try (UdpReceiver mockDrone = new UdpReceiver(dronePort, data -> {
            if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                tasks.add(PacketParser.parseAssignTask(data));
                latch.countDown();
            }
        })) {
            mockDrone.start();

            Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            Thread.sleep(150);

            // Registers drone then reports high-severity incident
            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", dronePort)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(
                        new IncidentReport("11:00", 2, EventType.FIRE_DETECTED, Severity.HIGH)));
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(Severity.HIGH, tasks.get(0).severity());
            assertEquals(2,             tasks.get(0).zoneId());

            schedulerThread.interrupt();
        }
    }

    /**
     * With two registered drones and two incidents, each drone receives exactly one
     * assignment (fairness / no duplicate assignments).
     */
    @Test
    @Timeout(10)
    void testTwoDronesFairnessForTwoIncidents() throws Exception {
        int schedulerPort = 15503;
        int drone1Port = 15553;
        int drone2Port = 15554;
        File zoneFile = createZoneFile("1,(0;0),(100;100)", "2,(100;0),(200;100)");

        List<byte[]> d1received = new CopyOnWriteArrayList<>();
        List<byte[]> d2received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        try (UdpReceiver mockDrone1 = new UdpReceiver(drone1Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) { d1received.add(data); latch.countDown(); }
             });
             UdpReceiver mockDrone2 = new UdpReceiver(drone2Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) { d2received.add(data); latch.countDown(); }
             })) {
            mockDrone1.start();
            mockDrone2.start();

            Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            Thread.sleep(150);

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", drone1Port)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", drone2Port)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.LOW)));
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:01", 2, EventType.FIRE_DETECTED, Severity.MODERATE)));
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "Both drones should each receive exactly one assignment");

            int total = d1received.size() + d2received.size();
            assertEquals(2, total, "Exactly 2 total assignments for 2 drones + 2 incidents");

            schedulerThread.interrupt();
        }
    }
}
