package DroneSwarmSim.scheduler;

import DroneSwarmSim.ui.GUI;
import DroneSwarmSim.ui.RuntimeGUI;
import java.util.logging.Logger;

/**
 * The SchedulerMain class serves as the entry point for initiating the Scheduler process.
 * It creates a new thread to execute the Scheduler, allowing the scheduling logic
 * to run concurrently with other processes.
 */
public class SchedulerMain {
    private static final Logger LOGGER = Logger.getLogger(SchedulerMain.class.getName());

    public static void main(String[] args) {
        LOGGER.info("[SchedulerMain] Starting Scheduler process");
        String zoneFilePath = args.length > 0
                ? args[0]
                : DroneSwarmSim.config.SchedulerConfig.ZONE_FILE_PATH;

        // Bootstrap scheduler to load zones from the same zone file as the running scheduler
        Scheduler bootstrapScheduler = new Scheduler(zoneFilePath, null);
        GUI gui = new RuntimeGUI(bootstrapScheduler.getZones());
        gui.start();

        Scheduler scheduler = new Scheduler(zoneFilePath, gui);
        Thread schedulerThread = new Thread(scheduler, "scheduler");
        schedulerThread.start();
    }
}
