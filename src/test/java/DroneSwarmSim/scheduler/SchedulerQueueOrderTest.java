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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests covering Scheduler incident-queue ordering.
 *
 * <p>Test IDs covered:
 * <ul>
 *   <li>UT-SCHED-QUEUE-01 – events are served in arrival (FIFO) order; severity
 *       affects required agent volume, not queue position.</li>
 *   <li>UT-SCHED-ASSIGN-02 – scheduler correctly selects the drone with fewer
 *       zones serviced when two drones are otherwise equivalent.</li>
 * </ul>
 */
@DisplayName("Scheduler Queue-Ordering Tests")
class SchedulerQueueOrderTest {

    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("queue_zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * UT-SCHED-QUEUE-01 – Insertion order respected across mixed severities.
     *
     * <p>Setup: a single drone, three zones (LOW → MODERATE → HIGH) all enqueued
     * before the drone becomes idle.  Because the scheduler uses arrival-FIFO
     * ordering (severity affects agent volume, not queue priority), the drone
     * must receive the tasks in zone-insertion order: 1 → 2 → 3.
     *
     * <p>We pause the drone between tasks by giving it a large zone it needs to
     * travel to, then counting the sequence of {@code AssignTask} messages it
     * receives.
     */
    @Test
    @Timeout(30)
    @DisplayName("UT-SCHED-QUEUE-01: incidents served in arrival order regardless of severity")
    void incidentsServedInArrivalOrder() throws Exception {
        int schedulerPort = 16600;
        int dronePort     = 16650;
        int droneId       = 300;

        // Three zones with different severities; all sent to the scheduler before
        // the drone is registered so they pile up in the incident queue first.
        File zoneFile = createZoneFile(
                "1,(0;0),(100;100)",
                "2,(0;0),(100;100)",
                "3,(0;0),(100;100)"
        );

        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath(), gui);
        Thread schedulerThread = new Thread(scheduler, "sched-queue-order-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        List<Integer> assignedZoneOrder = new CopyOnWriteArrayList<>();
        CountDownLatch threeTasksLatch  = new CountDownLatch(3);

        try (UdpReceiver droneListener = new UdpReceiver(dronePort, data -> {
            if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                AssignTask t = PacketParser.parseAssignTask(data);
                if (t != null) {
                    assignedZoneOrder.add(t.zoneId());
                    threeTasksLatch.countDown();
                }
            }
        })) {
            droneListener.start();

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                // Enqueue all three incidents in order 1 → 2 → 3.
                // All LOW severity (10 L each) so each is handled in one trip without
                // capacity-driven re-queuing — this test checks FIFO order, not capacity.
                sender.send(PacketBuilder.build(new IncidentReport(
                        "09:00:00", 1, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NONE)));
                sender.send(PacketBuilder.build(new IncidentReport(
                        "09:00:01", 2, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NONE)));
                sender.send(PacketBuilder.build(new IncidentReport(
                        "09:00:02", 3, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NONE)));
                Thread.sleep(100);

                // Register the drone and report it IDLE so it starts pulling from the queue
                sender.send(PacketBuilder.build(new DroneRegister(droneId, "localhost", dronePort)));
                Thread.sleep(50);
                sender.send(PacketBuilder.build(new DroneUpdate(droneId, DroneState.IDLE, 0, 0, 15.0)));
                Thread.sleep(50);

                // After the drone gets the first task, simulate rapid COMPLETED→IDLE cycles
                // so the scheduler can assign subsequent tasks
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(200);
                    sender.send(PacketBuilder.build(
                            new DroneUpdate(droneId, DroneState.ARRIVED,   0, 0, 15.0)));
                    sender.send(PacketBuilder.build(
                            new DroneUpdate(droneId, DroneState.COMPLETED, 0, 0, 0.0)));
                    sender.send(PacketBuilder.build(
                            new DroneUpdate(droneId, DroneState.IDLE,      0, 0, 15.0)));
                }
            }

