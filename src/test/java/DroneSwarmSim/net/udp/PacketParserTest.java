package DroneSwarmSim.net.udp;

import DroneSwarmSim.messaging.MessageType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PacketParserTest {
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void getTypeRejectsInvalidPayloads() {
        assertNull(PacketParser.getType(null));
        assertNull(PacketParser.getType(new byte[0]));
        assertNull(PacketParser.getType(bytes("HELLO")));
        assertNull(PacketParser.getType(bytes("{\"type\":null}")));
        assertNull(PacketParser.getType(bytes("{\"type\":\"UNKNOWN\"}")));
    }

    @Test
    void getTypeAcceptsJsonWhitespace() {
        assertEquals(MessageType.ASSIGN_TASK,
                PacketParser.getType(bytes("{ \"type\" : \"ASSIGN_TASK\" }")));
    }

    @Test
    void everyParserRejectsNull() {
        assertNull(PacketParser.parseIncidentReport(null));
        assertNull(PacketParser.parseAssignTask(null));
        assertNull(PacketParser.parseDroneRegister(null));
        assertNull(PacketParser.parseDroneUpdate(null));
        assertNull(PacketParser.parseAck(null));
        assertNull(PacketParser.parseFaultReport(null));
    }

    @Test
    void incidentRequiresZoneId() {
        assertNull(PacketParser.parseIncidentReport(bytes(
                "{\"time\":\"00:00:00\",\"eventType\":\"FIRE_DETECTED\",\"severity\":\"LOW\"}")));
    }

    @Test
    void assignTaskRejectsMissingAndMalformedNumbers() {
        assertNull(PacketParser.parseAssignTask(bytes("{\"droneId\":1}")));
        assertNull(PacketParser.parseAssignTask(bytes(
                "{\"droneId\":1x,\"zoneId\":2,\"zoneXOrigin\":0,\"zoneYOrigin\":0,"
                        + "\"zoneXEnd\":100,\"zoneYEnd\":100,\"severity\":\"LOW\"}")));
    }

    @Test
    void droneRegistrationRequiresAllFieldsAndValidPort() {
        assertNull(PacketParser.parseDroneRegister(bytes("{\"droneId\":5}")));
        assertNull(PacketParser.parseDroneRegister(bytes(
                "{\"droneId\":5,\"host\":\"localhost\",\"port\":70000}")));
        assertNotNull(PacketParser.parseDroneRegister(bytes(
                "{ \"droneId\" : 5, \"host\" : \"localhost\", \"port\" : 6001 }")));
    }

    @Test
    void droneUpdateRequiresFiniteNumericTelemetry() {
        assertNull(PacketParser.parseDroneUpdate(bytes("{\"droneId\":5}")));
        assertNull(PacketParser.parseDroneUpdate(bytes(
                "{\"droneId\":5,\"state\":\"IDLE\",\"xPos\":1e309,\"yPos\":0,\"water\":15}")));
    }

    @Test
    void ackRequiresBooleanAndMessage() {
        assertNull(PacketParser.parseAck(bytes(
                "{\"inResponseTo\":\"ASSIGN_TASK\",\"success\":yes,\"message\":\"ok\"}")));
        assertNull(PacketParser.parseAck(bytes(
                "{\"inResponseTo\":\"ASSIGN_TASK\",\"success\":true}")));
    }

    @Test
    void faultReportRequiresMessage() {
        assertNull(PacketParser.parseFaultReport(bytes(
                "{\"droneId\":1,\"faultType\":\"DRONE_STUCK\"}")));
    }
}
