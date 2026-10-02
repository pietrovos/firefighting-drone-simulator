package droneswarmsim.scheduler;

import droneswarmsim.fire.EventType;
import droneswarmsim.messaging.AssignTask;
import droneswarmsim.messaging.DroneRegister;
import droneswarmsim.messaging.IncidentReport;
import droneswarmsim.messaging.MessageType;
import droneswarmsim.model.Severity;
import droneswarmsim.net.udp.PacketBuilder;
import droneswarmsim.net.udp.PacketParser;
import droneswarmsim.net.udp.UdpReceiver;
import droneswarmsim.net.udp.UdpSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link DispatchPolicy}: how many drones the scheduler sends to one fire.
 * <p>
 * Mock drones are plain UDP receivers that record every {@link AssignTask} they get and
 * never report back, so each drone stays EN_ROUTE after its first assignment.
 */
@DisplayName("Water-Matched Dispatch Tests")
class WaterMatchedDispatchTest {

    private static final long SETTLE_MS = 2_500;

    private final List<UdpReceiver> mockDrones = new ArrayList<>();
    private Thread schedulerThread;

    @AfterEach
    void tearDown() {
        if (schedulerThread != null) { schedulerThread.interrupt(); }
        mockDrones.forEach(UdpReceiver::close);
    }

    private File createZoneFile() throws Exception {
        File f = File.createTempFile("wm_zones", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            pw.println("1,(0;0),(100;100)");
            pw.println("2,(100;0),(200;100)");
        }
        return f;
    }

    /**
     * Starts a scheduler with the given policy, registers {@code droneCount} mock drones,
     * sends one incident, and returns every task the drones received.
     */
    private List<AssignTask> dispatch(int schedulerPort, DispatchPolicy policy, int droneCount,
                                      IncidentReport incident) throws Exception {
        List<AssignTask> tasks = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < droneCount; i++) {
            UdpReceiver drone = new UdpReceiver(schedulerPort + 1 + i, data -> {
                if (PacketParser.getType(data) == MessageType.ASSIGN_TASK) {
                    tasks.add(PacketParser.parseAssignTask(data));
                }
            });
            drone.start();
            mockDrones.add(drone);
        }

        Scheduler scheduler = new Scheduler(schedulerPort, createZoneFile().getAbsolutePath(), null,
                Scheduler.DEFAULT_DRONE_TIMEOUT_MS, policy, Files.createTempDirectory("wm_logs"));
        schedulerThread = new Thread(scheduler);
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        Thread.sleep(200);

        try (UdpSender sender = new UdpSender("localhost", schedulerPort)) {
            for (int i = 0; i < droneCount; i++) {
                sender.send(PacketBuilder.build(new DroneRegister(i + 1, "localhost", schedulerPort + 1 + i)));
            }
            Thread.sleep(200);
            sender.send(PacketBuilder.build(incident));
        }
        Thread.sleep(SETTLE_MS);
        return new ArrayList<>(tasks);
    }

    @ParameterizedTest(name = "{0} fire needs {1} drone(s)")
    @CsvSource({"LOW, 1", "MODERATE, 2", "HIGH, 2"})
    @Timeout(15)
    @DisplayName("WATER_MATCHED sends enough drones to cover the fire's water")
    void waterMatchedSendsEnoughDrones(Severity severity, int expectedDrones) throws Exception {
        int port = 17000 + severity.ordinal() * 10;
        List<AssignTask> tasks = dispatch(port, DispatchPolicy.WATER_MATCHED, 4,
                new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, severity));

        assertEquals(expectedDrones, tasks.size(),
                "15 L drones should cover " + severity.getReqLitresOfWater() + " L with " + expectedDrones + " drone(s)");
        assertEquals(expectedDrones, tasks.stream().map(AssignTask::droneId).distinct().count(),
                "Each drone should be assigned once");
        assertTrue(tasks.stream().allMatch(t -> t.zoneId() == 1), "All drones should fly to zone 1");
    }

    @Test
    @Timeout(15)
    @DisplayName("SINGLE_DRONE sends one drone even when the fire needs two loads")
    void singleDroneSendsOneDrone() throws Exception {
        List<AssignTask> tasks = dispatch(17040, DispatchPolicy.SINGLE_DRONE, 4,
                new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.HIGH));

        assertEquals(1, tasks.size(), "Only one drone should be in flight to the zone");
    }

    @Test
    @Timeout(15)
    @DisplayName("WATER_MATCHED injects an incident's fault into only one drone")
    void faultGoesToOneDrone() throws Exception {
        List<AssignTask> tasks = dispatch(17050, DispatchPolicy.WATER_MATCHED, 4,
                new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.HIGH, FaultTypes.NOZZLE_STUCK_CLOSED));

        assertEquals(2, tasks.size());
        assertEquals(1, tasks.stream().filter(t -> t.faultType() == FaultTypes.NOZZLE_STUCK_CLOSED).count(),
                "Exactly one drone should carry the injected fault");
    }

    @Test
    @Timeout(15)
    @DisplayName("WATER_MATCHED never redirects a drone to the zone it is already flying to")
    void noSelfRedirectWhileWaitingForDrones() throws Exception {
        // One drone cannot cover a HIGH fire, so the scheduler keeps waiting for another.
        // The drone already flying there must not be "redirected" to the same zone.
        List<AssignTask> tasks = dispatch(17060, DispatchPolicy.WATER_MATCHED, 1,
                new IncidentReport("10:00", 1, EventType.FIRE_DETECTED, Severity.HIGH));

        assertEquals(1, tasks.size(), "The only drone should be assigned exactly once");
    }

    @Test
    @DisplayName("DispatchPolicy.parse accepts kebab-case and any letter case")
    void parsePolicyNames() {
        assertEquals(DispatchPolicy.WATER_MATCHED, DispatchPolicy.parse("water-matched"));
        assertEquals(DispatchPolicy.SINGLE_DRONE, DispatchPolicy.parse(" Single_Drone "));
        assertThrows(IllegalArgumentException.class, () -> DispatchPolicy.parse("nearest"));
    }
}
