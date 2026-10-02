package DroneSwarmSim.net.udp;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.scheduler.FaultTypes;
import java.nio.charset.StandardCharsets;
/**
 * A utility class for parsing and extracting information from byte-encoded JSON packets. This class provides
 * methods for determining the type of message and deserializing specific message types into their corresponding
 * domain objects.
 * <p>
 * The class operates primarily on flat, minimally structured JSON objects and provides convenience methods for
 * extracting common JSON field types including strings, integers, doubles, and booleans.
 */
public class PacketParser {
    private static final String ZONE_ID = "zoneId";
    private static final String FAULT_TYPE = "faultType";
    private static final String DRONE_ID = "droneId";
    private static final String SUCCESS = "success";

    /**
     * A utility class for parsing and handling various packet types within the system.
     * <p>
     * This class is designed to extract specific data from incoming packets, determine the
     * type of the packet, and provide deserialization methods for each supported packet type.
     * <p>
     * This constructor is private to prevent instantiation, as this class only provides
     * static utility methods.
     */
    private PacketParser() {}

    /**
     * Determines the {@link MessageType} of a given byte array payload.
     * <p>
     * The method expects the payload to be JSON-encoded, extracts the value
     * associated with the "type" field, and maps it to the corresponding
     * enumeration value in {@link MessageType}.
     *
     * @param data the byte array containing the JSON-encoded payload. Must not be null
     *             and should be properly encoded in UTF-8.
     * @return the {@link MessageType} corresponding to the "type" field in the payload,
     *         or {@code null} if the field is absent or unrecognized.
     */
    public static MessageType getType(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8).trim();
        String typeValue = extractString(json, "type");
        if (typeValue == null) return null;
        try { return MessageType.valueOf(typeValue); }
        catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Parses a byte array containing JSON-encoded data to create an {@link IncidentReport} object.
     * The JSON is expected to include the fields "time", "zoneId", "eventType", and "severity".
     *
     * @param data the byte array containing the JSON-encoded incident report data. Must not be null
     *             and must be properly encoded in UTF-8.
     * @return an {@link IncidentReport} object representing the parsed data, or {@code null} if
     *         any required field is missing or malformed.
     */
    public static IncidentReport parseIncidentReport(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        String time = extractString(json, "time");
        String eventTypeStr = extractString(json, "eventType");
        String severityStr = extractString(json, "severity");

        if (time == null || eventTypeStr == null || severityStr == null) return null;
        int zoneId = extractInt(json, ZONE_ID);

        try {
            EventType et = EventType.valueOf(eventTypeStr);
            Severity sev = Severity.valueOf(severityStr);
            FaultTypes fault = parseFaultType(json);
            return new IncidentReport(time, zoneId, et, sev, fault);
        } catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Parses a byte array containing JSON-encoded data to create an {@link AssignTask} object.
     * The JSON is expected to include the fields "droneId", "zoneId", "zoneXOrigin", "zoneYOrigin",
     * "zoneXEnd", "zoneYEnd", and "severity".
     *
     * @param data the byte array containing the JSON-encoded assign task data. Must not be null
     *             and should be properly encoded in UTF-8.
     * @return an {@link AssignTask} object representing the parsed data.
     *         Returns null or throws an exception if the payload is malformed or missing required fields.
     */
    public static AssignTask parseAssignTask(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        String severityStr = extractString(json, "severity");
        /* All fields are required; return null if any are missing. */
        if (severityStr == null
                || !containsKey(json, DRONE_ID)
                || !containsKey(json, ZONE_ID)
                || !containsKey(json, "zoneXOrigin")
                || !containsKey(json, "zoneYOrigin")
                || !containsKey(json, "zoneXEnd")
                || !containsKey(json, "zoneYEnd")) {
            return null;
        }

        try {
            int droneId     = extractInt(json, DRONE_ID);
            int zoneId      = extractInt(json, ZONE_ID);
            int zoneXOrigin = extractInt(json, "zoneXOrigin");
            int zoneYOrigin = extractInt(json, "zoneYOrigin");
            int zoneWidth   = extractInt(json, "zoneXEnd");
            int zoneLength  = extractInt(json, "zoneYEnd");
            Severity sev    = Severity.valueOf(severityStr);
            FaultTypes fault = parseFaultType(json);
            return new AssignTask(droneId, zoneId, zoneXOrigin, zoneYOrigin, zoneWidth, zoneLength, sev, fault);
        } catch (IllegalArgumentException e) { return null; } // Malformed numeric field or invalid severity enum.
    }

    /**
     * Parses a byte array containing JSON-encoded data to create a {@link DroneRegister} object.
     * The JSON is expected to include the fields "droneId", "host", and "port".
     *
     * @param data the byte array containing the JSON-encoded drone register data. Must not be null
     *             and must be properly encoded in UTF-8.
     * @return a {@link DroneRegister} object representing the parsed data, or {@code null} if
     *         any required field is missing or malformed.
     */
    public static DroneRegister parseDroneRegister(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        String host = extractString(json, "host");

        if (host == null) return null;
        int droneId = extractInt(json, DRONE_ID);
        int port = extractInt(json, "port");

        if (port < 1 || port > 65535) return null;
        return new DroneRegister(droneId, host, port);
    }

    /**
     * Parses a byte array containing JSON-encoded data to create a {@link DroneUpdate} object.
     * The JSON is expected to include the fields "droneId", "state", "xPos", "yPos", and "water".
     *
     * @param data the byte array containing the JSON-encoded drone update data. Must not be null
     *             and must be properly encoded in UTF-8.
     * @return a {@link DroneUpdate} object representing the parsed data, or {@code null} if
     *         any required field is missing or malformed.
     */
    public static DroneUpdate parseDroneUpdate(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        String stateStr = extractString(json, "state");

        if (stateStr == null) return null;
        try {
            int droneId    = extractInt(json, DRONE_ID);
            DroneState state = DroneState.valueOf(stateStr);
            double xPos    = extractDouble(json, "xPos");
            double yPos    = extractDouble(json, "yPos");
            double water   = extractDouble(json, "water");
            // Battery and fuel are optional; default to 100.0 for backward compatibility
            // with packets that pre-date the battery/fuel fields.
            double battery = containsKey(json, "battery") ? extractDouble(json, "battery") : 100.0;
            double fuel    = containsKey(json, "fuel")    ? extractDouble(json, "fuel")    : 100.0;
            return new DroneUpdate(droneId, state, xPos, yPos, water, battery, fuel);
        } catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Parses a byte array containing JSON-encoded data to create an {@link Ack} object.
     * The JSON is expected to include the fields "inResponseTo", "success", and "message".
     *
     * @param data the byte array containing the JSON-encoded acknowledgment data. Must not be null
     *             and must be properly encoded in UTF-8.
     * @return an {@link Ack} object representing the parsed data. Returns {@code null} or throws
     *         an exception if the payload is malformed or missing required fields.
     */
    public static Ack parseAck(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        String inResponseToStr = extractString(json, "inResponseTo");

        if (inResponseToStr == null) return null;
        try {
            MessageType inResponseTo = MessageType.valueOf(inResponseToStr);
            boolean success = extractBoolean(json, SUCCESS);
            String message  = extractString(json, "message");
            return new Ack(inResponseTo, success, message);
        } catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Parses a byte array containing JSON-encoded data to create a {@link FaultReport} object.
     * The JSON is expected to include the fields "droneId", "faultType", and "message".
     *
     * @param data the byte array containing the JSON-encoded fault report data.
     * @return a {@link FaultReport} object representing the parsed data, or {@code null} if
     *         any required field is missing or malformed.
     */
    public static FaultReport parseFaultReport(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        String faultTypeStr = extractString(json, FAULT_TYPE);

        if (faultTypeStr == null) return null;
        // Ensure that the required "droneId" field is actually present before parsing.
        if (!containsKey(json, DRONE_ID)) return null;
        try {
            int droneId = extractInt(json, DRONE_ID);
            FaultTypes faultType = FaultTypes.valueOf(faultTypeStr);
            String message = extractString(json, "message");
            return new FaultReport(droneId, faultType, message == null ? "" : message);
        } catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Parses the fault type from the provided JSON string.
     *
     * @param json the JSON string containing the fault type information
     * @return the corresponding {@code FaultTypes} enum value if parsing is successful;
     *         {@code FaultTypes.NONE} if the fault type is missing or unrecognized
     */
    private static FaultTypes parseFaultType(String json) {
        String faultStr = extractString(json, FAULT_TYPE);
        if (faultStr == null) { return FaultTypes.NONE; }
        try { return FaultTypes.valueOf(faultStr); }
        catch (IllegalArgumentException e) { return FaultTypes.NONE; } // Unrecognized fault type: default to NONE.
    }

    /**
     * Returns {@code true} if the given key is present in the JSON string.
     *
     * @param json the JSON-formatted string to search. Must not be null.
     * @param key  the key to locate. Must not be null.
     * @return {@code true} if the key is found, {@code false} otherwise.
     */
    static boolean containsKey(String json, String key) { return json.contains("\"" + key + "\":"); }

    /**
     * Extracts the value of a specific string field from a JSON-formatted string.
     * This method searches for a key-value pair in the JSON where the key
     * matches the provided {@code key} parameter and returns the corresponding value.
     *
     * @param json the JSON-formatted string to search. Must not be null.
     * @param key the key to locate in the JSON string. Must not be null.
     * @return the value associated with the specified key, with escaped characters
     *         properly unescaped, or {@code null} if the key is not found or malformed.
     */
    static String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf('"', start);
        while (end > 0 && json.charAt(end - 1) == '\\') { end = json.indexOf('"', end + 1); }
        if (end < 0) return null;
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /**
     * Extracts the integer value associated with a specific key from a JSON-formatted string.
     * This method searches for the specified key in the JSON string and returns the corresponding
     * integer value. If the key is not found or if the value is malformed, it returns 0.
     *
     * @param json the JSON-formatted string to search. Must not be null.
     * @param key the key to locate in the JSON string. Must not be null.
     * @return the integer value associated with the specified key, or 0 if the key is not found
     *         or the value is malformed.
     */
    static int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return 0;
        start += search.length();
        // skip the leading quote if this is a quoted number (sanity check)
        if (start < json.length() && json.charAt(start) == '"') start++;
        int end = start;
        // Allow a leading minus sign only at the start of the number
        if (end < json.length() && json.charAt(end) == '-') end++;
        while (end < json.length() && Character.isDigit(json.charAt(end))) { end++; }
        if (end == start) return 0;
        return Integer.parseInt(json.substring(start, end));
    }

    /**
     * Extracts the double value associated with a specific key from a JSON-formatted string.
     * This method searches for the specified key in the JSON string and returns the corresponding
     * double value. If the key is not found or if the value is malformed, it returns 0.0.
     *
     * @param json the JSON-formatted string to search. Must not be null.
     * @param key the key to locate in the JSON string. Must not be null.
     * @return the double value associated with the specified key, or 0.0 if the key is not found
     *         or the value is malformed.
     */
    static double extractDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return 0.0;
        start += search.length();
        if (start < json.length() && json.charAt(start) == '"') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || json.charAt(end) == '.')) { end++; }
        return Double.parseDouble(json.substring(start, end));
    }

    /**
     * Extracts the boolean value associated with the specified key from a JSON-formatted string.
     * This method searches for the key in the JSON string and determines if the associated
     * value is {@code true}. If the key is not found, the method returns {@code false}.
     *
     * @param json the JSON-formatted string to search. Must not be null.
     * @param key the key to locate in the JSON string. Must not be null.
     * @return {@code true} if the key is present and the associated value is {@code true},
     *         otherwise {@code false}.
     */
    static boolean extractBoolean(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return false;
        start += search.length();
        return json.startsWith("true", start);
    }
}