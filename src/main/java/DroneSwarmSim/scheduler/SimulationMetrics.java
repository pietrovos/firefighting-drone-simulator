package DroneSwarmSim.scheduler;

import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.IncidentReport;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
/**
 * SimulationMetrics is responsible for collecting, managing, and analyzing metrics
 * related to the fire incident simulation. It tracks event times, queue statistics,
 * drone performance, and other key metrics to provide insights into the simulation's
 * performance and behaviours.
 * <p>
 * Fields include:
 * - Simulation time tracking (start, end, durations)
 * - Fire incident lifecycle (detection, dispatch, arrival, extinguishment)
 * - Queue metrics (samples, max length, averages)
 * - Drone metrics (utilization, distances, faults)
 * - Input file processing metrics
 * <p>
 * Methods are provided to:
 * - Record simulation events and their timings
 * - Measure and retrieve key performance metrics
 * - Format summaries for display or logging
 */
public class SimulationMetrics {
    private record FireIncidentKey(String time, int zoneId, EventType eventType, String severityLabel) { }

    // Simulation-level timing
    private volatile Instant simulationStartTime;
    private volatile Instant simulationEndTime;
    private final AtomicInteger totalFiresReported = new AtomicInteger(0);
    private final AtomicInteger totalFiresExtinguished = new AtomicInteger(0);
    private final AtomicInteger totalServiceRequests = new AtomicInteger(0);
    // Input file processing metrics (set by FireIncidentSubsystem)
    private volatile int inputFileEventCount;
    private volatile long inputFileProcessingMs;
    // Per-zone (fire) metrics
    private final Map<FireIncidentKey, Instant> fireDetectedTime = new ConcurrentHashMap<>();
    private final Map<FireIncidentKey, Instant> fireFirstDispatchTime = new ConcurrentHashMap<>();
    // Time the first drone actually ARRIVED at the zone (start of response time measurement).
    private final Map<FireIncidentKey, Instant> fireFirstArrivalTime = new ConcurrentHashMap<>();
    private final Map<FireIncidentKey, Instant> fireExtinguishedTime = new ConcurrentHashMap<>();
    // Queue length tracking (optional metrics 6 & 7)
    private final AtomicInteger maxQueueLength = new AtomicInteger(0);
    private final AtomicLong queueLengthSum    = new AtomicLong(0);
    private final AtomicLong queueSampleCount  = new AtomicLong(0);
    // Per-drone metrics
    private final Map<Integer, DroneMetrics> droneMetrics = new ConcurrentHashMap<>();

    /**
     * Represents metrics for an individual drone in the simulation.
     * Tracks operational statistics such as flight time, idle time, distance travelled,
     * completed trips, and fault occurrences.
     */
    public static class DroneMetrics {
        private final int droneId;
        private volatile Instant lastIdleStart;
        private volatile Instant lastFlightStart;
        private volatile double lastX;
        private volatile double lastY;

        private long totalIdleMs;
        private long totalFlightMs;
        private double totalDistanceTraveled;
        private int tripsCompleted;
        private int faultCount;

        /**
         * Constructs a new {@code DroneMetrics} instance with the specified drone ID.
         * Initializes the drone's metrics, including setting the last idle start to the current time
         * and defaulting position coordinates to zero.
         *
         * @param droneId The unique identifier for the drone.
         */
        public DroneMetrics(int droneId) {
            this.droneId = droneId;
            this.lastIdleStart = Instant.now();
            this.lastX = 0;
            this.lastY = 0;
        }

        public int getDroneId() { return droneId; }

        public synchronized long getTotalIdleMs() { return totalIdleMs; }

        public synchronized long getTotalFlightMs() { return totalFlightMs; }

        public synchronized double getTotalDistanceTraveled() { return totalDistanceTraveled; }

        public synchronized int getTripsCompleted() { return tripsCompleted; }

        public synchronized int getFaultCount() { return faultCount; }

        /**
         * Calculates the utilization percentage of the drone based on the total time
         * spent in flight compared to the total operational time (flight time and idle time).
         *
         * @return The utilization percentage as a double value. If total operational time equals zero, it
         *         returns 0.0 to indicate no utilization.
         */
        public synchronized double getUtilizationPct() {
            long total = totalFlightMs + totalIdleMs;
            return total > 0 ? (100.0 * totalFlightMs / total) : 0.0;
        }

