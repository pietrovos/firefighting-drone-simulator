package droneswarmsim.benchmark;

import droneswarmsim.config.DroneConfig;
import droneswarmsim.config.FireIncidentConfig;
import droneswarmsim.config.SchedulerConfig;
import droneswarmsim.drone.DroneSubsystem;
import droneswarmsim.fire.FireIncidentSubsystem;
import droneswarmsim.scheduler.DispatchPolicy;
import droneswarmsim.scheduler.Scheduler;
import droneswarmsim.scheduler.SimulationMetrics;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one complete simulation without the dashboard: the scheduler, every drone, and
 * the fire incident playback start in this JVM and talk over local UDP exactly as the
 * three-process demo does. When the scheduler reports that every fire is out, the run's
 * metrics are printed as a single {@code RESULT,...} CSV line and the JVM exits.
 * <p>
 * Usage:
 * <pre>
 * HeadlessSimulation [policy=water-matched] [drones=20] [events=scenarios/Final_event_file_w26.csv]
 *                    [zones=scenarios/Final_zone_file_w26.csv] [schedulerPort=6000] [droneBasePort=7000]
 *                    [logDir=logs] [timeoutMinutes=30] [logLevel=WARNING]
 * </pre>
 */
public final class HeadlessSimulation {

    /** Metrics from one finished run. Times are in milliseconds. */
    public record Result(DispatchPolicy policy, int drones, boolean completed, long durationMs,
                         int firesReported, int firesExtinguished,
                         double avgResponseMs, long p95ResponseMs, long maxResponseMs,
                         double avgExtinguishMs, long p95ExtinguishMs, long maxExtinguishMs,
                         int trips, int dronesUsed, int faults, double fleetDistance) {

        static final String CSV_PREFIX = "RESULT,";

        String toCsv() {
            return CSV_PREFIX + String.join(",", policy.name(), Integer.toString(drones), Boolean.toString(completed),
                    Long.toString(durationMs), Integer.toString(firesReported), Integer.toString(firesExtinguished),
                    format(avgResponseMs), Long.toString(p95ResponseMs), Long.toString(maxResponseMs),
                    format(avgExtinguishMs), Long.toString(p95ExtinguishMs), Long.toString(maxExtinguishMs),
                    Integer.toString(trips), Integer.toString(dronesUsed), Integer.toString(faults),
                    format(fleetDistance));
        }

        static Result fromCsv(String line) {
            String[] f = line.substring(CSV_PREFIX.length()).split(",");
            return new Result(DispatchPolicy.valueOf(f[0]), Integer.parseInt(f[1]), Boolean.parseBoolean(f[2]),
                    Long.parseLong(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Double.parseDouble(f[6]), Long.parseLong(f[7]), Long.parseLong(f[8]),
                    Double.parseDouble(f[9]), Long.parseLong(f[10]), Long.parseLong(f[11]),
                    Integer.parseInt(f[12]), Integer.parseInt(f[13]), Integer.parseInt(f[14]),
                    Double.parseDouble(f[15]));
        }

        private static String format(double value) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
    }

    private HeadlessSimulation() { }

    public static void main(String[] args) throws InterruptedException {
        Map<String, String> options = parseOptions(args);
        Level logLevel = Level.parse(options.getOrDefault("logLevel", "WARNING"));
        Logger.getLogger("").setLevel(logLevel);
        for (var handler : Logger.getLogger("").getHandlers()) { handler.setLevel(logLevel); }

        Result result = run(
                DispatchPolicy.parse(options.getOrDefault("policy", SchedulerConfig.DISPATCH_POLICY.name())),
                Integer.parseInt(options.getOrDefault("drones", Integer.toString(DroneConfig.NUM_DRONES))),
                options.getOrDefault("events", FireIncidentConfig.EVENT_FILE_PATH),
                options.getOrDefault("zones", SchedulerConfig.ZONE_FILE_PATH),
                Integer.parseInt(options.getOrDefault("schedulerPort", Integer.toString(SchedulerConfig.RECEIVE_PORT))),
                Integer.parseInt(options.getOrDefault("droneBasePort", Integer.toString(DroneConfig.DRONE_BASE_PORT))),
                Paths.get(options.getOrDefault("logDir", "logs")),
                Long.parseLong(options.getOrDefault("timeoutMinutes", "30")) * 60_000);

        System.out.println(result.toCsv());
        // Drone threads wait for tasks indefinitely, so end the JVM explicitly.
        System.exit(result.completed() ? 0 : 2);
    }

    /**
     * Runs one simulation and blocks until every fire is out or the timeout passes.
     *
     * @param policy        the scheduler's dispatch policy
     * @param drones        number of drone threads to start
     * @param eventFile     fire event CSV to replay
     * @param zoneFile      zone definition CSV
     * @param schedulerPort UDP port for the scheduler
     * @param droneBasePort UDP port of drone 0; drone {@code i} listens on {@code droneBasePort + i}
     * @param logDir        directory for the scheduler's log files
     * @param timeoutMs     how long to wait for the scenario to finish
     * @return the run's metrics; {@link Result#completed()} is false unless every reported fire was put out
     */
    public static Result run(DispatchPolicy policy, int drones, String eventFile, String zoneFile,
                             int schedulerPort, int droneBasePort, Path logDir, long timeoutMs)
            throws InterruptedException {
        Scheduler scheduler = new Scheduler(schedulerPort, zoneFile, null,
                30_000, policy, logDir);
        Thread schedulerThread = new Thread(scheduler, "scheduler");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        Thread.sleep(500);

        for (int droneId = 0; droneId < drones; droneId++) {
            Thread drone = new Thread(new DroneSubsystem(droneId, "localhost", schedulerPort, droneBasePort + droneId),
                    "drone-" + droneId);
            drone.setDaemon(true);
            drone.start();
        }
        Thread.sleep(500);

        Thread fire = new Thread(new FireIncidentSubsystem(eventFile, "localhost", schedulerPort), "fire-incident-subsystem");
        fire.setDaemon(true);
        fire.start();

        schedulerThread.join(timeoutMs);

        // The scheduler thread also ends early if it cannot bind its port, so only a run
        // that put out every reported fire counts as complete.
        SimulationMetrics m = scheduler.getMetrics();
        boolean completed = !schedulerThread.isAlive()
                && m.getFiresReported() > 0
                && m.getFiresExtinguished() == m.getFiresReported();
        List<Long> responses = m.getResponseTimesMs();
        List<Long> extinguishes = m.getExtinguishTimesMs();
        return new Result(policy, drones, completed, m.getSimulationDurationMs(),
                m.getFiresReported(), m.getFiresExtinguished(),
                m.getAverageResponseTimeMs(), percentile(responses, 95), m.getMaxResponseTimeMs(),
                m.getAverageExtinguishTimeMs(), percentile(extinguishes, 95), m.getMaxExtinguishTimeMs(),
                m.getTotalTripsCompleted(), m.getDronesUsed(), m.getTotalFaults(), m.getTotalFleetDistance());
    }

    /** Nearest-rank percentile; returns 0 for an empty list. */
    static long percentile(List<Long> values, int pct) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(pct / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Expected key=value, got: " + arg);
            }
            options.put(arg.substring(0, eq), arg.substring(eq + 1));
        }
        return options;
    }
}
