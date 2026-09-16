package DroneSwarmSim.drone;

import DroneSwarmSim.config.DroneConfig;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * The DroneMain class serves as the entry point for the drone controller application.
 * It initializes and starts drone controller threads.
 * If a drone ID is passed as a command-line argument, only that drone is launched.
 * If no argument is provided, the default behaviour is to launch the configured fleet
 * using zero-based IDs.
 */
public class DroneMain {
    private static final Logger LOGGER = Logger.getLogger(DroneMain.class.getName()); // Logger for this class.
    private static final String USAGE = "Usage: DroneMain [droneId]"; // Usage message.

    /**
     * The main method serving as the entry point of the application.
     * Depending on the input arguments, it either launches a specific drone
     * or starts the default fleet of drones.
     *
     * @param args Command-line arguments where the first argument (if provided)
     *             represents the ID of the drone to be launched. If no argument
     *             is passed, the default fleet is started.
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            Integer droneId = parseDroneId(args[0]);
            if (droneId == null) {
                System.exit(1);
                return;
            }
            startDrone(droneId);
            return;
        }

        startDefaultFleet();
    }

    /**
     * Parses the provided raw argument to extract a valid drone ID.
     * The method ensures that the drone ID is a non-negative integer.
     * If the provided argument is invalid, an error is logged, and {@code null} is returned.
     *
     * @param rawArg The raw input string representing the drone ID.
     *               It must be a non-negative integer.
     * @return The parsed drone ID as an {@code Integer}, or {@code null} if the input is invalid.
     */
    private static Integer parseDroneId(String rawArg) {
        final int droneId;
        try { droneId = Integer.parseInt(rawArg); }
        catch (NumberFormatException e) {
            logInvalidArgument(rawArg, "must be a non-negative integer specifying the drone ID.");
            return null;
        }

        if (droneId < 0) {
            logInvalidArgument(rawArg, "drone ID must be a non-negative integer (got " + droneId + ").");
            return null;
        }

        return droneId;
    }

    /**
     * Logs a severe error indicating an invalid argument along with its associated message.
     * This method is typically used to notify users of incorrect input and provide guidance
     * on the correct usage of the application.
     *
     * @param rawArg The raw input argument that was determined to be invalid.
     * @param message A descriptive message explaining why the argument is invalid.
     */
    private static void logInvalidArgument(String rawArg, String message) {
        LOGGER.log(Level.SEVERE, "Invalid argument ''{0}'': {1}", new Object[]{rawArg, message});
        LOGGER.severe(USAGE);
    }

    /**
     * Starts a drone with the specified drone ID. This method initializes a new thread
     * associated with the drone's subsystem and begins its operation.
     *
     * @param droneId The unique identifier of the drone to be started. It must be a valid
     *                non-negative integer representing the specific drone.
     */
    private static void startDrone(int droneId) {
        LOGGER.log(Level.INFO, "Starting drone {0}", droneId);
        Thread droneThread = new Thread(new DroneSubsystem(droneId), "drone-" + droneId);
        droneThread.start();
    }

    /**
     * Starts the default fleet of drones as configured in the system.
     * This method retrieves the total number of drones from the configuration and
     * iteratively starts each drone by invoking their respective startup processes.
     * Logging is performed to indicate the initiation of the fleet and each individual drone.
     * <p>
     * The method relies on predefined configurations in the {@code DroneConfig} class,
     * such as {@code DroneConfig.NUM_DRONES}, to determine the number of drones to launch.
     * <p>
     * It is designed to be called when no specific drone is targeted, ensuring the
     * entire fleet is operational in a standard configuration.
     */
    private static void startDefaultFleet() {
        int numDrones = DroneConfig.NUM_DRONES;
        LOGGER.log(Level.INFO, "Starting default fleet of {0} drone(s)", numDrones);
        for (int droneId = 0; droneId < numDrones; droneId++) { startDrone(droneId); }
    }
}
