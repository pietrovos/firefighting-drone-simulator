package DroneSwarmSim.config;

public class FireIncidentConfig {
    public static final String EVENT_FILE_PATH = "src/Sample_event_file.csv";
    public static final String SCHEDULER_HOST = "localhost";
    public static final int SCHEDULER_PORT = SchedulerConfig.RECEIVE_PORT;
    // Replay CSV timestamps on a compressed clock: 1 simulated second = 20 ms.
    public static final long EVENT_TIME_SCALE_MS_PER_SIM_SECOND = 20L;

    private FireIncidentConfig() { }
}
