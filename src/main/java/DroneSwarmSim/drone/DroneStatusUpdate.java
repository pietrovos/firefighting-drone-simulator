package DroneSwarmSim.drone;
/**
 * Represents the status update of a drone within the drone swarm simulation system.
 * This class encapsulates critical information about the state and position of a drone,
 * as well as the amount of water it carries for fire suppression tasks.
 * <p>
 * The status update includes:
 * - The unique identifier of the drone.
 * - The current operational state of the drone, represented as a {@code DroneState}.
 * - The current x and y coordinates of the drone.
 * - The amount of water available for fire suppression tasks.
 * <p>
 * Instances of this class are immutable once created, ensuring thread safety when used
 * in a concurrent environment.
 */
public class DroneStatusUpdate {
    private final int droneId; // Unique identifier for the drone.
    private final DroneState state; // Current operational state of the drone.
    private final double xPos; // Current x-coordinate of the drone's position.'
    private final double yPos; // Current y-coordinate of the drone's position.'
    private final double water; // Current water level of the drone.

    /**
     * Constructs an instance of the DroneStatusUpdate class representing the current
     * state of a drone, including its unique identifier, operational state, position,
     * and water availability for fire suppression tasks.
     *
     * @param droneId the unique identifier of the drone.
     * @param state the current operational state of the drone, represented as a {@code DroneState}.
     * @param xPos the x-coordinate representing the drone's horizontal position.
     * @param yPos the y-coordinate representing the drone's vertical position.
     * @param water the amount of water available for fire suppression tasks, represented as double.
     */
    public DroneStatusUpdate(int droneId, DroneState state, double xPos, double yPos, double water) {
        this.droneId = droneId;
        this.state = state;
        this.xPos = xPos;
        this.yPos = yPos;
        this.water = water;
    }

    /**
     * Retrieves the unique identifier of the drone.
     *
     * @return the unique drone ID as an integer.
     */
    public int getDroneId() { return droneId; }

    /**
     * Retrieves the current operational state of the drone.
     *
     * @return the current state of the drone represented as a {@code DroneState} enum.
     */
    public DroneState getState() { return state; }

    /**
     * Retrieves the x-coordinate representing the drone's horizontal position.
     *
     * @return the x-coordinate of the drone as a double.
     */
    public double getXPos() { return xPos; }

    /**
     * Retrieves the y-coordinate representing the drone's vertical position.
     *
     * @return the y-coordinate of the drone as a double.
     */
    public double getYPos() { return yPos; }

    /**
     * Retrieves the amount of water currently available for fire suppression tasks.
     *
     * @return the amount of water available as a double.
     */
    public double getWater() { return water; }

    /**
     * Returns a string representation of the DroneStatusUpdate object.
     * The string includes detailed information about the drone's unique identifier,
     * its current state, positional coordinates (x and y), and the amount of water it has available.
     *
     * @return a string representation of this DroneStatusUpdate object.
     */
    @Override
    public String toString() {
        return "DroneStatusUpdate{droneId=" + droneId + ", state=" + state
                + ", xPos=" + xPos + ", yPos=" + yPos + ", water=" + water + "}";
    }
}