        /**
         * Starts a new flight session for the drone, marking the current time as the start of flight.
         * Calculates and accumulates the total idle time if the drone was previously idle.
         *
         * @param now The current timestamp when the flight starts, represented as an {@code Instant}.
         */
        synchronized void startFlight(Instant now) {
            if (lastIdleStart != null) {
                totalIdleMs += Duration.between(lastIdleStart, now).toMillis();
                lastIdleStart = null;
            }
            lastFlightStart = now;
        }

        /**
         * Ends the current flight session for the drone if one is active. Calculates and
         * accumulates the total flight time based on the duration between the last flight
         * start timestamp and the given timestamp. Resets the flight start timestamp to null
         * and updates the idle start timestamp to the provided time. Increments the counter
         * for total trips completed.
         *
         * @param now The current timestamp representing the time at which the flight ends,
         *            provided as an {@code Instant}.
         */
        synchronized void endFlight(Instant now) {
            if (lastFlightStart != null) {
                totalFlightMs += Duration.between(lastFlightStart, now).toMillis();
                lastFlightStart = null;
            }
            lastIdleStart = now;
            tripsCompleted++;
        }

        /**
         * Updates the position of the drone based on the provided coordinates. This method
         * calculates the distance travelled from the previous position to the new position
         * and adds it to the total distance travelled. It then updates the drone's last known
         * coordinates.
         *
         * @param x The new x-coordinate of the drone.
         * @param y The new y-coordinate of the drone.
         */
        synchronized void updatePosition(double x, double y) {
            double dx = x - lastX;
            double dy = y - lastY;
            totalDistanceTraveled += Math.hypot(dx, dy);
            lastX = x;
            lastY = y;
        }

        /**
         * Increments the fault count for the drone. This method is used to record a fault occurrence
         * during the drone's operation. Each invocation increases the fault count by one.
         */
        synchronized void recordFault() { faultCount++; }

        /**
         * Finalizes the current operational period for the drone by calculating and accumulating
         * the total idle and flight times based on the provided end timestamp. If the drone was in
         * an idle or flight state at the time of invocation, the time spent in that state is added
         * to the relevant metrics and the corresponding timestamps are reset.
         *
         * @param endTime The timestamp representing the end of the operational period, provided
         *                as an {@code Instant}.
         */
        synchronized void complete(Instant endTime) {
            if (lastIdleStart != null) {
                totalIdleMs += Duration.between(lastIdleStart, endTime).toMillis();
                lastIdleStart = null;
            }
            if (lastFlightStart != null) {
                totalFlightMs += Duration.between(lastFlightStart, endTime).toMillis();
                lastFlightStart = null;
            }
        }
    }

    /**
     * Marks the start of the simulation by recording the current timestamp.
     * <p>
     * If the simulation start time has not been previously set, this method
     * initializes it to the current instant. This timestamp is used to calculate
     * the total simulation duration and other time-based metrics.
     */
    public void markSimulationStart() { if (simulationStartTime == null) { simulationStartTime = Instant.now(); } }

    /**
     * Marks the end of the simulation by recording the current timestamp and completing all drone metrics.
     * <p>
     * This method records the end time of the simulation using the current instant, which facilitates
     * the calculation of the total simulation duration and other time-based metrics.
     * It also iterates through all registered drone metrics and finalizes them using the simulation end time.
     */
    public void markSimulationEnd() {
        simulationEndTime = Instant.now();
        // Finalize all drone metrics
        for (DroneMetrics dm : droneMetrics.values()) { dm.complete(simulationEndTime); }
    }

    /**
     * Records the detection time of a fire event in the specified zone.
     * If a fire in the given zone has not been detected previously, this method adds the zone ID
     * to the fire detection map with the current timestamp and increments the total fire reports.
     *
     * @param report the incident being recorded
     */
    public void recordIncidentReported(IncidentReport report) {
        FireIncidentKey key = keyOf(report);
        Instant previous = fireDetectedTime.putIfAbsent(key, Instant.now());
        if (previous == null) {
            totalFiresReported.incrementAndGet();
            if (report.eventType() == EventType.DRONE_REQUEST) {
                totalServiceRequests.incrementAndGet();
            }
        }
    }

