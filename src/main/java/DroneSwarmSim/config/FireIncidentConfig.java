package DroneSwarmSim.config;

import DroneSwarmSim.fire.FireIncidentSubsystem;

/**
 * Configuration class for constants used in the Fire Incident Subsystem.
 * <p>
 * This class provides configuration values that dictate the behaviour of the
 * Fire Incident Subsystem, including the path to the event data file and
 * default networking details for communication with the Scheduler.
 * <p>
 * Fields:
 * - EVENT_FILE_PATH: Path to the CSV file containing fire event data.
 * - SCHEDULER_HOST: Host address of the Scheduler for UDP communication.
 * - SCHEDULER_PORT: Port on which the Scheduler listens for incoming messages.
 * <p>
 * These constants are leveraged by {@link FireIncidentSubsystem} to initialize
 * its operation and communication parameters.
 */
public class FireIncidentConfig {
    public static final String EVENT_FILE_PATH = "src/Final_event_file_w26.csv";
    public static final String SCHEDULER_HOST = "localhost";
    public static final int SCHEDULER_PORT = SchedulerConfig.RECEIVE_PORT;
    /** Replay CSV timestamps on a compressed demo clock: 1 simulated second = 20 ms. */
    public static final long EVENT_TIME_SCALE_MS_PER_SIM_SECOND = 20L;

    /**
     * Private constructor to prevent instantiation of the FireIncidentConfig class.
     * <p>
     * This class is a utility class containing static configuration constants for the
     * Fire Incident Subsystem and is not meant to be instantiated. All configuration
     * values should be accessed statically.
     */
    private FireIncidentConfig() { }
}
