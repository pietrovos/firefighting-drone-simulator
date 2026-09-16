package DroneSwarmSim.drone;

import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.model.Severity;

/**
 * Represents information about an individual drone in the system, including its
 * identifier, host information, port, current state, position, and scheduling
 * metadata used for load balancing and path-through optimization.
 * <p>
 * This class encapsulates essential details about a drone, which can be used
 * for communication, monitoring, and task assignment purposes within the
 * drone swarm simulation system.
 */
public class DroneInfo {
    private final int droneId; // Unique identifier for the drone.
    private final String host; // Hostname or IP address where the drone can be reached.
    private final int port; // Port number used for communication with the drone.
    private volatile DroneState state; // Current operational state of the drone.
    private volatile double xPos; // Current x-coordinate of the drone's position.'
    private volatile double yPos; // Current y-coordinate of the drone's position.'
    private volatile double water; // Current water level of the drone.
    private volatile double battery; // Current battery level of the drone.
    private volatile double fuel; // Current fuel level of the drone.
    private volatile int completedAssignments; // Total number of assignments completed by this drone.
    private volatile Integer assignedZoneId; // ID of the zone currently assigned to the drone.
    private volatile int zonesServiced; // Total number of zones serviced by this drone.
    private volatile Severity assignedSeverity; // Severity of the incident currently assigned to the drone.
    private volatile double targetZoneCenterX; // X-coordinate of the centre of the zone the drone is heading towards.
    private volatile double targetZoneCenterY; // Y-coordinate of the centre of the zone the drone is heading towards.
    private volatile IncidentReport currentIncident; // Incident report currently assigned to this drone.
    private volatile long dispatchTimeMs; // Time (ms since epoch) when this drone was dispatched to its current task.
    private volatile long lastUpdateTimeMs; // Timestamp of the last telemetry update received from the drone.
    private volatile boolean offline; // Whether this drone has been taken offline due to a hard fault.

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

    /**
     * Retrieves the unique identifier of the drone.
     *
     * @return the unique drone ID as an integer.
     */
    public int getDroneId() { return droneId; }

    /**
     * Retrieves the hostname or IP address where the drone can be reached.
     *
     * @return the hostname or IP address as a String.
     */
    public String getHost() { return host; }

    /**
     * Retrieves the port number used for communication with the drone.
     *
     * @return the port number as an integer.
     */
    public int getPort() { return port; }

    /**
     * Retrieves the current operational state of the drone.
     *
     * @return the current state of the drone as a {@code DroneState} enum value.
     */
    public DroneState getState() { return state; }

    /**
     * Retrieves the current water level of the drone.
     *
     * @return the water level as a double.
     */
    public double getWater() { return water; }

    /**
     * Retrieves the drone's current battery level.
     *
     * @return battery level in percentage (0.0 – 100.0).
     */
    public double getBattery() { return battery; }

    /**
     * Retrieves the drone's current fuel level.
     *
     * @return fuel level in percentage (0.0 – 100.0).
     */
    public double getFuel() { return fuel; }

    /**
     * Retrieves the total number of assignments that this drone has completed.
     *
     * @return the number of completed assignments as an integer.
     */
    public int getCompletedAssignments() { return completedAssignments; }

    /**
     * Retrieves the ID of the zone currently assigned to the drone.
     *
     * @return the assigned zone ID as an {@code Integer}, or {@code null} if the drone has no assigned zone.
     */
    public Integer getAssignedZoneId() { return assignedZoneId; }

    /**
     * Updates the current operational state of the drone.
     *
     * @param state the new state to be assigned to the drone, represented as a {@code DroneState} enum value.
     */
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

    /**
     * Assigns the specified zone ID to the drone.
     *
     * @param zoneId the unique identifier of the zone to be assigned to the drone
     */
    public void assignZone(int zoneId) {
        this.assignedZoneId = zoneId;
    }

