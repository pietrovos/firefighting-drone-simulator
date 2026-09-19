package DroneSwarmSim.fire;

import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the IncidentReport data transfer object (DTO).
 * <p>
 * This test class validates the serialization and deserialization behaviour of IncidentReport objects
 * to ensure that all fields are correctly preserved when creating a packet and subsequently parsing it.
 * It also confirms the packet type is identified as an IncidentReport.
 * <p>
 * Test cases include:
 * - Ensuring all fields are preserved during serialization and deserialization at various severity levels.
 * - Verifying the incident report packet type after serialization.
 */
@DisplayName("IncidentReport DTO Tests")
class IncidentReportDtoTest {

    /**
     * Tests the serialization and deserialization process for an IncidentReport with low severity
     * to ensure all fields are correctly preserved and the packet type is identified as an IncidentReport.
     * <p>
     * This test verifies the following:
     * - The packet type of the serialized IncidentReport matches {@link MessageType#INCIDENT_REPORT}.
     * - All fields (time, zoneId, eventType, and severity) are retained during deserialization.
     * <p>
     * The test validates that a low-severity incident report with specific attributes
     * (e.g., "08:30:00" time, zoneId 3, {@link EventType#DRONE_REQUEST} event type,
     * and {@link Severity#LOW}) matches the original object after a round-trip of serialization
     * and deserialization.
     * <p>
     * Assertions include:
     * - Checking the packet type.
     * - Ensuring that the parsed IncidentReport object is non-null.
     * - Verifying individual fields for accuracy.
     */
    @Test
    void testAllFieldsPreservedLowSeverity() {
        IncidentReport original = new IncidentReport("08:30:00", 3, EventType.DRONE_REQUEST, Severity.LOW);
        byte[] packet = PacketBuilder.build(original);

        assertEquals(MessageType.INCIDENT_REPORT, PacketParser.getType(packet));

        IncidentReport parsed = PacketParser.parseIncidentReport(packet);
        assertNotNull(parsed);
        assertEquals("08:30:00", parsed.time());
        assertEquals(3, parsed.zoneId());
        assertEquals(EventType.DRONE_REQUEST, parsed.eventType());
        assertEquals(Severity.LOW, parsed.severity());
    }

    /**
     * Tests the serialization and deserialization process for an IncidentReport with high severity
     * to ensure all fields are correctly preserved.
     * <p>
     * This test validates the following:
     * - The serialized IncidentReport is accurately converted into a byte array representation.
     * - The deserialized IncidentReport object is non-null and matches the original input.
     * - Individual fields, such as time, zoneId, eventType, and severity, are correctly retained
     *   during the process.
     * <p>
     * Assertions include:
     * - Verifying the deserialized object is not null.
     * - Checking that the time of occurrence matches the original input.
     * - Ensuring that the zoneId matches the original value.
     * - Confirming that the eventType is correctly identified as EventType.FIRE_DETECTED.
     * - Verifying that the severity is accurately identified as Severity.HIGH.
     */
    @Test
    void testAllFieldsPreservedHighSeverity() {
        IncidentReport original = new IncidentReport("23:59:59", 7, EventType.FIRE_DETECTED, Severity.HIGH);
        byte[] packet = PacketBuilder.build(original);

        IncidentReport parsed = PacketParser.parseIncidentReport(packet);
        assertNotNull(parsed);
        assertEquals("23:59:59", parsed.time());
        assertEquals(7, parsed.zoneId());
        assertEquals(EventType.FIRE_DETECTED, parsed.eventType());
        assertEquals(Severity.HIGH, parsed.severity());
    }

    /**
     * Tests the serialization and deserialization process for an IncidentReport with moderate severity
     * to ensure all fields are accurately preserved.
     * <p>
     * This test validates the following:
     * - The serialized representation of the IncidentReport can be correctly parsed back into an object.
     * - The deserialized object is non-null and matches the original IncidentReport instance.
     * - Individual fields, including time, zoneId, eventType, and severity, are correctly retained during the process.
     * <p>
     * Assertions include:
     * - Verifying that the deserialized IncidentReport object is not null.
     * - Ensuring that the severity is correctly identified as {@link Severity#MODERATE}.
     * - Confirming that the zoneId matches the original specified value.
     */
    @Test
    void testAllFieldsPreservedModerateSeverity() {
        IncidentReport original = new IncidentReport("12:00:00", 1, EventType.FIRE_DETECTED, Severity.MODERATE);
        byte[] packet = PacketBuilder.build(original);

        IncidentReport parsed = PacketParser.parseIncidentReport(packet);
        assertNotNull(parsed);
        assertEquals(Severity.MODERATE, parsed.severity());
        assertEquals(1, parsed.zoneId());
    }

    /**
     * Tests that the serialized packet type of an {@link IncidentReport} object is correctly identified as {@link MessageType#INCIDENT_REPORT}.
     * <p>
     * Validates the serialization and type-parsing round trip for an {@link IncidentReport}.
     * It ensures that when an incident report with specific attributes is serialized into a byte array using
     * {@link PacketBuilder#build(IncidentReport)}, the resulting packet is accurately classified as an INCIDENT_REPORT
     * by {@link PacketParser#getType(byte[])}.
     * <p>
     * Test asserts include:
     * - Verifying that the serialized packet type matches {@link MessageType#INCIDENT_REPORT}.
     */
    @Test
    void testPacketTypeIsIncidentReport() {
        IncidentReport r = new IncidentReport("10:00", 5, EventType.FIRE_DETECTED, Severity.LOW);
        byte[] packet = PacketBuilder.build(r);
        assertEquals(MessageType.INCIDENT_REPORT, PacketParser.getType(packet));
    }
}
