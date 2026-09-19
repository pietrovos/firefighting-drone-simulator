package DroneSwarmSim;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Integration tests for queue draining and load distribution with mock drones. */
@DisplayName("Multi-Incident Queue-Drain and Balanced-Load Integration Tests")
class MultiIncidentQueueDrainIT {

    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("qi_zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Verifies that all queued incidents of mixed severities (LOW, MODERATE, HIGH) are
     * eventually assigned to the available drones with no incident permanently dropped.
     *
     * <p>Scenario: 3 mock drones, 6 incidents (two of each severity). Mock drones
     * automatically respond with {@code DRONE_UPDATE / IDLE} after each task so they
     * become available for re-assignment. All 6 incidents must be assigned within the
     * timeout window.
     *
     * @throws Exception if any networking or synchronisation error occurs
     */
    @Test
    @Timeout(60)
    void testAllIncidentsAssignedWithMixedSeverities() throws Exception {
        int schedulerPort = 15900;
        int[] dronePorts  = { 15950, 15951, 15952 };

        File zoneFile = createZoneFile(
                "1,(0;0),(100;100)",   "2,(100;0),(200;100)",  "3,(200;0),(300;100)",
                "4,(0;100),(100;200)", "5,(100;100),(200;200)", "6,(200;100),(300;200)");

        AtomicInteger[] counts = {
            new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0)
        };
        CountDownLatch allAssigned = new CountDownLatch(6);
        AtomicReference<UdpSender> senderRef = new AtomicReference<>();

        UdpReceiver[] mockDrones = new UdpReceiver[3];
        for (int i = 0; i < 3; i++) {
            final int idx    = i;
            final int dId    = i + 1;
            mockDrones[i] = new UdpReceiver(dronePorts[i], data -> {
                if (PacketParser.getType(data) != MessageType.ASSIGN_TASK) return;
                counts[idx].incrementAndGet();
                allAssigned.countDown();
                // Simulate mission completion: report IDLE so the scheduler can reassign us.
                UdpSender s = senderRef.get();
                if (s != null) {
                    try {
                        Thread.sleep(50);
                        s.send(PacketBuilder.build(
                                new DroneUpdate(dId, DroneState.IDLE, 0, 0, 15)));
                    } catch (Exception ignored) { /* test teardown */ }
                }
            });
            mockDrones[i].start();
        }

        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        Thread.sleep(200);

        try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
            senderRef.set(sender);

            // Register all three drones.
            for (int i = 0; i < 3; i++) {
                sender.send(PacketBuilder.build(
                        new DroneRegister(i + 1, "localhost", dronePorts[i])));
            }
            Thread.sleep(200);

            // Send 6 incidents: LOW×2, MODERATE×2, HIGH×2 across distinct zones.
            Severity[] sev = {
                Severity.LOW, Severity.MODERATE, Severity.HIGH,
                Severity.LOW, Severity.HIGH,     Severity.MODERATE
            };
            for (int i = 0; i < 6; i++) {
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:0" + i, i + 1, EventType.FIRE_DETECTED, sev[i])));
                Thread.sleep(80);
            }

            assertTrue(allAssigned.await(45, TimeUnit.SECONDS),
                    "All 6 incidents should be assigned (queue fully drained)");
        } finally {
            schedulerThread.interrupt();
            for (UdpReceiver dr : mockDrones) dr.close();
        }

        // Verify every incident was assigned.
        int total = counts[0].get() + counts[1].get() + counts[2].get();
        assertEquals(6, total, "All 6 incidents must be assigned");
    }

    /**
     * Balanced Load Demo: ≥3 drones, 6 incidents → each drone receives 1–3 tasks,
     * demonstrating that the scheduler's load-balancing heuristic prevents any single
     * drone from monopolising all assignments.
     *
     * <p>After each assignment the mock drone immediately reports {@code IDLE} so the
     * scheduler can re-use it. With 3 drones and 6 incidents, a perfectly balanced
     * system assigns 2 tasks per drone; we allow ±1 deviation for scheduling jitter.
     *
     * @throws Exception if any networking or synchronisation error occurs
     */
    @Test
    @Timeout(60)
    void testBalancedLoadAcrossThreeDrones() throws Exception {
        int schedulerPort = 15901;
        int[] dronePorts  = { 15953, 15954, 15955 };

        // All 6 zones are equidistant from the origin so ETA does not skew distribution.
        File zoneFile = createZoneFile(
                "1,(0;0),(100;100)",    "2,(100;0),(200;100)",  "3,(200;0),(300;100)",
                "4,(300;0),(400;100)",  "5,(400;0),(500;100)",  "6,(500;0),(600;100)");

        AtomicInteger[] counts = {
            new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0)
        };
        CountDownLatch allAssigned = new CountDownLatch(6);
        AtomicReference<UdpSender> senderRef = new AtomicReference<>();

        UdpReceiver[] mockDrones = new UdpReceiver[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            final int dId = i + 1;
            mockDrones[i] = new UdpReceiver(dronePorts[i], data -> {
                if (PacketParser.getType(data) != MessageType.ASSIGN_TASK) return;
                counts[idx].incrementAndGet();
                allAssigned.countDown();
                UdpSender s = senderRef.get();
                if (s != null) {
                    try {
                        Thread.sleep(50);
                        s.send(PacketBuilder.build(
                                new DroneUpdate(dId, DroneState.IDLE, 0, 0, 15)));
                    } catch (Exception ignored) { /* test teardown */ }
                }
            });
            mockDrones[i].start();
        }

        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath());
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        Thread.sleep(200);

        try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
            senderRef.set(sender);

            for (int i = 0; i < 3; i++) {
                sender.send(PacketBuilder.build(
                        new DroneRegister(i + 1, "localhost", dronePorts[i])));
            }
            Thread.sleep(200);

            // 6 incidents of the same severity so only load count affects selection.
            for (int i = 0; i < 6; i++) {
                sender.send(PacketBuilder.build(
                        new IncidentReport("10:0" + i, i + 1, EventType.FIRE_DETECTED, Severity.LOW)));
                Thread.sleep(100);
            }

            assertTrue(allAssigned.await(45, TimeUnit.SECONDS),
                    "All 6 incidents should be assigned");
        } finally {
            schedulerThread.interrupt();
            for (UdpReceiver dr : mockDrones) dr.close();
        }

        int total = counts[0].get() + counts[1].get() + counts[2].get();
        assertEquals(6, total, "All 6 incidents must be assigned");

        // Fair distribution: each drone should receive between 1 and 3 tasks.
        for (int i = 0; i < 3; i++) {
            int c = counts[i].get();
            assertTrue(c >= 1 && c <= 3,
                    "Drone " + (i + 1) + " should receive 1-3 tasks for balanced load; got " + c);
        }
    }
}
