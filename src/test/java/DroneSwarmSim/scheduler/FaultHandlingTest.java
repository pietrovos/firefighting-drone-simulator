package DroneSwarmSim.scheduler;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import DroneSwarmSim.net.udp.UdpSender;
import DroneSwarmSim.ui.StubGUI;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Integration tests for Iteration 04 fault handling.
 * Verifies that the Scheduler correctly:
 * <ol>
 *   <li>Passes fault types from IncidentReport to AssignTask (fault injection via CSV column).</li>
 *   <li>Handles FAULT_REPORT from a drone and re-queues the affected incident.</li>
 *   <li>Reassigns the incident to a healthy drone after a fault is reported.</li>
 * </ol>
 */
@DisplayName("Fault Handling Tests")
class FaultHandlingTest {

    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("fault_zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Verifies that when a NOZZLE_STUCK_CLOSED fault is specified in the IncidentReport,
     * the Scheduler forwards it to the drone via AssignTask.faultType().
     *
     * @throws Exception on networking or timeout error
     */
    @Test
    @Timeout(15)
    void testFaultTypeForwardedInAssignTask() throws Exception {
        int schedulerPort = 16100;
        int dronePort     = 16150;

        File zoneFile = createZoneFile("1,(0;0),(100;100)");
        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath(), gui);

        Thread schedulerThread = new Thread(scheduler, "sched-fault-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        AssignTask received;
        try (UdpReceiver droneListener = new UdpReceiver(dronePort, data -> {})) {
            droneListener.start();

            CountDownLatch latch = new CountDownLatch(1);
            AssignTask[] captured = new AssignTask[1];

            try (UdpReceiver captureListener = new UdpReceiver(dronePort + 1, data -> {
                if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                    captured[0] = PacketParser.parseAssignTask(data);
                    latch.countDown();
                }
            })) {
                captureListener.start();

                try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                    // Register drone on the capture port
                    sender.send(PacketBuilder.build(new DroneRegister(99, "localhost", dronePort + 1)));
                    Thread.sleep(100);
                    sender.send(PacketBuilder.build(new DroneUpdate(99, DroneState.IDLE, 50, 50, 15.0)));
                    Thread.sleep(100);

                    // Send an incident with NOZZLE_STUCK_CLOSED fault
                    IncidentReport report = new IncidentReport(
                            "14:00:00", 1, EventType.FIRE_DETECTED, Severity.LOW,
                            FaultTypes.NOZZLE_STUCK_CLOSED);
                    sender.send(PacketBuilder.build(report));
                }

                assertTrue(latch.await(10, TimeUnit.SECONDS),
                        "Drone should receive AssignTask within 10s");
                received = captured[0];
            }
        }

