package DroneSwarmSim.drone;

import DroneSwarmSim.messaging.AssignTask;
import DroneSwarmSim.messaging.DroneUpdate;
import DroneSwarmSim.messaging.MessageType;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import DroneSwarmSim.net.udp.UdpSender;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;

/**
 * Test class for validating the water capacity and refill behaviour of the DroneSubsystem.
 * These tests ensure that the drone's water tank is properly managed and refilled
 * during system operation.
 */
@DisplayName("Drone Capacity and Refill Tests")
class DroneCapacityRefillTest {

    /**
     * Tests the initial water capacity and state of a newly created drone subsystem.
     * <p>
     * Verifies that the water tank of the drone is initialized to its full capacity of 15 litres
     * when the subsystem is created. Also ensures that the initial state of the drone
     * is set to IDLE.
     * <p>
     * Asserts:
     * - The water capacity is 15.0 litres with a tolerance of 0.001.
     * - The state of the drone is IDLE.
     */
    @Test
    void testInitialWaterCapacity() {
        DroneSubsystem drone = new DroneSubsystem(210, "localhost", 15210, 15310);
        assertEquals(15.0, drone.getWater(), 0.001, "Full tank should be 15 L");
        assertEquals(DroneState.IDLE, drone.getState());
    }

    /**
     * Tests the behaviour of a drone subsystem when its water tank is depleted during a mission
     * and subsequently refilled upon completion.
     * <p>
     * <ul>
     * - Simulates a single mission where the drone completes its task, depleting the water tank in the process.
     * - Verifies that the drone is observed in the COMPLETED state with an empty water tank.
     * - Ensures that the drone transitions to the IDLE state with the water tank fully refilled.
     * - Confirms that all state transitions and water level updates are properly communicated
     *   via the system's updates mechanism.
     * </ul>
     * <p>
     * Expected Assertions:
     * - The COMPLETED state update indicates a water level of zero.
     * - The final IDLE state update reflects a full water tank of 15.0 litres within a tolerance of 0.001.
     * <p>
     * Conditions:
     * - Utilizes a mock scheduler to observe and capture drone updates.
     * - A dedicated thread runs the {@code DroneSubsystem}.
     * - Operates within a timeout of 30 seconds to ensure test completion in a timely manner.
     * <p>
     * Throws:
     * - Exception if any errors occur during test execution, including interrupted threads or timeout failures.
     */
    @Test
    @Timeout(30)
    void testWaterDepletedThenRefilled() throws Exception {
        int schedulerPort = 15211;
        int droneId = 211;
        int dronePort = 15311;

        List<DroneUpdate> updates = new CopyOnWriteArrayList<>();
        CountDownLatch idleLatch = new CountDownLatch(1);

        // Launches mock scheduler and drone; verifies water depletion, then refill cycle after task completion
        try (UdpReceiver mockScheduler = new UdpReceiver(schedulerPort, data -> {
            // Collects drone updates and signals on idle state
            if (PacketParser.getType(data) == MessageType.DRONE_UPDATE) {
                DroneUpdate u = PacketParser.parseDroneUpdate(data);
                updates.add(u);
                assertNotNull(u);
                if (u.getState() == DroneState.IDLE) idleLatch.countDown();
            }
        })) {
            mockScheduler.start();

            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, dronePort);
            Thread droneThread = new Thread(drone);
            droneThread.setDaemon(true);
            droneThread.start();

            Thread.sleep(300);

            // Zone at origin -> no travel needed, fast cycle
            AssignTask task = new AssignTask(droneId, 1, 0, 0, 0, 0, Severity.LOW);
            try (UdpSender sender = new UdpSender("localhost", dronePort)) {
                sender.send(PacketBuilder.build(task));
            }

            assertTrue(idleLatch.await(25, TimeUnit.SECONDS),
                    "Drone should reach final IDLE. Observed: " + updates);

            // The COMPLETED update should show water = 0
            boolean sawEmptyTank = updates.stream()
                    .anyMatch(u -> u.getState() == DroneState.COMPLETED && u.getWater() <= 0.001);
            assertTrue(sawEmptyTank, "Water should be 0 at COMPLETED state");

            // The final IDLE update should show a refilled tank
            DroneUpdate finalIdle = updates.stream()
                    .filter(u -> u.getState() == DroneState.IDLE)
                    .reduce((a, b) -> b)
                    .orElse(null);
            assertNotNull(finalIdle, "Final IDLE update must be received");
            assertEquals(15.0, finalIdle.getWater(), 0.001, "Tank must be refilled to 15 L");

            droneThread.interrupt();
            droneThread.join(2000);
        }
    }

    /**
     * Tests the behaviour of a drone subsystem when consecutive missions are assigned, verifying that
     * the water tank is refilled between missions upon completion of each task.
     * <p>
     * This test simulates two sequential tasks assigned to a single drone and observes
     * the relevant state transitions and water tank refills. The core checks involve ensuring
     * that the drone transitions to the IDLE state after each mission with its water tank refilled to full capacity.
     * <p>
     * Key Test Steps:
     * - Initialize a mock scheduler to listen for and capture updates from the drone subsystem.
     * - Start a drone subsystem in a separate thread and assign two successive tasks to it.
     * - Utilize latches to synchronize and await the completion of each mission.
     * - Observe the drone's state transitions to ensure it returns to IDLE after each mission.
     * - Assert that the water capacity is completely refilled (15.0 litres) after both missions.
     * <p>
     * Assertions:
     * - The drone transitions to the IDLE state following the completion of each mission.
     * - Upon each transition to the IDLE state, the water tank level is fully restored to 15.0 liters
     *   within a tolerance of 0.001.
     * <p>
     * Conditions and Configuration:
     * - Operates with a total test timeout of 60 seconds to ensure timely execution.
     * - Utilizes a mock UDP scheduler to simulate the external system's interaction with the drone.
     * - Drone subsystem and communication mechanisms are restricted to localhost for the test scope.
     * <p>
     * Throws:
     * - Exception if an error occurs during execution, such as interrupted threads or timeout failures.
     */
    @Test
    @Timeout(60)
    void testRefillBetweenConsecutiveMissions() throws Exception {
        int schedulerPort = 15212;
        int droneId = 212;
        int dronePort = 15312;

        // Two separate latches: one per mission completion
        CountDownLatch firstIdleLatch  = new CountDownLatch(1);
        CountDownLatch secondIdleLatch = new CountDownLatch(1);
        List<DroneUpdate> idleUpdates = new CopyOnWriteArrayList<>();

        // Tests consecutive missions trigger refills to full tank
        try (UdpReceiver mockScheduler = new UdpReceiver(schedulerPort, data -> {
            if (PacketParser.getType(data) == MessageType.DRONE_UPDATE) {
                DroneUpdate u = PacketParser.parseDroneUpdate(data);
                // Detects idle drone states and signals mission completion latches
                assertNotNull(u);
                // Detects idle drone states and signals mission completion latches
                if (u.getState() == DroneState.IDLE) {
                    idleUpdates.add(u);
                    if (idleUpdates.size() == 1) firstIdleLatch.countDown();
                    else secondIdleLatch.countDown();
                }
            }
        })) {
            mockScheduler.start();

            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, dronePort);
            Thread droneThread = new Thread(drone);
            droneThread.setDaemon(true);
            droneThread.start();

            Thread.sleep(300);

            // Send the first task
            try (UdpSender sender = new UdpSender("localhost", dronePort)) {
                sender.send(PacketBuilder.build(new AssignTask(droneId, 1, 0, 0, 0, 0, Severity.LOW)));
            }

            // Wait for the first mission to complete
            assertTrue(firstIdleLatch.await(25, TimeUnit.SECONDS),
                    "Drone should complete first mission and return to IDLE");
            Thread.sleep(200); // brief stabilisation

            // Send the second task
            try (UdpSender sender = new UdpSender("localhost", dronePort)) {
                sender.send(PacketBuilder.build(new AssignTask(droneId, 1, 0, 0, 0, 0, Severity.LOW)));
            }

            assertTrue(secondIdleLatch.await(25, TimeUnit.SECONDS),
                    "Drone should complete second mission and return to IDLE");

            // Both IDLE updates should show a full tank
            for (DroneUpdate u : idleUpdates) {
                assertEquals(15.0, u.getWater(), 0.001, "Tank must be 15 L after each refill");
            }

            droneThread.interrupt();
            droneThread.join(2000);
        }
    }
}
