package DroneSwarmSim.net.udp;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Severity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/**
 * Test class for verifying the correctness of packet generation and parsing
 * in the drone swarm simulation system.
 * <p>
 * This class ensures that various message types can be serialized into packets
 * and then deserialized back into their original objects without data loss or corruption.
 * It validates the functionality of the {@code PacketBuilder} and {@code PacketParser}
 * components for multiple message types, including
 * - IncidentReport
 * - AssignTask
 * - DroneRegister
 * - DroneUpdate
 * - Ack
 * <p>
 * Additionally, it tests utility methods like string quoting for packet generation.
 * <p>
 * Each test case compares the original object with its deserialized counterpart
 * to verify the round-trip consistency of serialization and deserialization.
 */
class PacketRoundTripTest {

    /**
     * Tests the round-trip functionality of an IncidentReport object through the serialization and
     * deserialization process. This method ensures that an IncidentReport can be converted to a byte
     * array using the PacketBuilder and subsequently parsed back to its original form using the
     * PacketParser without any data loss or corruption.
     * <p>
     * The test verifies the following:
     * - That the serialized packet has the correct type as specified by MessageType.INCIDENT_REPORT.
     * - That the parsed IncidentReport retains the same values for all properties (time, zoneId,
     *   eventType, severity) as the original object.
     * <p>
     * This test is crucial for validating the integrity and consistency of the communication mechanism
     * in the drone swarm simulation system.
     */
    @Test
    void incidentReport_roundTrip() {
        IncidentReport original = new IncidentReport("14:03:15", 2, EventType.FIRE_DETECTED, Severity.HIGH);
        byte[] packet = PacketBuilder.build(original);

        assertEquals(MessageType.INCIDENT_REPORT, PacketParser.getType(packet));

        IncidentReport parsed = PacketParser.parseIncidentReport(packet);
        assertNotNull(parsed);
        assertEquals(original.time(), parsed.time());
        assertEquals(original.zoneId(), parsed.zoneId());
        assertEquals(original.eventType(), parsed.eventType());
        assertEquals(original.severity(), parsed.severity());
    }

    /**
     * Tests the round-trip functionality of an AssignTask object through the serialization and
     * deserialization process. This method ensures that an AssignTask can be converted to a byte
     * array using the PacketBuilder and subsequently parsed back to its original form using the
     * PacketParser without any data loss or corruption.
     * <p>
     * The test validates the following:
     * - That the serialized packet has the correct type as specified by MessageType.ASSIGN_TASK.
     * - That the parsed AssignTask retains the same values for all properties (droneId, zoneId,
     *   zoneXOrigin, zoneYOrigin, zoneXEnd, zoneYEnd, severity) as the original object.
     * <p>
     * This test is essential for ensuring the integrity and correctness of task assignment data
     * within the drone swarm simulation system during communication processes.
     */
    @Test
    void assignTask_roundTrip() {
        AssignTask original = new AssignTask(1, 2, 0, 600, 650, 1500, Severity.MODERATE);
        byte[] packet = PacketBuilder.build(original);

        assertEquals(MessageType.ASSIGN_TASK, PacketParser.getType(packet));

        AssignTask parsed = PacketParser.parseAssignTask(packet);
        assertNotNull(parsed);
        assertEquals(original.droneId(), parsed.droneId());
        assertEquals(original.zoneId(), parsed.zoneId());
        assertEquals(original.zoneXOrigin(), parsed.zoneXOrigin());
        assertEquals(original.zoneYOrigin(), parsed.zoneYOrigin());
        assertEquals(original.zoneXEnd(), parsed.zoneXEnd());
        assertEquals(original.zoneYEnd(), parsed.zoneYEnd());
        assertEquals(original.severity(), parsed.severity());
    }

