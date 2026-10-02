package DroneSwarmSim.config;
/**
 * Configuration class containing constants for the Scheduler in the Drone Swarm Simulation system.
 * <p>
 * This class provides essential configuration values that dictate the network behaviour of the Scheduler
 * component, including the host and port used for UDP communication between the Scheduler and other
 * subsystems such as drones and the Fire Incident Subsystem.
 * <p>
 * Fields:
 * - HOST: The hostname or IP address where the Scheduler is accessible.
 * - RECEIVE_PORT: The port on which the Scheduler listens for incoming UDP messages. This value is
 *   referenced by various components across the system to transmit data to the Scheduler.
 */
public class SchedulerConfig {
    public static final String HOST = "localhost"; // Default scheduler host.
    public static final int RECEIVE_PORT = 6000; // Default scheduler port.
    public static final String ZONE_FILE_PATH = "src/Final_zone_file_w26.csv"; // Default zone file path.

    /**
     * Private constructor to prevent instantiation of the SchedulerConfig class.
     * <p>
     * SchedulerConfig is a utility class containing static configuration constants for the Scheduler in the Drone Swarm Simulation system.
     * It is not intended to be instantiated.
     * All configuration values should be accessed statically to ensure consistent usage across components.
     */
    private SchedulerConfig() { }
}
