package DroneSwarmSim.drone;

import DroneSwarmSim.config.DroneConfig;
import DroneSwarmSim.messaging.AssignTask;
import DroneSwarmSim.messaging.DroneRegister;
import DroneSwarmSim.messaging.DroneUpdate;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.scheduler.FaultTypes;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates drone status metrics: battery depletion during flight, fuel depletion
 * per metre, REFILLING state reset, and capacity enforcement (liquid-agent fully
 * depleted during DROPPING_AGENT and restored during REFILLING).
 *
 * <p>Design notes for robustness across the full test suite:
 * <ul>
 *   <li>The {@code fakeScheduler} socket uses port 0 (OS-assigned ephemeral port)
 *       so it never conflicts with any statically assigned port in other test classes.</li>
 *   <li>Drone listen ports (16551-16554) are in a range not used by any other test
 *       class; see port registry at the top of each test class.</li>
 *   <li>The drone's actual listen port is confirmed by parsing its REGISTER packet,
 *       eliminating any uncertainty about which port it bound to.</li>
 * </ul>
 */
class DroneStatusMetricsTest {

    // Drone listen ports: 16551-16554 (not used by any other test class)
    private static final int DRONE_LISTEN_51 = 16551;
    private static final int DRONE_LISTEN_52 = 16552;
    private static final int DRONE_LISTEN_53 = 16553;
    private static final int DRONE_LISTEN_54 = 16554;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the drone's listen port from the first (REGISTER) packet it sends.
     */
    private int readDroneListenPort(DatagramSocket fakeScheduler) throws Exception {
        byte[] buf = new byte[4096];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        fakeScheduler.receive(pkt);
        byte[] data = new byte[pkt.getLength()];
        System.arraycopy(pkt.getData(), 0, data, 0, pkt.getLength());
        DroneRegister reg = PacketParser.parseDroneRegister(data);
        assertNotNull(reg, "First packet from drone must be REGISTER");
        return reg.port();
    }

