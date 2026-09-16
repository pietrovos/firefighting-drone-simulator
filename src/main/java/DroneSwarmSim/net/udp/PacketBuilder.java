package DroneSwarmSim.net.udp;

import DroneSwarmSim.messaging.*;
import java.nio.charset.StandardCharsets;
/**
 * Provides utility methods to build byte arrays from various data objects
 * for serialization into JSON format. These byte arrays can then be used
 * for communication within the drone swarm simulation system.
 * <p>
 * The JSON structure generated for each method aligns with the type of
 * object being serialized, with embedded fields specific to the object.
 */
public class PacketBuilder {
    /**
     * A private constructor to prohibit direct instantiation of the PacketBuilder class.
     * <p>
     * This class provides static utility methods for constructing byte arrays from
     * various message types such as IncidentReport, AssignTask, DroneRegister, DroneUpdate, and Ack.
     * <p>
     * All methods in this class are static, ensuring the construction logic is centralized
     * and prevents creating unnecessary instances of PacketBuilder.
     */
    private PacketBuilder() {}

    /**
     * Constructs a byte array representation of an {@code IncidentReport}.
     * The method serializes the {@code IncidentReport} into a JSON-formatted string
     * and encodes it as a UTF-8 byte array.
     *
     * @param report the {@code IncidentReport} instance to be serialized into a byte array.
     * @return a UTF-8 encoded byte array representing the provided {@code IncidentReport}.
     */
    public static byte[] build(IncidentReport report) {
        String json = "{"
                + "\"type\":\"INCIDENT_REPORT\","
                + "\"time\":" + quote(report.time()) + ","
                + "\"zoneId\":" + report.zoneId() + ","
                + "\"eventType\":" + quote(report.eventType().name()) + ","
                + "\"severity\":" + quote(report.severity().name()) + ","
                + "\"faultType\":" + quote(report.faultType().name())
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a byte array representation of an {@code AssignTask}.
     * The method serializes the {@code AssignTask} into a JSON-formatted string
     * and encodes it as a UTF-8 byte array.
     *
     * @param task the {@code AssignTask} instance to be serialized into a byte array.
     * @return a UTF-8 encoded byte array representing the provided {@code AssignTask}.
     */
    public static byte[] build(AssignTask task) {
        String json = "{"
                + "\"type\":\"ASSIGN_TASK\","
                + "\"droneId\":" + task.droneId() + ","
                + "\"zoneId\":" + task.zoneId() + ","
                + "\"zoneXOrigin\":" + task.zoneXOrigin() + ","
                + "\"zoneYOrigin\":" + task.zoneYOrigin() + ","
                + "\"zoneXEnd\":" + task.zoneXEnd() + ","
                + "\"zoneYEnd\":" + task.zoneYEnd() + ","
                + "\"severity\":" + quote(task.severity().name()) + ","
                + "\"faultType\":" + quote(task.faultType().name())
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a byte array representation of a {@code DroneRegister}.
     * The method serializes the {@code DroneRegister} into a JSON-formatted string
     * and encodes it as a UTF-8 byte array.
     *
     * @param reg the {@code DroneRegister} instance containing the registration details
     *            of the drone, including its unique identifier, hostname or IP,
     *            and communication port.
     * @return a UTF-8 encoded byte array representing the provided {@code DroneRegister}.
     */
    public static byte[] build(DroneRegister reg) {
        String json = "{"
                + "\"type\":\"DRONE_REGISTER\","
                + "\"droneId\":" + reg.droneId() + ","
                + "\"host\":" + quote(reg.host()) + ","
                + "\"port\":" + reg.port()
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a byte array representation of a {@code DroneUpdate}.
     * The method serializes the {@code DroneUpdate} into a JSON-formatted string
     * and encodes it as a UTF-8 byte array.
     *
     * @param update the {@code DroneUpdate} instance containing the state, position,
     *               and water level of a drone to be serialized into a byte array.
     * @return a UTF-8 encoded byte array representing the provided {@code DroneUpdate}.
     */
    public static byte[] build(DroneUpdate update) {
        String json = "{"
                + "\"type\":\"DRONE_UPDATE\","
                + "\"droneId\":" + update.getDroneId() + ","
                + "\"state\":" + quote(update.getState().name()) + ","
                + "\"xPos\":" + update.getXPos() + ","
                + "\"yPos\":" + update.getYPos() + ","
                + "\"water\":" + update.getWater() + ","
                + "\"battery\":" + update.getBattery() + ","
                + "\"fuel\":" + update.getFuel()
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a byte array representation of an {@code Ack}.
     * The method serializes the {@code Ack} into a JSON-formatted string
     * and encodes it as a UTF-8 byte array.
     *
     * @param ack the {@code Ack} instance to be serialized into a byte array.
     *            This object encapsulates the response type, success status, and
     *            an optional message providing additional context.
     * @return a UTF-8 encoded byte array representing the provided {@code Ack}.
     */
    public static byte[] build(Ack ack) {
        String json = "{"
                + "\"type\":\"ACK\","
                + "\"inResponseTo\":" + quote(ack.inResponseTo().name()) + ","
                + "\"success\":" + ack.success() + ","
                + "\"message\":" + quote(ack.message())
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a byte array representation of a {@code FaultReport}.
     * The method serializes the {@code FaultReport} into a JSON-formatted string
     * and encodes it as a UTF-8 byte array.
     *
     * @param report the {@code FaultReport} instance to be serialized.
     * @return a UTF-8 encoded byte array representing the provided {@code FaultReport}.
     */
    public static byte[] build(FaultReport report) {
        String json = "{"
                + "\"type\":\"FAULT_REPORT\","
                + "\"droneId\":" + report.droneId() + ","
                + "\"faultType\":" + quote(report.faultType().name()) + ","
                + "\"message\":" + quote(report.message())
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Quotes the provided string value by surrounding it with double quotes and escaping
     * special characters such as backslashes and double quotes within the string.
     * If the input value is {@code null}, the method returns the string "null".
     *
     * @param value the string to be quoted; may be {@code null}.
     * @return a quoted string with escaped special characters, or the string "null" if {@code value} is {@code null}.
     */
    static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
