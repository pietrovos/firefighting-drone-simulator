package DroneSwarmSim.messaging;
/**
 * Defines the types of messages used within the drone swarm simulation system.
 * This enumeration serves as a central point for identifying and categorizing
 * the various messages exchanged between components of the system.
 * <p>
 * Each message type corresponds to a specific functionality or operation:
 * - INCIDENT_REPORT: Represents a message reporting an incident or emergency.
 * - ASSIGN_TASK: Represents a message for assigning a task to a drone.
 * - DRONE_REGISTER: Represents a message for registering a drone in the system.
 * - DRONE_UPDATE: Represents a message for updating the state and position of a drone.
 * - ACK: Represents an acknowledgment message sent in response to received messages.
 */
public enum MessageType { INCIDENT_REPORT, ASSIGN_TASK, DRONE_REGISTER, DRONE_UPDATE, ACK, FAULT_REPORT }