    /**
     * Records the time when a fire dispatch is initiated for the specified zone.
     * If this is the first dispatch for the given zone, the current timestamp is recorded.
     *
     * @param report the incident being dispatched
     */
    public void recordFireDispatched(IncidentReport report) {
        fireFirstDispatchTime.putIfAbsent(keyOf(report), Instant.now());
    }

    /**
     * Records the instant a drone first arrives at the fire zone.
     * This is used for the official <em>Event Response Time</em> metric
     * (event creation → drone arrives).
     */
    public void recordFireArrived(IncidentReport report) {
        fireFirstArrivalTime.putIfAbsent(keyOf(report), Instant.now());
    }

    /**
     * Records the time a fire has been extinguished in the specified zone.
     * Updates the corresponding extinguishment time and increments the total fires extinguished count.
     *
     * @param report the incident that was extinguished
     */
    public void recordFireExtinguished(IncidentReport report) {
        Instant previous = fireExtinguishedTime.putIfAbsent(keyOf(report), Instant.now());
        if (previous == null) {
            totalFiresExtinguished.incrementAndGet();
        }
    }

    private FireIncidentKey keyOf(IncidentReport report) {
        return new FireIncidentKey(report.time(), report.zoneId(), report.eventType(), report.severity().name());
    }

    /**
     * Samples the current incident queue length for queue-depth metrics.
     * Should be called whenever the queue is modified (offer / take).
     *
     * @param currentSize current number of pending incidents in the queue
     */
    public void recordQueueSample(int currentSize) {
        // Update max atomically
        int prev;
        do { prev = maxQueueLength.get(); }
        while (currentSize > prev && !maxQueueLength.compareAndSet(prev, currentSize));
        queueLengthSum.addAndGet(currentSize);
        queueSampleCount.incrementAndGet();
    }

    /**
     * Registers a drone for metric tracking in the simulation.
     * If the specified drone ID does not already exist in the metrics,
     * a new instance of {@code DroneMetrics} is created and associated with the drone ID.
     *
     * @param droneId the unique identifier of the drone to be registered
     */
    public void registerDrone(int droneId) { droneMetrics.putIfAbsent(droneId, new DroneMetrics(droneId)); }

    /**
     * Records the dispatch event of a drone identified by its unique ID.
     * Updates the drone's metrics to reflect the start of a new flight,
     * marking the current timestamp as the flight start time.
     *
     * @param droneId the unique identifier of the dispatched drone
     */
    public void recordDroneDispatched(int droneId) {
        DroneMetrics dm = droneMetrics.get(droneId);
        if (dm != null) { dm.startFlight(Instant.now()); }
    }

    /**
     * Records the idling state of a drone by marking the end of its flight time.
     * This method updates the {@code DroneMetrics} associated with the specified drone ID.
     * If the drone is tracked, it sets the current timestamp as the end of the flight.
     *
     * @param droneId the unique identifier of the drone whose idling state is being recorded
     */
    public void recordDroneIdle(int droneId) {
        DroneMetrics dm = droneMetrics.get(droneId);
        if (dm != null) { dm.endFlight(Instant.now()); }
    }

    /**
     * Updates the position of a drone identified by its unique ID.
     * This method retrieves the drone's metrics and updates its position
     * to the specified coordinates if the drone is registered in the system.
     *
     * @param droneId the unique identifier of the drone
     * @param x the X-coordinate of the drone's new position
     * @param y the Y-coordinate of the drone's new position
     */
    public void recordDronePosition(int droneId, double x, double y) {
        DroneMetrics dm = droneMetrics.get(droneId);
        if (dm != null) { dm.updatePosition(x, y); }
    }

    /**
     * Records a fault for the specified drone by updating its associated metrics.
     * If the drone is registered in the system, the fault count is increased.
     *
     * @param droneId the unique identifier of the drone for which the fault is being recorded
     */
    public void recordDroneFault(int droneId) {
        DroneMetrics dm = droneMetrics.get(droneId);
        if (dm != null) { dm.recordFault(); }
    }

