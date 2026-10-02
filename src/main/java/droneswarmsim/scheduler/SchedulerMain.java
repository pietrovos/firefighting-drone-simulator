package droneswarmsim.scheduler;

import droneswarmsim.ui.GUI;
import droneswarmsim.ui.RuntimeGUI;
import java.util.logging.Logger;

/**
 * The SchedulerMain class serves as the entry point for initiating the Scheduler process.
 * It creates a new thread to execute the Scheduler, allowing the scheduling logic
 * to run concurrently with other processes.
 */
public class SchedulerMain {
    private static final Logger LOGGER = Logger.getLogger(SchedulerMain.class.getName());

    public static void main(String[] args) throws Exception {
        LOGGER.info("[SchedulerMain] Starting Scheduler process");
        String zoneFilePath = args.length > 0
                ? args[0]
                : droneswarmsim.config.SchedulerConfig.ZONE_FILE_PATH;

        // Bootstrap scheduler to load zones from the same zone file as the running scheduler
        Scheduler bootstrapScheduler = new Scheduler(zoneFilePath, null);
        GUI[] window = new GUI[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            window[0] = new RuntimeGUI(bootstrapScheduler.getZones());
            window[0].start();
        });
        GUI gui = window[0];

        DispatchPolicy dispatchPolicy = args.length > 1
                ? DispatchPolicy.parse(args[1])
                : droneswarmsim.config.SchedulerConfig.DISPATCH_POLICY;
        LOGGER.info("[SchedulerMain] Dispatch policy: " + dispatchPolicy);
        Scheduler scheduler = new Scheduler(zoneFilePath, gui, dispatchPolicy);
        Thread schedulerThread = new Thread(scheduler, "scheduler");
        schedulerThread.start();
    }
}
