package DroneSwarmSim.scheduler;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Writes simulation events, drone telemetry, task assignments, queue depth
 * changes, and the final metrics summary to persistent log files on disk.
 * <p>
 * Log files are created in the directory supplied at construction time
 * (typically {@code logs/} relative to the working directory).  Five separate
 * files are written in parallel:
 * <ul>
 *   <li>{@code events.log}      – high-level scheduler events (dispatch, fault, etc.)</li>
 *   <li>{@code telemetry.log}   – per-step drone status updates</li>
 *   <li>{@code assignments.log} – task assignment / completion history</li>
 *   <li>{@code queue.log}       – incident-queue depth changes</li>
 *   <li>{@code metrics.log}     – final simulation metrics summary</li>
 * </ul>
 * <p>
 * Every line is prefixed with a wall-clock timestamp in {@code HH:mm:ss.SSS} format.
 * Files are opened in appended mode so that multiple simulation runs do not overwrite each other.
 * Call {@link #close()} (or use try-with-resources) to flush and close all writers when the simulation ends.
 */
public class SimulationLogger implements Closeable {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private final PrintWriter events;
    private final PrintWriter telemetry;
    private final PrintWriter assignments;
    private final PrintWriter queue;
    private final PrintWriter metrics;

    /**
     * Creates a {@code SimulationLogger} that writes log files inside {@code logDir}.
     * The directory (and any parent directories) is created if it does not already exist.
     *
     * @param logDir directory in which the five log files will be written
     * @throws IOException if the directory cannot be created or any file cannot be opened
     */
    public SimulationLogger(Path logDir) throws IOException {
        Files.createDirectories(logDir);

        events      = openWriter(logDir.resolve("events.log"));
        telemetry   = openWriter(logDir.resolve("telemetry.log"));
        assignments = openWriter(logDir.resolve("assignments.log"));
        queue       = openWriter(logDir.resolve("queue.log"));
        metrics     = openWriter(logDir.resolve("metrics.log"));
        String header = stamped("=== Simulation session started ===");

        for (PrintWriter pw : new PrintWriter[]{events, telemetry, assignments, this.queue, metrics}) {
            pw.println(header);
            pw.flush();
        }
    }

    /**
     * Appends a log entry to the events log file.
     *
     * @param message the log message to be appended to the events log.
     */
    public void logEvent(String message) {
        writeLine(events, message);
    }

    /**
     * Appends a telemetry log entry to {@code telemetry.log}.
     *
     * @param message the log message to be appended to the telemetry log.
     */
    public void logTelemetry(String message) {
        writeLine(telemetry, message);
    }

    /**
     * Appends a log entry to the assignment log file.
     *
     * @param message the log message to be appended to the assignment log.
     */
    public void logAssignment(String message) {
        writeLine(assignments, message);
    }

    /**
     * Appends a log entry to the queue log file.
     *
     * @param message the log message to be appended to the queue log.
     */
    public void logQueue(String message) {
        writeLine(queue, message);
    }

    /**
     * Appends the complete simulation-metrics summary to {@code metrics.log}.
     * Each line of {@code summary} is written as-is (no additional timestamp prefix).
     *
     * @param summary multi-line string produced by {@link SimulationMetrics#getSummaryString()}
     */
    public void logMetricsSummary(String summary) {
        metrics.println(stamped("=== FINAL METRICS SUMMARY ==="));
        metrics.println(summary);
        metrics.println(stamped("=== END SUMMARY ==="));
        metrics.flush();
    }

    /**
     * Opens a {@code PrintWriter} for the specified file path.
     * The writer is configured to append to the file if it exists and to use buffering for improved performance.
     *
     * @param p the path of the file to open for writing
     * @return a {@code PrintWriter} instance for writing to the specified file
     * @throws IOException if the file cannot be opened or accessed
     */
    private static PrintWriter openWriter(Path p) throws IOException { return new PrintWriter(new BufferedWriter(new FileWriter(p.toFile(), true))); }

    /**
     * Writes a log message to the specified {@code PrintWriter}, appending a timestamp to the message.
     * The writer is flushed after writing the message to ensure that all data is immediately written
     * to the underlying file or output stream.
     *
     * @param pw the {@code PrintWriter} to which the message will be written
     * @param message the log message to be written, which will be prefixed with a timestamp
     */
    private void writeLine(PrintWriter pw, String message) {
        pw.println(stamped(message));
        pw.flush();
    }

    /**
     * Creates a stamped log message by appending the current timestamp in the predefined format to the provided message.
     *
     * @param message the log message to be stamped with a timestamp
     * @return a string containing the current timestamp followed by the provided message
     */
    private static String stamped(String message) { return "[" + TS_FORMAT.format(Instant.now()) + "] " + message; }

    /**
     * Flushes and closes all log-file writers.
     * After this call the logger must not be used.
     */
    @Override
    public void close() {
        for (PrintWriter pw : new PrintWriter[]{events, telemetry, assignments, queue, metrics}) {
            if (pw != null) {
                pw.flush();
                pw.close();
            }
        }
    }
}