    /**
     * Calculates and returns the duration of the simulation in milliseconds.
     * The duration is computed as the time elapsed between the simulation start time and
     * either the simulation end time (if available) or the current system time.
     *
     * @return the simulation duration in milliseconds. Returns 0 if the simulation start time is null.
     */
    public long getSimulationDurationMs() {
        if (simulationStartTime == null) return 0;
        Instant end = simulationEndTime != null ? simulationEndTime : Instant.now();
        return Duration.between(simulationStartTime, end).toMillis();
    }

    /**
     * Calculates the response time in milliseconds for a specified zone.
     *
     * @param zoneId the identifier of the zone for which the response time is to be computed
     * @return the time difference in milliseconds between fire detection and first arrival in the specified zone,
     *         or null if either timestamp is unavailable
     */
    public Long getResponseTimeMs(FireIncidentKey incidentKey) {
        Instant detected = fireDetectedTime.get(incidentKey);
        Instant arrived  = fireFirstArrivalTime.get(incidentKey);
        if (detected != null && arrived != null) { return Duration.between(detected, arrived).toMillis(); }
        return null;
    }

    /**
     * Calculates the time in milliseconds between when a fire is detected and the first dispatch occurs for a given zone.
     *
     * @param zoneId the identifier of the zone for which the dispatch time is being calculated
     * @return the dispatch time in milliseconds if both the detection and dispatch times are available; null otherwise
     */
    public Long getDispatchTimeMs(FireIncidentKey incidentKey) {
        Instant detected   = fireDetectedTime.get(incidentKey);
        Instant dispatched = fireFirstDispatchTime.get(incidentKey);
        if (detected != null && dispatched != null) { return Duration.between(detected, dispatched).toMillis(); }
        return null;
    }

    /**
     * Calculates the time in milliseconds between when a fire was detected and extinguished
     * for a specified zone.
     *
     * @param zoneId the identifier of the zone for which the extinguished time is to be calculated
     * @return the extinguished time in milliseconds if both detection and extinguished times are available;
     *         otherwise, returns null
     */
    public Long getExtinguishTimeMs(FireIncidentKey incidentKey) {
        Instant detected = fireDetectedTime.get(incidentKey);
        Instant extinguished = fireExtinguishedTime.get(incidentKey);
        if (detected != null && extinguished != null) { return Duration.between(detected, extinguished).toMillis(); }
        return null;
    }

    /**
     * Calculates the average response time in milliseconds across all zones
     * that have a recorded response time.
     *
     * @return the average response time in milliseconds as a double.
     * Returns 0 if no response times are available.
     */
    public double getAverageResponseTimeMs() {
        long total = 0;
        int count = 0;
        for (FireIncidentKey incidentKey : fireFirstArrivalTime.keySet()) {
            Long rt = getResponseTimeMs(incidentKey);
            if (rt != null) {
                total += rt;
                count++;
            }
        }
        return count > 0 ? (double) total / count : 0;
    }

    /**
     * Calculates and returns the maximum response time in milliseconds
     * across all zones based on their fire first arrival times.
     *
     * @return the maximum response time in milliseconds, or 0 if no zones are available or all response times are null
     */
    public long getMaxResponseTimeMs() {
        long max = 0;
        for (FireIncidentKey incidentKey : fireFirstArrivalTime.keySet()) {
            Long rt = getResponseTimeMs(incidentKey);
            if (rt != null && rt > max) max = rt;
        }
        return max;
    }

    /**
     * Calculates the average extinguished time in milliseconds based on the recorded fire extinguish times.
     * <p>
     * The method iterates through all available fire extinguished time entries, sums up the values,
     * and computes the average. If no extinguished times are recorded, the method returns 0.
     *
     * @return the average extinguished time in milliseconds as a double. Returns 0 if no entries are present.
     */
    public double getAverageExtinguishTimeMs() {
        long total = 0;
        int count = 0;
        for (FireIncidentKey incidentKey : fireExtinguishedTime.keySet()) {
            Long et = getExtinguishTimeMs(incidentKey);
            if (et != null) {
                total += et;
                count++;
            }
        }
        return count > 0 ? (double) total / count : 0;
    }

