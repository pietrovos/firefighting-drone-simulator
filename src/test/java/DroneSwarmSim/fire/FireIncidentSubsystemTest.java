package DroneSwarmSim.fire;

import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.messaging.MessageType;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Unit tests for the FireIncidentSubsystem class.
 * <p>
 * These tests verify the functionality of the FireIncidentSubsystem by simulating its behaviour
 * under various scenarios. The tests ensure correct handling of event files, expected behaviour for
 * different inputs, and accurate incident report generation and transmission over UDP.
 * <p>
 * Test methods included:
 * - Validating incident report generation from a CSV file with multiple events.
 * - Ensuring no packets are sent for an empty event file.
 * - Verifying proper handling of a file with a single event.
 * <p>
 * Dependencies:
 * - UdpReceiver: A utility used to capture packets transmitted by the subsystem.
 * - IncidentReport: A data structure parsed and validated during testing.
 * - PacketParser and MessageType: Used for parsing and identifying packet types.
 * <p>
 * Utility methods:
 * - createEventFile: Generates temporary CSV files with specified event data for testing purposes.
 * - startCapture: Initializes a UdpReceiver to collect transmitted incident packets during tests.
 * <p>
 * Annotations used:
 * - @DisplayName: Provides a descriptive name for the class under test.
 * - @Test: Marks individual test cases.
 * - @Timeout: Ensures that tests do not hang indefinitely and complete within a specified duration.
 */
@DisplayName("FireIncidentSubsystem Tests")
class FireIncidentSubsystemTest {

