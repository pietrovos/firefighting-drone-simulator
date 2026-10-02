package DroneSwarmSim.drone;

import DroneSwarmSim.model.Severity;
import DroneSwarmSim.model.Zone;
/**
 * Represents a task assigned to a drone, consisting of a specific zone to operate in
 * and the severity of the task based on the urgency or amount of resources required.
 * <p>
 * Tasks include important operational parameters used by the drone system for dispatch,
 * management, and execution of activities within the designated zone.
 */
public record DroneTask(int zoneId, Zone zone, Severity severity) {
    /**
     * Retrieves the unique identifier of the zone associated with this task.
     * The zone ID is used to specify the operational area assigned to the drone.
     *
     * @return the unique zone ID as an integer.
     */
    @Override
    public int zoneId() { return zoneId; }

    /**
     * Retrieves the {@code Zone} object associated with this task.
     * The {@code Zone} object represents the geographical area where the task is to be performed.
     *
     * @return the {@code Zone} object linked to this task, containing details such as zone ID, origin, xEnd, and yEnd.
     */
    @Override
    public Zone zone() { return zone; }

    /**
     * Retrieves the severity level of this task.
     * The severity indicates the urgency or amount of resources required
     * for completing the task, such as the litres of water needed to extinguish a fire.
     *
     * @return the {@code Severity} enum value representing the severity of this task.
     */
    @Override
    public Severity severity() { return severity; }

    /**
     * Returns a string representation of the DroneTask object.
     * The string includes the zone ID and severity level associated with the task.
     *
     * @return a string representation of the DroneTask object, formatted as
     * "DroneTask{zoneId=#, severity=#}" where # represents the values of the respective fields.
     */
    @Override
    public String toString() {
        return "DroneTask{zoneId=" + zoneId + ", severity=" + severity + "}";
    }
}
