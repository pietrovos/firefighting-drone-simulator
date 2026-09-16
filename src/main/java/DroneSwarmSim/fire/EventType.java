package DroneSwarmSim.fire;
/**
 * Represents the type of events related to fire incidents in the drone swarm simulation system.
 * <p>
 * The EventType enum is used to classify the nature of events that occur in the context of fire incidents.
 * It provides two distinct types of events:
 * <p>
 * - FIRE_DETECTED: Represents an event where fire is detected automatically by the sensors of a drone.
 * - DRONE_REQUEST: Represents an event where a user or system operator manually triggers a request for a drone
 *   due to heightened risk or direct observation.
 * <p>
 * This enum is used to ensure consistent and type-safe representation of event types throughout the system,
 * avoiding reliance on raw strings or other non-standard identifiers.
 */
public enum EventType { FIRE_DETECTED, DRONE_REQUEST }