            boolean allReceived = threeTasksLatch.await(20, TimeUnit.SECONDS);
            assertTrue(allReceived,
                    "Drone should receive all 3 task assignments; got "
                    + assignedZoneOrder.size() + ": " + assignedZoneOrder);

            // The scheduler must serve incidents in the order they arrived (FIFO)
            assertEquals(3, assignedZoneOrder.size(), "Exactly 3 assignments expected");
            assertEquals(1, (int) assignedZoneOrder.get(0), "First task must be zone 1 (arrived first)");
            assertEquals(2, (int) assignedZoneOrder.get(1), "Second task must be zone 2 (arrived second)");
            assertEquals(3, (int) assignedZoneOrder.get(2), "Third task must be zone 3 (arrived third)");
        } finally {
            schedulerThread.interrupt();
        }
    }

    /**
     * UT-SCHED-ASSIGN-02 – scheduler prefers the drone with fewer missions completed
     * (load-balance) when two drones are equally positioned and both idle.
     *
     * <p>This is the assignment-policy counterpart to the queue-ordering test:
     * it verifies that the scheduler's drone-selection criterion is observed.
     */
    @Test
    @Timeout(15)
    @DisplayName("UT-SCHED-ASSIGN-02: scheduler picks less-loaded drone (fewest zones serviced)")
    void schedulerSelectsLessLoadedDrone() throws Exception {
        int schedulerPort = 16700;
        int drone1Port    = 16751;
        int drone2Port    = 16752;

        File zoneFile = createZoneFile(
                "1,(0;0),(100;100)",
                "2,(0;0),(100;100)"
        );

        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath(), gui);
        Thread schedulerThread = new Thread(scheduler, "sched-lb-assign-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        List<AssignTask> drone1Tasks = new CopyOnWriteArrayList<>();
        List<AssignTask> drone2Tasks = new CopyOnWriteArrayList<>();
        CountDownLatch secondTask = new CountDownLatch(1);

        try (UdpReceiver dr1 = new UdpReceiver(drone1Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     AssignTask t = PacketParser.parseAssignTask(data);
                     if (t != null) drone1Tasks.add(t);
                 }
             });
             UdpReceiver dr2 = new UdpReceiver(drone2Port, data -> {
                 if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                     AssignTask t = PacketParser.parseAssignTask(data);
                     if (t != null) { drone2Tasks.add(t); secondTask.countDown(); }
                 }
             })) {
            dr1.start();
            dr2.start();

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                // Register both drones
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", drone1Port)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", drone2Port)));
                Thread.sleep(100);
                sender.send(PacketBuilder.build(new DroneUpdate(1, DroneState.IDLE, 0, 0, 15.0)));
                sender.send(PacketBuilder.build(new DroneUpdate(2, DroneState.IDLE, 0, 0, 15.0)));
                Thread.sleep(100);

                // First incident → should go to one of the drones (both equal at this point)
                sender.send(PacketBuilder.build(new IncidentReport(
                        "10:00:00", 1, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NONE)));
                Thread.sleep(300);

                // Simulate drone 1 completing its mission (it has now serviced 1 zone)
                sender.send(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.ARRIVED,   0, 0, 15.0)));
                sender.send(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.COMPLETED, 0, 0, 0.0)));
                sender.send(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.IDLE,      0, 0, 15.0)));
                Thread.sleep(100);

                // Second incident — drone 2 has served 0 zones, drone 1 has served 1.
                // Load-balancer must prefer drone 2.
                sender.send(PacketBuilder.build(new IncidentReport(
                        "10:00:05", 2, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NONE)));
            }

            assertTrue(secondTask.await(10, TimeUnit.SECONDS),
                    "Drone 2 should receive the second assignment (load-balancing)");
            assertFalse(drone2Tasks.isEmpty(),
                    "Less-loaded drone (drone 2) must receive the second task");
            assertEquals(2, drone2Tasks.get(0).zoneId(),
                    "Second task must be for zone 2");
        } finally {
            schedulerThread.interrupt();
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("UT-SCHED-QUEUE-03: repeated fires in one zone wait for the active fire")
    void repeatedZoneIncidentsWaitUntilActiveFireIsExtinguished() throws Exception {
        int schedulerPort = 16710;
        int drone1Port = 16761;
        int drone2Port = 16762;

        File zoneFile = createZoneFile("1,(0;0),(100;100)");

        StubGUI gui = new StubGUI();
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile.getAbsolutePath(), gui);
        Thread schedulerThread = new Thread(scheduler, "sched-same-zone-queue-test");
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        List<Integer> assignedDroneOrder = new CopyOnWriteArrayList<>();
        CountDownLatch firstAssignment = new CountDownLatch(1);
        CountDownLatch secondAssignment = new CountDownLatch(1);

        try (UdpReceiver drone1 = new UdpReceiver(drone1Port, data -> {
                 if (PacketParser.getType(data) != MessageType.ASSIGN_TASK) return;
                 AssignTask task = PacketParser.parseAssignTask(data);
                 if (task != null) {
                     assignedDroneOrder.add(1);
                     if (assignedDroneOrder.size() == 1) firstAssignment.countDown();
                     if (assignedDroneOrder.size() == 2) secondAssignment.countDown();
                 }
             });
             UdpReceiver drone2 = new UdpReceiver(drone2Port, data -> {
                 if (PacketParser.getType(data) != MessageType.ASSIGN_TASK) return;
                 AssignTask task = PacketParser.parseAssignTask(data);
                 if (task != null) {
                     assignedDroneOrder.add(2);
                     if (assignedDroneOrder.size() == 1) firstAssignment.countDown();
                     if (assignedDroneOrder.size() == 2) secondAssignment.countDown();
                 }
             })) {
            drone1.start();
            drone2.start();

            try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
                sender.send(PacketBuilder.build(new DroneRegister(1, "localhost", drone1Port)));
                sender.send(PacketBuilder.build(new DroneRegister(2, "localhost", drone2Port)));
                Thread.sleep(100);

                sender.send(PacketBuilder.build(new DroneUpdate(1, DroneState.IDLE, 0, 0, 15.0)));
                sender.send(PacketBuilder.build(new DroneUpdate(2, DroneState.IDLE, 0, 0, 15.0)));
                Thread.sleep(100);

                sender.send(PacketBuilder.build(new IncidentReport(
                        "11:00:00", 1, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NONE)));

                assertTrue(firstAssignment.await(5, TimeUnit.SECONDS),
                        "First incident should be assigned promptly");

                sender.send(PacketBuilder.build(new IncidentReport(
                        "11:00:01", 1, EventType.FIRE_DETECTED, Severity.LOW, FaultTypes.NONE)));

                assertFalse(secondAssignment.await(2, TimeUnit.SECONDS),
                        "Second fire in the same zone should stay queued until the active fire is extinguished");
                assertEquals(1, assignedDroneOrder.size(),
                        "Only one assignment should exist while the first zone fire is still active");

                int firstDroneId = assignedDroneOrder.get(0);
                sender.send(PacketBuilder.build(new DroneUpdate(firstDroneId, DroneState.ARRIVED, 0, 0, 15.0)));
                sender.send(PacketBuilder.build(new DroneUpdate(firstDroneId, DroneState.COMPLETED, 0, 0, 0.0)));
                sender.send(PacketBuilder.build(new DroneUpdate(firstDroneId, DroneState.IDLE, 0, 0, 15.0)));

                assertTrue(secondAssignment.await(5, TimeUnit.SECONDS),
                        "Queued same-zone fire should be assigned after the active fire is extinguished");
                assertEquals(2, assignedDroneOrder.size(),
                        "Second same-zone fire should be dispatched only after the first one completes");
            }
        } finally {
            schedulerThread.interrupt();
        }
    }
}
