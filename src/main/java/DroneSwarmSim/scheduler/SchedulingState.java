package DroneSwarmSim.scheduler;
/**
 * Represents the various states of a scheduling process.
 * <p>
 * This enum is used to define the logical stages in a scheduling workflow.
 * The states include:
 * - WAITING: Indicates that scheduling is waiting to be initiated or triggered.
 * - ASSIGNING: Represents the phase where tasks or resources are being assigned.
 * - MONITORING: Denotes the stage where ongoing schedules are being monitored.
 */
public enum SchedulingState { WAITING, ASSIGNING, MONITORING }
