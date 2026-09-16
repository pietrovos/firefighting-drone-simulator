package DroneSwarmSim.messaging;

import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.scheduler.FaultTypes;
/**
 * Represents an incident report within the drone swarm simulation system.
 * This record encapsulates the details of a specific incident, including:
 * - The time of occurrence,
 * - The affected zone,
 * - The type of event,
 * - The severity of the incident, and
 * - An optional fault to inject into the drone assigned to handle this incident.
 * <p>
 * The information provided by this record can be used to track and monitor fire-related events
 * and their impact. It serves as a crucial data structure for effective response management
 * and decision-making in the context of the simulation.
 *
 * @param time      the timestamp indicating when the incident occurred
 * @param zoneId    the unique identifier of the zone where the incident took place
 * @param eventType the type of event associated with the incident, represented as an {@link EventType}
 * @param severity  the severity level of the incident, represented as a {@link Severity}
 * @param faultType the fault to inject into the assigned drone, or {@link FaultTypes#NONE} for normal operation
 */
public record IncidentReport(String time, int zoneId, EventType eventType, Severity severity, FaultTypes faultType) {

    /**
     * Backward-compatible constructor that defaults {@code faultType} to {@link FaultTypes#NONE}.
     *
     * @param time      the timestamp indicating when the incident occurred
     * @param zoneId    the unique identifier of the zone where the incident took place
     * @param eventType the type of event associated with the incident
     * @param severity  the severity level of the incident
     */
    public IncidentReport(String time, int zoneId, EventType eventType, Severity severity) { this(time, zoneId, eventType, severity, FaultTypes.NONE); }

    /**
     * Returns a string representation of the IncidentReport object.
     * The string includes details about the time of the incident, the affected zone ID,
     * the type of event, and the severity level.
     *
     * @return a string representing the IncidentReport object.
     */
    @Override
    public String toString() {
        return "IncidentReport{time='" + time + "', zoneId=" + zoneId
                + ", eventType=" + eventType + ", severity=" + severity
                + (faultType != FaultTypes.NONE ? ", faultType=" + faultType : "") + "}";
    }
}