    /**
     * Clears the zone currently assigned to the drone by setting the
     * `assignedZoneId` field to {@code null}. This indicates that the drone
     * is no longer associated with any specific zone.
     */
    public void clearAssignedZone() {
        this.assignedZoneId = null;
    }

    /**
     * Increments the count of completed assignments by 1.
     * <p>
     * This method updates the value of the 'completedAssignments' variable
     * by increasing its current value by one. It is typically used to track
     * progress or completion of assignments in a system or application.
     */
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

    /**
     * Returns the drone's last-known x-coordinate.
     *
     * @return x-coordinate
     */
    public double getXPos() { return xPos; }

    /**
     * Returns the drone's last-known y-coordinate.
     *
     * @return y-coordinate
     */
    public double getYPos() { return yPos; }

    /**
     * Returns the total number of zones that have been assigned to this drone.
     * Used by the scheduler for load-balancing decisions.
     *
     * @return zones-serviced count
     */
    public int getZonesServiced() { return zonesServiced; }

    /**
     * Increments the count of zones serviced by the entity.
     * This method increases the value of the internal field representing
     * the number of zones serviced by one.
     */
    public void incrementZonesServiced() { zonesServiced++; }

    /**
     * Returns the severity of the incident currently assigned to this drone,
     * or {@code null} if the drone is idle.
     *
     * @return current assigned severity
     */
    public Severity getAssignedSeverity() { return assignedSeverity; }

    /**
     * Sets the severity of the incident currently assigned to this drone.
     *
     * @param severity assigned severity, or {@code null} to clear
     */
    public void setAssignedSeverity(Severity severity) { this.assignedSeverity = severity; }

    /**
     * Returns the x-coordinate of the centre of the zone the drone is currently
     * heading towards (used for path-through optimization).
     *
     * @return target zone centre x
     */
    public double getTargetZoneCenterX() { return targetZoneCenterX; }

    /**
     * Returns the y-coordinate of the centre of the zone the drone is currently
     * heading towards (used for path-through optimization).
     *
     * @return target zone centre y
     */
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

    /**
     * Returns the incident report currently assigned to this drone, or {@code null}
     * if the drone is idle.  Stored so the scheduler can re-queue the original
     * incident if the drone is redirected mid-flight.
     *
     * @return current incident report
     */
    public IncidentReport getCurrentIncident() { return currentIncident; }

    /**
     * Stores the incident report that corresponds to the drone's current assignment.
     *
     * @param incident the incident being handled, or {@code null} to clear
     */
    public void setCurrentIncident(IncidentReport incident) { this.currentIncident = incident; }

    /**
     * Records the time (ms since epoch) when this drone was dispatched to its current task.
     * Used by the scheduler's timeout detector.
     *
     * @param timeMs system time in milliseconds
     */
    public void setDispatchTimeMs(long timeMs) { this.dispatchTimeMs = timeMs; }

    /**
     * Returns the dispatch timestamp in milliseconds, or 0 if the drone is not on a mission.
     *
     * @return dispatch time in ms since epoch
     */
    public long getDispatchTimeMs() { return dispatchTimeMs; }

    /**
     * Returns the timestamp (ms since epoch) of the most recent DroneUpdate received.
     *
     * @return last update time in ms since epoch
     */
    public long getLastUpdateTimeMs() { return lastUpdateTimeMs; }

    /**
     * Returns whether this drone has been taken offline due to a hard fault.
     *
     * @return {@code true} if the drone is permanently offline
     */
    public boolean isOffline() { return offline; }

    /**
     * Marks this drone as permanently offline (e.g., after a NOZZLE_STUCK_OPEN hard fault).
     */
    public void markOffline() {
        this.offline = true;
        this.state = DroneState.FAULTED;
    }

    /**
     * Returns a string representation of the DroneInfo object. The string includes
     * the drone's unique identifier, host address, port, and current state.
     *
     * @return a string representation of the DroneInfo object.
     */
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
