package DroneSwarmSim.fire;

import DroneSwarmSim.config.FireIncidentConfig;
import DroneSwarmSim.messaging.Ack;
import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.messaging.MessageType;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.UdpSender;
import DroneSwarmSim.scheduler.FaultTypes;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The FireIncidentSubsystem class is responsible for processing fire incident events
 * from a specified CSV file and transmitting them to a remote Scheduler system over UDP.
 * <p>
 * This class implements the Runnable interface, enabling it to be executed as a single
 * task or within a thread. The primary function is to read and parse fire incident event
 * data, validate the information, and send the data in a structured format to the Scheduler.
 * <p>
 * Key Features:
 * - Reads event data from a specified file, formatted as CSV with a specific structure.
 * - Parses the event data into instances of {@link IncidentReport}, validating its content and handling missing or malformed entries gracefully.
 * - Transmits structured event reports to the Scheduler using UDP connection details (host and port) provided during initialization.
 * - Logs the details of processed events and records a summary of the execution, including the number of events processed, elapsed time, and average processing time per event.
 * - Provides a default configuration using {@link FireIncidentConfig}, which provides values for the file path, Scheduler host, and Scheduler port.
 * <p>
 * Constructor Summary:
 * - FireIncidentSubsystem(String eventFilePath, String schedulerHost, int schedulerPort):
 *   Allows specification of custom file path and UDP communication parameters.
 * - FireIncidentSubsystem(): Uses default configurations defined in {@link FireIncidentConfig}.
 * <p>
 * Thread-Safe Design:
 * - The FireIncidentSubsystem handles its own lifecycle and communication within its run method.
 * - Interruption and error handling are managed properly to ensure smooth execution.
 * <p>
 * Internal Processing:
 * - Each line of the event file is parsed to extract relevant details like time, zone ID, event type,
 *   severity, and optional fault type.
 * - Fault types are handled with fallback defaults in case of missing or invalid data.
 * - A time delay is imposed between subsequent events to simulate realistic event dispatching.
 * <p>
 * Logging and Monitoring:
 * - Informational and error logs are used to provide detailed step-by-step progression.
 * - Summary logs include statistics such as total events processed and average processing time.
 */
