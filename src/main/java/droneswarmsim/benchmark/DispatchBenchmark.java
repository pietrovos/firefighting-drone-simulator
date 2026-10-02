package droneswarmsim.benchmark;

import droneswarmsim.benchmark.HeadlessSimulation.Result;
import droneswarmsim.config.FireIncidentConfig;
import droneswarmsim.config.SchedulerConfig;
import droneswarmsim.scheduler.DispatchPolicy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.ToDoubleFunction;

/**
 * Compares {@link DispatchPolicy} options on the same fire scenario. Each run is a separate
 * {@link HeadlessSimulation} JVM on its own UDP ports, so runs can execute in parallel
 * without sharing scheduler or drone state. Results are averaged per policy and fleet size
 * and written as a Markdown report.
 * <p>
 * Usage:
 * <pre>
 * DispatchBenchmark [runs=3] [drones=10,20] [policies=single-drone,water-matched] [parallel=4]
 *                   [events=scenarios/Final_event_file_w26.csv] [zones=scenarios/Final_zone_file_w26.csv]
 *                   [out=docs/benchmarks/dispatch-policy.md]
 * </pre>
 */
public final class DispatchBenchmark {
    private static final int FIRST_PORT = 24_000;
    private static final int PORTS_PER_RUN = 200;

    private DispatchBenchmark() { }

    private record RunSpec(int index, DispatchPolicy policy, int drones) { }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = HeadlessSimulation.parseOptions(args);
        int runs = Integer.parseInt(options.getOrDefault("runs", "3"));
        int[] fleetSizes = Arrays.stream(options.getOrDefault("drones", "10,20").split(","))
                .mapToInt(s -> Integer.parseInt(s.trim())).toArray();
        List<DispatchPolicy> policies = Arrays.stream(
                        options.getOrDefault("policies", "single-drone,water-matched").split(","))
                .map(DispatchPolicy::parse).toList();
        int parallel = Integer.parseInt(options.getOrDefault("parallel", "4"));
        String events = options.getOrDefault("events", FireIncidentConfig.EVENT_FILE_PATH);
        String zones = options.getOrDefault("zones", SchedulerConfig.ZONE_FILE_PATH);
        Path out = Paths.get(options.getOrDefault("out", "docs/benchmarks/dispatch-policy.md"));

        List<RunSpec> specs = new ArrayList<>();
        for (int drones : fleetSizes) {
            for (DispatchPolicy policy : policies) {
                for (int r = 0; r < runs; r++) { specs.add(new RunSpec(specs.size(), policy, drones)); }
            }
        }

        System.out.printf("Running %d simulations, %d at a time...%n", specs.size(), parallel);
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        List<Future<Result>> futures = new ArrayList<>();
        for (RunSpec spec : specs) { futures.add(pool.submit(() -> runChild(spec, events, zones))); }
        List<Result> results = new ArrayList<>();
        for (Future<Result> future : futures) {
            Result result = future.get();
            System.out.println(result.toCsv());
            results.add(result);
        }
        pool.shutdown();

        String report = formatReport(results, fleetSizes, policies, runs, events);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("Wrote " + out);
    }

    private static Result runChild(RunSpec spec, String events, String zones) throws IOException, InterruptedException {
        int basePort = FIRST_PORT + spec.index() * PORTS_PER_RUN;
        String logDir = "target/benchmark-logs/" + spec.policy().name().toLowerCase(Locale.ROOT)
                + "-" + spec.drones() + "-" + spec.index();
        Process process = new ProcessBuilder(
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                HeadlessSimulation.class.getName(),
                "policy=" + spec.policy().name(),
                "drones=" + spec.drones(),
                "events=" + events,
                "zones=" + zones,
                "schedulerPort=" + basePort,
                "droneBasePort=" + (basePort + 1),
                "logDir=" + logDir)
                .redirectErrorStream(true)
                .start();

        Result result = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith(Result.CSV_PREFIX)) { result = Result.fromCsv(line); }
            }
        }
        int exit = process.waitFor();
        if (result == null) {
            throw new IllegalStateException("Run " + spec + " exited with " + exit + " and printed no result");
        }
        return result;
    }

    static String formatReport(List<Result> results, int[] fleetSizes, List<DispatchPolicy> policies,
                               int runs, String events) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Dispatch policy benchmark\n\n");
        sb.append(String.format(Locale.ROOT,
                "Generated %s by `DispatchBenchmark` from `%s`. Each row averages up to %d full runs of the real "
                        + "scheduler, drone threads, and UDP messaging in one headless JVM per run. "
                        + "Times are wall-clock seconds from the scheduler receiving a fire report. "
                        + "Averages include only completed runs, where every reported fire was put out.%n%n",
                LocalDate.now(), events, runs));
        sb.append("| Drones | Policy | Completed | Avg response | P95 response | Avg extinguish | P95 extinguish "
                + "| Max extinguish | Scenario time | Drones used | Fleet distance |\n");
        sb.append("| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (int drones : fleetSizes) {
            for (DispatchPolicy policy : policies) {
                List<Result> all = results.stream()
                        .filter(r -> r.drones() == drones && r.policy() == policy).toList();
                List<Result> group = all.stream().filter(Result::completed).toList();
                sb.append(String.format(Locale.ROOT,
                        "| %d | %s | %d/%d | %.1f s | %.1f s | %.1f s | %.1f s | %.1f s | %.0f s | %.1f | %.0f m |%n",
                        drones, policy.name(), group.size(), all.size(),
                        mean(group, Result::avgResponseMs) / 1000, mean(group, Result::p95ResponseMs) / 1000,
                        mean(group, Result::avgExtinguishMs) / 1000, mean(group, Result::p95ExtinguishMs) / 1000,
                        mean(group, Result::maxExtinguishMs) / 1000, mean(group, Result::durationMs) / 1000,
                        mean(group, Result::dronesUsed), mean(group, Result::fleetDistance)));
            }
        }
        sb.append("\n### Individual runs\n\n```text\n");
        results.forEach(r -> sb.append(r.toCsv().substring(Result.CSV_PREFIX.length())).append('\n'));
        sb.append("```\n\nColumns: policy, drones, completed, duration ms, fires reported, fires extinguished, "
                + "avg/p95/max response ms, avg/p95/max extinguish ms, trips, drones used, faults, fleet distance m.\n");
        return sb.toString();
    }

    private static double mean(List<Result> group, ToDoubleFunction<Result> metric) {
        return group.stream().mapToDouble(metric).average().orElse(0);
    }
}
