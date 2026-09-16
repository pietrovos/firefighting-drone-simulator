package DroneSwarmSim.scheduler;

import DroneSwarmSim.config.SchedulerConfig;
import DroneSwarmSim.drone.DroneInfo;
import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.messaging.*;
import DroneSwarmSim.model.Zone;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import DroneSwarmSim.net.udp.UdpSender;
import DroneSwarmSim.ui.GUI;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * The Scheduler class is responsible for managing the assignment of drones to handle
 * fire incident reports within a simulation system. It listens for incoming messages,
 * processes incident reports, manages drone registry updates, and ensures that drones
 * are assigned to zones based on incident severity and drone availability.
 * <p>
 * This class implements the {@link Runnable} interface, allowing it to be executed in
 * its own thread. It listens for UDP packets, handles different message types such as
 * incident reports and drone updates, and enqueues or assigns tasks accordingly.
 * <p>
 * Key Responsibilities:
 * - Manage and process a queue of incident reports.
 * - Keep track of registered drones and their states.
 * - Assign idle drones to incidents using load-balancing (fewest zones serviced first).
 * - Redirect EN_ROUTE drones that pass through a newly reported zone of the same severity
 *   to minimize fire-response wait times.
 * - Handle communication with drones via UDP messaging.
 * <p>
 * Internal Workflow:
 * - The {@code run()} method starts a UDP receiver to listen for incoming messages.
 * - Incident reports are added to a queue for processing.
 * - Available drones are assigned to incidents based on the specified logic.
 * - Tasks are sent to drones using UDP communication.
 * <p>
 * Threading Model:
 * - Uses a {@link LinkedBlockingQueue} to queue incident reports for thread-safe access.
 * - Employs a {@link ConcurrentHashMap} to manage the registry of drones for thread-safe
 *   storage and updates.
 * <p>
 * State Management:
 * The {@code schedulingState} field indicates the current state of the scheduler:
 * - {@code WAITING}: Waiting for incidents.
 * - {@code ASSIGNING}: Actively assigning drones to incidents.
 * <p>
 * Error Handling:
 * - Handles interruptions during queuing and sleeping.
 * - Manages I/O errors and packet parsing errors gracefully.
 */