    /**
     * Collects DroneUpdate packets from {@code socket} until the target state is
     * seen or the socket times out / maxPackets is reached.
     */
    private List<DroneUpdate> collectUpdatesUntilState(DatagramSocket socket,
                                                        DroneState targetState,
                                                        int maxPackets) throws Exception {
        List<DroneUpdate> updates = new ArrayList<>();
        byte[] buf = new byte[4096];
        for (int i = 0; i < maxPackets; i++) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(pkt);
            } catch (java.net.SocketTimeoutException e) {
                break;
            }
            byte[] data = new byte[pkt.getLength()];
            System.arraycopy(pkt.getData(), 0, data, 0, pkt.getLength());
            DroneUpdate u = PacketParser.parseDroneUpdate(data);
            if (u != null) {
                updates.add(u);
                if (u.getState() == targetState) break;
            }
        }
        return updates;
    }

    // -------------------------------------------------------------------------
    // Test 1: initial resource levels (pure unit test – no network)
    // -------------------------------------------------------------------------

    @Test
    void droneStartsWithFullConsumables() {
        DroneSubsystem drone = new DroneSubsystem(1, "localhost", 1, 2);
        assertEquals(100.0, drone.getBattery(), 0.01, "Battery should start at 100%");
        assertEquals(100.0, drone.getFuel(),    0.01, "Fuel should start at 100%");
        assertEquals(DroneConfig.AGENT_CAPACITY_LITRES, drone.getWater(), 0.01,
                "Water should start at full capacity");
    }

    // -------------------------------------------------------------------------
    // Test 2: battery drains during flight
    // -------------------------------------------------------------------------

    @Test
    void batteryDepletedDuringFlight() throws Exception {
        try (DatagramSocket fakeScheduler = new DatagramSocket(0)) {
            int schedulerPort = fakeScheduler.getLocalPort();
            fakeScheduler.setSoTimeout(20_000);

            int droneId = 51;
            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, DRONE_LISTEN_51);
            Thread t = new Thread(drone);
            t.setDaemon(true);
            t.start();

            int dronePort = readDroneListenPort(fakeScheduler);

            // Zone ~300 m away to ensure meaningful battery drain
            AssignTask task = new AssignTask(droneId, 1, 0, 0, 300, 300, Severity.LOW, FaultTypes.NONE);
            fakeScheduler.send(new DatagramPacket(PacketBuilder.build(task),
                    PacketBuilder.build(task).length,
                    InetAddress.getLoopbackAddress(), dronePort));

            List<DroneUpdate> updates = collectUpdatesUntilState(fakeScheduler, DroneState.ARRIVED, 200);

            DroneUpdate arrived = updates.stream()
                    .filter(u -> u.getState() == DroneState.ARRIVED)
                    .findFirst().orElse(null);

            assertNotNull(arrived, "Drone should reach ARRIVED state");
            assertTrue(arrived.getBattery() < 100.0,
                    "Battery should be depleted after flight; got " + arrived.getBattery() + "%");
            assertTrue(arrived.getFuel() < 100.0,
                    "Fuel should be depleted after flight; got " + arrived.getFuel() + "%");

            t.interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Test 3: water depleted after dropping agent
    // -------------------------------------------------------------------------

    @Test
    void waterDepletedAfterDrop() throws Exception {
        try (DatagramSocket fakeScheduler = new DatagramSocket(0)) {
            int schedulerPort = fakeScheduler.getLocalPort();
            fakeScheduler.setSoTimeout(30_000);

            int droneId = 52;
            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, DRONE_LISTEN_52);
            Thread t = new Thread(drone);
            t.setDaemon(true);
            t.start();

            int dronePort = readDroneListenPort(fakeScheduler);

            // Zone close to origin for speed
            AssignTask task = new AssignTask(droneId, 1, 0, 0, 100, 100, Severity.LOW, FaultTypes.NONE);
            byte[] taskBytes = PacketBuilder.build(task);
            fakeScheduler.send(new DatagramPacket(taskBytes, taskBytes.length,
                    InetAddress.getLoopbackAddress(), dronePort));

            List<DroneUpdate> updates = collectUpdatesUntilState(fakeScheduler, DroneState.COMPLETED, 300);

            DroneUpdate completed = updates.stream()
                    .filter(u -> u.getState() == DroneState.COMPLETED)
                    .findFirst().orElse(null);

            assertNotNull(completed, "Drone should reach COMPLETED state");
            assertEquals(0.0, completed.getWater(), 0.01, "Water should be 0 after dropping agent");

            t.interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: REFILLING restores all consumables
    // -------------------------------------------------------------------------

    @Test
    void refillingRestoresAllConsumables() throws Exception {
        try (DatagramSocket fakeScheduler = new DatagramSocket(0)) {
            int schedulerPort = fakeScheduler.getLocalPort();
            fakeScheduler.setSoTimeout(60_000);

            int droneId = 53;
            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, DRONE_LISTEN_53);
            Thread t = new Thread(drone);
            t.setDaemon(true);
            t.start();

            int dronePort = readDroneListenPort(fakeScheduler);

            AssignTask task = new AssignTask(droneId, 1, 0, 0, 100, 100, Severity.LOW, FaultTypes.NONE);
            byte[] taskBytes = PacketBuilder.build(task);
            fakeScheduler.send(new DatagramPacket(taskBytes, taskBytes.length,
                    InetAddress.getLoopbackAddress(), dronePort));

            List<DroneUpdate> updates = collectUpdatesUntilState(fakeScheduler, DroneState.REFILLING, 400);
            assertTrue(updates.stream().anyMatch(u -> u.getState() == DroneState.REFILLING),
                    "Drone should enter REFILLING state before returning to IDLE");

            List<DroneUpdate> postRefill = collectUpdatesUntilState(fakeScheduler, DroneState.IDLE, 100);
            DroneUpdate idle = postRefill.stream()
                    .filter(u -> u.getState() == DroneState.IDLE)
                    .findFirst().orElse(null);

            assertNotNull(idle, "Drone should return to IDLE after REFILLING");
            assertEquals(100.0, idle.getBattery(), 0.01,
                    "Battery should be 100% after refill; got " + idle.getBattery() + "%");
            assertEquals(100.0, idle.getFuel(), 0.01,
                    "Fuel should be 100% after refill; got " + idle.getFuel() + "%");
            assertEquals(DroneConfig.AGENT_CAPACITY_LITRES, idle.getWater(), 0.01,
                    "Water should be " + DroneConfig.AGENT_CAPACITY_LITRES + "L after refill");

            t.interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: task queue handles multiple queued tasks
    // -------------------------------------------------------------------------

    @Test
    void droneQueuesMultipleTasks() throws Exception {
        try (DatagramSocket fakeScheduler = new DatagramSocket(0)) {
            int schedulerPort = fakeScheduler.getLocalPort();
            fakeScheduler.setSoTimeout(120_000);

            int droneId = 54;
            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, DRONE_LISTEN_54);
            Thread t = new Thread(drone);
            t.setDaemon(true);
            t.start();

            int dronePort = readDroneListenPort(fakeScheduler);

            // Both tasks: close zone so test is fast
            byte[] t1Bytes = PacketBuilder.build(
                    new AssignTask(droneId, 1, 0, 0, 100, 100, Severity.LOW, FaultTypes.NONE));
            byte[] t2Bytes = PacketBuilder.build(
                    new AssignTask(droneId, 2, 0, 0, 100, 100, Severity.LOW, FaultTypes.NONE));
            fakeScheduler.send(new DatagramPacket(t1Bytes, t1Bytes.length,
                    InetAddress.getLoopbackAddress(), dronePort));
            // Send the second task after the first drone starts its drop. Sending it
            // during EN_ROUTE intentionally redirects the active mission instead.
            List<DroneUpdate> allUpdates = new ArrayList<>();
            boolean secondTaskSent = false;
            for (int i = 0; i < 800; i++) {
                DatagramPacket pkt = new DatagramPacket(new byte[4096], 4096);
                try {
                    fakeScheduler.receive(pkt);
                } catch (java.net.SocketTimeoutException e) {
                    break;
                }
                byte[] data = new byte[pkt.getLength()];
                System.arraycopy(pkt.getData(), 0, data, 0, pkt.getLength());
                DroneUpdate u = PacketParser.parseDroneUpdate(data);
                if (u != null) {
                    allUpdates.add(u);
                    if (!secondTaskSent && u.getState() == DroneState.DROPPING_AGENT) {
                        fakeScheduler.send(new DatagramPacket(t2Bytes, t2Bytes.length,
                                InetAddress.getLoopbackAddress(), dronePort));
                        secondTaskSent = true;
                    }
                }
                if (secondTaskSent && allUpdates.stream().filter(x -> x.getState() == DroneState.IDLE).count() >= 2) break;
            }

            long completedCount = allUpdates.stream()
                    .filter(u -> u.getState() == DroneState.COMPLETED).count();
            assertTrue(completedCount >= 2,
                    "Expected at least 2 COMPLETED states; got " + completedCount
                    + ". Both queued tasks should be processed.");

            t.interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Test 6: DroneUpdate round-trip with battery/fuel (pure unit test)
    // -------------------------------------------------------------------------

    @Test
    void droneUpdateRoundTripIncludesBatteryAndFuel() {
        DroneUpdate original = new DroneUpdate(7, DroneState.EN_ROUTE, 100.0, 200.0, 12.5, 77.3, 63.8);
        byte[] bytes = PacketBuilder.build(original);
        DroneUpdate parsed = PacketParser.parseDroneUpdate(bytes);

        assertNotNull(parsed);
        assertEquals(7, parsed.getDroneId());
        assertEquals(DroneState.EN_ROUTE, parsed.getState());
        assertEquals(77.3, parsed.getBattery(), 0.01, "Battery must survive round-trip");
        assertEquals(63.8, parsed.getFuel(),    0.01, "Fuel must survive round-trip");
    }

    // -------------------------------------------------------------------------
    // Test 7: legacy DroneUpdate defaults battery/fuel to 100% (pure unit test)
    // -------------------------------------------------------------------------

    @Test
    void legacyDroneUpdateDefaultsBatteryFuelTo100() {
        DroneUpdate legacy = new DroneUpdate(3, DroneState.IDLE, 0, 0, 15.0);
        byte[] bytes = PacketBuilder.build(legacy);
        DroneUpdate parsed = PacketParser.parseDroneUpdate(bytes);

        assertNotNull(parsed);
        assertEquals(100.0, parsed.getBattery(), 0.01, "Legacy packet battery should default to 100%");
        assertEquals(100.0, parsed.getFuel(),    0.01, "Legacy packet fuel should default to 100%");
    }
}
