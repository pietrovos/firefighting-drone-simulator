package DroneSwarmSim.fire;

import DroneSwarmSim.model.Severity;
/**
 * Represents an incident of fire detected in a specific zone and its associated details.
 * This class is used to record and process information about fire incidents
 * detected by drones or reported manually in the drone swarm simulation system.
 * <p>
 * Instances of this class are immutable and provide information regarding
 * the time of the fire, the zone it occurred in, the type of event that triggered
 * the incident, and its severity.
 *
 * @param time      Time of incident (ex: "14:03:15")
 * @param zoneId    Zone of fire identifier (ex: 3)
 * @param eventType Detected or called in (FIRE_DETECTED || DRONE_REQUEST)
 * @param severity  Severity of fire (LOW || MODERATE || HIGH)
 */
public record FireIncident(String time, int zoneId, EventType eventType, Severity severity) {
    /**
     * Retrieves the time of the fire incident.
     *
     * @return the time of the incident as a string, typically formatted as "HH:mm:ss".
     */
    @Override
    public String time() {
        return time;
    }

    /**
     * Retrieves the identifier of the zone where the fire incident occurred.
     *
     * @return the zone identifier associated with the fire incident.
     */
    @Override
    public int zoneId() { return zoneId; }

    /**
     * Retrieves the type of event associated with the fire incident.
     *
     * @return the event type that triggered the fire incident, represented as an {@link EventType}.
     */
    @Override
    public EventType eventType() { return eventType; }

    /**
     * Retrieves the severity level of the fire incident.
     *
     * @return the severity of the fire incident, represented as a {@link Severity} enum.
     */
    @Override
    public Severity severity() { return severity; }

    /**
     * Returns a string representation of the FireIncident object.
     * The string contains the time, zone ID, event type, and severity of the fire incident
     * formatted in a human-readable form. For tracking and testing purposes in log files.
     *
     * @return a string describing the FireIncident object, including its time, zone ID,
     * event type, and severity.
     */
    @Override
    public String toString() { return "FireIncident{time='" + time + "', zoneId=" + zoneId + ", eventType=" + eventType + ", severity=" + severity + "}"; }
}
