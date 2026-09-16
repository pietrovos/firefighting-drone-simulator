package DroneSwarmSim.drone;

import DroneSwarmSim.messaging.AssignTask;
import DroneSwarmSim.messaging.DroneUpdate;
import DroneSwarmSim.messaging.FaultReport;
import DroneSwarmSim.messaging.MessageType;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import DroneSwarmSim.net.udp.UdpSender;
import DroneSwarmSim.scheduler.FaultTypes;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for fault-flag detection and drone recovery behaviour.
 *
 * <p>Test IDs covered:
 * <ul>
 *   <li>UT-DRONE-FAULTFLAG-01 – nozzle stuck-closed: state moves to FAULTED,
 *       fault notification sent, drone subsequently recovers to IDLE.</li>
 *   <li>UT-DRONE-FAULTFLAG-02 – nozzle stuck-open (hard fault): state moves to
 *       FAULTED and the drone shuts down permanently (no IDLE recovery).</li>
 * </ul>
 */
@DisplayName("Drone Fault Recovery Tests")
class DroneFaultRecoveryTest {

    /**
     * UT-DRONE-FAULTFLAG-01 – Nozzle stuck closed (soft fault).
     *
     * <p>When a drone detects a NOZZLE_STUCK_CLOSED fault it must:
     * <ol>
     *   <li>Transition its state to {@code FAULTED}.</li>
     *   <li>Send a {@code FaultReport} carrying {@code NOZZLE_STUCK_CLOSED} to the
     *       scheduler.</li>
     *   <li>Subsequently recover and return to {@code IDLE} with a full tank, so it
     *       can accept future missions.</li>
     * </ol>
     */
    @Test
    @Timeout(20)
    @DisplayName("UT-DRONE-FAULTFLAG-01: nozzle stuck-closed → FAULTED, fault report sent, recovers to IDLE")
    void nozzleStuckClosedFaultFlagTest() throws Exception {
        int schedulerPort = 16500;
        int droneId = 215;
        int dronePort = 16550;

        List<DroneUpdate> updates = new CopyOnWriteArrayList<>();
        List<FaultReport> faultReports = new CopyOnWriteArrayList<>();
        CountDownLatch faultLatch = new CountDownLatch(1);
        CountDownLatch idleLatch = new CountDownLatch(1);

        try (UdpReceiver mockScheduler = new UdpReceiver(schedulerPort, data -> {
            MessageType type = PacketParser.getType(data);
            if (type == MessageType.DRONE_UPDATE) {
                DroneUpdate update = PacketParser.parseDroneUpdate(data);
                if (update != null) {
                    updates.add(update);
                    if (update.getState() == DroneState.IDLE && !faultReports.isEmpty()) {
                        idleLatch.countDown();
                    }
                }
            } else if (type == MessageType.FAULT_REPORT) {
                FaultReport report = PacketParser.parseFaultReport(data);
                if (report != null) {
                    faultReports.add(report);
                    faultLatch.countDown();
                }
            }
        })) {
            mockScheduler.start();

            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, dronePort);
            Thread droneThread = new Thread(drone, "drone-fault-recovery-test");
            droneThread.setDaemon(true);
            droneThread.start();

            Thread.sleep(300);

            try (UdpSender sender = new UdpSender("localhost", dronePort)) {
                sender.send(PacketBuilder.build(new AssignTask(
                        droneId, 1, 0, 0, 0, 0, Severity.LOW, FaultTypes.NOZZLE_STUCK_CLOSED)));
            }

            assertTrue(faultLatch.await(10, TimeUnit.SECONDS),
                    "Drone should send a FAULT_REPORT for the nozzle-stuck-closed fault");
            assertTrue(idleLatch.await(10, TimeUnit.SECONDS),
                    "Drone should recover and return to IDLE after a soft fault");

            assertFalse(faultReports.isEmpty(), "Expected at least one FaultReport from the drone");
            assertEquals(FaultTypes.NOZZLE_STUCK_CLOSED, faultReports.get(0).faultType(),
                    "FaultReport must carry NOZZLE_STUCK_CLOSED type");

            assertTrue(updates.stream().anyMatch(u -> u.getState() == DroneState.FAULTED),
                    "Drone must publish a FAULTED DroneUpdate before recovering");

            DroneUpdate finalIdle = updates.stream()
                    .filter(u -> u.getState() == DroneState.IDLE)
                    .reduce((first, second) -> second)
                    .orElse(null);
            assertNotNull(finalIdle, "Drone should publish a final IDLE update after recovery");
            assertEquals(15.0, finalIdle.getWater(), 0.001,
                    "Drone water tank should be full after recovery");

            droneThread.interrupt();
            droneThread.join(2000);
        }
    }

    /**
     * UT-DRONE-FAULTFLAG-02 – Nozzle stuck open (hard fault).
     *
     * <p>A NOZZLE_STUCK_OPEN fault must cause the drone to enter {@code FAULTED}
     * permanently.  The drone sends a {@code FaultReport} and does <em>not</em>
     * transition back to {@code IDLE}, because the nozzle jam is unrecoverable.
     */
    @Test
    @Timeout(15)
    @DisplayName("UT-DRONE-FAULTFLAG-02: nozzle stuck-open (hard fault) → permanently FAULTED, no IDLE recovery")
    void nozzleStuckOpenHardFaultTest() throws Exception {
        int schedulerPort = 16501;
        int droneId = 216;
        int dronePort = 16560;

        List<DroneUpdate>  updates      = new CopyOnWriteArrayList<>();
        List<FaultReport>  faultReports = new CopyOnWriteArrayList<>();
        CountDownLatch     faultLatch   = new CountDownLatch(1);

        try (UdpReceiver mockScheduler = new UdpReceiver(schedulerPort, data -> {
            MessageType type = PacketParser.getType(data);
            if (type == MessageType.DRONE_UPDATE) {
                DroneUpdate u = PacketParser.parseDroneUpdate(data);
                if (u != null) updates.add(u);
            } else if (type == MessageType.FAULT_REPORT) {
                FaultReport r = PacketParser.parseFaultReport(data);
                if (r != null) { faultReports.add(r); faultLatch.countDown(); }
            }
        })) {
            mockScheduler.start();

            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, dronePort);
            Thread droneThread = new Thread(drone, "drone-hard-fault-test");
            droneThread.setDaemon(true);
            droneThread.start();

            Thread.sleep(300);

            try (UdpSender sender = new UdpSender("localhost", dronePort)) {
                sender.send(PacketBuilder.build(new AssignTask(
                        droneId, 1, 0, 0, 0, 0, Severity.LOW, FaultTypes.NOZZLE_STUCK_OPEN)));
            }

            assertTrue(faultLatch.await(10, TimeUnit.SECONDS),
                    "Drone should send a FAULT_REPORT for the nozzle-stuck-open hard fault");

            assertEquals(FaultTypes.NOZZLE_STUCK_OPEN, faultReports.get(0).faultType(),
                    "Hard fault report must carry NOZZLE_STUCK_OPEN type");

            // Allow a short window for any further state updates
            Thread.sleep(500);

            assertTrue(updates.stream().anyMatch(u -> u.getState() == DroneState.FAULTED),
                    "Drone must publish FAULTED state for hard fault");
            assertFalse(updates.stream().anyMatch(u -> u.getState() == DroneState.IDLE),
                    "Drone must NOT recover to IDLE after a hard (nozzle stuck-open) fault");

            droneThread.interrupt();
            droneThread.join(2000);
        }
    }
}
