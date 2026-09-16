package DroneSwarmSim.drone;
/**
 * Represents the various operational states a drone can have within the
 * drone swarm simulation system.
 * <p>
 * The states are used to track and manage the lifecycle of a drone as it
 * performs its assigned tasks.
 * <p>
 * The states include:
 * - IDLE: The drone is inactive and awaiting a task.
 * - EN_ROUTE: The drone is travelling to its designated location for a task.
 * - ARRIVED: The drone has reached its destination.
 * - DROPPING_AGENT: The drone is currently deploying fire suppression agents.
 * - COMPLETED: The drone has successfully completed the task.
 * - RETURNING: The drone is returning to its base or refill station.
 * - REFILLING: The drone is being refilled with necessary supplies for its tasks.
 * - FAULTED: The drone has encountered a fault or error and requires attention.
 */
public enum DroneState {IDLE, EN_ROUTE, ARRIVED, DROPPING_AGENT, COMPLETED, RETURNING, REFILLING, FAULTED }
