package DroneSwarmSim.scheduler;
/**
 * Enumeration representing the different types of faults that can occur in the system.
 * <p>
 * These fault types provide a standardized way to categorize and handle errors or
 * issues within the Drone Swarm Simulation:
 * <p>
 * - NONE: No fault — normal operation.
 * - DRONE_STUCK: Drone stops responding mid-flight (detected by scheduler timeout).
 * - NOZZLE_STUCK_CLOSED: Nozzle/bay doors are stuck closed; drone cannot drop water.
 * - NOZZLE_STUCK_OPEN: Nozzle/bay doors are stuck open — hard fault; drone must be shut down.
 * - PACKET_LOSS: Simulated communication fault; drone drops outgoing status packets.
 * - PACKET_CORRUPTION: Simulated communication fault; drone sends one corrupted packet.
 * - DRONE_TIMEOUT: Legacy alias — drone failed to respond within the expected timeframe.
 * - COMMUNICATION_ERROR: Legacy alias — general communication failure.
 * - MISSION_FAILURE: Legacy alias — drone could not complete its assigned mission.
 */
public enum FaultTypes {
    NONE, DRONE_STUCK, NOZZLE_STUCK_CLOSED, NOZZLE_STUCK_OPEN, PACKET_LOSS, PACKET_CORRUPTION,
    // Legacy aliases kept for backward compatibility
    DRONE_TIMEOUT, COMMUNICATION_ERROR, MISSION_FAILURE
}