    /**
     * Retrieves the maximum extinguished time in milliseconds across all zones.
     * <p>
     * This method iterates through all the zone identifiers and finds the maximum
     * extinguished time from the fireExtinguishedTime map. If no extinguished times
     * are available or all times are null, the method returns 0.
     *
     * @return the maximum extinguished time in milliseconds, or 0 if no valid times are found.
     */
    public long getMaxExtinguishTimeMs() {
        long max = 0;
        for (FireIncidentKey incidentKey : fireExtinguishedTime.keySet()) {
            Long et = getExtinguishTimeMs(incidentKey);
            if (et != null && et > max) max = et;
        }
        return max;
    }

    /**
     * Calculates and returns the average queue length based on the total sum of queue lengths
     * and the number of samples collected.
     *
     * @return the average queue length as a double value; returns 0.0 if no samples have been collected.
     */
    public double getAverageQueueLength() {
        long samples = queueSampleCount.get();
        return samples > 0 ? (double) queueLengthSum.get() / samples : 0.0;
    }

    /**
     * Returns the maximum length of the queue.
     *
     * @return the maximum queue length as an integer
     */
    public int getMaxQueueLength() { return maxQueueLength.get(); }

    /**
     * Calculates the average idle time of drones in milliseconds based on the
     * cumulative idle times and number of drones available in the system.
     *
     * @return the average idle time in milliseconds as a double. Returns 0 if there
     *         are no drones available to calculate the idle time.
     */
    public double getAverageDroneIdleTimeMs() {
        long total = 0;
        int count = 0;
        for (DroneMetrics dm : droneMetrics.values()) {
            total += dm.getTotalIdleMs();
            count++;
        }
        return count > 0 ? (double) total / count : 0;
    }

    /**
     * Calculates and returns the average flight time of drones in milliseconds.
     * The method iterates over all drone metrics, sums their total flight times,
     * and divides by the number of drones to compute the average. If no drones
     * are present, the method returns 0.
     *
     * @return The average drone flight time in milliseconds as a double.
     */
    public double getAverageDroneFlightTimeMs() {
        long total = 0;
        int count = 0;
        for (DroneMetrics dm : droneMetrics.values()) {
            total += dm.getTotalFlightMs();
            count++;
        }
        return count > 0 ? (double) total / count : 0;
    }

    /**
     * Calculates the total distance travelled by all drones in the fleet.
     * <p>
     * This method iterates through the collection of drone metrics and
     * sums up the total distance travelled by each drone to compute
     * the aggregate fleet distance.
     *
     * @return The total distance travelled by all drones in the fleet.
     */
    public double getTotalFleetDistance() {
        double total = 0;
        for (DroneMetrics dm : droneMetrics.values()) { total += dm.getTotalDistanceTraveled(); }
        return total;
    }

    /**
     * Calculates and returns the total number of trips completed by all drones.
     *
     * @return the sum of trips completed across all drone metrics.
     */
    public int getTotalTripsCompleted() {
        int total = 0;
        for (DroneMetrics dm : droneMetrics.values()) { total += dm.getTripsCompleted(); }
        return total;
    }

    /**
     * Calculates the total number of faults across all drone metrics.
     *
     * @return the sum of fault counts from all DroneMetrics instances.
     */
    public int getTotalFaults() {
        int total = 0;
        for (DroneMetrics dm : droneMetrics.values()) { total += dm.getFaultCount(); }
        return total;
    }

    /**
     * Returns the mean wall-clock time per event in milliseconds, derived from
     * total processing time divided by event count.
     * <p>
     * Note: This value is derived (total / count), not directly measured per event.
     * Individual per-event measurement is impractical because event replay uses
     * compressed timestamp-based spacing and the JVM clock resolution is still too
     * coarse to make sub-millisecond comparisons meaningful.
     *
     * @return mean ms per event, or 0 if no events were recorded
     */
    public double getMeanTimePerEventMs() { return inputFileEventCount > 0 ? (double) inputFileProcessingMs / inputFileEventCount : 0.0; }

    /**
     * Generates a comprehensive summary of simulation metrics, including information on
     * simulation overview, input file processing, response and completion times, drone
     * utilization, queue depth, per-fire breakdown, and drone statistics.
     * <p>
     * The returned summary is formatted as a human-readable string, intended to provide
     * a clear and structured overview of the simulation's performance and outcomes.
     * <p>
     * Metrics and data points in the summary include:
     * - Total simulation duration
     * - Number of fires reported and extinguished
     * - Drone-related statistics (trips, utilization, flight/idle times)
     * - Processing times for input file events
     * - Response and completion time metrics for events
     * - Queue depth details
     * - Zone-specific fire event details
     * - Total fleet distance
     *
     * @return A formatted string providing a detailed summary of simulation metrics.
     */
    public String getSummaryString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n").append("=".repeat(60)).append("\n");
        sb.append("              SIMULATION METRICS SUMMARY\n");
        sb.append("=".repeat(60)).append("\n");