        assertNotNull(received, "AssignTask must be received");
        assertEquals(FaultTypes.NOZZLE_STUCK_CLOSED, received.faultType(),
                "Fault type must be forwarded to drone via AssignTask");
        schedulerThread.interrupt();
    }

    /**
     * Verifies that when a drone sends a FAULT_REPORT, the Scheduler:
     * <ol>
     *   <li>Marks the drone as FAULTED.</li>
     *   <li>Re-queues the original incident (with NONE fault type).</li>
     *   <li>Assigns the re-queued incident to the healthy backup drone.</li>
     * </ol>
     *
     * @throws Exception on networking or timeout error
     */
    @Test
    @Timeout(20)
    void testFaultReportTriggersReassignment() throws Exception {
        int schedulerPort = 16200;
        int drone1Port    = 16251;
        int drone2Port    = 16252;

        File zoneFile = createZoneFile("1,(0;0),(100;100)");
        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath(), gui);

        Thread schedulerThread = new Thread(scheduler, "sched-reassign-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        // Track what each drone receives
        List<AssignTask> drone1Tasks = new CopyOnWriteArrayList<>();
        List<AssignTask> drone2Tasks = new CopyOnWriteArrayList<>();
        CountDownLatch drone2Latch = new CountDownLatch(1);

        try (UdpReceiver dr1 = new UdpReceiver(drone1Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     AssignTask t = PacketParser.parseAssignTask(data);
                     if (t != null) drone1Tasks.add(t);
                 }
             });
             UdpReceiver dr2 = new UdpReceiver(drone2Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     AssignTask t = PacketParser.parseAssignTask(data);
                     if (t != null) {
                         drone2Tasks.add(t);
                         drone2Latch.countDown();
                     }
                 }
             })) {

            dr1.start();
            dr2.start();

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                // Register two drones — drone 1 will fault, drone 2 will be the backup
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", drone1Port)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", drone2Port)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(new DroneUpdate(1, DroneState.IDLE, 50, 50, 15.0)));
                sender.send(PacketBuilder.build(new DroneUpdate(2, DroneState.IDLE, 50, 50, 15.0)));
                Thread.sleep(100);

                // Send a zone 1 incident with NOZZLE_STUCK_CLOSED fault for drone 1
                IncidentReport report = new IncidentReport(
                        "14:00:00", 1, EventType.FIRE_DETECTED, Severity.LOW,
                        FaultTypes.NOZZLE_STUCK_CLOSED);
                sender.send(PacketBuilder.build(report));

                // Wait a bit for drone 1 to receive the task
                Thread.sleep(500);

                // Drone 1 reports the fault back to the scheduler
                FaultReport faultReport = new FaultReport(1, FaultTypes.NOZZLE_STUCK_CLOSED,
                        "Nozzle stuck closed — cannot drop water");
                sender.send(PacketBuilder.build(faultReport));
            }

            // Wait for drone 2 to receive the reassigned task
            assertTrue(drone2Latch.await(12, TimeUnit.SECONDS),
                    "Drone 2 should receive the reassigned task within 12s");

            assertFalse(drone2Tasks.isEmpty(), "Drone 2 must receive reassigned task");
            assertEquals(1, drone2Tasks.get(0).zoneId(), "Reassigned task must be for zone 1");
            assertEquals(FaultTypes.NONE, drone2Tasks.get(0).faultType(),
                    "Reassigned task must have NONE fault type");

            // Verify GUI logged the fault and reassignment for the correct drone
            String logs = gui.allLogs.toString();
            assertTrue(logs.contains("[FAULT] Drone 1") || logs.contains("[HARD FAULT] Drone 1"),
                    "GUI must log the fault event for drone 1 — was:\n" + logs);

            schedulerThread.interrupt();
        }
    }

    @Test
    @Timeout(15)
    void testDroneStuckTimeoutTriggersReassignment() throws Exception {
        int schedulerPort = 16300;
        int drone1Port = 16351;
        int drone2Port = 16352;

        File zoneFile = createZoneFile("1,(0;0),(100;100)");
        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath(), gui, 1500);

        Thread schedulerThread = new Thread(scheduler, "sched-timeout-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        CountDownLatch drone2Latch = new CountDownLatch(1);
        List<AssignTask> drone2Tasks = new CopyOnWriteArrayList<>();

        try (UdpReceiver dr1 = new UdpReceiver(drone1Port, data -> { });
             UdpReceiver dr2 = new UdpReceiver(drone2Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     AssignTask task = PacketParser.parseAssignTask(data);
                     if (task != null) {
                         drone2Tasks.add(task);
                         drone2Latch.countDown();
                     }
                 }
             })) {
            dr1.start();
            dr2.start();

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", drone1Port)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", drone2Port)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(new DroneUpdate(1, DroneState.IDLE, 50, 50, 15.0)));
                sender.send(PacketBuilder.build(new DroneUpdate(2, DroneState.IDLE, 50, 50, 15.0)));
                Thread.sleep(100);

                IncidentReport report = new IncidentReport(
                        "14:05:00", 1, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.DRONE_STUCK);
                sender.send(PacketBuilder.build(report));
            }

            assertTrue(drone2Latch.await(8, TimeUnit.SECONDS),
                    "Backup drone should receive the reassigned task after timeout");
            assertFalse(drone2Tasks.isEmpty(), "Backup drone must receive a task");
            assertEquals(FaultTypes.NONE, drone2Tasks.get(0).faultType(),
                    "Reassigned stuck-flight task should not carry the fault again");

            String logs = gui.allLogs.toString();
            assertTrue(logs.contains("[TIMEOUT] Drone 1"), "GUI should log the timeout");
            assertTrue(logs.contains("[REASSIGN] Reassigning zone 1"), "GUI should log the reassignment");
        } finally {
            schedulerThread.interrupt();
        }
    }

    @Test
    @Timeout(20)
    void testHardFaultMarksDroneOffline() throws Exception {
        int schedulerPort = 16400;
        int drone1Port = 16451;
        int drone2Port = 16452;

        File zoneFile = createZoneFile("1,(0;0),(100;100)");
        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath(), gui);

        Thread schedulerThread = new Thread(scheduler, "sched-hard-fault-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        List<AssignTask> drone1Tasks = new CopyOnWriteArrayList<>();
        List<AssignTask> drone2Tasks = new CopyOnWriteArrayList<>();
        CountDownLatch backupLatch = new CountDownLatch(1);

        try (UdpReceiver dr1 = new UdpReceiver(drone1Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     AssignTask task = PacketParser.parseAssignTask(data);
                     if (task != null) {
                         drone1Tasks.add(task);
                     }
                 }
             });
             UdpReceiver dr2 = new UdpReceiver(drone2Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     AssignTask task = PacketParser.parseAssignTask(data);
                     if (task != null) {
                         drone2Tasks.add(task);
                         backupLatch.countDown();
                     }
                 }
             })) {
            dr1.start();
            dr2.start();

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", drone1Port)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", drone2Port)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(new DroneUpdate(1, DroneState.IDLE, 50, 50, 15.0)));
                sender.send(PacketBuilder.build(new DroneUpdate(2, DroneState.IDLE, 50, 50, 15.0)));
                Thread.sleep(100);

                sender.send(PacketBuilder.build(new IncidentReport(
                        "14:10:00", 1, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NOZZLE_STUCK_OPEN)));

                Thread.sleep(500);
                sender.send(PacketBuilder.build(new FaultReport(1, FaultTypes.NOZZLE_STUCK_OPEN,
                        "Nozzle stuck open - hard fault")));
            }

            assertTrue(backupLatch.await(12, TimeUnit.SECONDS),
                    "Healthy drone should pick up the reassigned hard-fault mission");
            assertEquals(1, drone1Tasks.size(), "Faulted drone should only get the original task");
            assertFalse(drone2Tasks.isEmpty(), "Healthy drone must receive the replacement task");
            assertEquals(FaultTypes.NONE, drone2Tasks.get(0).faultType(),
                    "Replacement task should run without the injected hard fault");

            String logs = gui.allLogs.toString();
            assertTrue(logs.contains("[HARD FAULT] Drone 1"), "GUI should log the hard fault");
            assertTrue(logs.contains("offline") || logs.contains("OFFLINE"),
                    "GUI output should make it clear the drone is offline");
        } finally {
            schedulerThread.interrupt();
        }
    }

    /**
     * Verifies that FaultReport messages can be serialized and deserialized correctly.
     */
    @Test
    void testFaultReportPacketRoundTrip() {
        FaultReport original = new FaultReport(42, FaultTypes.NOZZLE_STUCK_OPEN,
                "Nozzle stuck open — hard fault");
        byte[] packet = PacketBuilder.build(original);

        assertEquals(MessageType.FAULT_REPORT, PacketParser.getType(packet),
                "Packet type must be FAULT_REPORT");

        FaultReport parsed = PacketParser.parseFaultReport(packet);
        assertNotNull(parsed, "Parsed FaultReport must not be null");
        assertEquals(original.droneId(), parsed.droneId());
        assertEquals(original.faultType(), parsed.faultType());
        assertEquals(original.message(), parsed.message());
    }

    /**
     * Verifies that AssignTask records now carry fault type and round-trip correctly.
     */
    @Test
    void testAssignTaskWithFaultRoundTrip() {
        AssignTask original = new AssignTask(3, 7, 0, 0, 100, 100, Severity.HIGH, FaultTypes.DRONE_STUCK);
        byte[] packet = PacketBuilder.build(original);

        AssignTask parsed = PacketParser.parseAssignTask(packet);
        assertNotNull(parsed);
        assertEquals(FaultTypes.DRONE_STUCK, parsed.faultType());
        assertEquals(3, parsed.droneId());
    }

    /**
     * Verifies that IncidentReport records with a fault type round-trip correctly.
     */
    @Test
    void testIncidentReportWithFaultRoundTrip() {
        IncidentReport original = new IncidentReport(
                "10:00:00", 5, EventType.FIRE_DETECTED, Severity.MODERATE, FaultTypes.PACKET_LOSS);
        byte[] packet = PacketBuilder.build(original);

        IncidentReport parsed = PacketParser.parseIncidentReport(packet);
        assertNotNull(parsed);
        assertEquals(FaultTypes.PACKET_LOSS, parsed.faultType());
        assertEquals(5, parsed.zoneId());
    }

    /**
     * Verifies that IncidentReport records without a fault default to NONE.
     */
    @Test
    void testIncidentReportDefaultsToNoFault() {
        IncidentReport original = new IncidentReport("09:00:00", 3, EventType.FIRE_DETECTED, Severity.LOW);
        assertEquals(FaultTypes.NONE, original.faultType());
    }
}
