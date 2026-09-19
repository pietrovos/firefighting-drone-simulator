package DroneSwarmSim.messaging;

import DroneSwarmSim.drone.DroneState;

/** Telemetry sent from a drone to the scheduler. */
public class DroneUpdate {
    private final int droneId;
    private final DroneState state;
    private final double xPos;
    private final double yPos;
    private final double water;
    private final double battery;
    private final double fuel;

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

    public DroneUpdate(int droneId, DroneState state, double xPos, double yPos, double water) { this(droneId, state, xPos, yPos, water, 100.0, 100.0); }

    public int getDroneId() { return droneId; }

    public DroneState getState() { return state; }

    public double getXPos() { return xPos; }

    public double getYPos() { return yPos; }

    public double getWater() { return water; }

    public double getBattery() { return battery; }

    public double getFuel() { return fuel; }

    @Override
    public String toString() {
        return "DroneUpdate{droneId=" + droneId + ", state=" + state
                + ", xPos=" + xPos + ", yPos=" + yPos + ", water=" + water
                + ", battery=" + String.format("%.1f", battery) + "%"
                + ", fuel=" + String.format("%.1f", fuel) + "%" + "}";
    }
}
