package DroneSwarmSim.messaging;

import DroneSwarmSim.model.Severity;
import DroneSwarmSim.scheduler.FaultTypes;
/**
 * Represents a task assigned to a drone for fire-extinguishing operations.
 * This class encapsulates the details of the assignment, including the drone's ID,
 * the zone to be managed, the severity of the incident requiring attention, and
 * an optional fault to inject into the drone for testing purposes.
 */
public record AssignTask(int droneId, int zoneId,
                         int zoneXOrigin, int zoneYOrigin, int zoneXEnd, int zoneYEnd,
                         Severity severity, FaultTypes faultType) {

    /**
     * Backward-compatible constructor that defaults {@code faultType} to {@link FaultTypes#NONE}.
     *
     * @param droneId      the drone to assign
     * @param zoneId       the zone ID
     * @param zoneXOrigin  zone origin x
     * @param zoneYOrigin  zone origin y
     * @param zoneWidth    zone xEnd
     * @param zoneLength   zone yEnd
     * @param severity     fire severity
     */
    public AssignTask(int droneId, int zoneId,
                      int zoneXOrigin, int zoneYOrigin, int zoneWidth, int zoneLength,
                      Severity severity) {
        this(droneId, zoneId, zoneXOrigin, zoneYOrigin, zoneWidth, zoneLength, severity, FaultTypes.NONE);
    }

    /**
     * Returns a string representation of the assigned task.
     * The string includes details about the drone ID, the zone ID,
     * and the severity level associated with the task.
     *
     * @return a string representing the assigned task
     */
    @Override
    public String toString() {
        return "AssignTask{droneId=" + droneId + ", zoneId=" + zoneId
                + ", severity=" + severity
                + (faultType != FaultTypes.NONE ? ", faultType=" + faultType : "") + "}";
    }
}
