package DroneSwarmSim.fire;

import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * Entry point for the Fire Incident Subsystem in the Drone Swarm Simulation system.
 * This class is responsible for initializing and starting the FireIncidentSubsystem.
 * <p>
 * The FireIncidentMain class creates an instance of the FireIncidentSubsystem,
 * either with default configuration values or with the parameters provided as
 * a command-line argument. It then starts the subsystem as a separate thread
 * to process fire incident events asynchronously.
 * <p>
 * Usage Notes:
 * - The subsystem reads fire incident events from a CSV file.
 * - It sends processed event data to a scheduler service using UDP communication.
 * - The subsystem parameters include the event file path, scheduler host, and port,
 *   which can either be provided explicitly or default to values specified in
 *   FireIncidentConfig.
 * <p>
 * Responsibilities:
 * - Initialize the `FireIncidentSubsystem` with configuration details.
 * - Start the subsystem in a dedicated thread.
 * - Provide an entry point for the standalone execution of the Fire Incident Subsystem.
 */
public class FireIncidentMain {
    private static final Logger LOGGER = Logger.getLogger(FireIncidentMain.class.getName());

    public static void main(String[] args) {
        LOGGER.log(Level.INFO, "Starting FireIncidentSubsystem process");
        FireIncidentSubsystem subsystem = args.length > 0 ? new FireIncidentSubsystem(args[0],
                        DroneSwarmSim.config.FireIncidentConfig.SCHEDULER_HOST,
                        DroneSwarmSim.config.FireIncidentConfig.SCHEDULER_PORT) : new FireIncidentSubsystem();
        Thread thread = new Thread(subsystem, "fire-incident-subsystem");
        thread.start();
    }
}