    /**
     * Creates a temporary CSV file containing event data and writes the specified data lines to it.
     * The file includes a header row and the provided event details, with each line written in CSV format.
     * The file is set to be deleted on JVM exit.
     *
     * @param dataLines Variable number of strings representing event details to be written to the CSV file.
     *                  Each string should represent a line of event data in CSV format.
     * @return A {@code File} object representing the temporary CSV file containing the event data.
     * @throws Exception If an error occurs while creating or writing to the temporary file.
     */
    private File createEventFile(String... dataLines) throws Exception {
        File f = File.createTempFile("events", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Time,Zone ID,Event type,Severity");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Starts capturing UDP packets on the specified port and processes them to detect incident reports.
     * The method creates a {@link UdpReceiver}, which listens for incoming packets and processes each
     * packet with a handler function. If the packet is identified as an incident report, it is parsed
     * and added to the provided list. A countdown latch is decremented for each detected report.
     *
     * @param port the port on which the receiver will listen for UDP packets. Must be a valid port number (0-65535).
     * @param out the list to which parsed {@link IncidentReport} objects will be added if an incident report
     *            is detected in the received packets. This must not be {@code null}.
     * @param latch a {@link CountDownLatch} that will be decremented each time an incident report is detected.
     *              This must not be {@code null}.
     * @return a {@link UdpReceiver} instance that has been started and is actively receiving packets.
     *         The receiver must be properly stopped or closed when no longer needed.
     * @throws IOException if an error occurs while starting the {@link UdpReceiver}.
     */
    private UdpReceiver startCapture(int port, List<IncidentReport> out, CountDownLatch latch) throws IOException {
        UdpReceiver rx = new UdpReceiver(port, data -> {
            if (PacketParser.getType(data) == MessageType.INCIDENT_REPORT) {
                out.add(PacketParser.parseIncidentReport(data));
                latch.countDown();
            }
        });
        rx.start();
        return rx;
    }

    /**
     * Tests that the system correctly reads incident event data from a CSV file, processes the events,
     * and sends the corresponding incident reports via UDP to a predefined port.
     * <p>
     * The test verifies the following:
     * - Incident events from the CSV file are transmitted as {@link IncidentReport} objects.
     * - The number of transmitted reports matches the expected count.
     * - Each {@link IncidentReport} contains the expected field values based on the input CSV data.
     * <p>
     * Test Setup:
     * - A temporary CSV file with predefined incident event data is created using the {@code createEventFile} method.
     * - A mock UDP receiver is started on a specific port to capture the transmitted {@link IncidentReport} objects.
     * - A countdown latch is used to synchronize the test and ensure the expected number of packets is processed.
     * <p>
     * Assertions:
     * - Ensures that all expected incident reports are received within a given timeout.
     * - Validates the contents of each {@link IncidentReport} to confirm proper event parsing and field mapping.
     * <p>
     * Annotations:
     * - {@code @Test}: Specifies that this is a test case.
     * - {@code @Timeout(10)}: Enforces a maximum execution time of 10 seconds for the test.
     * <p>
     * @throws Exception if any error occurs while setting up the test or processing the incident events.
     */
    @Test
    @Timeout(10)
    void testSendsIncidentReportsFromCsv() throws Exception {
        int port = 15401;
        File f = createEventFile("14:00:00,1,FIRE_DETECTED,HIGH", "14:05:00,2,DRONE_REQUEST,LOW");

        List<IncidentReport> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        // Launches a subsystem; awaits receipt of two UDP reports within timeout
        try (UdpReceiver mockScheduler = startCapture(port, received, latch)) {
            Thread t = new Thread(new FireIncidentSubsystem(f.getAbsolutePath(), "localhost", port));
            t.setDaemon(true);
            t.start();

            assertTrue(latch.await(8, TimeUnit.SECONDS), "Both events should arrive within 8 s");
        }

        assertEquals(2, received.size());

        IncidentReport first = received.get(0);
        assertEquals("14:00:00", first.time());
        assertEquals(1, first.zoneId());
        assertEquals(EventType.FIRE_DETECTED, first.eventType());
        assertEquals(Severity.HIGH, first.severity());

        IncidentReport second = received.get(1);
        assertEquals("14:05:00", second.time());
        assertEquals(2, second.zoneId());
        assertEquals(EventType.DRONE_REQUEST, second.eventType());
        assertEquals(Severity.LOW, second.severity());
    }

    /**
     * Tests that when an event file containing only a header row (and no actual event data) is processed
     * by the FireIncidentSubsystem, no packets are transmitted via UDP.
     * <p>
     * The method verifies the following:
     * - The system does not send any {@link IncidentReport} objects when the input event file is empty.
     * - No UDP packets are captured by the mock receiver during the test execution.
     * <p>
     * Test setup:
     * - A temporary CSV file containing only a header row is created using the {@code createEventFile()} method.
     * - A mock UDP receiver is started on a specific port to capture any transmitted {@link IncidentReport} objects.
     * - A {@link CountDownLatch} is used to synchronize the test and detect if any packets are sent.
     * <p>
     * Assertions:
     * - Confirms that the countdown latch is not triggered, indicating no packets were sent.
     * - Validates that the list of received {@link IncidentReport} objects is empty.
     * <p>
     * Annotations:
     * - {@code @Test}: Specifies that this is a test case.
     * - {@code @Timeout(5)}: Ensures the test method completes within 5 seconds to avoid hanging.
     * <p>
     * @throws Exception if an error occurs while setting up the test or processing the event file.
     */
    @Test
    @Timeout(5)
    void testEmptyFileProducesNoPackets() throws Exception {
        int port = 15402;
        File f = createEventFile(); // header only

        List<IncidentReport> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Verifies no packets sent for empty file within timeout
        try (UdpReceiver mockScheduler = startCapture(port, received, latch)) {
            Thread t = new Thread(new FireIncidentSubsystem(f.getAbsolutePath(), "localhost", port));
            t.setDaemon(true);
            t.start();

            assertFalse(latch.await(2, TimeUnit.SECONDS), "No packets expected for empty file");
        }

        assertTrue(received.isEmpty(), "No IncidentReport should be sent for an empty event file");
    }

    /**
     * Verifies that the FireIncidentSubsystem processes a single event from a CSV file and sends
     * the corresponding {@link IncidentReport} to a predefined port via UDP.
     * <p>
     * The test sets up a temporary CSV file containing a single incident event, starts a mock UDP
     * receiver to capture the transmitted {@link IncidentReport}, and uses a countdown latch to
     * synchronize the test execution. The received incident is validated against the input data
     * to ensure proper parsing and event handling.
     * <p>
     * Test setup:
     * - A temporary CSV file with one incident event is created using {@code createEventFile}.
     * - A UDP receiver is started to collect incident reports sent by the FireIncidentSubsystem.
     * - A separate thread is used to execute the FireIncidentSubsystem.
     * <p>
     * Assertions:
     * - Confirms that the expected incident report is received within 8 seconds.
     * - Validates the contents of the received {@link IncidentReport}, including
     *   the zone ID and severity, to match the input event data.
     * <p>
     * Annotations:
     * - {@code @Test}: Marks this as a test case.
     * - {@code @Timeout(10)}: Ensures the test completes within 10 seconds.
     * <p>
     * @throws Exception if any error occurs during test setup, execution, or validation.
     */
    @Test
    @Timeout(10)
    void testSingleEventFile() throws Exception {
        int port = 15403;
        File f = createEventFile("09:30:00,3,FIRE_DETECTED,MODERATE");

        List<IncidentReport> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Launches a subsystem; awaits and verifies single event reception
        try (UdpReceiver mockScheduler = startCapture(port, received, latch)) {
            Thread t = new Thread(new FireIncidentSubsystem(f.getAbsolutePath(), "localhost", port));
            t.setDaemon(true);
            t.start();

            assertTrue(latch.await(8, TimeUnit.SECONDS), "Single event should arrive within 8 s");
        }

        assertEquals(1, received.size());
        assertEquals(3, received.get(0).zoneId());
        assertEquals(Severity.MODERATE, received.get(0).severity());
    }

    /**
     * UT-FIRE-CSV-04 – Malformed lines in the event CSV are skipped without
     * aborting processing; valid lines that follow a malformed one are still sent.
     * <p>
     * This test verifies the per-line {@code try/catch} added to
     * {@code FireIncidentSubsystem.parseIncidentReport}: a bad row (wrong number of
     * columns, unknown enum value, etc.) must be logged and skipped so that the
     * remaining valid events are dispatched normally.
     *
     * @throws Exception if an unexpected error occurs during test setup or execution
     */
    @Test
    @Timeout(10)
    void testMalformedLineSkippedAndValidLineStillSent() throws Exception {
        int port = 15404;
        // Row 2 is malformed (too few columns); rows 1 and 3 are valid.
        File f = createEventFile(
                "10:00:00,1,FIRE_DETECTED,HIGH",
                "THIS_IS_NOT_VALID",
                "10:01:00,2,FIRE_DETECTED,LOW"
        );

        List<IncidentReport> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        try (UdpReceiver mockScheduler = startCapture(port, received, latch)) {
            Thread t = new Thread(new FireIncidentSubsystem(f.getAbsolutePath(), "localhost", port));
            t.setDaemon(true);
            t.start();

            assertTrue(latch.await(8, TimeUnit.SECONDS),
                    "Both valid events should arrive even with a malformed line in between");
        }

        assertEquals(2, received.size(), "Only the two valid rows should produce reports");
        assertEquals(1, received.get(0).zoneId());
        assertEquals(2, received.get(1).zoneId());
    }
}