public class FireIncidentSubsystem implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(FireIncidentSubsystem.class.getName());
    private static final String LOG_PREFIX = "[FireIncidentSubsystem] ";
    private static final DateTimeFormatter TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
            .optionalEnd()
            .toFormatter();
    private final String eventFilePath;
    private final String schedulerHost;
    private final int schedulerPort;

    /**
     * Constructs an instance of the FireIncidentSubsystem for handling fire incident events.
     * This subsystem reads fire incident events from a specified file, processes them,
     * and communicates the data to a scheduler service using UDP.
     *
     * @param eventFilePath  The path to the file containing fire incident event data.
     * @param schedulerHost  The hostname or IP address of the scheduler service for communication.
     * @param schedulerPort  The port number of the scheduler service for communication.
     */
    public FireIncidentSubsystem(String eventFilePath, String schedulerHost, int schedulerPort) {
        this.eventFilePath = eventFilePath;
        this.schedulerHost = schedulerHost;
        this.schedulerPort = schedulerPort;
    }

    /**
     * Default constructor for the FireIncidentSubsystem.
     * <p>
     * Initializes a new instance of the FireIncidentSubsystem using default configuration values
     * defined in the {@link FireIncidentConfig} class. These defaults include:
     * - The path to the fire incident event file.
     * - The hostname or IP address of the scheduler service.
     * - The port number of the scheduler service.
     * <p>
     * This constructor delegates to the parameterized constructor
     * {@link #FireIncidentSubsystem(String, String, int)} with these default values.
     */
    public FireIncidentSubsystem() { this( FireIncidentConfig.EVENT_FILE_PATH, FireIncidentConfig.SCHEDULER_HOST, FireIncidentConfig.SCHEDULER_PORT ); }

    /**
     * Executes the main logic for handling fire incident events.
     * This method reads incident data from a file, processes the data line by line,
     * and sends the corresponding incident reports to a scheduler service over UDP.
     * <p>
     * The file is expected to contain event data in a structured format, with the first
     * line being a header that is skipped. Each subsequent line is parsed into an
     * {@link IncidentReport} object. Valid reports are then sent to the scheduler service
     * with a delay between transmissions to manage flow control.
     * <p>
     * In case of errors during file reading, data transmission, or interruptions, appropriate
     * logging is performed, and the thread's interrupt status is set if interrupted.
     * <p>
     * Resources for file reading and UDP communication are managed using a try-with-resources block,
     * ensuring proper cleanup once processing completes.
     * <p>
     * Exceptions handled:
     * - {@link IOException}: For file access or network communication failures.
     * - {@link InterruptedException}: For handling thread interruptions during the processing.
     */
    @Override
    public void run () {
        try (BufferedReader reader = new BufferedReader(new FileReader(eventFilePath));
             UdpSender sender = new UdpSender(schedulerHost, schedulerPort)) {

            int eventCount = 0;
            long startMs = System.currentTimeMillis();
            String header = reader.readLine(); // skip header
            if (header == null) {
                LOGGER.log(Level.WARNING, "{0}Event file is empty: {1}", new Object[]{LOG_PREFIX, eventFilePath});
                return;
            }

            LocalTime previousEventTime = null;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                IncidentReport report = parseIncidentReport(line);
                if (report != null) {
                    previousEventTime = sleepUntilNextEvent(previousEventTime, report.time());
                    LOGGER.log(Level.INFO, "{0}Sending: {1}", new Object[]{LOG_PREFIX, report});
                    sender.send(PacketBuilder.build(report));
                    eventCount++;
                }
            }
            sender.send(PacketBuilder.build(new Ack(MessageType.INCIDENT_REPORT, true, "FIRE_INPUT_COMPLETE")));
            logSummary(eventCount, startMs);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, LOG_PREFIX + "Fire incident processing interrupted", e);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, LOG_PREFIX + "Failed to process fire incident events", e);
        }
    }

    /**
     * Parses a given string representing an incident report into an {@link IncidentReport} object.
     * The input string is expected to be a comma-separated line containing details such as the
     * timestamp, zone ID, event type, severity, and optional fault type. If the input string is invalid
     * or insufficient to create an {@link IncidentReport}, this method returns {@code null}.
     *
     * @param line the input string containing the incident report data, structured in a
     *             comma-separated format with at least four components (time, zone ID,
     *             event type, and severity).
     * @return an {@link IncidentReport} object constructed from the given string, or {@code null}
     *         if the input string is invalid or lacks the necessary components.
     */
    private IncidentReport parseIncidentReport(String line) {
        String[] parts = line.split(",");
        if (parts.length < 4) { return null; }
        try {
            String time = parts[0].trim();
            int zoneId = Integer.parseInt(parts[1].trim());
            EventType eventType = EventType.valueOf(parts[2].trim());
            Severity severity = Severity.valueOf(parts[3].trim().toUpperCase());
            FaultTypes faultType = parseFaultType(parts);
            return new IncidentReport(time, zoneId, eventType, severity, faultType);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "{0}Skipping malformed incident report line: {1}",
                    new Object[]{LOG_PREFIX, line});
            return null;
        }
    }

    /**
     * Parses the fault type from the given array of string components.
     * The fault type is expected to be located at a specific index in the array.
     * If the fault type is missing, empty, or invalid, a default value of {@code FaultTypes.NONE} is returned.
     *
     * @param parts an array of strings containing components of incident data,
     *              where the fault type is expected to be found at index 4.
     *              The array must have a length of at least 5 for the fault type to be processed.
     * @return the parsed {@code FaultTypes} enum value if the fault type is valid,
     *         or {@code FaultTypes.NONE} if the type is missing, empty, or unrecognized.
     */
    private FaultTypes parseFaultType(String[] parts) {
        if (parts.length < 5 || parts[4].trim().isEmpty()) { return FaultTypes.NONE; }
        try { return FaultTypes.valueOf(parts[4].trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "{0}Unknown fault type: {1}", new Object[]{LOG_PREFIX, parts[4].trim()});
            return FaultTypes.NONE;
        }
    }

    /**
     * Replays the CSV event spacing on a compressed demo timeline.
     * The first event is sent immediately; later events sleep based on the delta
     * between consecutive timestamps multiplied by the configured scale factor.
     */
    private LocalTime sleepUntilNextEvent(LocalTime previousEventTime, String nextEventTime) throws InterruptedException {
        LocalTime currentEventTime = LocalTime.parse(nextEventTime.trim(), TIME_FORMATTER);
        if (previousEventTime != null) {
            long simulatedDelayMs = java.time.Duration.between(previousEventTime, currentEventTime).toMillis();
            long scaledDelayMs = Math.max(0L,
                    Math.round(simulatedDelayMs * (FireIncidentConfig.EVENT_TIME_SCALE_MS_PER_SIM_SECOND / 1000.0)));
            if (scaledDelayMs > 0L) {
                Thread.sleep(scaledDelayMs);
            }
        }
        return currentEventTime;
    }

    /**
     * Logs a summary of the fire incident processing execution, including total events processed,
     * elapsed time, and mean processing time per event.
     *
     * @param eventCount The number of events successfully processed during the execution.
     * @param startMs    The timestamp (in milliseconds) indicating the start of the event processing.
     */
    private void logSummary(int eventCount, long startMs) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        double elapsedSeconds = elapsedMs / 1000.0;
        double meanMsPerEvent = eventCount > 0 ? (double) elapsedMs / eventCount : 0.0;
        String summary = String.format(
                    "Processed %d events in %.2f s (mean %.1f ms/event).",
                    eventCount,
                    elapsedSeconds,
                    meanMsPerEvent
            );
        LOGGER.log(Level.INFO, "{0}Finished reading all events", LOG_PREFIX);
        LOGGER.log(Level.INFO, "{0}{1}", new Object[]{LOG_PREFIX, summary});
    }
}