        sb.append("\n--- Simulation Overview ---\n");
        sb.append(String.format("Total duration:          %.2f seconds%n", getSimulationDurationMs() / 1000.0));
        sb.append(String.format("Fires reported:          %d%n", totalFiresReported.get()));
        sb.append(String.format("Fires extinguished:      %d%n", totalFiresExtinguished.get()));
        sb.append(String.format("Service requests:        %d%n", totalServiceRequests.get()));
        sb.append(String.format("Total drone trips:       %d%n", getTotalTripsCompleted()));
        sb.append(String.format("Total faults:            %d%n", getTotalFaults()));

        if (inputFileEventCount > 0) {
            sb.append("\n--- Input File Processing ---\n");
            sb.append(String.format("Events processed:        %d%n", inputFileEventCount));
            sb.append(String.format("Total processing time:   %.2f seconds%n", inputFileProcessingMs / 1000.0));
            sb.append(String.format("Mean time per event:     %.1f ms/event%n", getMeanTimePerEventMs()));
            sb.append("  (mean is derived: total_wall_clock / event_count."
                    + " Individual event measurement is still dominated by replay timing and scheduler delays.)\n");
        }

        sb.append("\n--- Response & Completion Times (Required Metrics) ---\n");
        sb.append(String.format("1. Avg Event Response Time:     %.2f seconds%n", getAverageResponseTimeMs() / 1000.0));
        sb.append(String.format("2. Max Event Response Time:     %.2f seconds%n", getMaxResponseTimeMs() / 1000.0));
        sb.append(String.format("3. Avg Event Completion Time:   %.2f seconds%n", getAverageExtinguishTimeMs() / 1000.0));
        sb.append(String.format("4. Max Event Completion Time:   %.2f seconds%n", getMaxExtinguishTimeMs() / 1000.0));

        sb.append("\n5. Drone Utilization (Required Metric) ---\n");
        for (DroneMetrics dm : droneMetrics.values()) {
            long total = dm.getTotalFlightMs() + dm.getTotalIdleMs();
            sb.append(String.format("   Drone %d: %.1f%% utilization (flight=%.1fs, idle=%.1fs, total=%.1fs)%n",
                    dm.getDroneId(),
                    dm.getUtilizationPct(),
                    dm.getTotalFlightMs() / 1000.0,
                    dm.getTotalIdleMs() / 1000.0,
                    total / 1000.0));
        }

        sb.append("\n--- Queue Depth (Optional Metrics) ---\n");
        sb.append(String.format("6. Avg Queue Length:            %.2f events%n", getAverageQueueLength()));
        sb.append(String.format("7. Max Queue Length:            %d events%n", getMaxQueueLength()));

        sb.append("\n--- Per-Fire Breakdown ---\n");
        fireDetectedTime.keySet().stream()
                .sorted(Comparator.comparing(FireIncidentKey::time).thenComparingInt(FireIncidentKey::zoneId))
                .forEach(incidentKey -> {
            Long response   = getResponseTimeMs(incidentKey);
            Long dispatch   = getDispatchTimeMs(incidentKey);
            Long extinguish = getExtinguishTimeMs(incidentKey);
            sb.append(String.format("  Zone %d @ %s: dispatch=%.2fs, arrival=%.2fs, extinguish=%s%n",
                    incidentKey.zoneId(), incidentKey.time(),
                    dispatch    != null ? dispatch    / 1000.0 : 0,
                    response    != null ? response    / 1000.0 : 0,
                    extinguish  != null ? String.format("%.2fs", extinguish / 1000.0) : "pending"));
        });

        sb.append("\n--- Drone Statistics ---\n");
        sb.append(String.format("Avg flight time:         %.2f seconds%n", getAverageDroneFlightTimeMs() / 1000.0));
        sb.append(String.format("Avg idle time:           %.2f seconds%n", getAverageDroneIdleTimeMs() / 1000.0));
        sb.append(String.format("Total fleet distance:    %.1f units%n", getTotalFleetDistance()));