public class Scheduler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(Scheduler.class.getName());
    private static final DateTimeFormatter GUI_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    static final double PATH_TOLERANCE = 1.2; // 120% tolerance for path-through redirect
    static final long DEFAULT_DRONE_TIMEOUT_MS = 30_000; // 30 seconds
    static final int DRONE_STUCK_RECOVERY_SECONDS = 10; // 10 seconds

    // Queue of incoming fire incident reports waiting to be processed.
    private final LinkedBlockingQueue<IncidentReport> incidentQueue = new LinkedBlockingQueue<>();
    // Registry of connected drones keyed by droneId.
    private final Map<Integer, DroneInfo> droneRegistry = new ConcurrentHashMap<>();
    private final Map<Integer, IncidentReport> activeIncidentsByZone = new ConcurrentHashMap<>();
    private final Map<Integer, Deque<IncidentReport>> pendingIncidentsByZone = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> remainingWaterByZone = new ConcurrentHashMap<>();
    private final Map<Integer, Zone> zones = new HashMap<>(); // Zone definitions keyed by zone ID, loaded from config CSV.
    private final GUI gui;
    private final SimulationMetrics metrics = new SimulationMetrics();
    private volatile boolean fireInputComplete;
    private volatile boolean finalSummaryPrinted;
    private volatile boolean shutdownRequested;
    private volatile Thread schedulerThread;
    private SimulationLogger fileLogger; // may be null if log directory cannot be created.
    private SchedulingState schedulingState = SchedulingState.WAITING;
    private final int receivePort; // UDP port on which this Scheduler instance listens to.
    private final long droneTimeoutMs;

    /**
     * Creates a Scheduler with default settings: the port and zone file path are taken
     * from {@link DroneSwarmSim.config.SchedulerConfig}, no GUI is attached, and the default
     * drone-timeout interval is used.
     */
    public Scheduler() { this(SchedulerConfig.RECEIVE_PORT, SchedulerConfig.ZONE_FILE_PATH, null, DEFAULT_DRONE_TIMEOUT_MS); }

    /**
     * Creates a Scheduler that reads zones from the given file and updates the given GUI.
     * The UDP receive port and drone-timeout interval are taken from
     * {@link DroneSwarmSim.config.SchedulerConfig} defaults.
     *
     * @param zoneFilePath path to the zone CSV file
     * @param gui          the GUI instance to update; may be {@code null}
     */
    public Scheduler(String zoneFilePath, GUI gui) { this(SchedulerConfig.RECEIVE_PORT, zoneFilePath, gui, DEFAULT_DRONE_TIMEOUT_MS); }

    public Map<Integer, Zone> getZones() {
        return Collections.unmodifiableMap(zones);
    }


    /**
     * Public constructor primarily used in tests to supply a custom port and zone file.
     *
     * @param receivePort  UDP port to listen on
     * @param zoneFilePath path to the zone CSV file
     */
    public Scheduler(int receivePort, String zoneFilePath) {
        this(receivePort, zoneFilePath, null, DEFAULT_DRONE_TIMEOUT_MS);
    }

    /** Package-private for testing: create a Scheduler with all three parameters. */
    Scheduler(int receivePort, String zoneFilePath, GUI gui) {
        this(receivePort, zoneFilePath, gui, DEFAULT_DRONE_TIMEOUT_MS);
    }

    /** Package-private for testing: create a Scheduler with a custom timeout. */
    Scheduler(int receivePort, String zoneFilePath, GUI gui, long droneTimeoutMs) {
        this.receivePort = receivePort;
        this.gui = gui;
        this.droneTimeoutMs = droneTimeoutMs;
        loadZones(zoneFilePath);
        try {
            this.fileLogger = new SimulationLogger(Paths.get("logs"));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Scheduler] Could not open log directory: {0}", e.getMessage());
            this.fileLogger = null;
        }
    }

    /** Returns the number of zones loaded from the zone file (used in tests). */
    int getZoneCount() { return zones.size(); }

    /** Returns the Zone with the given id, or {@code null} if not found (used in tests). */
    Zone getZone(int id) { return zones.get(id); }

    /**
     * Loads zone definitions from a CSV file into the zone map.
     * <p>
     * The expected CSV format is:
     * <pre>Zone ID,Zone Start,Zone End
     * 1,(x1;y1),(x2;y2)
     * ...</pre>
     * where {@code (x1;y1)} is the zone origin and {@code (x2;y2)} are the end coordinates
     * (stored as the zone's xEnd and yEnd fields).
     *
     * @param filePath the path to the CSV file containing zone definitions.
     */
    private void loadZones(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line = reader.readLine(); // skip header
            if (line == null) return;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                int zoneId;
                try {
                    zoneId = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                int[] start = parseCoord(parts[1].trim());
                int[] end   = parseCoord(parts[2].trim());
                if (start != null && end != null) {
                    Zone zone = new Zone(zoneId, start[0], start[1], end[0], end[1]);
                    if (!zone.isGridAligned()) {
                        LOGGER.log(Level.WARNING, "[Scheduler] Skipping zone {0}: zones must have positive dimensions aligned to 100m grid cells: {1}", new Object[]{zoneId, zone});
                        continue;
                    }
                    zones.put(zoneId, zone);
                }
            }
            LOGGER.log(Level.INFO, "[Scheduler] Loaded {0} zone(s) from {1}", new Object[]{zones.size(), filePath});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Scheduler] Failed to load zone config from {0}: {1}", new Object[]{filePath, e.getMessage()});
        }
    }

    /**
     * Parses a coordinate string in the format {@code "(x;y)"} into an integer array.
     *
     * @param s the coordinate string to parse.
     * @return a two-element array {@code [x, y]}, or {@code null} if the string is malformed.
     */
    private int[] parseCoord(String s) {
        if (!s.startsWith("(") || !s.endsWith(")")) return null;
        s = s.substring(1, s.length() - 1);
        String[] parts = s.split(";");
        if (parts.length != 2) return null;
        try { return new int[]{ Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) }; }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * Starts the main scheduling loop for the Scheduler.
     * <p>
     * This method is executed when the Scheduler thread is started. It initializes a UDP receiver
     * to listen for incoming packets on the designated port. The Scheduler continuously monitors
     * the incident queue for incoming incident reports and processes them by assigning available drones.
     * <p>
     * The scheduling process includes the following steps:
     * - Transitioning to the WAITING state to indicate readiness.
     * - Waiting for an incident report to be submitted to the incident queue.
     * - Transitioning to the ASSIGNING state upon receiving an incident report.
     * - Assigning a drone to handle the incident via the `assignDrone` method.
     * - Adding a brief delay between operations to avoid excessive CPU usage.
     * <p>
     * If the process is interrupted, the thread's interrupt flag is set, and the loop ends gracefully.
     * In case of an I/O error while setting up the UDP receiver, the error message will be logged to the console.
     */
    @Override
    public void run() {
        schedulerThread = Thread.currentThread();
        try (UdpReceiver receiver = new UdpReceiver(receivePort, this::handlePacket)) {
            receiver.start();
            LOGGER.log(Level.INFO, "Listening on port {0}", receivePort);

            // Start the background timeout watcher
            ScheduledExecutorService timeoutWatcher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "scheduler-timeout-watcher");
                t.setDaemon(true);
                return t;
            });
            try {
                timeoutWatcher.scheduleAtFixedRate(this::checkDroneTimeouts, 5, 5, TimeUnit.SECONDS);

                // Start the metrics refresh timer (updates GUI every 2 seconds)
                ScheduledExecutorService metricsRefresher = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "scheduler-metrics-refresher");
                    t.setDaemon(true);
                    return t;
                });
                try {
                    metricsRefresher.scheduleAtFixedRate(this::refreshMetricsDisplay, 2, 2, TimeUnit.SECONDS);

                    while (!shutdownRequested) {
                        schedulingState = SchedulingState.WAITING;
                        LOGGER.info("Waiting for incidents...");
                        IncidentReport report;
                    try {
                            report = incidentQueue.take();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        metrics.recordQueueSample(incidentQueue.size()); // sample after take

                        if (!isCurrentQueueIncident(report)) {
                            if (gui != null) {
                                logQueueEvent("Discarded stale queued incident for zone " + report.zoneId());
                            }
                            continue;
                        }

                        schedulingState = SchedulingState.ASSIGNING;
                        LOGGER.log(Level.INFO, "Received incident, scheduling: {0}", report);
                        assignDrone(report);

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } finally { metricsRefresher.shutdownNow(); }
            } finally { timeoutWatcher.shutdownNow(); }
        } catch (IOException e) { LOGGER.log(Level.SEVERE, "IO error: {0}", e.getMessage()); }
        finally {
            if (fileLogger != null) {
                fileLogger.close();
                fileLogger = null;
            }
        }
    }

    /**
     * Updates the GUI metrics display with current stats.
     */
    private void refreshMetricsDisplay() { if (gui != null) { gui.updateMetrics(metrics.formatForDisplay()); } }

    /**
     * Processes a packet of data received by the Scheduler.
     * <p>
     * This method interprets the packet type and performs appropriate actions
     * based on the identified message type. Depending on the message type:
     * - INCIDENT_REPORT: Parses the incident report and adds it to the incident queue.
     * - DRONE_REGISTER: Registers a new drone and updates its information in the drone registry.
     * - DRONE_UPDATE: Updates the state and position of an existing drone in the drone registry.
     * If an unknown message type is encountered, it logs an unhandled message type warning.
     * Any errors encountered during processing are logged as error messages.
     *
     * @param data a byte array containing the serialized packet to be processed.
     */
    private void handlePacket(byte[] data) {
        try {
            MessageType type = PacketParser.getType(data);
            if (type == null) {
                LOGGER.warning("Ignoring malformed packet");
                if (gui != null) { logEvent("[FAULT] Ignored malformed packet"); }
                return;
            }
            switch (type) {
                case INCIDENT_REPORT -> {
                    IncidentReport report = PacketParser.parseIncidentReport(data);
                    if (report == null) { return; }
                    // Validate that the incident refers to a known zone before tracking it
                    if (!zones.containsKey(report.zoneId())) {
                        LOGGER.log(Level.INFO, "Ignoring incident report for unknown zone: {0}", report.zoneId());
                        if (gui != null) { logEvent("Ignoring incident for unknown zone " + report.zoneId()); }
                        return;
                    }
                    LOGGER.log(Level.INFO, "Received incident report: {0}", report);
                    metrics.markSimulationStart();
                    metrics.recordIncidentReported(report);
                    if (activeIncidentsByZone.containsKey(report.zoneId())) {
                        pendingIncidentsByZone
                                .computeIfAbsent(report.zoneId(), ignored -> new ConcurrentLinkedDeque<>())
                                .offer(report);
                        if (gui != null) {
                            logQueueEvent("Zone " + report.zoneId() + " already active; queued follow-up fire ("
                                    + report.severity() + ")");
                        }
                    } else {
                        activateIncident(report);
                    }
                }
                case DRONE_REGISTER -> {
                    DroneRegister reg = PacketParser.parseDroneRegister(data);
                    if (reg != null) {
                        LOGGER.log(Level.INFO, "Drone registered: {0}", reg);
                        droneRegistry.put(reg.droneId(),
                                new DroneInfo(reg.droneId(), reg.host(), reg.port()));
                        metrics.registerDrone(reg.droneId());
                        if (gui != null) { logEvent("Drone " + reg.droneId() + " registered on port " + reg.port()); }
                    }
                }
                case DRONE_UPDATE -> {
                    DroneUpdate update = PacketParser.parseDroneUpdate(data);
                    if (update == null) { return; }
                    LOGGER.log(Level.INFO, "Drone update: {0}", update);
                    DroneInfo info = droneRegistry.get(update.getDroneId());
                    if (info != null) { handleDroneUpdate(info, update); }
                }
                case FAULT_REPORT -> {
                    FaultReport faultReport = PacketParser.parseFaultReport(data);
                    if (faultReport == null) {
                        LOGGER.severe("Received malformed FAULT_REPORT");
                        return;
                    }
                    LOGGER.log(Level.INFO, "*** FAULT REPORT from drone {0}: {1}", new Object[]{faultReport.droneId(), faultReport});
                    handleFaultReport(faultReport);
                }
                case ACK -> {
                    Ack ack = PacketParser.parseAck(data);
                    if (ack != null && ack.inResponseTo() == MessageType.INCIDENT_REPORT
                            && "FIRE_INPUT_COMPLETE".equals(ack.message())) {
                        fireInputComplete = true;
                        maybePrintFinalMetricsSummary();
                    }
                }
                default -> LOGGER.log(Level.INFO, "Unhandled message type: {0}", type);
            }
        } catch (Exception e) { LOGGER.log(Level.SEVERE, "Error handling packet: {0}", e.getMessage()); }
    }

    /**
     * Assigns a drone to handle the specified incident report using two strategies:
     * <ol>
     *   <li><b>Path-through redirect</b> – if an EN_ROUTE drone is heading to a zone of the
     *       same severity and its flight path passes through the new incident zone, that drone
     *       is redirected to service the new zone first.  Its original zone is re-queued so
     *       that it will be handled afterwards by the same or another drone.</li>
     *   <li><b>Load-balanced idle assignment</b> – if no redirect candidate exists, the idle
     *       drone that has serviced the fewest zones so far is selected, spreading work evenly
     *       across the fleet.</li>
     * </ol>
     * If no drone is available at all, this method blocks in a loop (sleeping 1 s between polls)
     * until an IDLE drone becomes available, preserving FIFO ordering for the incident.
     *
     * @param report the incident report detailing the zone, severity, and other information
     *               associated with the incident to be handled by a drone
     */
    private void assignDrone(IncidentReport report) {
        Zone newZone = zones.get(report.zoneId());
        if (newZone == null) {
            LOGGER.log(Level.INFO, "Unknown zone id: {0}", report.zoneId());
            if (gui != null) { logEvent("Unknown zone id: " + report.zoneId()); }
            return;
        }

        double newZoneCenterX = newZone.centerX();
        double newZoneCenterY = newZone.centerY();
        boolean allowPathRedirect = report.faultType() == FaultTypes.NONE;

        while (true) {
            if (!isCurrentQueueIncident(report)) {
                return;
            }

            if (isZoneAlreadyAssigned(report.zoneId())) {
                try { Thread.sleep(250); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }

            // --- Strategy 1: redirect an EN_ROUTE drone that passes through the new zone ---
            DroneInfo redirectCandidate = null;
            if (allowPathRedirect) {
                redirectCandidate = droneRegistry.values().stream()
                        .filter(d -> d.getState() == DroneState.EN_ROUTE
                                && d.getAssignedSeverity() == report.severity()
                                && d.getCurrentIncident() != null
                                && d.getCurrentIncident().faultType() == FaultTypes.NONE
                                && passesThroughZone(d, newZoneCenterX, newZoneCenterY))
                        .findFirst()
                        .orElse(null);
            }

            if (redirectCandidate != null) {
                LOGGER.log(Level.INFO, "[Scheduler] Redirecting drone {0} from zone {1} to pass-through zone {2}",
                        new Object[]{redirectCandidate.getDroneId(),
                                redirectCandidate.getCurrentIncident() != null
                                        ? redirectCandidate.getCurrentIncident().zoneId() : "?",
                                newZone.zoneID()});

                // Re-queue the original incident so it will be serviced later.
                IncidentReport originalIncident = redirectCandidate.getCurrentIncident();
                if (originalIncident != null) {
                    incidentQueue.offer(originalIncident);
                    metrics.recordQueueSample(incidentQueue.size());
                    LOGGER.log(Level.INFO, "[Scheduler] Re-queued original incident: {0}", originalIncident);
                }

                sendTask(redirectCandidate, newZone, report);
                return;
            }

            // --- Strategy 2: pick the idle drone with fewest zones serviced (load balancing) ---
            // Ties are broken by proximity to the target zone, then by droneId, so that selection
            // is deterministic regardless of map-iteration order.
            DroneInfo idleDrone = droneRegistry.values().stream()
                    .filter(d -> d.getState() == DroneState.IDLE)
                    .min(Comparator.comparingInt(DroneInfo::getZonesServiced)
                            .thenComparingDouble(d -> distance(d.getXPos(), d.getYPos(),
                                    newZoneCenterX, newZoneCenterY))
                            .thenComparingInt(DroneInfo::getDroneId))
                    .orElse(null);

            if (idleDrone != null) {
                sendTask(idleDrone, newZone, report);
                return;
            }

            LOGGER.log(Level.INFO, "[Scheduler] No idle drones available for zone {0}; waiting to preserve order", report.zoneId());
            if (gui != null) { logQueueEvent("No idle drones available for zone " + report.zoneId() + "; waiting to preserve order"); }
            try { Thread.sleep(1000); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Determines whether the given drone's current flight path passes through a candidate
     * zone (represented by its centre coordinates).
     * <p>
     * The check uses the triangle-inequality principle: if the sum of the distance from the
     * drone to the candidate zone centre and the distance from the candidate zone centre to
     * the drone's current target is within {@link #PATH_TOLERANCE} of the direct
     * drone-to-target distance, the candidate zone is considered to lie on (or very close to)
     * the path.
     *
     * @param drone          the drone to evaluate
     * @param candidateCx    x-coordinate of the candidate zone centre
     * @param candidateCy    y-coordinate of the candidate zone centre
     * @return {@code true} if the drone's path passes through the candidate zone
     */
    boolean passesThroughZone(DroneInfo drone, double candidateCx, double candidateCy) {
        double dx = drone.getXPos();
        double dy = drone.getYPos();
        double tx = drone.getTargetZoneCenterX();
        double ty = drone.getTargetZoneCenterY();

        double distToCandidate  = Math.hypot(candidateCx - dx, candidateCy - dy);
        double distCandToTarget = Math.hypot(candidateCx - tx, candidateCy - ty);
        double distToTarget     = Math.hypot(tx - dx, ty - dy);

        // Avoid spurious matches when drone is already at (or very near) the target.
        if (distToTarget < 1e-6) return false;

        return distToCandidate + distCandToTarget <= distToTarget * PATH_TOLERANCE;
    }

    /**
     * Sends an {@link AssignTask} message to the specified drone, updating all relevant
     * tracking information in the drone's registry entry.
     *
     * @param drone  the drone to receive the assignment
     * @param zone   the zone the drone should fly to
     * @param report the incident report that triggered this assignment
     */
    private void sendTask(DroneInfo drone, Zone zone, IncidentReport report) {
        double centerX = zone.centerX();
        double centerY = zone.centerY();

        AssignTask task = new AssignTask(
                drone.getDroneId(),
                zone.zoneID(),
                zone.xOrigin(),
                zone.yOrigin(),
                zone.xEnd(),
                zone.yEnd(),
                report.severity(),
                report.faultType()
        );

        // Save previous state so we can roll back if the send fails.
        DroneState prevState = drone.getState();
        Integer prevZoneId = drone.getAssignedZoneId();
        Severity prevSeverity = drone.getAssignedSeverity();
        double prevTargetX = drone.getTargetZoneCenterX();
        double prevTargetY = drone.getTargetZoneCenterY();
        IncidentReport prevIncident = drone.getCurrentIncident();
        long prevDispatchTime = drone.getDispatchTimeMs();

        try (UdpSender sender = new UdpSender(drone.getHost(), drone.getPort())) {
            // Update state before sending the packet so that if the drone responds
            // immediately after receiving it, all scheduler-side state is already current.
            drone.setState(DroneState.EN_ROUTE);
            drone.assignZone(zone.zoneID());
            drone.setAssignedSeverity(report.severity());
            drone.setTargetZoneCenter(centerX, centerY);
            drone.setCurrentIncident(report);
            drone.incrementZonesServiced();
            drone.setDispatchTimeMs(System.currentTimeMillis());

            // Record metrics for dispatch
            metrics.recordFireDispatched(report);
            metrics.recordDroneDispatched(drone.getDroneId());

            LOGGER.log(Level.INFO, "[Scheduler] [{0}] Assigned drone {1} to zone {2} (zonesServiced={3}{4})",
                    new Object[]{Instant.now(), drone.getDroneId(), zone.zoneID(),
                            drone.getZonesServiced(),
                            report.faultType() != FaultTypes.NONE ? ", faultType=" + report.faultType() : ""});
            if (gui != null) {
                gui.showAssignment(drone.getDroneId(), zone.zoneID(), report.severity().getLabel(),
                        remainingWaterByZone.getOrDefault(zone.zoneID(), report.severity().getReqLitresOfWater()),
                        "en route");
                logEvent("Drone " + drone.getDroneId() + " dispatched to zone " + zone.zoneID()
                        + " (" + report.severity() + ")");
            }
            sender.send(PacketBuilder.build(task));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[Scheduler] Failed to send task to drone {0}: {1}",
                    new Object[]{drone.getDroneId(), e.getMessage()});
            // Roll back drone state so it remains eligible for future assignments,
            // and the timeout detector does not mistakenly declare it stuck.
            drone.setState(prevState);
            if (prevZoneId != null) { drone.assignZone(prevZoneId); }
            else { drone.clearAssignedZone(); }
            drone.setAssignedSeverity(prevSeverity);
            drone.setTargetZoneCenter(prevTargetX, prevTargetY);
            drone.setCurrentIncident(prevIncident);
            drone.setDispatchTimeMs(prevDispatchTime);
            if (gui != null) { logEvent("Failed to assign drone " + drone.getDroneId() + ": " + e.getMessage()); }
        }
    }

    private void activateIncident(IncidentReport report) {
        activeIncidentsByZone.put(report.zoneId(), report);
        remainingWaterByZone.put(report.zoneId(), report.severity().getReqLitresOfWater());
        if (gui != null) {
            gui.showFireIncident(report.zoneId(), report.severity().getLabel());
            logEvent("Fire detected at zone " + report.zoneId() + " (" + report.severity()
                    + ", remaining " + remainingWaterByZone.get(report.zoneId()) + "L)");
        }
        incidentQueue.offer(report);
        metrics.recordQueueSample(incidentQueue.size());
    }

    private boolean isCurrentQueueIncident(IncidentReport report) {
        IncidentReport active = activeIncidentsByZone.get(report.zoneId());
        return active != null && sameIncidentKey(active, report);
    }

    private boolean sameIncidentKey(IncidentReport left, IncidentReport right) {
        return left.zoneId() == right.zoneId()
                && left.eventType() == right.eventType()
                && left.severity() == right.severity()
                && left.time().equals(right.time());
    }

    private boolean isZoneAlreadyAssigned(int zoneId) {
        return droneRegistry.values().stream().anyMatch(d -> d.getAssignedZoneId() != null
                && d.getAssignedZoneId() == zoneId
                && (d.getState() == DroneState.EN_ROUTE
                || d.getState() == DroneState.ARRIVED
                || d.getState() == DroneState.DROPPING_AGENT
                || d.getState() == DroneState.COMPLETED));
    }

    private void startNextIncidentForZone(int zoneId) {
        Deque<IncidentReport> pendingIncidents = pendingIncidentsByZone.get(zoneId);
        if (pendingIncidents == null) {
            return;
        }

        IncidentReport nextIncident = pendingIncidents.poll();
        if (pendingIncidents.isEmpty()) {
            pendingIncidentsByZone.remove(zoneId, pendingIncidents);
        }

        if (nextIncident != null) {
            activateIncident(nextIncident);
            if (gui != null) {
                logQueueEvent("Zone " + zoneId + " follow-up fire is now active");
            }
        }
    }


    /**
     * Applies a {@link DroneUpdate} packet from a drone to the scheduler's internal state.
     * <p>
     * This method updates the drone's telemetry (position, state, water/battery/fuel),
     * drives the GUI, and implements the water-accounting and extinguish logic:
     * <ul>
     *   <li>When a drone transitions to {@code COMPLETED}, the water it dropped is subtracted
     *       from the zone's remaining requirement.  If more water is still needed, the zone
     *       incident is re-queued; otherwise the zone is marked extinguished.</li>
     *   <li>When a drone transitions to {@code IDLE}, its zone assignment is cleared and its
     *       idle-time metric is recorded.</li>
     *   <li>When a drone transitions out of {@code FAULTED} to {@code IDLE}, the GUI fault
     *       indicator is cleared and a recovery event is logged.</li>
     *   <li>On first arrival at a zone ({@code ARRIVED}), the official response-time metric
     *       is recorded.</li>
     * </ul>
     *
     * @param info   the {@link DroneInfo} registry entry for this drone
     * @param update the incoming {@link DroneUpdate} with new state and telemetry
     */
    private void handleDroneUpdate(DroneInfo info, DroneUpdate update) {
        DroneState previousState = info.getState();
        double previousWater = info.getWater();
        Integer assignedZoneBeforeUpdate = info.getAssignedZoneId();
        info.updateTelemetry(update.getState(), update.getXPos(), update.getYPos(), update.getWater(), update.getBattery(), update.getFuel());

        // Track drone movement for distance metrics
        metrics.recordDronePosition(update.getDroneId(), update.getXPos(), update.getYPos());

        if (gui != null) {
            gui.updateDroneState(update.getDroneId(), update.getState());
            gui.updateDroneWater(update.getDroneId(), update.getWater());
            gui.updateDronePosition(update.getDroneId(), update.getXPos(), update.getYPos());
            gui.updateDroneWaterLevel(update.getDroneId(), update.getWater());
            gui.updateDroneBatteryFuel(update.getDroneId(), update.getBattery(), update.getFuel());
            // Don't update tile position if drone is faulted - let fault color persist
            if (update.getState() != DroneState.FAULTED) { gui.showDronePosition(update.getDroneId(), update.getXPos(), update.getYPos()); }
            logTelemetry("Drone " + update.getDroneId() + " is " + update.getState()
                    + " at (" + Math.round(update.getXPos()) + ", " + Math.round(update.getYPos()) + ")"
                    + " batt=" + String.format("%.0f", update.getBattery()) + "%"
                    + " fuel=" + String.format("%.0f", update.getFuel()) + "%"
                    + " water=" + String.format("%.1f", update.getWater()) + "L");
        }

        if (previousState != update.getState() && gui != null) {
            logEvent("Drone " + update.getDroneId() + " -> " + update.getState());
            // Start/stop water drop animation when entering/leaving DROPPING_AGENT state
            if (update.getState() == DroneState.DROPPING_AGENT && info.getAssignedZoneId() != null) {
                gui.showDroneDropping(update.getDroneId(), info.getAssignedZoneId());
            }
            if (previousState == DroneState.DROPPING_AGENT) { gui.clearDroneDropping(update.getDroneId()); }
        }

        if (gui != null && info.getAssignedZoneId() != null) {
            IncidentReport activeIncident = activeIncidentsByZone.get(info.getAssignedZoneId());
            if (activeIncident != null && previousState != update.getState()) {
                gui.updateAssignmentProgress(update.getDroneId(), info.getAssignedZoneId(),
                        activeIncident.severity().getLabel(),
                        remainingWaterByZone.getOrDefault(info.getAssignedZoneId(), activeIncident.severity().getReqLitresOfWater()),
                        formatAssignmentStatus(update.getState()));
            }
        }

        if (update.getState() == DroneState.COMPLETED && info.getAssignedZoneId() != null) {
            int zoneId = info.getAssignedZoneId();
            if (isDroneAtAssignedZone(info, zoneId)) {
                int droppedWater = Math.max(0, (int) Math.round(previousWater - update.getWater()));
                applyWaterDrop(info, zoneId, droppedWater);
                info.incrementCompletedAssignments();
            } else {
                LOGGER.log(Level.WARNING,
                        "Ignoring COMPLETED update from drone {0} for zone {1}: drone position ({2}, {3}) is outside assigned zone",
                        new Object[]{info.getDroneId(), zoneId,
                                String.format("%.1f", info.getXPos()), String.format("%.1f", info.getYPos())});
            }
        }

        if (update.getState() == DroneState.RETURNING && previousState == DroneState.COMPLETED && assignedZoneBeforeUpdate != null) {
            int zoneId = assignedZoneBeforeUpdate;
            IncidentReport followUp = activeIncidentsByZone.get(zoneId);
            if (followUp != null && remainingWaterByZone.getOrDefault(zoneId, 0) > 0) {
                incidentQueue.offer(followUp);
                metrics.recordQueueSample(incidentQueue.size());
                if (gui != null) {
                    logQueueEvent("Zone " + zoneId + " re-queued after drone " + info.getDroneId() + " started returning");
                }
            }
            releaseDroneMissionState(info);
        }

        // Some tests and manual packet sequences jump straight back to IDLE without
        // sending a COMPLETED update first. Treat that as an immediate mission finish
        // so the zone does not remain artificially active forever.
        boolean legacyDirectCompletion = previousState == DroneState.EN_ROUTE
                || previousState == DroneState.ARRIVED
                || previousState == DroneState.DROPPING_AGENT;
        if (update.getState() == DroneState.IDLE && info.getAssignedZoneId() != null
                && legacyDirectCompletion && previousState != DroneState.COMPLETED && previousState != DroneState.FAULTED) {
            int zoneId = info.getAssignedZoneId();
            IncidentReport activeIncident = activeIncidentsByZone.get(zoneId);
            if (activeIncident != null && isDroneAtAssignedZone(info, zoneId)) {
                int previousRemaining = remainingWaterByZone.getOrDefault(zoneId, activeIncident.severity().getReqLitresOfWater());
                int droppedWater = Math.max(0, (int) Math.round(previousWater - update.getWater()));
                if (droppedWater == 0) {
                    droppedWater = previousRemaining;
                }
                applyWaterDrop(info, zoneId, droppedWater);
                info.incrementCompletedAssignments();
            }
        }

        if (update.getState() == DroneState.IDLE) {
            metrics.recordDroneIdle(update.getDroneId());
            if (gui != null) {
                gui.clearAssignment(update.getDroneId());
                // Only clear drone position if it wasn't faulted - faulted drones
                // stay visible at their stuck location until they recover and move
                if (previousState != DroneState.FAULTED) { gui.clearDronePosition(update.getDroneId()); }
            }
            info.clearAssignedZone();
        }

        if (previousState == DroneState.FAULTED && update.getState() == DroneState.IDLE && gui != null) {
            gui.clearDroneFault(update.getDroneId());
            // Restore drone to normal color at its current position, then clear it
            // (the drone is back at base after recovery)
            gui.clearDronePosition(update.getDroneId());
            logEvent("[RECOVERY] Drone " + update.getDroneId() + " reset and is back in service");
        }

        if (previousState != update.getState() && update.getState() == DroneState.ARRIVED && info.getAssignedZoneId() != null) {
            // Record arrival for official Response Time metric (event creation → first arrival)
            if (info.getCurrentIncident() != null) {
                metrics.recordFireArrived(info.getCurrentIncident());
            }
            if (gui != null) { logEvent("Drone " + info.getDroneId() + " reached zone " + info.getAssignedZoneId()); }
        }
    }

    private void applyWaterDrop(DroneInfo info, int zoneId, int droppedWater) {
        int previousRemaining = remainingWaterByZone.getOrDefault(zoneId, 0);
        int remainingWater = Math.max(0, previousRemaining - droppedWater);
        if (remainingWater > 0) {
            remainingWaterByZone.put(zoneId, remainingWater);
            if (gui != null) {
                logAssignmentHistory("Drone " + info.getDroneId() + " dropped " + droppedWater
                        + "L on Zone " + zoneId + "; " + remainingWater + "L still needed");
                IncidentReport activeIncident = activeIncidentsByZone.get(zoneId);
                if (activeIncident != null) {
                    gui.updateAssignmentProgress(info.getDroneId(), zoneId, activeIncident.severity().getLabel(),
                            remainingWater, "returning / refill");
                    int totalWater = activeIncident.severity().getReqLitresOfWater();
                    gui.shrinkFireSpread(zoneId, remainingWater, totalWater);
                }
                logQueueEvent("Zone " + zoneId + " still needs " + remainingWater + "L; waiting for drone return to re-queue");
            }
        } else {
            IncidentReport extinguishedIncident = activeIncidentsByZone.get(zoneId);
            remainingWaterByZone.remove(zoneId);
            activeIncidentsByZone.remove(zoneId);
            if (extinguishedIncident != null) {
                metrics.recordFireExtinguished(extinguishedIncident);
            }
            if (gui != null) {
                logAssignmentHistory("Drone " + info.getDroneId() + " extinguished Zone " + zoneId);
                gui.updateAssignmentProgress(info.getDroneId(), zoneId, "done", 0, "extinguished");
                gui.clearFireIncident(zoneId);
                logEvent("Zone " + zoneId + " extinguished by drone " + info.getDroneId());
            }
            releaseDroneMissionState(info);
            startNextIncidentForZone(zoneId);
            maybePrintFinalMetricsSummary();
        }
    }

    private void maybePrintFinalMetricsSummary() {
        if (finalSummaryPrinted || !fireInputComplete) {
            return;
        }
        if (activeIncidentsByZone.isEmpty() && incidentQueue.isEmpty() && pendingIncidentsByZone.isEmpty()) {
            finalSummaryPrinted = true;
            printMetricsSummary();
            shutdownRequested = true;
            Thread thread = schedulerThread;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private void releaseDroneMissionState(DroneInfo info) {
        info.clearAssignedZone();
        info.setAssignedSeverity(null);
        info.setCurrentIncident(null);
        info.setDispatchTimeMs(0);
    }

    private boolean isDroneAtAssignedZone(DroneInfo info, int zoneId) {
        Zone zone = zones.get(zoneId);
        if (zone == null) {
            return false;
        }
        return info.getXPos() >= zone.xOrigin() && info.getXPos() <= zone.xEnd()
                && info.getYPos() >= zone.yOrigin() && info.getYPos() <= zone.yEnd();
    }

    /**
     * Maps a {@link DroneState} to a short human-readable assignment status string
     * suitable for display in the GUI assignment panel.
     *
     * @param state the current drone state
     * @return a concise status label (e.g. {@code "en route"}, {@code "dropping"})
     */
    private String formatAssignmentStatus(DroneState state) {
        return switch (state) {
            case EN_ROUTE -> "en route";
            case ARRIVED -> "arrived";
            case DROPPING_AGENT -> "dropping";
            case COMPLETED -> "completed drop";
            case RETURNING -> "returning";
            case REFILLING -> "refilling";
            case FAULTED -> "faulted";
            case IDLE -> "idle";
        };
    }

    /**
     * Handles a fault report received from a drone.
     * <p>
     * Depending on the fault type:
     * - NOZZLE_STUCK_OPEN: marks the drone permanently offline (hard fault) and reassigns its incident.
     * - All other faults: marks the drone as FAULTED and reassigns its incident; the scheduler does not
     *   initiate a return-to-base or recovery sequence, and the drone remains faulted until handled
     *   by external logic or manual intervention.
     *
     * @param report the fault report from the drone
     */
    private void handleFaultReport(FaultReport report) {
        DroneInfo drone = droneRegistry.get(report.droneId());
        if (drone == null) {
            LOGGER.log(Level.SEVERE, "Fault report from unknown drone {0}", report.droneId());
            return;
        }

        FaultTypes faultType = report.faultType();
        LOGGER.log(Level.INFO, "Handling fault {0} for drone {1}", new Object[]{faultType, report.droneId()});

        metrics.recordDroneFault(report.droneId());
        IncidentReport pendingIncident = drone.getCurrentIncident();

        if (faultType == FaultTypes.NOZZLE_STUCK_OPEN) {
            // Hard fault — drone is permanently shut down
            drone.markOffline();
            LOGGER.log(Level.INFO, "Drone {0} permanently shut down (hard fault: NOZZLE_STUCK_OPEN).", report.droneId());
            if (gui != null) {
                gui.showDroneOffline(report.droneId());
                logEvent("[HARD FAULT] Drone " + report.droneId() + " shut down: NOZZLE_STUCK_OPEN");
            }
        } else {
            drone.setState(DroneState.FAULTED);
            LOGGER.log(Level.INFO, "Drone {0} marked FAULTED: {1}", new Object[]{report.droneId(), faultType});
            if (gui != null) {
                gui.showDroneFault(report.droneId(), faultType.name());
                if (faultType == FaultTypes.DRONE_STUCK) { gui.startDroneFaultCountdown(report.droneId(), faultType.name(), DRONE_STUCK_RECOVERY_SECONDS); }
                logEvent("[FAULT] Drone " + report.droneId() + " faulted: " + faultType);
            }
        }

        // Reassign the pending incident if there is one
        if (pendingIncident != null) {
            drone.clearAssignedZone();
            drone.setCurrentIncident(null);
            drone.setDispatchTimeMs(0);
            if (gui != null) { gui.clearAssignment(report.droneId()); }

            LOGGER.log(Level.INFO, "Reassigning incident from faulted drone {0} for zone {1}", new Object[]{report.droneId(), pendingIncident.zoneId()});
            if (gui != null) { logEvent("[REASSIGN] Reassigning zone " + pendingIncident.zoneId() + " from drone " + report.droneId()); }
            // Re-queue with NONE faults so the replacement drone operates normally
            IncidentReport reassigned = new IncidentReport(
                    pendingIncident.time(), pendingIncident.zoneId(),
                    pendingIncident.eventType(), pendingIncident.severity(), FaultTypes.NONE);
            activeIncidentsByZone.put(reassigned.zoneId(), reassigned);
            incidentQueue.offer(reassigned);
            metrics.recordQueueSample(incidentQueue.size());
        }
    }

    /**
     * Scans all registered drones and marks any that appear stuck as {@code FAULTED}.
     * <p>
     * A drone is considered timed-out when both of the following are true:
     * <ol>
     *   <li>It is not {@code IDLE}, {@code FAULTED}, or offline.</li>
     *   <li>The time elapsed since the later of its dispatch timestamp and its last
     *       telemetry update exceeds {@link #droneTimeoutMs}.</li>
     * </ol>
     * When a timeout is detected the drone is marked {@code FAULTED}, its pending incident
     * is re-queued with {@code FaultTypes.NONE} so a healthy drone can service it, and the
     * GUI is updated with a fault indicator.
     * <p>
     * This method is scheduled to run every 5 seconds by the background
     * {@code scheduler-timeout-watcher} executor started in {@link #run()}.
     */
    private void checkDroneTimeouts() {
        long now = System.currentTimeMillis();
        for (DroneInfo drone : droneRegistry.values()) {
            if (drone == null || drone.isOffline()) continue;
            if (drone.getState() == DroneState.IDLE || drone.getState() == DroneState.FAULTED) continue;

            // Only time out drones that have actually been dispatched on a mission.
            // Using dispatchTimeMs ensures we don't timeout drones that registered
            // but haven't yet been assigned a task.
            long dispatchTime = drone.getDispatchTimeMs();
            if (dispatchTime <= 0) continue;

            long lastUpdate = drone.getLastUpdateTimeMs();
            // Use the latter of dispatch time and last update time as the reference
            long referenceTime = Math.max(dispatchTime, lastUpdate);
            if ((now - referenceTime) > droneTimeoutMs) {
                LOGGER.log(Level.WARNING, "Drone {0} timed out (dispatched {1}s ago, last update {2}s ago) — assumed stuck.",
                        new Object[]{drone.getDroneId(), (now - dispatchTime) / 1000, (now - lastUpdate) / 1000});
                if (gui != null) {
                    gui.showDroneFault(drone.getDroneId(), FaultTypes.DRONE_STUCK.name());
                    logEvent("[TIMEOUT] Drone " + drone.getDroneId() + " timed out — assumed stuck mid-flight");
                }

                IncidentReport pending = drone.getCurrentIncident();
                drone.setState(DroneState.FAULTED);
                drone.clearAssignedZone();
                drone.setCurrentIncident(null);
                drone.setDispatchTimeMs(0);

                if (pending != null) {
                    LOGGER.log(Level.INFO, "Reassigning zone {0} after drone {1} timeout.", new Object[]{pending.zoneId(), drone.getDroneId()});
                    if (gui != null) { logEvent("[REASSIGN] Reassigning zone " + pending.zoneId() + " from timed-out drone " + drone.getDroneId()); }
                    IncidentReport reassigned = new IncidentReport(
                            pending.time(), pending.zoneId(),
                            pending.eventType(), pending.severity(), FaultTypes.NONE);
                    activeIncidentsByZone.put(reassigned.zoneId(), reassigned);
                    incidentQueue.offer(reassigned);
                    metrics.recordQueueSample(incidentQueue.size());
                }
            }
        }
    }

    /**
     * Computes the Euclidean distance between two 2-D points.
     *
     * @param x1 x-coordinate of the first point
     * @param y1 y-coordinate of the first point
     * @param x2 x-coordinate of the second point
     * @param y2 y-coordinate of the second point
     * @return the straight-line distance between the two points
     */
    private double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    /**
     * Prepends a wall-clock timestamp (formatted as {@code HH:mm:ss.SSS}) to the given
     * message, for consistent log-line formatting in the GUI log panels.
     *
     * @param message the raw message text
     * @return the message prefixed with {@code [HH:mm:ss.SSS] }
     */
    private String stamped(String message) {
        return "[" + GUI_TIME_FORMAT.format(Instant.now()) + "] " + message;
    }

    /**
     * Logs a high-level scheduler event to the GUI event panel and the
     * {@code events.log} file (via {@link SimulationLogger}).
     *
     * @param message the event description to log
     */
    private void logEvent(String message) {
        if (gui != null) { gui.logEvent(stamped(message)); }
        if (fileLogger != null) { fileLogger.logEvent(message); }
    }

    /**
     * Logs a drone telemetry update to the GUI telemetry panel and the
     * {@code telemetry.log} file (via {@link SimulationLogger}).
     *
     * @param message the telemetry line to log
     */
    private void logTelemetry(String message) {
        if (gui != null) { gui.logTelemetry(stamped(message)); }
        if (fileLogger != null) { fileLogger.logTelemetry(message); }
    }

    /**
     * Logs an incident-queue depth or status change to the GUI queue panel and the
     * {@code queue.log} file (via {@link SimulationLogger}).
     *
     * @param message the queue event description to log
     */
    private void logQueueEvent(String message) {
        if (gui != null) { gui.logQueueEvent(stamped(message)); }
        if (fileLogger != null) { fileLogger.logQueue(message); }
    }

    /**
     * Logs a drone-to-zone assignment or completion record to the GUI assignment panel
     * and the {@code assignments.log} file (via {@link SimulationLogger}).
     *
     * @param message the assignment history entry to log
     */
    private void logAssignmentHistory(String message) {
        if (gui != null) { gui.logAssignmentHistory(stamped(message)); }
        if (fileLogger != null) { fileLogger.logAssignment(message); }
    }

    /**
     * Returns the {@link SimulationMetrics} instance that accumulates all runtime
     * performance data for this scheduler run.
     *
     * @return the simulation metrics collector; never {@code null}
     */
    public SimulationMetrics getMetrics() {
        return metrics;
    }

    /**
     * Marks the simulation as ended, formats the complete metrics summary, and
     * writes it both to the {@link Logger} and to {@code logs/metrics.log} via
     * {@link SimulationLogger}.
     * <p>
     * This method is called automatically when the last active fire zone is
     * extinguished. The {@link SimulationLogger} is closed in the {@code finally}
     * block of {@link #run()}, not here, so that all log files remain open until
     * the scheduler thread exits.
     */
    public void printMetricsSummary() {
        metrics.markSimulationEnd();
        String summary = metrics.getSummaryString();
        LOGGER.info(summary);
        if (fileLogger != null) {
            fileLogger.logMetricsSummary(summary);
        }
    }
}
