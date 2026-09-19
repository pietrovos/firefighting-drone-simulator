package DroneSwarmSim.drone;
import DroneSwarmSim.config.DroneConfig;
import DroneSwarmSim.messaging.AssignTask;
import DroneSwarmSim.messaging.DroneRegister;
import DroneSwarmSim.messaging.DroneUpdate;
import DroneSwarmSim.messaging.FaultReport;
import DroneSwarmSim.messaging.MessageType;
import DroneSwarmSim.net.udp.PacketBuilder;
import DroneSwarmSim.net.udp.PacketParser;
import DroneSwarmSim.net.udp.UdpReceiver;
import DroneSwarmSim.net.udp.UdpSender;
import DroneSwarmSim.scheduler.FaultTypes;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * The {@code DroneSubsystem} class simulates the behaviour and lifecycle of a drone
 * within a drone swarm simulation system. It implements the {@code Runnable} interface
 * to allow concurrent execution, handling tasks such as navigation, status updates,
 * and fire suppression.
 * <p>
 * This class contains properties to manage the drone's state, position, water supply,
 * and assigned tasks. It also establishes communication with the Scheduler via
 * UDP for task assignments and status updates.
 * <p>
 * Key responsibilities of this class include:
 * - Registering the drone with the Scheduler.
 * - Listening for incoming task assignments via an internal task queue.
 * - Navigating to the designated target location to perform tasks.
 * - Tracking and reporting operational status including battery and fuel levels.
 * - Transitioning through REFILLING state to reset all consumables before returning to IDLE.
 * - Handling any faults or interruptions during execution.
 * <p>
 * Battery model: drained continuously during flight (per-second rate) plus one-time
 * costs for opening/closing the nozzle.  Fuel model: drained per metre of distance
 * travelled plus acceleration/deceleration surcharges at the start/end of each trip.
 * Both battery and fuel are reset to 100 % during the REFILLING state at base.
 * <p>
 * Task queue: incoming AssignTask packets are placed in a {@link LinkedBlockingQueue}.
 * The drone blocks in IDLE until the next task is available, which means the scheduler
 * can enqueue more than one task without any being lost — they are processed sequentially.
 */
