package DroneSwarmSim.config;
/**
 * Configuration class for drone-related constants in the Drone Swarm Simulation system.
 * <p>
 * This class provides configuration values used by the drones for communication and operation within the system.
 */
public class DroneConfig {
    public static final String SCHEDULER_HOST = "localhost"; // Default scheduler host.
    public static final int SCHEDULER_PORT = SchedulerConfig.RECEIVE_PORT; // Default scheduler port.
    /** Each drone instance listens on DRONE_BASE_PORT + droneId. */
    public static final int DRONE_BASE_PORT = 7000; // Base port for drone communication.
    /** Default number of drone instances to launch when no argument is provided. */
    public static final int NUM_DRONES = 20; // Default number of drones to launch.
    public static final double AGENT_CAPACITY_LITRES = 15.0; // Default agent capacity.
    public static final double BATTERY_FLIGHT_DRAIN_RATE_PCT_PER_SEC = 0.5; // 0.5 percentage points drain per second.
    public static final double BATTERY_NOZZLE_OPEN_PCT = 1.5; // 1.5 percentage points nozzle-open battery usage.
    public static final double BATTERY_NOZZLE_CLOSE_PCT = 1.0; // 1.0 percentage points nozzle-close battery usage.
    public static final double FUEL_PER_METRE_PCT = 0.005; // 0.005 percentage points fuel per metre.
    public static final double FUEL_ACCELERATION_SURCHARGE_PCT = 2.0; // 2.0 percentage points fuel surcharge for acceleration.
    public static final double FUEL_DECELERATION_SURCHARGE_PCT = 1.0; // 1.0 percentage points fuel surcharge for deceleration.
    public static final long REFILL_DURATION_MS = 2_000; // 2 seconds.

    /**
     * Private constructor to prevent instantiation of the DroneConfig class.
     * <p>
     * DroneConfig is a utility class containing static configuration constants
     * for the Drone Swarm Simulation system. It is not intended to be instantiated.
     */
    private DroneConfig() { }
}
