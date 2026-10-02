package DroneSwarmSim.net.udp;

import DroneSwarmSim.messaging.MessageType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PacketParser class, ensuring robustness and correct behaviour
 * in handling various types of input payloads, including malformed, incomplete,
 * or random data. The tests verify that the PacketParser behaves as expected
 * and gracefully handles invalid input scenarios without crashing.
 * <p>
 * Tests include:
 * - Handling of empty input payloads.
 * - Processing random byte arrays that do not conform to expected formats.
 * - Behaviour with plain-text input that does not represent valid JSON.
 * - Handling invalid JSON objects, missing required fields, or undefined type values.
 * - Graceful handling of null input to prevent unexpected crashes.
 */
@DisplayName("PacketParser Robustness Tests")
class PacketParserTest {

    /**
     * Verifies that the `PacketParser.getType` method returns a null `MessageType` when invoked with an empty byte array.
     * <p>
     * This test ensures that the parser can handle empty payloads gracefully by not throwing any exceptions
     * and returning a null type, as expected for invalid or unsupported input.
     * <p>
     * The following assertions are made:
     * - The method call does not throw any exceptions.
     * - The returned `MessageType` is null, indicating that an empty payload produces no valid type.
     */
    @Test
    void testEmptyBytesReturnUnknownType() {
        assertDoesNotThrow(() -> {
            MessageType t = PacketParser.getType(new byte[0]);
            assertNull(t, "Empty payload should produce null type");
        });
    }

    /**
     * Tests that the `PacketParser.getType` method returns a null `MessageType`
     * when passed a random sequence of bytes.
     * <p>
     * This test ensures that the parser does not incorrectly interpret random byte
     * sequences as valid message types. It validates the following conditions:
     * - The method invocation does not throw any exceptions.
     * - The returned `MessageType` is null, indicating that random bytes do not
     *   correspond to any valid type.
     */
    @Test
    void testRandomBytesReturnUnknownType() {
        byte[] junk = {0x01, 0x02, 0x03, 0x04, 0x05};
        assertDoesNotThrow(() -> {
            MessageType t = PacketParser.getType(junk);
            assertNull(t, "Random bytes should produce null type");
        });
    }

    /**
     * Tests that the `PacketParser.getType` method returns a null `MessageType`
     * when invoked with a plaintext string converted to a byte array.
     * <p>
     * This test verifies that the message parser correctly handles inputs that
     * do not correspond to any valid structured message type and ensures that:
     * - The method invocation does not throw any exceptions.
     * - The returned `MessageType` is null, indicating that plaintext bytes
     *   do not map to a valid message type.
     */
    @Test
    void testPlaintextStringProducesNullType() {
        byte[] text = "HELLO WORLD".getBytes();
        assertDoesNotThrow(() -> {
            MessageType t = PacketParser.getType(text);
            assertNull(t, "Plain-text bytes should produce null type");
        });
    }

    /**
     * Ensures that the `PacketParser.getType` method correctly handles an invalid JSON object
     * with a `null` "type" field and produces a `null` `MessageType` result.
     * <p>
     * This test verifies:
     * - The method does not throw any exceptions when parsing an invalid JSON object.
     * - The returned `MessageType` is `null`, indicating that the input is not recognized
     *   as representing a valid message type.
     */
    @Test
    void testInvalidJsonObjectProducesNullType() {
        byte[] invalidJson = "{\"type\":null}".getBytes();
        assertDoesNotThrow(() -> {
            MessageType t = PacketParser.getType(invalidJson);
            assertNull(t);
        });
    }

    /**
     * Tests that the `PacketParser.getType` method returns a null `MessageType`
     * when invoked with a JSON byte array that is missing the "type" field.
     * <p>
     * This test validates the behaviour of the parser when required fields are absent
     * in the JSON payload. The following conditions are verified:
     * - The method invocation does not throw any exceptions.
     * - The returned `MessageType` is null, indicating that a missing "type" field
     *   results in a null interpretation of the message type.
     */
    @Test
    void testMissingTypeFieldProducesNullType() {
        byte[] noType = "{\"droneId\":1,\"zoneId\":2}".getBytes();
        assertDoesNotThrow(() -> {
            MessageType t = PacketParser.getType(noType);
            assertNull(t, "JSON without 'type' field should produce null type");
        });
    }

    /**
     * Verifies that the `PacketParser.getType` method returns a null `MessageType`
     * when invoked with a JSON byte array containing an unsupported or unknown "type" value.
     * <p>
     * This test ensures that the parser correctly handles cases where the "type" field in the payload
     * does not correspond to any value defined in the `MessageType` enumeration. It validates the following:
     * - The method call does not throw any exceptions.
     * - The returned `MessageType` is null, indicating that unrecognized "type" values are treated as invalid.
     */
    @Test
    void testUnknownTypeValueProducesNullType() {
        byte[] unknownType = "{\"type\":\"BANANA\"}".getBytes();
        assertDoesNotThrow(() -> {
            MessageType t = PacketParser.getType(unknownType);
            assertNull(t, "Unknown 'type' string should produce null type");
        });
    }

