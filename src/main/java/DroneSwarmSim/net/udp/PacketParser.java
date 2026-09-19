package DroneSwarmSim.net.udp;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.Ack;
import DroneSwarmSim.messaging.AssignTask;
import DroneSwarmSim.messaging.DroneRegister;
import DroneSwarmSim.messaging.DroneUpdate;
import DroneSwarmSim.messaging.FaultReport;
import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.messaging.MessageType;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.scheduler.FaultTypes;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the flat JSON messages produced by {@link PacketBuilder}. */
public final class PacketParser {
    private static final String ZONE_ID = "zoneId";
    private static final String FAULT_TYPE = "faultType";
    private static final String DRONE_ID = "droneId";
    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private PacketParser() { }

    public static MessageType getType(byte[] data) {
        String json = payload(data);
        String type = extractString(json, "type");
        if (type == null) return null;
        try {
            return MessageType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static IncidentReport parseIncidentReport(byte[] data) {
        String json = payload(data);
        if (json == null) return null;

        String time = extractString(json, "time");
        Integer zoneId = extractInt(json, ZONE_ID);
        String eventType = extractString(json, "eventType");
        String severity = extractString(json, "severity");
        if (time == null || zoneId == null || eventType == null || severity == null) return null;

        try {
            return new IncidentReport(time, zoneId, EventType.valueOf(eventType),
                    Severity.valueOf(severity), parseFaultType(json));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static AssignTask parseAssignTask(byte[] data) {
        String json = payload(data);
        if (json == null) return null;

        Integer droneId = extractInt(json, DRONE_ID);
        Integer zoneId = extractInt(json, ZONE_ID);
        Integer zoneXOrigin = extractInt(json, "zoneXOrigin");
        Integer zoneYOrigin = extractInt(json, "zoneYOrigin");
        Integer zoneXEnd = extractInt(json, "zoneXEnd");
        Integer zoneYEnd = extractInt(json, "zoneYEnd");
        String severity = extractString(json, "severity");
        if (droneId == null || zoneId == null || zoneXOrigin == null || zoneYOrigin == null
                || zoneXEnd == null || zoneYEnd == null || severity == null) return null;

        try {
            return new AssignTask(droneId, zoneId, zoneXOrigin, zoneYOrigin, zoneXEnd, zoneYEnd,
                    Severity.valueOf(severity), parseFaultType(json));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static DroneRegister parseDroneRegister(byte[] data) {
        String json = payload(data);
        if (json == null) return null;

        Integer droneId = extractInt(json, DRONE_ID);
        String host = extractString(json, "host");
        Integer port = extractInt(json, "port");
        if (droneId == null || host == null || host.isBlank() || port == null || port < 1 || port > 65535) {
            return null;
        }
        return new DroneRegister(droneId, host, port);
    }

    public static DroneUpdate parseDroneUpdate(byte[] data) {
        String json = payload(data);
        if (json == null) return null;

        Integer droneId = extractInt(json, DRONE_ID);
        String state = extractString(json, "state");
        Double xPos = extractDouble(json, "xPos");
        Double yPos = extractDouble(json, "yPos");
        Double water = extractDouble(json, "water");
        if (droneId == null || state == null || xPos == null || yPos == null || water == null) return null;

        Double battery = containsKey(json, "battery") ? extractDouble(json, "battery") : 100.0;
        Double fuel = containsKey(json, "fuel") ? extractDouble(json, "fuel") : 100.0;
        if (battery == null || fuel == null) return null;

        try {
            return new DroneUpdate(droneId, DroneState.valueOf(state), xPos, yPos, water, battery, fuel);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Ack parseAck(byte[] data) {
        String json = payload(data);
        if (json == null) return null;

        String inResponseTo = extractString(json, "inResponseTo");
        Boolean success = extractBoolean(json, "success");
        String message = extractString(json, "message");
        if (inResponseTo == null || success == null || message == null) return null;

        try {
            return new Ack(MessageType.valueOf(inResponseTo), success, message);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static FaultReport parseFaultReport(byte[] data) {
        String json = payload(data);
        if (json == null) return null;

        Integer droneId = extractInt(json, DRONE_ID);
        String faultType = extractString(json, FAULT_TYPE);
        String message = extractString(json, "message");
        if (droneId == null || faultType == null || message == null) return null;

        try {
            return new FaultReport(droneId, FaultTypes.valueOf(faultType), message);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String payload(byte[] data) {
        if (data == null) return null;
        String json = new String(data, StandardCharsets.UTF_8).trim();
        return json.startsWith("{") && json.endsWith("}") ? json : null;
    }

    private static FaultTypes parseFaultType(String json) {
        String fault = extractString(json, FAULT_TYPE);
        if (fault == null) return FaultTypes.NONE;
        try {
            return FaultTypes.valueOf(fault);
        } catch (IllegalArgumentException e) {
            return FaultTypes.NONE;
        }
    }

    static boolean containsKey(String json, String key) {
        return valueStart(json, key) >= 0;
    }

    static String extractString(String json, String key) {
        int start = valueStart(json, key);
        if (start < 0 || start >= json.length() || json.charAt(start) != '"') return null;

        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaped) {
                switch (current) {
                    case '"', '\\', '/' -> value.append(current);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    default -> { return null; }
                }
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return validValueEnd(json, i + 1) ? value.toString() : null;
            } else if (current < 0x20) {
                return null;
            } else {
                value.append(current);
            }
        }
        return null;
    }

    static Integer extractInt(String json, String key) {
        String token = extractNumberToken(json, key);
        if (token == null || token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
            return null;
        }
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double extractDouble(String json, String key) {
        String token = extractNumberToken(json, key);
        if (token == null) return null;
        try {
            double value = Double.parseDouble(token);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Boolean extractBoolean(String json, String key) {
        int start = valueStart(json, key);
        if (start < 0) return null;
        if (json.startsWith("true", start) && validValueEnd(json, start + 4)) return true;
        if (json.startsWith("false", start) && validValueEnd(json, start + 5)) return false;
        return null;
    }

    private static String extractNumberToken(String json, String key) {
        int start = valueStart(json, key);
        if (start < 0) return null;
        Matcher matcher = NUMBER.matcher(json);
        matcher.region(start, json.length());
        if (!matcher.lookingAt() || !validValueEnd(json, matcher.end())) return null;
        return matcher.group();
    }

    private static int valueStart(String json, String key) {
        if (json == null || key == null) return -1;
        Pattern field = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*");
        Matcher matcher = field.matcher(json);
        return matcher.find() ? matcher.end() : -1;
    }

    private static boolean validValueEnd(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
        return index < json.length() && (json.charAt(index) == ',' || json.charAt(index) == '}');
    }
}