        sb.append("\n").append("=".repeat(60)).append("\n");
        return sb.toString();
    }

    /**
     * Formats live simulation metrics into a human-readable summary.
     * The output includes various statistics, such as simulation duration, fire response
     * metrics, drone utilization, queue depth, per-zone status, and drone statistics.
     * <p>
     * The formatted string is structured with section headers and corresponding metrics
     * to provide an overview of the current simulation state.
     * <p>
     * @return A string containing the formatted live simulation metrics.
     */
    public String formatForDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LIVE SIMULATION METRICS ===\n\n");

        sb.append("SIMULATION\n");
        sb.append(String.format("  Duration:        %.1f seconds%n", getSimulationDurationMs() / 1000.0));
        sb.append(String.format("  Fires reported:  %d%n", totalFiresReported.get()));
        sb.append(String.format("  Fires done:      %d%n", totalFiresExtinguished.get()));
        sb.append(String.format("  Requests:        %d%n", totalServiceRequests.get()));
        sb.append(String.format("  Trips made:      %d%n", getTotalTripsCompleted()));
        sb.append(String.format("  Total faults:    %d%n", getTotalFaults()));

        sb.append("\nREQUIRED METRICS\n");
        sb.append(String.format("  1. Avg response time:    %.2fs%n", getAverageResponseTimeMs() / 1000.0));
        sb.append(String.format("  2. Max response time:    %.2fs%n", getMaxResponseTimeMs() / 1000.0));
        sb.append(String.format("  3. Avg completion time:  %.2fs%n", getAverageExtinguishTimeMs() / 1000.0));
        sb.append(String.format("  4. Max completion time:  %.2fs%n", getMaxExtinguishTimeMs() / 1000.0));

        sb.append("\nDRONE UTILIZATION (req. metric 5)\n");
        for (DroneMetrics dm : droneMetrics.values()) {
            sb.append(String.format("  D%d: %.1f%% util (%.0fs active)%n",
                    dm.getDroneId(),
                    dm.getUtilizationPct(),
                    dm.getTotalFlightMs() / 1000.0));
        }
        sb.append("\nQUEUE DEPTH (optional)\n");
        sb.append(String.format("  6. Avg queue len: %.2f%n", getAverageQueueLength()));
        sb.append(String.format("  7. Max queue len: %d%n", getMaxQueueLength()));

        sb.append("\nPER-ZONE STATUS\n");
        fireDetectedTime.keySet().stream()
                .sorted(Comparator.comparing(FireIncidentKey::time).thenComparingInt(FireIncidentKey::zoneId))
                .forEach(incidentKey -> {
            Long response   = getResponseTimeMs(incidentKey);
            Long extinguish = getExtinguishTimeMs(incidentKey);
            String status   = extinguish != null ? String.format("%.1fs", extinguish / 1000.0) : "in progress";
            sb.append(String.format("  Zone %d @ %s: arrival=%.1fs, total=%s%n",
                    incidentKey.zoneId(), incidentKey.time(),
                    response != null ? response / 1000.0 : 0,
                    status));
        });
        sb.append("\nDRONE STATISTICS\n");
        sb.append(String.format("  Avg flight:      %.1fs%n", getAverageDroneFlightTimeMs() / 1000.0));
        sb.append(String.format("  Avg idle:        %.1fs%n", getAverageDroneIdleTimeMs() / 1000.0));
        sb.append(String.format("  Fleet distance:  %.0f units%n", getTotalFleetDistance()));

        sb.append("\nPER-DRONE BREAKDOWN\n");
        for (DroneMetrics dm : droneMetrics.values()) {
            sb.append(String.format("  D%d: %d trips, %.0fs flight, %.0fs idle, %.0f dist%s%n",
                    dm.getDroneId(),
                    dm.getTripsCompleted(),
                    dm.getTotalFlightMs() / 1000.0,
                    dm.getTotalIdleMs() / 1000.0,
                    dm.getTotalDistanceTraveled(),
                    dm.getFaultCount() > 0 ? ", " + dm.getFaultCount() + " faults" : ""));
        }
        return sb.toString();
    }
}
