package DroneSwarmSim.drone;

import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.model.Severity;

/** Scheduler-side state for a registered drone. */
public class DroneInfo {
    private final int droneId;
    private final String host;
    private final int port;
    private volatile DroneState state;
    private volatile double xPos;
    private volatile double yPos;
    private volatile double water;
    private volatile double battery;
    private volatile double fuel;
    private volatile int completedAssignments;
    private volatile Integer assignedZoneId;
    private volatile int zonesServiced;
    private volatile Severity assignedSeverity;
    private volatile double targetZoneCenterX;
    private volatile double targetZoneCenterY;
    private volatile IncidentReport currentIncident;
    private volatile long dispatchTimeMs;
    private volatile long lastUpdateTimeMs;
    private volatile boolean offline;

    /**
     * Constructs a new DroneInfo instance with the specified drone identifier,
     * host address, and port number. The initial state of the drone is set to IDLE.
     *
     * @param droneId the unique identifier for the drone.
     * @param host the hostname or IP address where the drone can be reached.
     * @param port the port number used for communication with the drone.
     */
    public DroneInfo(int droneId, String host, int port) {
        this.droneId = droneId;
        this.host = host;
        this.port = port;
        this.state = DroneState.IDLE;
        this.xPos = 0.0;
        this.yPos = 0.0;
        this.water = 15.0;
        this.battery = 100.0;
        this.fuel = 100.0;
        this.completedAssignments = 0;
        this.assignedZoneId = null;
        this.dispatchTimeMs = 0;
        this.lastUpdateTimeMs = System.currentTimeMillis();
        this.offline = false;
    }

    public int getDroneId() { return droneId; }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public DroneState getState() { return state; }

    public double getWater() { return water; }

    public double getBattery() { return battery; }

    public double getFuel() { return fuel; }

    public int getCompletedAssignments() { return completedAssignments; }

    public Integer getAssignedZoneId() { return assignedZoneId; }

    public void setState(DroneState state) { this.state = state; }

    /**
     * Updates the telemetry information for the drone, including its current state,
     * position, and resource levels (water, battery, and fuel). This method also
     * updates the timestamp of the last telemetry update.
     *
     * @param state   the current operational state of the drone, represented as a {@code DroneState} enum value.
     * @param xPos    the current x-coordinate of the drone's position.
     * @param yPos    the current y-coordinate of the drone's position.
     * @param water   the current water level of the drone.
     * @param battery the current battery level of the drone, as a percentage (0.0 – 100.0).
     * @param fuel    the current fuel level of the drone, as a percentage (0.0 – 100.0).
     */
    public void updateTelemetry(DroneState state, double xPos, double yPos,
                                double water, double battery, double fuel) {
        this.state   = state;
        this.xPos    = xPos;
        this.yPos    = yPos;
        this.water   = water;
        this.battery = battery;
        this.fuel    = fuel;
        this.lastUpdateTimeMs = System.currentTimeMillis();
    }

    public void assignZone(int zoneId) {
        this.assignedZoneId = zoneId;
    }

    public void clearAssignedZone() {
        this.assignedZoneId = null;
    }

    public void incrementCompletedAssignments() { completedAssignments++; }

    /**
     * Updates the drone's last-known position.
     *
     * @param xPos x-coordinate reported in the most recent DroneUpdate
     * @param yPos y-coordinate reported in the most recent DroneUpdate
     */
    public void updatePosition(double xPos, double yPos) {
        this.xPos = xPos;
        this.yPos = yPos;
    }

    public double getXPos() { return xPos; }

    public double getYPos() { return yPos; }

    public int getZonesServiced() { return zonesServiced; }

    public void incrementZonesServiced() { zonesServiced++; }

    public Severity getAssignedSeverity() { return assignedSeverity; }

    public void setAssignedSeverity(Severity severity) { this.assignedSeverity = severity; }

    public double getTargetZoneCenterX() { return targetZoneCenterX; }

    public double getTargetZoneCenterY() { return targetZoneCenterY; }

    /**
     * Records the centre coordinates of the zone this drone has been assigned to fly
     * towards.  Used when evaluating whether the drone passes through a newly reported
     * incident zone.
     *
     * @param x x-coordinate of the target zone centre
     * @param y y-coordinate of the target zone centre
     */
    public void setTargetZoneCenter(double x, double y) {
        this.targetZoneCenterX = x;
        this.targetZoneCenterY = y;
    }

    public IncidentReport getCurrentIncident() { return currentIncident; }

    public void setCurrentIncident(IncidentReport incident) { this.currentIncident = incident; }

    /**
     * Records the time (ms since epoch) when this drone was dispatched to its current task.
     * Used by the scheduler's timeout detector.
     *
     * @param timeMs system time in milliseconds
     */
    public void setDispatchTimeMs(long timeMs) { this.dispatchTimeMs = timeMs; }

    public long getDispatchTimeMs() { return dispatchTimeMs; }

    public long getLastUpdateTimeMs() { return lastUpdateTimeMs; }

    public boolean isOffline() { return offline; }

    /**
     * Marks this drone as permanently offline (e.g., after a NOZZLE_STUCK_OPEN hard fault).
     */
    public void markOffline() {
        this.offline = true;
        this.state = DroneState.FAULTED;
    }

    @Override
    public String toString() {
        return "DroneInfo{droneId=" + droneId + ", host='" + host
                + "', port=" + port + ", state=" + state
                + ", xPos=" + xPos + ", yPos=" + yPos
                + ", water=" + water
                + ", battery=" + String.format("%.1f", battery) + "%"
                + ", fuel=" + String.format("%.1f", fuel) + "%"
                + ", completedAssignments=" + completedAssignments
                + ", assignedZoneId=" + assignedZoneId
                + ", zonesServiced=" + zonesServiced + "}";
    }
}
