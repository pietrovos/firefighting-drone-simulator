package DroneSwarmSim.net.udp;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.AssignTask;
import DroneSwarmSim.messaging.DroneRegister;
import DroneSwarmSim.messaging.DroneUpdate;
import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.messaging.MessageType;
import DroneSwarmSim.model.Severity;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;

/**
 * Integration tests for validating message contracts in the drone swarm simulation system.
 * These tests ensure that messages can be serialized, transmitted, and deserialized correctly
 * between components using UDP communication. Each test is focused on a specific message type
 * and checks the integrity and correctness of the data through end-to-end processing.
 * <p>
 * The tests cover the following message types:
 * - IncidentReport: Validates the structure and contents of incident reports.
 * - AssignTask: Ensures tasks assigned to drones are transmitted and parsed correctly.
 * - DroneRegister: Verifies registration messages for drones.
 * - DroneUpdate: Confirms updates related to drone status and location.
 * - Type Detection: Ensures all message types can be correctly identified.
 */
@DisplayName("Message Contracts Integration Tests")
class MessageContractsIT {

    private static final int PORT_BASE = 15800;

    /**
     * Validates the contract for transmitting and receiving an {@link IncidentReport} object
     * via UDP communication by simulating sending and receiving operations between a sender and receiver.
     * <p>
     * This test case ensures that the serialized representation of an {@link IncidentReport}
     * created by the {@code PacketBuilder.build} method can be successfully deserialized
     * using the {@code PacketParser.parseIncidentReport} method, and that the integrity of the data
     * is preserved during the round-trip transmission.
     * <p>
     * Key verification steps include:
     * - Checking that the received {@link IncidentReport} is not null.
     * - Asserting that all fields of the deserialized {@link IncidentReport} match the original values:
     *   - Timestamp of the incident.
     *   - Zone identifier where the incident occurred.
     *   - Type of event ({@link EventType}).
     *   - Severity level ({@link Severity}).
     * <p>
     * Timeout is set to 5 seconds to ensure the test completes within a reasonable time,
     * accounting for potential delays in UDP communication.
     *
     * @throws Exception if any error occurs during UDP communication or deserialization.
     */
    @Test
    @Timeout(5)
    void testIncidentReportContract() throws Exception {
        IncidentReport original = new IncidentReport("14:03:15", 3, EventType.FIRE_DETECTED, Severity.HIGH);
        byte[] packet = PacketBuilder.build(original);

        CountDownLatch latch = new CountDownLatch(1);
        IncidentReport[] decoded = {null};

        // Sets up UDP receiver; awaits and parses a single packet
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE, data -> {
            decoded[0] = PacketParser.parseIncidentReport(data);
            latch.countDown();
        })) {
            rx.start();
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE)) { tx.send(packet); }
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        }

        assertNotNull(decoded[0]);
        assertEquals("14:03:15", decoded[0].time());
        assertEquals(3, decoded[0].zoneId());
        assertEquals(EventType.FIRE_DETECTED, decoded[0].eventType());
        assertEquals(Severity.HIGH, decoded[0].severity());
    }

    /**
     * Validates the contract for transmitting and receiving an {@link AssignTask} object
     * through UDP communication by simulating a round-trip between a sender and a receiver.
     * <p>
     * This test ensures that the serialized representation of an {@link AssignTask} created
     * using the {@code PacketBuilder.build} method can be successfully parsed and reconstructed
     * with the {@code PacketParser.parseAssignTask} method while maintaining data integrity.
     * <p>
     * Key validation steps performed in this test include:
     * - Verifying that the received {@link AssignTask} object is not null.
     * - Asserting that all fields of the deserialized {@link AssignTask} match the original values:
     *   - Drone ID.
     *   - Zone ID.
     *   - Zone origin coordinates (X and Y).
     *   - Zone dimensions (xEnd and yEnd).
     *   - Severity level.
     * <p>
     * The test makes use of a {@code CountDownLatch} to synchronize sender and receiver operations,
     * ensuring that the test waits for the full UDP communication to complete within a timeout.
     * <p>
     * The timeout is set to 5 seconds to ensure the test concludes in a reasonable timeframe,
     * accounting for potential latency in UDP communication.
     * <p>
     * @throws Exception if an error occurs during UDP transmission, reception, or deserialization.
     */
    @Test
    @Timeout(5)
    void testAssignTaskContract() throws Exception {
        AssignTask original = new AssignTask(5, 2, 100, 200, 300, 400, Severity.MODERATE);
        byte[] packet = PacketBuilder.build(original);

        CountDownLatch latch = new CountDownLatch(1);
        AssignTask[] decoded = {null};

        // Launches timed UDP receiver; awaits and verifies packet delivery
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE + 1, data -> {
            decoded[0] = PacketParser.parseAssignTask(data);
            latch.countDown();
        })) {
            rx.start();
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE + 1)) { tx.send(packet); }
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        }

        assertNotNull(decoded[0]);
        assertEquals(5, decoded[0].droneId());
        assertEquals(2, decoded[0].zoneId());
        assertEquals(100, decoded[0].zoneXOrigin());
        assertEquals(200, decoded[0].zoneYOrigin());
        assertEquals(300, decoded[0].zoneXEnd());
        assertEquals(400, decoded[0].zoneYEnd());
        assertEquals(Severity.MODERATE, decoded[0].severity());
    }

    /**
     * Validates the contract for transmitting and receiving a {@link DroneRegister} object
     * via UDP communication by simulating the send-and-receive process between a sender and receiver.
     * <p>
     * This test ensures that the serialized representation of the {@link DroneRegister} created
     * using the {@code PacketBuilder.build} method can be successfully deserialized using
     * the {@code PacketParser.parseDroneRegister} method, while maintaining the integrity
     * of the original data during the round-trip transmission.
     * <p>
     * Key validation steps performed in this test include:
     * - Verifying that the received {@link DroneRegister} object is not null.
     * - Asserting that the deserialized {@link DroneRegister} fields match the original values:
     *   - Unique drone identifier.
     *   - Hostname or IP address.
     *   - Communication port.
     * <p>
     * This test uses a {@code CountDownLatch} to handle synchronization between sender and
     * receiver operations, ensuring that the test waits for the full communication cycle
     * to complete within a specified timeout.
     * <p>
     * The test is configured with a timeout of 5 seconds to prevent it from running indefinitely,
     * accounting for potential delays in UDP communication.
     * <p>
     * @throws Exception if an error occurs during UDP transmission, reception, or deserialization.
     */
    @Test
    @Timeout(5)
    void testDroneRegisterContract() throws Exception {
        DroneRegister original = new DroneRegister(7, "127.0.0.1", 9999);
        byte[] packet = PacketBuilder.build(original);

        CountDownLatch latch = new CountDownLatch(1);
        DroneRegister[] decoded = {null};

        // Awaits and receives a UDP packet for parsing
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE + 2, data -> {
            decoded[0] = PacketParser.parseDroneRegister(data);
            latch.countDown();
        })) {
            rx.start();
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE + 2)) { tx.send(packet); }
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        }

        assertNotNull(decoded[0]);
        assertEquals(7,           decoded[0].droneId());
        assertEquals("127.0.0.1", decoded[0].host());
        assertEquals(9999,        decoded[0].port());
    }

    /**
     * Validates the contract for transmitting and receiving a {@link DroneUpdate} object
     * via UDP communication by simulating the complete send-and-receive process between
     * a sender and a receiver.
     * <p>
     * This test ensures that a serialized {@link DroneUpdate} object constructed using the
     * {@code PacketBuilder.build} method can be accurately deserialized using the
     * {@code PacketParser.parseDroneUpdate} method, while preserving the integrity
     * of the original data during the communication process.
     * <p>
     * Key validation steps performed in this test include:
     * - Verifying that the received {@link DroneUpdate} object is not null.
     * - Asserting that all fields of the deserialized {@link DroneUpdate} match the original values:
     *   - Drone ID.
     *   - Drone state ({@link DroneState}).
     *   - X position.
     *   - Y position.
     *   - Current water level.
     * <p>
     * The test leverages a {@code CountDownLatch} to coordinate sender and receiver operations,
     * ensuring synchronization and allowing the test to wait for the complete communication process
     * to occur before making assertions.
     * <p>
     * The timeout is set to 5 seconds to ensure that the test concludes in a reasonable timeframe,
     * accommodating potential delays inherent in UDP communication.
     *
     * @throws Exception if any error occurs during UDP transmission, reception, or deserialization.
     */
    @Test
    @Timeout(5)
    void testDroneUpdateContract() throws Exception {
        DroneUpdate original = new DroneUpdate(3, DroneState.EN_ROUTE, 45.0, 90.0, 12.5);
        byte[] packet = PacketBuilder.build(original);

        CountDownLatch latch = new CountDownLatch(1);
        DroneUpdate[] decoded = {null};

        // Sets up timed UDP receiver; sends packet; awaits and verifies decode
        try (UdpReceiver rx = new UdpReceiver(PORT_BASE + 3, data -> {
            decoded[0] = PacketParser.parseDroneUpdate(data);
            latch.countDown();
        })) {
            rx.start();
            try (UdpSender tx = new UdpSender("localhost", PORT_BASE + 3)) { tx.send(packet); }
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        }

        assertNotNull(decoded[0]);
        assertEquals(3,                 decoded[0].getDroneId());
        assertEquals(DroneState.EN_ROUTE, decoded[0].getState());
        assertEquals(12.5,              decoded[0].getWater(),  0.001);
        assertEquals(45.0,              decoded[0].getXPos(),   0.001);
        assertEquals(90.0,              decoded[0].getYPos(),   0.001);
    }

    /**
     * Verifies that the {@code PacketParser.getType} method correctly identifies all predefined
     * {@link MessageType} values based on the data packets created by the {@code PacketBuilder.build} method.
     * <p>
     * This test ensures that the {@code getType} method can accurately determine the type of message
     * encapsulated within a serialized packet for the following message types:
     * - {@link MessageType#INCIDENT_REPORT}: Corresponds to an {@link IncidentReport} object.
     * - {@link MessageType#ASSIGN_TASK}: Corresponds to an {@link AssignTask} object.
     * - {@link MessageType#DRONE_REGISTER}: Corresponds to a {@link DroneRegister} object.
     * - {@link MessageType#DRONE_UPDATE}: Corresponds to a {@link DroneUpdate} object.
     * <p>
     * Key assertions in this test confirm that the {@code getType} method produces the correct
     * {@link MessageType} enumeration value for each serialized packet type, validating the
     * integrity and correctness of the message type identification logic.
     */
    @Test
    void testGetTypeIdentifiesAllMessageTypes() {
        assertEquals(MessageType.INCIDENT_REPORT,
                PacketParser.getType(PacketBuilder.build(
                        new IncidentReport("t", 1, EventType.FIRE_DETECTED, Severity.LOW))));

        assertEquals(MessageType.ASSIGN_TASK,
                PacketParser.getType(PacketBuilder.build(
                        new AssignTask(1, 1, 0, 0, 0, 0, Severity.LOW))));

        assertEquals(MessageType.DRONE_REGISTER,
                PacketParser.getType(PacketBuilder.build(
                        new DroneRegister(1, "localhost", 1234))));

        assertEquals(MessageType.DRONE_UPDATE,
                PacketParser.getType(PacketBuilder.build(
                        new DroneUpdate(1, DroneState.IDLE, 15, 0, 0))));
    }
}
