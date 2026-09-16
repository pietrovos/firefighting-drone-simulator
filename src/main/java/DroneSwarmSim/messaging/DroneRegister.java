package DroneSwarmSim.messaging;
/**
 * Represents a record for registering a drone in the system.
 * This record encapsulates the essential information about a drone,
 * including its unique identifier, hostname or IP address, and communication port.
 * <p>
 * It is used within the system to identify and manage drones
 * in the context of communication and task assignment.
 *
 * @param droneId the unique identifier of the drone
 * @param host the hostname or IP address associated with the drone
 * @param port the port number for communication with the drone
 */
public record DroneRegister(int droneId, String host, int port) {

    /**
     * Returns a string representation of the drone registration record.
     * The string includes the drone's unique identifier, host (hostname or IP),
     * and port used for communication.
     *
     * @return a string representing the drone registration record
     */
    @Override
    public String toString() { return "DroneRegister{droneId=" + droneId + ", host='" + host + "', port=" + port + "}"; }
}
