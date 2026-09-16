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
 * A test class for verifying the behaviour and state transitions of the drone subsystem
 * through its state machine. This class includes various test scenarios that evaluate
 * the initial state, specific state transitions, and the full sequence of state transitions
 * in the drone's lifecycle.
 */
@DisplayName("Drone State Machine Tests")
class DroneStateMachineTest {

    /**
     * Tests that the initial state of the drone subsystem is correctly set to IDLE.
     * <p>
     * This method instantiates a new {@code DroneSubsystem} object and asserts that:
     * - The initial state of the drone is {@code DroneState.IDLE}.
     * - The initial water level of the drone is 15.0 units (with a tolerance of 0.001).
     * - The drone's unique identifier is correctly initialized with the given value (203).
     * <p>
     * This ensures that the drone subsystem is correctly initialized under default
     * conditions before interacting with the state machine.
     */
    @Test
    void testInitialStateIsIdle() {
        DroneSubsystem drone = new DroneSubsystem(203, "localhost", 15203, 15303);
        assertEquals(DroneState.IDLE, drone.getState());
        assertEquals(15.0, drone.getWater(), 0.001);
        assertEquals(203, drone.getDroneId());
    }

    /**
     * Tests the state transition of a drone from IDLE to ARRIVED upon task assignment.
     * <p>
     * This method simulates a scenario where a drone receives a task assignment
     * requiring no movement (zero-sized zone at the origin). It verifies that:
     * - The drone's initial state is properly set to IDLE.
     * - The drone transitions to the ARRIVED state upon completing the task.
     * - An ARRIVED {@code DroneUpdate} message is sent to a mock scheduler.
     * - The {@code DroneUpdate} message contains the expected drone ID and state.
     * <p>
     * The test uses a mocked scheduler (UDP receiver) to capture the stream of
     * {@code DroneUpdate} messages from the drone. A latch mechanism ensures that
     * the test waits until the drone reaches the ARRIVED state or a timeout occurs.
     * <p>
     * @throws Exception if an error occurs during the test execution.
     */
    @Test
    @Timeout(15)
    void testIdleToArrivedTransition() throws Exception {
        int schedulerPort = 15201;
        int droneId = 201;
        int dronePort = 15301;

        List<DroneUpdate> updates = new CopyOnWriteArrayList<>();
        CountDownLatch arrivedLatch = new CountDownLatch(1);

        // Validates idle-to-arrived transition via mock scheduler and task assignment
        try (UdpReceiver mockScheduler = new UdpReceiver(schedulerPort, data -> {
            // Collects drone updates and signals on arrived state
            if (PacketParser.getType(data) == MessageType.DRONE_UPDATE) {
                DroneUpdate u = PacketParser.parseDroneUpdate(data);
                updates.add(u);
                assertNotNull(u);
                if (u.getState() == DroneState.ARRIVED) arrivedLatch.countDown();
            }
        })) {
            mockScheduler.start();

            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, dronePort);
            assertEquals(DroneState.IDLE, drone.getState());

            Thread droneThread = new Thread(drone);
            droneThread.setDaemon(true);
            droneThread.start();

            // Give the drone time to bind its receiver socket
            Thread.sleep(300);

            // Zero-sized zone at origin — drone is already there, so movement is skipped
            AssignTask task = new AssignTask(droneId, 1, 0, 0, 0, 0, Severity.LOW);
            try (UdpSender sender = new UdpSender("localhost", dronePort)) {
                sender.send(PacketBuilder.build(task));
            }

            assertTrue(arrivedLatch.await(10, TimeUnit.SECONDS),
                    "Drone should reach ARRIVED state within 10 s");

            DroneUpdate arrivedUpdate = updates.stream()
                    .filter(u -> u.getState() == DroneState.ARRIVED)
                    .findFirst().orElse(null);
            assertNotNull(arrivedUpdate, "ARRIVED DroneUpdate must be received");
            assertEquals(droneId, arrivedUpdate.getDroneId());

            droneThread.interrupt();
            droneThread.join(2000);
        }
    }

    /**
     * Tests the complete operational cycle of a drone state machine transitioning
     * through multiple states during task execution.
     * <p>
     * This test verifies the following:
     * - The drone subsystem correctly transitions through the expected states:
     *   IDLE, ARRIVED, DROPPING_AGENT (or COMPLETED), and back to IDLE.
     * - The system correctly sends {@code DroneUpdate} messages with the expected
     *   state values to a mock scheduler.
     * - The drone reverts to the IDLE state within the specified timeout duration.
     * <p>
     * The test uses a mock UDP receiver (`mockScheduler`) to capture and examine
     * {@code DroneUpdate} messages sent from the drone. It also employs a
     * {@code CountDownLatch} mechanism to synchronize on the final IDLE state.
     * <p>
     * Correctness is asserted by checking the presence of key states in the
     * observed sequence of {@code DroneUpdate} messages and ensuring the overall
     * workflow adheres to expectations.
     * <p>
     * @throws Exception if an error occurs during execution, such as timeout
     *                   failures or unexpected state transitions.
     */
    @Test
    @Timeout(30)
    void testFullStateMachineCycle() throws Exception {
        int schedulerPort = 15202;
        int droneId = 202;
        int dronePort = 15302;

        List<DroneState> observedStates = new CopyOnWriteArrayList<>();
        CountDownLatch idleLatch = new CountDownLatch(1);

        // Validates full drone state machine cycle with task assignment
        try (UdpReceiver mockScheduler = new UdpReceiver(schedulerPort, data -> {
            // Captures and asserts drone state updates; triggers latch on idle
            if (PacketParser.getType(data) == MessageType.DRONE_UPDATE) {
                DroneUpdate u = PacketParser.parseDroneUpdate(data);
                assertNotNull(u);
                DroneState s = u.getState();
                observedStates.add(s);
                if (s == DroneState.IDLE) idleLatch.countDown();
            }
        })) {
            mockScheduler.start();

            DroneSubsystem drone = new DroneSubsystem(droneId, "localhost", schedulerPort, dronePort);
            Thread droneThread = new Thread(drone);
            droneThread.setDaemon(true);
            droneThread.start();

            Thread.sleep(300);

            AssignTask task = new AssignTask(droneId, 1, 0, 0, 0, 0, Severity.LOW);
            try (UdpSender sender = new UdpSender("localhost", dronePort)) {
                sender.send(PacketBuilder.build(task));
            }

            assertTrue(idleLatch.await(25, TimeUnit.SECONDS),
                    "Drone should return to IDLE within 25 s. Observed: " + observedStates);

            assertTrue(observedStates.contains(DroneState.ARRIVED),   "ARRIVED expected");
            assertTrue(observedStates.contains(DroneState.DROPPING_AGENT)
                    || observedStates.contains(DroneState.COMPLETED),  "DROPPING/COMPLETED expected");
            assertTrue(observedStates.contains(DroneState.IDLE),       "Final IDLE expected");

            droneThread.interrupt();
            droneThread.join(2000);
        }
    }
}