public class DroneSubsystem implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(DroneSubsystem.class.getName());
    private static final long SOFT_RESET_MS = 4_000; // 4 seconds
    private static final long STUCK_FAULT_MS = 10_000; // 10 seconds
    private static final long STEP_MS = 25; // 25ms
    private final int droneId; // Unique identifier for this drone
    volatile DroneState state; // Current operational state of the drone
    volatile double water; // Current level of liquid-agent in litres
    double battery; // Current level of battery in percentage
    double fuel; // Current level of fuel in percentage
    double xPos; // Current x-coordinate of the drone
    double yPos; // Current y-coordinate of the drone
    private double speed; // Current speed of the drone in metres per second
    private final LinkedBlockingQueue<AssignTask> taskQueue = new LinkedBlockingQueue<>(); // Queue of incoming tasks
    private volatile AssignTask currentTask; // Current task being executed
    private UdpSender schedulerSender; // UDP sender for sending status updates
    private int packetsSentThisTask; // Number of packets sent during the current task
    private boolean corruptedPacketSent; // Whether a corrupted packet has been sent
    private boolean stuckFaultTriggered; // Whether a stuck fault has been triggered
    private final String schedulerHost; // Hostname of the scheduler
    private final int schedulerPort; // Port number of the scheduler
    private final int listenPort; // Local port number this drone binds for incoming commands
    private int tasksAccepted;
    private static final long TASK_POLL_TIMEOUT_MS = Long.MAX_VALUE;
    private static final long WATER_DROP_DELAY_MS = 4000L;

    /**
     * Constructs a new instance of the DroneSubsystem using the default configuration.
     *
     * @param droneId the unique identifier assigned to this drone.
     */
    public DroneSubsystem(int droneId) { this(droneId, DroneConfig.SCHEDULER_HOST, DroneConfig.SCHEDULER_PORT, DroneConfig.DRONE_BASE_PORT + droneId); }

    /**
     * Constructor used to supply custom network parameters (e.g., in tests).
     *
     * @param droneId       unique drone identifier
     * @param schedulerHost hostname for scheduler update messages
     * @param schedulerPort port for scheduler update messages
     * @param listenPort    local port this drone binds for incoming commands
     */
    public DroneSubsystem(int droneId, String schedulerHost, int schedulerPort, int listenPort) {
        this.droneId = droneId;
        this.state = DroneState.IDLE;
        this.water = DroneConfig.AGENT_CAPACITY_LITRES;
        this.battery = 100.0;
        this.fuel = 100.0;
        this.xPos = 0;
        this.yPos = 0;
        this.speed = 0;
        this.schedulerHost = schedulerHost;
        this.schedulerPort = schedulerPort;
        this.listenPort = listenPort;
    }

    public int getDroneId() { return droneId; }

    public DroneState getState() { return state; }

    public double getWater() { return water; }

    public double getBattery() { return battery; }

    public double getFuel() { return fuel; }

    /**
     * Executes the main runtime loop for the drone, managing its states and interactions.
     * The method initializes communication components such as a UDP sender and receiver,
     * registers the drone with the scheduler, and transitions through various operational
     * states based on the current mission status and drone conditions.
     * <p>
     * The runtime loop includes the following state handling:
     * - IDLE: Handles idle state logic and determines if the drone should continue running.
     * - EN_ROUTE: Executes tasks while the drone is en route to a destination.
     * - ARRIVED: Handles actions upon arrival at a destination.
     * - DROPPING_AGENT: Manages the process of dropping off an agent.
     * - COMPLETED: Transitions to the RETURNING state if resources are depleted.
     * - RETURNING: Executes the logic for returning the drone to its base.
     * - REFILLING: Manages refilling operations for the drone.
     * - FAULTED: Stops the drone's operation if a fault occurs.
     * <p>
     * In case of errors, such as IO issues, appropriate logging is performed, and resources
     * such as the UDP receiver and sender are released in the `finally` block to ensure
     * proper cleanup.
     */
    @Override
    public void run () {
        int listenPort = this.listenPort;

        try (UdpSender sender = new UdpSender(schedulerHost, schedulerPort);
             UdpReceiver localReceiver = new UdpReceiver(listenPort, this::handlePacket)) {
            schedulerSender = sender;
            // UDP receiver for receiving task assignments
            localReceiver.start();
            registerWithScheduler(listenPort);
            boolean running = true;

            while (running) {
                switch (state) {
                    case IDLE:
                        running = handleIdleState();
                        break;
                    case EN_ROUTE:
                        handleEnRouteState();
                        break;
                    case ARRIVED:
                        handleArrivedState();
                        break;
                    case DROPPING_AGENT:
                        handleDroppingAgentState();
                        break;
                    case COMPLETED:
                        if (water <= 0) state = DroneState.RETURNING;
                        break;
                    case RETURNING:
                        returnToBase();
                        break;
                    case REFILLING:
                        performRefill();
                        break;
                    case FAULTED:
                        LOGGER.log(Level.INFO, "[Drone {0}] Faulted — stopping.", droneId);
                        running = false;
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException e) { LOGGER.log(Level.SEVERE, "[Drone {0}] IO error: {1}", new Object[]{droneId, e.getMessage()}); }
    }

    /**
     * Registers the drone with the scheduler by sending a registration packet.
     *
     * @param listenPort the port on which the drone is listening for communication
     * @throws IOException if an I/O error occurs while sending the registration packet
     */
    private void registerWithScheduler(int listenPort) throws IOException {
        DroneRegister reg = new DroneRegister(droneId, "localhost", listenPort);
        schedulerSender.send(PacketBuilder.build(reg));
        LOGGER.log(Level.INFO, "[Drone {0}] Registered with Scheduler on port {1}", new Object[]{droneId, listenPort});
    }

    /**
     * Handles the drone's state when it is idle, polling for the next task in the queue
     * and initializing state variables based on the task's fault type.
     * <p>
     * This method handles specific fault types by transitioning the drone into an appropriate
     * state (e.g., faulted or en route) and dispatching the necessary updates or fault reports.
     * If no task is available in the queue within the specified timeout, the drone remains idle.
     *
     * @return {@code true} if the state was successfully handled;
     *         {@code false} if the thread was interrupted while waiting for a task.
     */
    private boolean handleIdleState() {
        speed = 0;
        try { currentTask = taskQueue.poll(TASK_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (currentTask == null) { return true; }
        FaultTypes fault = currentTask.faultType();
        packetsSentThisTask = 0;
        corruptedPacketSent = false;
        stuckFaultTriggered = false;

        if (fault == FaultTypes.NOZZLE_STUCK_OPEN) {
            LOGGER.log(Level.WARNING, "[Drone {0}] HARD FAULT: nozzle stuck open — shutting down.", droneId);
            state = DroneState.FAULTED;
            sendStatusUpdate();
            sendFaultReport(fault, "Nozzle stuck open — hard fault; drone shut down");
        } else {
            state = DroneState.EN_ROUTE;
            drainFuel(DroneConfig.FUEL_ACCELERATION_SURCHARGE_PCT); // Acceleration surcharge at the start of each trip
            sendStatusUpdate();
        }
        return true;
    }

    /**
     * Handles the drone's behaviour while it is in the "en-route" state.
     * This method calculates a target position based on the current task's zone
     * and processes any faults that may occur during the en-route phase.
     * Depending on the fault type, it may simulate a stuck fault, send corrupted
     * packets, handle packet loss, or simply update the drone's position.
     * <p>
     * Fault handling includes:
     * - Simulating a stuck fault when the fault type is DRONE_STUCK and the fault has not yet been triggered.
     * - Sending corrupted telemetry packets and handling related issues when the fault type is PACKET_CORRUPTION.
     * - Managing telemetry packet loss and resetting the radio when the fault type is PACKET_LOSS.
     * <p>
     * In the case of no active faults, this method updates the drone's position normally.
     */
    private void handleEnRouteState() {
        double targetX = currentTask.zoneXOrigin() + ((currentTask.zoneXEnd() - currentTask.zoneXOrigin()) / 2.0);
        double targetY = currentTask.zoneYOrigin() + ((currentTask.zoneYEnd() - currentTask.zoneYOrigin()) / 2.0);

        FaultTypes fault = currentTask.faultType();
        if (fault == FaultTypes.DRONE_STUCK && !stuckFaultTriggered) {
            LOGGER.log(Level.WARNING, "[Drone {0}] FAULT: drone stuck mid-flight.", droneId);
            simulateStuckFault(targetX, targetY);
        } else if (fault == FaultTypes.PACKET_CORRUPTION) {
            LOGGER.log(Level.WARNING, "[Drone {0}] FAULT: packet corruption during dispatch.", droneId);
            sendCorruptedPacket();
            handleSoftFault(FaultTypes.PACKET_CORRUPTION, "Corrupted outbound status packet; drone resetting telemetry");
        } else if (fault == FaultTypes.PACKET_LOSS) {
            LOGGER.log(Level.WARNING, "[Drone {0}] FAULT: packet loss during dispatch.", droneId);
            handleSoftFault(FaultTypes.PACKET_LOSS, "Telemetry link dropped packets; drone resetting radio");
        } else { updatePosition(); }
    }

    /**
     * Handles the transition of the drone into the "Arrived" state.
     * <p>
     * This method is triggered when the drone reaches its destination and begins
     * preparing to perform its task, such as dropping an agent. The method performs
     * the following actions:
     * <p>
     * 1. Deducts a one-time battery cost associated with opening the nozzle.
     * 2. Updates the drone's state to "DROPPING_AGENT".
     * 3. Sends a status update to ensure the system is aware of the state change.
     * <p>
     * Precondition:
     * - The drone should be in a state where transitioning to "Arrived" is valid.
     * <p>
     * Postcondition:
     * - The battery level is decremented based on the configured nozzle open percentage.
     * - The state of the drone is set to "DROPPING_AGENT".
     * - A status update is sent to the relevant monitoring or control system.
     */
    private void handleArrivedState() {
        drainBattery(DroneConfig.BATTERY_NOZZLE_OPEN_PCT); // Nozzle opens — one-time battery cost
        state = DroneState.DROPPING_AGENT;
        sendStatusUpdate();
    }

    /**
     * Handles the dropping water operation for the drone. This method executes the process
     * of releasing water from the drone, ensuring that the necessary states and resources
     * are updated accordingly. If a fault is detected during execution, appropriate fault
     * handling logic is triggered.
     * <p>
     * Faults:
     * - If the nozzle is stuck closed (FaultTypes.NOZZLE_STUCK_CLOSED), the method logs
     *   the issue, sets the drone state to {@code DroneState.FAULTED}, sends a status update,
     *   and invokes the soft fault handling mechanism.
     * <p>
     * Normal Operation:
     * - Logs the start of the water drop process and waits for the water-dropping delay
     *   to simulate water release.
     * - Logs the completion of the water drop process, sets the drone's water level to zero,
     *   depletes the battery by an amount defined for closing the nozzle, updates the
     *   state to {@code DroneState.COMPLETED}, and sends a status update.
     * <p>
     * Exception Handling:
     * - Handles {@code InterruptedException} by restoring the interrupt status, setting the
     *   drone's state to {@code DroneState.FAULTED}, and sending a status update.
     * <p>
     * Preconditions:
     * - The drone must have a valid task assigned (may or may not include faults).
     * <p>
     * Postconditions:
     * - The drone's state is updated based on the success or failure of the operation.
     * - Relevant faults and resource changes (e.g., water depletion and battery drain)
     *   are logged and applied.
     */
    private void handleDroppingAgentState() {
        try {
            FaultTypes activeFault = currentTask != null ? currentTask.faultType() : FaultTypes.NONE;
            if (activeFault == FaultTypes.NOZZLE_STUCK_CLOSED) {
                LOGGER.log(Level.WARNING, "[Drone {0}] FAULT: nozzle stuck closed — cannot drop water.", droneId);
                state = DroneState.FAULTED;
                sendStatusUpdate();
                handleSoftFault(activeFault, "Nozzle stuck closed — unable to drop water", false);
                return;
            }

            LOGGER.log(Level.INFO, "[Drone {0}] Dropping water...", droneId);
            Thread.sleep(WATER_DROP_DELAY_MS);
            LOGGER.log(Level.INFO, "[Drone {0}] Water drop complete!", droneId);
            water = 0;
            drainBattery(DroneConfig.BATTERY_NOZZLE_CLOSE_PCT); // Nozzle close — one-time battery cost
            state = DroneState.COMPLETED;
            sendStatusUpdate();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state = DroneState.FAULTED;
            sendStatusUpdate();
        }
    }

    /**
     * Processes an incoming data packet. AssignTask packets are normally enqueued in the
     * task queue, but a new assignment received while the drone is EN_ROUTE is treated as
     * a scheduler redirect and replaces the current task immediately.
     *
     * @param data the incoming packet data in byte array format.
     */
    private void handlePacket(byte[] data) {
        try {
            MessageType type = PacketParser.getType(data);
            if (type == MessageType.ASSIGN_TASK) {
                AssignTask task = PacketParser.parseAssignTask(data);
                if (task != null) {
                    LOGGER.log(Level.INFO, "[Drone {0}] Received task: {1}", new Object[]{droneId, task});
                    if (state == DroneState.EN_ROUTE && currentTask != null) {
                        LOGGER.log(Level.INFO, "[Drone {0}] Redirecting mid-flight from zone {1} to zone {2}",
                                new Object[]{droneId, currentTask.zoneId(), task.zoneId()});
                        currentTask = task;
                        taskQueue.clear();
                        tasksAccepted++;
                    } else if (taskQueue.offer(task)) {
                        tasksAccepted++;
                    }
                }
            }
        } catch (Exception e) { LOGGER.log(Level.SEVERE, "[Drone {0}] Error parsing packet: {1}", new Object[]{droneId, e.getMessage()}); }
    }

    /**
     * Sends a status update for the drone's current state to the scheduler system.
     * <p>
     * The method performs the following actions:
     * - Checks if the scheduler sender is available. If not, the method returns without taking action.
     * - Identifies the active fault type for the current task, defaulting to NONE if no task is associated.
     * - Handles the PACKET_LOSS fault type by intentionally dropping every other status update packet.
     * - Constructs a {@code DroneUpdate} that contains details about the drone's current state,
     *   including its identifier, position, and resource levels.
     * - Sends the status update packet using the scheduler sender. If sending fails, logs an error message.
     */
    private void sendStatusUpdate() {
        if (schedulerSender == null) return;
        FaultTypes activeFault = currentTask != null ? currentTask.faultType() : FaultTypes.NONE;

        // PACKET_LOSS: drop every other packet during the mission
        if (activeFault == FaultTypes.PACKET_LOSS) {
            packetsSentThisTask++;
            if (packetsSentThisTask % 2 == 0) {
                LOGGER.log(Level.WARNING, "[Drone {0}] FAULT: packet loss — dropping status update.", droneId);
                return;
            }
        }

        DroneUpdate update = new DroneUpdate(droneId, state, xPos, yPos, water, battery, fuel);

        try { schedulerSender.send(PacketBuilder.build(update)); }
        catch (IOException e) { LOGGER.log(Level.SEVERE, "[Drone {0}] Failed to send status update: {1}", new Object[]{droneId, e.getMessage()}); }
    }

    /**
     * Sends a fault report for the specified fault type and message.
     *
     * @param faultType The type of fault to report. This categorizes the issue being reported.
     * @param message A detailed message describing the fault or providing additional context.
     */
    private void sendFaultReport(FaultTypes faultType, String message) {
        if (schedulerSender == null) return;
        FaultReport report = new FaultReport(droneId, faultType, message);
        try {
            LOGGER.log(Level.INFO, "[Drone {0}] Sending fault report: {1}", new Object[]{droneId, report});
            schedulerSender.send(PacketBuilder.build(report));
        } catch (IOException e) { LOGGER.log(Level.SEVERE, "[Drone {0}] Failed to send fault report: {1}", new Object[]{droneId, e.getMessage()}); }
    }

    /**
     * Sends a corrupted packet to the scheduler sender if it has not already been sent.
     * This method ensures that a corrupted packet is sent only once by maintaining a flag.
     * <p>
     * The corrupted packet data is predefined as "CORRUPTED_PACKET_DATA" and encoded using UTF-8.
     * If the scheduler sender is null or if the corrupted packet has already been sent,
     * the method will return without taking any action.
     * <p>
     * In case of an {@link IOException} during the sending process, an error is logged with
     * the relevant drone ID and the exception message.
     */
    private void sendCorruptedPacket() {
        if (schedulerSender == null || corruptedPacketSent) return;
        corruptedPacketSent = true;
        try { schedulerSender.send("CORRUPTED_PACKET_DATA".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (IOException e) { LOGGER.log(Level.SEVERE, "[Drone {0}] Failed to send corrupted packet: {1}", new Object[]{droneId, e.getMessage()}); }
    }

    /**
     * Moves the drone towards a target location.
     *
     * @param targetX The target x-coordinate.
     * @param targetY The target y-coordinate.
     * @param ratio The fraction of the distance to move (0.0 to 1.0).
     * @return true if the target was reached, false if interrupted or the mission aborted.
     */
    private boolean flyAway(double targetX, double targetY, double ratio) {
        speed = 15;
        double startX = xPos;
        double startY = yPos;
        double partialTargetX = resolveFlightTargetX(startX, targetX, ratio);
        double partialTargetY = resolveFlightTargetY(startY, targetY, ratio);
        double deltaX = partialTargetX - xPos;
        double deltaY = partialTargetY - yPos;
        double dist = Math.hypot(deltaX, deltaY);

        while (dist > 15) {
            if (state == DroneState.EN_ROUTE && ratio >= 1.0 && currentTask != null) {
                partialTargetX = getCurrentTargetCenter()[0];
                partialTargetY = getCurrentTargetCenter()[1];
            }
            deltaX = partialTargetX - xPos;
            deltaY = partialTargetY - yPos;
            dist = Math.hypot(deltaX, deltaY);
            double stepDist = Math.min(dist, speed);
            xPos += (deltaX / dist) * stepDist;
            yPos += (deltaY / dist) * stepDist;

            drainBattery(DroneConfig.BATTERY_FLIGHT_DRAIN_RATE_PCT_PER_SEC * (STEP_MS / 1000.0));
            drainFuel(DroneConfig.FUEL_PER_METRE_PCT * stepDist);

            if (dist < 50) speed = 1; // Simplify speed logic
            if (state == DroneState.RETURNING && dist < 50) speed = 3;

            try {
                sendStatusUpdate();
                Thread.sleep(STEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                state = DroneState.FAULTED;
                sendStatusUpdate();
                return true;
            }
        }

        xPos = partialTargetX;
        yPos = partialTargetY;
        sendStatusUpdate();
        return false;
    }

    private double resolveFlightTargetX(double startX, double targetX, double ratio) {
        return startX + ((targetX - startX) * ratio);
    }

    private double resolveFlightTargetY(double startY, double targetY, double ratio) {
        return startY + ((targetY - startY) * ratio);
    }

    /**
     * Simulates a "stuck" fault scenario for the drone during a flight operation.
     * This method assumes the drone gets stuck mid-flight, transitions the drone
     * to a faulted state, sends relevant fault reports and status updates, and
     * automatically resets the drone after a pre-defined period.
     *
     * @param targetX the target X-coordinate that the drone is flying towards when the fault is triggered
     * @param targetY the target Y-coordinate that the drone is flying towards when the fault is triggered
     */
    private void simulateStuckFault(double targetX, double targetY) {
        stuckFaultTriggered = true;
        if (flyAway(targetX, targetY, 0.4)) return;

        state = DroneState.FAULTED;
        sendStatusUpdate();
        sendFaultReport(FaultTypes.DRONE_STUCK, "Drone stuck mid-flight; auto-reset in " + (STUCK_FAULT_MS / 1000) + "s");
        try { Thread.sleep(STUCK_FAULT_MS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        resetConsumables();
        currentTask = null;
        state = DroneState.IDLE;
        sendStatusUpdate();
    }

    /**
     * Updates the position of the drone by directing it towards the current target center.
     * If the target centre is null, the method exits without performing any action.
     * The drone attempts to fly towards the specified target coordinates, and if successful,
     * it applies a deceleration fuel surcharge upon reaching the zone.
     * Updates the drone state to 'ARRIVED' and sends a status update after arrival.
     *
     * @throws NullPointerException if the current target centre is null.
     */
    private void updatePosition() throws NullPointerException {
        double[] target = getCurrentTargetCenter();
        if (flyAway(target[0], target[1], 1.0)) return;
        drainFuel(DroneConfig.FUEL_DECELERATION_SURCHARGE_PCT); // Deceleration surcharge when arriving at the zone
        state = DroneState.ARRIVED;
        sendStatusUpdate();
    }

    /**
     * Handles the process of guiding the drone back to its base location (0, 0)
     * and transitioning it to a refilling state. This operation includes the necessary
     * adjustments such as fuel consumption for acceleration and deceleration during
     * the return journey. Once the base is reached, the drone's operational state
     * is updated, and a status update is sent.
     * <p>
     * Main steps:
     * - Applies an acceleration fuel surcharge at the start of the return trip.
     * - Commands the drone to move towards base coordinates (0, 0) using a specified ratio.
     * - Applies a deceleration fuel surcharge upon nearing the base.
     * - Updates the drone's state to refilling and clears any current task.
     * - Sends an updated status report after reaching the base.
     * <p>
     * If the movement towards the base fails or is interrupted, further steps are
     * skipped, and the method exits early.
     */
    private void returnToBase() {
        drainFuel(DroneConfig.FUEL_ACCELERATION_SURCHARGE_PCT); // Acceleration surcharge for the return trip
        if (flyAway(0, 0, 1.0)) return;
        drainFuel(DroneConfig.FUEL_DECELERATION_SURCHARGE_PCT); // Deceleration surcharge on final approach to base
        state = DroneState.REFILLING;
        currentTask = null;
        sendStatusUpdate();
    }

    /**
     * Simulates the drone refilling its liquid-agent tank, recharging its battery,
     * and topping up its fuel at the home base. After the configured refill duration,
     * all consumables are restored and the drone transitions to IDLE.
     */
    private void handleSoftFault(FaultTypes faultType, String message) { handleSoftFault(faultType, message, true); }

    /**
     * Handles a soft fault condition in the drone's operation, transitioning the drone to a faulted
     * state, reporting the fault, and attempting a recovery by resetting consumables and updating
     * the drone's status.
     *
     * @param faultType            the type of fault that occurred, represented as a {@code FaultTypes} enum.
     * @param message              a description of the fault, providing more details for diagnostic or logging purposes.
     * @param sendFaultedUpdate    a boolean indicating whether to send a status update immediately after entering the faulted state.
     */
    private void handleSoftFault(FaultTypes faultType, String message, boolean sendFaultedUpdate) {
        state = DroneState.FAULTED;
        if (sendFaultedUpdate) { sendStatusUpdate(); }
        sendFaultReport(faultType, message);
        try { Thread.sleep(SOFT_RESET_MS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        resetConsumables();
        currentTask = null;
        state = DroneState.IDLE;
        sendStatusUpdate();
    }

    /**
     * Computes the center coordinates of the current target zone assigned to the drone.
     * The center is calculated as the midpoint between the origin and end coordinates
     * of the task's defined zone in both the x and y dimensions.
     * If there is no current task assigned to the drone, an empty array is returned.
     *
     * @return a double array containing the x and y coordinates of the target centre.
     *         The array contains no elements if there is no current task.
     */
    private double[] getCurrentTargetCenter() {
        AssignTask task = currentTask;
        if (task == null) { return new double[0]; }
        return new double[]{
                task.zoneXOrigin() + ((task.zoneXEnd() - task.zoneXOrigin()) / 2.0),
                task.zoneYOrigin() + ((task.zoneYEnd() - task.zoneYOrigin()) / 2.0)
        };
    }

    /**
     * Simulates the drone's refilling process at the base station.
     * During this process, the drone's operational consumables such as
     * water, battery, and fuel are restored to full capacity, and the
     * drone transitions back to the IDLE state.
     * <p>
     * The method logs the start and completion of the refill operation. If interrupted
     * during the process, the drone transitions to the FAULTED state and sends
     * a status update indicating the fault. Otherwise, upon successful completion,
     * the consumables are reset, and the drone becomes ready for its next operation.
     */
    private void performRefill() {
        LOGGER.log(Level.INFO, "[Drone {0}] Refilling at base...", droneId);
        try { Thread.sleep(DroneConfig.REFILL_DURATION_MS); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state = DroneState.FAULTED;
            sendStatusUpdate();
            return;
        }
        resetConsumables();
        state = DroneState.IDLE;
        LOGGER.log(Level.INFO, "[Drone {0}] Refill complete — back to IDLE.", droneId);
        sendStatusUpdate();
    }

    /**
     * Resets water, battery, and fuel to their full levels.
     * Called after a refill, or after a fault reset, so the drone is ready
     * for its next mission with a full set of consumables.
     */
    private void resetConsumables() {
        xPos = 0;
        yPos = 0;
        water = DroneConfig.AGENT_CAPACITY_LITRES;
        battery = 100.0;
        fuel = 100.0;
    }

    /**
     * Drains battery by the given amount, clamping at 0 %.
     *
     * @param pct percentage points to drain (must be ≥ 0)
     */
    private void drainBattery(double pct) { battery = Math.max(0.0, battery - pct); }

    /**
     * Drains fuel by the given amount, clamping at 0 %.
     *
     * @param pct percentage points to drain (must be ≥ 0)
     */
    private void drainFuel(double pct) { fuel = Math.max(0.0, fuel - pct); }
}