    /**
     * Tests the round-trip functionality of a DroneRegister object through the serialization
     * and deserialization process. This method ensures that a DroneRegister instance can be
     * converted to a byte array using the PacketBuilder and subsequently parsed back to its
     * original form using the PacketParser without any data loss or corruption.
     * <p>
     * The test validates the following:
     * - That the serialized packet has the correct type as specified by MessageType.DRONE_REGISTER.
     * - That the parsed DroneRegister retains the same values for all properties
     *   (droneId, host, port) as the original object.
     * <p>
     * This test is essential for verifying the integrity and correctness of the drone registration
     * data during communication procedures in the drone swarm simulation system.
     */
    @Test
    void droneRegister_roundTrip() {
        DroneRegister original = new DroneRegister(3, "localhost", 7003);
        byte[] packet = PacketBuilder.build(original);

        assertEquals(MessageType.DRONE_REGISTER, PacketParser.getType(packet));

        DroneRegister parsed = PacketParser.parseDroneRegister(packet);
        assertNotNull(parsed);
        assertEquals(original.droneId(), parsed.droneId());
        assertEquals(original.host(), parsed.host());
        assertEquals(original.port(), parsed.port());
    }

    /**
     * Tests the round-trip functionality of a DroneUpdate object through the serialization and
     * deserialization process. This method validates that a DroneUpdate instance can be correctly
     * serialized into a byte array using the PacketBuilder and subsequently deserialized back into
     * its original form using the PacketParser, without any loss or corruption of data.
     * <p>
     * The test performs the following verifications:
     * - Ensures that the serialized packet has the correct type as specified by MessageType.DRONE_UPDATE.
     * - Confirms that all properties of the parsed DroneUpdate object, including droneId, state, xPos,
     *   yPos, and water, match exactly with those of the original object. This includes verifying
     *   equality for double values within reasonable precision.
     * <p>
     * This test is essential for ensuring communication integrity in the drone swarm simulation
     * system, particularly for relaying updates about drones' state and positional information.
     */
    @Test
    void droneUpdate_roundTrip() {
        DroneUpdate original = new DroneUpdate(1, DroneState.EN_ROUTE, 150.5, 300.25, 12.0);
        byte[] packet = PacketBuilder.build(original);

        assertEquals(MessageType.DRONE_UPDATE, PacketParser.getType(packet));

        DroneUpdate parsed = PacketParser.parseDroneUpdate(packet);
        assertNotNull(parsed);
        assertEquals(original.getDroneId(), parsed.getDroneId());
        assertEquals(original.getState(), parsed.getState());
        assertEquals(original.getXPos(), parsed.getXPos(), 0.001);
        assertEquals(original.getYPos(), parsed.getYPos(), 0.001);
        assertEquals(original.getWater(), parsed.getWater(), 0.001);
    }

    /**
     * Tests the round-trip functionality of an Ack object through the serialization
     * and deserialization process. This method ensures that an Ack instance can be
     * converted to a byte array using the PacketBuilder and subsequently parsed back
     * to its original form using the PacketParser without any data loss or corruption.
     * <p>
     * The test verifies the following:
     * - That the serialized packet has the correct type as specified by MessageType.ACK.
     * - That the parsed Ack retains the same values for all properties (inResponseTo,
     *   success, message) as the original object.
     * <p>
     * This test is crucial for validating the integrity and consistency of the communication
     * mechanism in the drone swarm simulation system when handling acknowledgment messages.
     */
    @Test
    void ack_roundTrip() {
        Ack original = new Ack(MessageType.INCIDENT_REPORT, true, "OK");
        byte[] packet = PacketBuilder.build(original);

        assertEquals(MessageType.ACK, PacketParser.getType(packet));

        Ack parsed = PacketParser.parseAck(packet);
        assertNotNull(parsed);
        assertEquals(original.inResponseTo(), parsed.inResponseTo());
        assertEquals(original.success(), parsed.success());
        assertEquals(original.message(), parsed.message());
    }

    /**
     * Tests the behaviour of the PacketBuilder's quoting mechanism for strings containing
     * special characters. This test ensures that the quote method properly escapes special
     * characters, such as double quotes and backslashes, to produce the correct output.
     * <p>
     * The method verifies:
     * - That embedded double quotes within the input string are escaped with a preceding backslash.
     * - That backslashes within the input string are also escaped with an additional backslash.
     * - That the resulting string is wrapped with double quotes.
     * <p>
     * This test is critical for validating the proper behaviour of the quoting utility,
     * especially in scenarios where strings with special characters need to be serialized
     * or safely embedded within larger contexts.
     */
    @Test
    void quoteHelper_escapesSpecialChars() {
        String result = PacketBuilder.quote("say \"hello\" \\world");
        assertEquals("\"say \\\"hello\\\" \\\\world\"", result);
    }
}
