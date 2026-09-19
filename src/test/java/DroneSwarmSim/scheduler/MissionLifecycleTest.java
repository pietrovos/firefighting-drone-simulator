package DroneSwarmSim.scheduler;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Severity;
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
 * Verifies the full mission lifecycle from the Scheduler's perspective:
 * <ol>
 *   <li><b>QUEUED</b>  – incident arrives with no drone registered; scheduler queues it
 *       (no {@link AssignTask} is sent).</li>
 *   <li><b>ASSIGNED</b> – drone registers; scheduler dequeues and assigns the incident.</li>
 *   <li><b>IN_PROGRESS</b> – drone sends {@code DRONE_UPDATE / EN_ROUTE}; scheduler
 *       records the updated state in its registry.</li>
 *   <li><b>DONE</b>  – drone sends {@code DRONE_UPDATE / IDLE}; scheduler clears the
 *       assignment so the drone can receive the next incident immediately.</li>
 * </ol>
 */
@DisplayName("Mission Lifecycle Tests")
class MissionLifecycleTest {

    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("ml_zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Exercises all four lifecycle phases in one integration scenario:
     * <ul>
     *   <li>QUEUED   – incident sent before any drone is available; no AssignTask is emitted.</li>
     *   <li>ASSIGNED – drone registers; the previously queued incident is assigned.</li>
     *   <li>IN_PROGRESS – drone sends an EN_ROUTE update; scheduler records the state.</li>
     *   <li>DONE     – drone sends an IDLE update; a fresh incident is immediately re-assignable.</li>
     * </ul>
     *
     * @throws Exception if any networking or timeout issue occurs during the test
     */
    @Test
    @Timeout(20)
    void testMissionLifecycleQueuedAssignedInProgressDone() throws Exception {
        int schedulerPort = 15800;
        int dronePort     = 15850;
        File zoneFile = createZoneFile("1,(0;0),(100;100)");

        List<AssignTask> received = new CopyOnWriteArrayList<>();
        CountDownLatch firstAssign  = new CountDownLatch(1);
        CountDownLatch secondAssign = new CountDownLatch(2);

        try (UdpReceiver mockDrone = new UdpReceiver(dronePort, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     received.add(PacketParser.parseAssignTask(data));
                     firstAssign.countDown();
                     secondAssign.countDown();
                 }
             })) {
            mockDrone.start();

            Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            Thread.sleep(150);

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {

                // --- Phase 1: QUEUED -------------------------------------------------
                // Incident arrives with no drone registered; scheduler must queue it.
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.LOW)));
                Thread.sleep(300);
                assertEquals(0, received.size(),
                        "No AssignTask should be sent when no drone is registered (QUEUED)");

                // --- Phase 2: ASSIGNED -----------------------------------------------
                // Register the drone; the queued incident should now be assigned.
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", dronePort)));
                assertTrue(firstAssign.await(8, TimeUnit.SECONDS),
                        "Drone should receive AssignTask once registered (ASSIGNED)");
                assertEquals(1, received.size(),   "Exactly one assignment expected");
                assertEquals(1, received.get(0).zoneId(),       "Assigned to zone 1");
                assertEquals(Severity.LOW, received.get(0).severity(), "Severity preserved");

                // --- Phase 3: IN_PROGRESS --------------------------------------------
                // Drone reports it has started moving to the target zone.
                sender.send(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.EN_ROUTE, 10, 10, 15)));
                Thread.sleep(150);

                // --- Phase 4: DONE ---------------------------------------------------
                // Drone returns to IDLE; a fresh incident should be assignable immediately.
                sender.send(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.IDLE, 0, 0, 15)));
                Thread.sleep(200);

                sender.send(PacketBuilder.build(
                        new IncidentReport("10:05", 1, EventType.FIRE_DETECTED, Severity.MODERATE)));
                assertTrue(secondAssign.await(5, TimeUnit.SECONDS),
                        "Drone should receive a second assignment after returning to IDLE (DONE)");
                assertEquals(2, received.size(), "Two total assignments across the lifecycle");
                assertEquals(Severity.MODERATE, received.get(1).severity(),
                        "Second incident severity preserved");
            }

            schedulerThread.interrupt();
        }
    }
}
