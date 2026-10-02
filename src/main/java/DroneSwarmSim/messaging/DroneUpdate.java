package DroneSwarmSim.messaging;

import DroneSwarmSim.drone.DroneState;
/**
 * Represents an update message for a specific drone within the drone swarm simulation system.
 * This class encapsulates the state, positional information, liquid-agent level, battery level,
 * and fuel level of a drone.
 * <p>
 * The information provided in this class is used to track and manage drones in the system,
 * enabling the real-time monitoring of their state and resource levels.
 */
public class DroneUpdate {
    private final int droneId;
    private final DroneState state;
    private final double xPos;
    private final double yPos;
    private final double water;
    private final double battery; // Battery level in percentage (0.0 – 100.0).
    private final double fuel; // Fuel level in percentage (0.0 – 100.0).

    /**
     * Full constructor including battery and fuel telemetry.
     *
     * @param droneId the unique identifier of the drone
     * @param state   the current operational state of the drone
     * @param xPos    the x-coordinate of the drone's current position
     * @param yPos    the y-coordinate of the drone's current position
     * @param water   the remaining liquid-agent quantity in litres
     * @param battery the battery level in percentage (0.0 – 100.0)
     * @param fuel    the fuel level in percentage (0.0 – 100.0)
     */
    public DroneUpdate(int droneId, DroneState state, double xPos, double yPos,
                       double water, double battery, double fuel) {
        this.droneId = droneId;
        this.state   = state;
        this.xPos    = xPos;
        this.yPos    = yPos;
        this.water   = water;
        this.battery = battery;
        this.fuel    = fuel;
    }

    /**
     * Backward-compatible constructor that defaults battery and fuel to 100 %.
     *
     * @param droneId the unique identifier of the drone
     * @param state   the current operational state of the drone, represented as a {@link DroneState}
     * @param xPos    the x-coordinate of the drone's current position
     * @param yPos    the y-coordinate of the drone's current position
     * @param water   the remaining amount of water (or another fire suppression agent) in the drone
     */
    public DroneUpdate(int droneId, DroneState state, double xPos, double yPos, double water) { this(droneId, state, xPos, yPos, water, 100.0, 100.0); }

    /**
     * Returns the unique identifier of the drone.
     *
     * @return the drone ID as an integer.
     */
    public int getDroneId() { return droneId; }

    /**
     * Retrieves the current operational state of the drone.
     *
     * @return the drone's state represented as a {@link DroneState} enum value.
     */
    public DroneState getState() { return state; }

    /**
     * Retrieves the x-coordinate of the drone's current position.
     *
     * @return the x-coordinate of the drone's position as a double.
     */
    public double getXPos() { return xPos; }

    /**
     * Retrieves the y-coordinate of the drone's current position.
     *
     * @return the y-coordinate of the drone's position as a double.
     */
    public double getYPos() { return yPos; }

    /**
     * Retrieves the remaining amount of water or fire suppression agent in the drone.
     *
     * @return the quantity of water as a double.
     */
    public double getWater() { return water; }

    /**
     * Retrieves the drone's battery level.
     *
     * @return battery level in percentage (0.0 – 100.0).
     */
    public double getBattery() { return battery; }

    /**
     * Retrieves the drone's fuel level.
     *
     * @return fuel level in percentage (0.0 – 100.0).
     */
    public double getFuel() { return fuel; }

    /**
     * Returns a string representation of the DroneUpdate object.
     * The string includes the drone's unique identifier, operational state,
     * current positional coordinates (x and y), and the remaining amount of water.
     *
     * @return a string representation of the DroneUpdate object.
     */
    @Override
    public String toString() {
        return "DroneUpdate{droneId=" + droneId + ", state=" + state
                + ", xPos=" + xPos + ", yPos=" + yPos + ", water=" + water
                + ", battery=" + String.format("%.1f", battery) + "%"
                + ", fuel=" + String.format("%.1f", fuel) + "%" + "}";
    }
}
