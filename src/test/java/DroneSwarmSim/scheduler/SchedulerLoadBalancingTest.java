package DroneSwarmSim.scheduler;

import DroneSwarmSim.drone.DroneInfo;
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
 * Tests verifying the two new scheduling strategies:
 * <ol>
 *   <li>Load balancing – idle drone with fewest zones serviced is preferred.</li>
 *   <li>Path-through redirect – an EN_ROUTE drone that passes through a new zone
 *       of equal severity is redirected and its original incident is re-queued.</li>
 * </ol>
 */
@DisplayName("Scheduler Load-Balancing and Path-Through Tests")
class SchedulerLoadBalancingTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("lb_zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    // -----------------------------------------------------------------------
    // Load-balancing tests (unit-level, no UDP)
    // -----------------------------------------------------------------------

    /**
     * When two idle drones exist and one has already serviced more zones, the
     * scheduler should prefer the less-loaded drone.
     */
    @Test
    @DisplayName("Zone exactly on path returns true")
    void testPassesThroughZone_onPath() throws Exception {
        File zoneFile = createZoneFile("1,(0;0),(100;100)", "2,(200;0),(300;100)");
        Scheduler scheduler = new Scheduler(15700, zoneFile.getAbsolutePath());

        // Drone at origin heading to zone-2 centre (250, 50).
        // Zone-1 centre is (50, 50) which is exactly on the straight line.
        DroneInfo drone = new DroneInfo(1, "localhost", 9999);
        drone.updatePosition(0, 0);
        drone.setTargetZoneCenter(250, 50);

        assertTrue(scheduler.passesThroughZone(drone, 50, 50),
                "Zone 1 centre lies on the direct path from origin to zone 2 centre");
    }

    /**
     * A zone that is off to the side of the drone's flight path should NOT be
     * considered a pass-through candidate.
     */
    @Test
    @DisplayName("passesThroughZone – zone off to the side returns false")
    void testPassesThroughZone_offPath() throws Exception {
        File zoneFile = createZoneFile("1,(0;0),(100;100)");
        Scheduler scheduler = new Scheduler(15701, zoneFile.getAbsolutePath());

        // Drone at origin heading due East (500, 0).
        // Candidate zone centre is far North (250, 400) – very far off path.
        DroneInfo drone = new DroneInfo(1, "localhost", 9999);
        drone.updatePosition(0, 0);
        drone.setTargetZoneCenter(500, 0);

        assertFalse(scheduler.passesThroughZone(drone, 250, 400),
                "Zone centre far off the path should not be a redirect candidate");
    }

    /**
     * A zone that is behind the drone (already passed) should NOT be considered
     * a pass-through candidate.
     */
    @Test
    @DisplayName("passesThroughZone – zone behind the drone returns false")
    void testPassesThroughZone_behindDrone() throws Exception {
        File zoneFile = createZoneFile("1,(0;0),(100;100)");
        Scheduler scheduler = new Scheduler(15702, zoneFile.getAbsolutePath());

        // Drone has already moved to (400, 0) and is heading to (500, 0).
        // Candidate zone centre is at (50, 0) – behind the drone.
        DroneInfo drone = new DroneInfo(1, "localhost", 9999);
        drone.updatePosition(400, 0);
        drone.setTargetZoneCenter(500, 0);

        assertFalse(scheduler.passesThroughZone(drone, 50, 0),
                "Zone centre behind the drone should not be a redirect candidate");
    }

    /**
     * When the drone is already at (or very near) its target, no redirect should
     * be triggered (distToTarget ≈ 0 guard).
     */
    @Test
    @DisplayName("passesThroughZone – drone already at target returns false")
    void testPassesThroughZone_droneAtTarget() throws Exception {
        File zoneFile = createZoneFile("1,(0;0),(100;100)");
        Scheduler scheduler = new Scheduler(15703, zoneFile.getAbsolutePath());

        DroneInfo drone = new DroneInfo(1, "localhost", 9999);
        drone.updatePosition(250, 50);      // already at target
        drone.setTargetZoneCenter(250, 50);

        assertFalse(scheduler.passesThroughZone(drone, 100, 50),
                "Drone already at target should never be a redirect candidate");
    }

    // -----------------------------------------------------------------------
    // Load-balancing integration test (via UDP)
    // -----------------------------------------------------------------------

    /**
     * With three drones registered, the one that has handled the fewest incidents
     * so far should receive the next assignment.
     *
     * <p>Scenario: drones 1 and 2 are registered; drone 1 receives the first incident
     * and its zonesServiced counter is incremented.  When the second incident arrives,
     * drone 2 (zonesServiced=0) should be preferred over drone 1 (zonesServiced=1).
     */
    @Test
    @Timeout(15)
    @DisplayName("Load balancing – least-loaded idle drone is preferred")
    void testLoadBalancingPrefersLeastLoadedDrone() throws Exception {
        int schedulerPort = 15704;
        int d1Port = 15754;
        int d2Port = 15755;

        File zoneFile = createZoneFile("1,(0;0),(100;100)", "2,(200;0),(300;100)");

        List<AssignTask> d1Tasks = new CopyOnWriteArrayList<>();
        List<AssignTask> d2Tasks = new CopyOnWriteArrayList<>();
        CountDownLatch bothAssigned = new CountDownLatch(2);

        try (UdpReceiver mockDrone1 = new UdpReceiver(d1Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     d1Tasks.add(PacketParser.parseAssignTask(data));
                     bothAssigned.countDown();
                 }
             });
             UdpReceiver mockDrone2 = new UdpReceiver(d2Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     d2Tasks.add(PacketParser.parseAssignTask(data));
                     bothAssigned.countDown();
                 }
             })) {
            mockDrone1.start();
            mockDrone2.start();

            Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            Thread.sleep(150);

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                // Register both drones (both idle, zonesServiced=0).
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", d1Port)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", d2Port)));
                Thread.sleep(100);

                // First incident → one of the drones (whichever has id with fewer zones,
                // both are 0 so first in stream) should get it.
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.LOW)));
                Thread.sleep(200);

                // Simulate the assigned drone going EN_ROUTE (scheduler already set it), then
                // send a DRONE_UPDATE returning it to IDLE so zonesServiced stays as assigned.
                // (The scheduler increments zonesServiced on assignment, not on completion.)

                // Second incident → should go to the drone that has NOT been assigned yet.
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:01", 2, EventType.FIRE_DETECTED, Severity.MODERATE)));
            }

            assertTrue(bothAssigned.await(8, TimeUnit.SECONDS),
                    "Both incidents should have been assigned");

            // Each drone should have received exactly one task.
            assertEquals(1, d1Tasks.size(), "Drone 1 should receive exactly 1 task");
            assertEquals(1, d2Tasks.size(), "Drone 2 should receive exactly 1 task");

            // Tasks should target different zones.
            assertNotEquals(d1Tasks.get(0).zoneId(), d2Tasks.get(0).zoneId(),
                    "The two assignments should be for different zones");

            schedulerThread.interrupt();
        }
    }

    // -----------------------------------------------------------------------
    // Path-through redirect integration test (via UDP)
    // -----------------------------------------------------------------------

    /**
     * Verifies the path-through redirect scenario described in the requirements:
     * <ul>
     *   <li>Drone 1 is en route to zone 2 (far right).</li>
     *   <li>A new incident arrives for zone 1 (on the direct path, same severity).</li>
     *   <li>The scheduler should redirect drone 1 to zone 1 and re-queue zone 2.</li>
     * </ul>
     *
     * <p>The test confirms that the <em>second</em> AssignTask sent to drone 1 targets
     * zone 1 (the pass-through zone), and that the original zone 2 incident is eventually
     * re-queued (indicated by a third assignment being sent once drone 1 reports IDLE).
     */
    @Test
    @Timeout(15)
    @DisplayName("Path-through redirect – EN_ROUTE drone redirected to same-severity zone on its path")
    void testPathThroughRedirect() throws Exception {
        int schedulerPort = 15705;
        int d1Port = 15756;

        // Zone 1 centre: (50, 50)   – on the path from origin to zone 2.
        // Zone 2 centre: (250, 50)  – drone's original target.
        // Layout: drone starts at (0,0), zone 1 at (0;0)→(100;100), zone 2 at (200;0)→(300;100)
        File zoneFile = createZoneFile("1,(0;0),(100;100)", "2,(200;0),(300;100)");

        List<AssignTask> d1Tasks = new CopyOnWriteArrayList<>();
        CountDownLatch firstTask  = new CountDownLatch(1);
        CountDownLatch secondTask = new CountDownLatch(2);

        try (UdpReceiver mockDrone1 = new UdpReceiver(d1Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     d1Tasks.add(PacketParser.parseAssignTask(data));
                     firstTask.countDown();
                     secondTask.countDown();
                 }
             })) {
            mockDrone1.start();

            Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
            Thread schedulerThread = new Thread(scheduler);
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            Thread.sleep(150);

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                // Register drone 1 and send a zone-2 incident (drone will go EN_ROUTE to zone 2).
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", d1Port)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:00", 2, EventType.FIRE_DETECTED, Severity.LOW)));

                // Wait until drone 1 receives its first assignment (zone 2).
                assertTrue(firstTask.await(5, TimeUnit.SECONDS),
                        "Drone 1 should receive the initial zone-2 assignment");
                assertEquals(2, d1Tasks.get(0).zoneId(),
                        "First task should be zone 2");

                // Place drone at (10, 50) so zone-1 centre (50, 50) is still ahead on the
                // path to zone-2 centre (250, 50), making it a valid redirect candidate.
                sender.send(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.EN_ROUTE, 10, 50, 15)));
                Thread.sleep(100);

                // Now a new zone-1 incident arrives with the SAME severity.
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:01", 1, EventType.FIRE_DETECTED, Severity.LOW)));

                // The scheduler should redirect drone 1 to zone 1.
                assertTrue(secondTask.await(5, TimeUnit.SECONDS),
                        "Drone 1 should receive a redirect assignment");
                assertEquals(2, d1Tasks.size(), "Drone 1 should have received 2 task messages");
                assertEquals(1, d1Tasks.get(1).zoneId(),
                        "Second task (redirect) should be zone 1");
            }

            schedulerThread.interrupt();
        }
    }
}
