package DroneSwarmSim.messaging;

import DroneSwarmSim.scheduler.FaultTypes;
/**
 * Represents a fault report sent from a drone to the Scheduler when the drone
 * detects or experiences a fault during task execution.
 * <p>
 * The Scheduler uses this message to:
 * - Mark the affected drone as faulty/offline.
 * - Reassign the pending fire incident to a healthy drone.
 * - Log timestamps for fault occurrence and reassignment.
 *
 * @param droneId   the ID of the drone reporting the fault
 * @param faultType the type of fault that occurred
 * @param message   a human-readable description of the fault
 */
public record FaultReport(int droneId, FaultTypes faultType, String message) {

    /**
     * Returns a string representation of the fault report.
     * The string includes details about the drone ID, the fault type,
     * and a human-readable description of the fault.
     *
     * @return a string representing the fault report
     */
    @Override
    public String toString() { return "FaultReport{droneId=" + droneId + ", faultType=" + faultType + ", message='" + message + "'}"; }
}