    /**
     * Tests that the `PacketParser.parseIncidentReport` method returns `null`
     * when invoked with a JSON byte array representing an `IncidentReport` that
     * is missing required fields.
     * <p>
     * This test ensures that the parser can gracefully handle incomplete or
     * improperly formatted `IncidentReport` payloads without throwing exceptions
     * and correctly returns a `null` result to indicate the invalidity of the input.
     * <p>
     * The following conditions are verified:
     * - The method invocation does not throw any exceptions.
     * - The method returns `null` when critical fields are missing in the input JSON.
     */
    @Test
    void testParseIncidentReportWithMissingFieldsReturnsNull() {
        // JSON looks like an incident report but is missing required fields
        byte[] incomplete = "{\"type\":\"INCIDENT_REPORT\",\"zoneId\":1}".getBytes();
        assertDoesNotThrow(() -> {
            var r = PacketParser.parseIncidentReport(incomplete);
            assertNull(r, "Incomplete IncidentReport JSON should return null");
        });
    }

    /**
     * Tests that the `PacketParser.parseAssignTask` method returns `null` when
     * invoked with a JSON byte array representing an `AssignTask` message that
     * is missing required fields.
     * <p>
     * This test ensures that the parser correctly handles incomplete or improperly
     * formatted `AssignTask` payloads without throwing exceptions and returns a
     * `null` result to indicate that the input is invalid.
     * <p>
     * The following conditions are verified:
     * - The method invocation does not throw any exceptions.
     * - The method returns `null` when critical fields are missing in the input JSON.
     */
    @Test
    void testParseAssignTaskWithMissingFieldsReturnsNull() {
        byte[] incomplete = "{\"type\":\"ASSIGN_TASK\",\"droneId\":1}".getBytes();
        assertDoesNotThrow(() -> {
            var r = PacketParser.parseAssignTask(incomplete);
            assertNull(r, "Incomplete AssignTask JSON should return null");
        });
    }

    /**
     * Tests that the `PacketParser.parseDroneRegister` method returns `null` when
     * invoked with a JSON byte array representing a `DroneRegister` message that
     * is missing required fields.
     * <p>
     * This test ensures that the parser handles incomplete or improperly formatted
     * `DroneRegister` payloads without throwing exceptions and correctly produces
     * a `null` result to indicate the input is invalid.
     * <p>
     * Key verifications performed by this test:
     * - The method invocation does not throw any exceptions.
     * - The method returns `null` when critical fields are missing in the input JSON.
     */
    @Test
    void testParseDroneRegisterWithMissingFieldsReturnsNull() {
        byte[] incomplete = "{\"type\":\"DRONE_REGISTER\",\"droneId\":5}".getBytes();
        assertDoesNotThrow(() -> {
            var r = PacketParser.parseDroneRegister(incomplete);
            assertNull(r, "Incomplete DroneRegister JSON should return null");
        });
    }

    /**
     * Tests that the `PacketParser.parseDroneUpdate` method returns `null`
     * when provided with a JSON byte array representing a `DroneUpdate` message
     * that lacks required fields.
     * <p>
     * This test ensures that the parser can handle incomplete or improperly
     * structured `DroneUpdate` payloads gracefully, by not throwing exceptions
     * and correctly returning a `null` result to signify the invalidity of the input.
     * <p>
     * Verifications performed by this test:
     * - The method invocation does not throw any exceptions.
     * - The method returns `null` when critical fields are omitted in the input JSON.
     */
    @Test
    void testParseDroneUpdateWithMissingFieldsReturnsNull() {
        byte[] incomplete = "{\"type\":\"DRONE_UPDATE\",\"droneId\":5}".getBytes();
        assertDoesNotThrow(() -> {
            var r = PacketParser.parseDroneUpdate(incomplete);
            assertNull(r, "Incomplete DroneUpdate JSON should return null");
        });
    }

    /**
     * Validates that the `PacketParser.getType` method handles a null byte array input gracefully.
     * <p>
     * This test ensures that when a null input is passed to the method, the following conditions
     * are upheld:
     * - The method does not crash the JVM.
     * - The method either returns a null `MessageType` or throws a well-defined exception
     *   (`NullPointerException` or `IllegalArgumentException`), which is considered acceptable behaviour.
     * <p>
     * The primary purpose is to verify robust handling of null inputs without undefined behaviour
     * or unhandled exceptions.
     */
    @Test
    void testNullByteArrayHandledGracefully() {
        // Passing null should either return null or throw a defined exception, not crash the JVM
        try {
            MessageType t = PacketParser.getType(null);
            assertNull(t);
        } catch (NullPointerException | IllegalArgumentException e) {
            // Acceptable — the important thing is no unhandled crash
        }
    }
}
