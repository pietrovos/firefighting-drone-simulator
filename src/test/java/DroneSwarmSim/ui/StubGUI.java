package DroneSwarmSim.ui;

import DroneSwarmSim.drone.DroneState;

/**
 * StubGUI is a specialized subclass of the GUI class that provides a lightweight,
 * non-functional implementation intended for testing and simulation purposes.
 * It overrides key methods of the parent GUI class with no-op or minimal implementations,
 * suppressing graphical output while maintaining internal state tracking.
 * <p>
 * This class is designed for scenarios where a headless GUI simulation is necessary,
 * such as in automated unit tests or simulation environments where the graphical interface
 * is not required or would interfere with automated processes.
 */
public class StubGUI extends GUI {

    public volatile DroneState lastDroneState = null;
    public volatile double lastWater = -1;
    public volatile double lastBattery = 100.0;
    public volatile double lastFuel = 100.0;
    public volatile double lastX = Double.NaN, lastY = Double.NaN;
    public volatile int activeFireCount = 0;
    public volatile String lastLog = "";
    public volatile StringBuffer allLogs = new StringBuffer();
    public volatile int clearFireCount = 0;

    public StubGUI() {
        super();
        if (mainWindow != null) {
            mainWindow.setVisible(false);
            mainWindow.dispose();
        }
    }

    /**
     * Overrides the start method to provide a no-op implementation in the StubGUI class.
     * This method is intentionally left empty to prevent the default GUI from being displayed,
     * making it suitable for headless unit testing scenarios.
     * <p>
     * The method replaces the functionality defined in the parent GUI class, which
     * by default makes the main application window visible. In StubGUI, this behaviour
     * is suppressed to allow simulation and testing without rendering a graphical interface.
     */
    @Override public void start() { /* no-op */ }

    /**
     * Overrides the tile update method with a no-op implementation for testing.
     * Suppresses all tile rendering in headless/test environments.
     *
     * @param t   The type of tile.
     * @param row The row index of the tile.
     * @param col The column index of the tile.
     */
    @Override public void updateTile(TileTypes t, int row, int col) { /* no-op */ }

    /**
     * Increments the internal counter for cleared fire tiles.
     * This method is part of the implementation for tracking when a fire tile has been cleared.
     *
     * @param row The row index of the fire tile in the grid.
     * @param col The column index of the fire tile in the grid.
     */
    @Override public void clearFireTile(int row, int col) { clearFireCount++; }

    /**
     * Updates the current operational state of the drone.
     * This method updates the internal tracking of the drone's state
     * based on the provided {@code DroneState} enumeration value.
     *
     * @param droneId The ID of the drone being updated.
     * @param state The new state of the drone. Valid values are defined
     *              in the {@code DroneState} enum and include states such as
     *              IDLE, EN_ROUTE, ARRIVED, DROPPING_AGENT, COMPLETED,
     *              RETURNING, REFILLING, and FAULTED.
     */
    @Override public void updateDroneState(int droneId, DroneState state) { lastDroneState = state; }

    /**
     * Updates the internal tracking of the drone's current water level.
     * This method sets the provided water level as the drone's latest water measurement.
     *
     * @param droneId The ID of the drone being updated.
     * @param water The amount of water currently available in the drone
     *              for firefighting or other operations. This value should
     *              represent the quantity in litres or an equivalent unit.
     */
    @Override public void updateDroneWater(int droneId, double water) { lastWater = water; }

    /**
     * Updates the current position of the drone in the simulation.
     * This method sets the latest known X and Y coordinates to the provided values.
     * It is typically used to simulate the movement or repositioning of the drone
     * within the grid or operational area.
     *
     * @param droneId The ID of the drone being updated.
     * @param x The new X-coordinate of the drone. Represents the horizontal position.
     * @param y The new Y-coordinate of the drone. Represents the vertical position.
     */
    @Override
    public void updateDronePosition(int droneId, double x, double y) {
        lastX = x;
        lastY = y;
    }

    /**
     * Increments the internal count of active fire tiles in the simulation.
     * This method is used to track the number of ongoing fires by increasing
     * the {@code activeFireCount} field by one.
     * <p>
     * It is typically called when a new fire has been detected or initialized
     * in the simulation, ensuring the system maintains an accurate count
     * for monitoring or operational purposes.
     */
    @Override public void incrementActiveFires() { activeFireCount++; }

    /**
     * Decrements the internal count of active fire tiles in the simulation.
     * This method reduces the value of the {@code activeFireCount} field by one,
     * but ensures that the count never drops below zero.
     * <p>
     * It is typically called when a fire has been successfully extinguished or
     * resolved in the simulation. This helps maintain an accurate count of active
     * fires for monitoring and operational tracking purposes.
     */
    @Override public void decrementActiveFires() { activeFireCount = Math.max(0, activeFireCount - 1); }

    /**
     * Logs an event message to the internal state of the GUI.
     * This method appends the provided message to an internal log buffer
     * and updates the most recent log message tracker.
     *
     * @param message The event message to be logged. This is typically a descriptive
     *                string detailing the event or operation that occurred within
     *                the system.
     */
    @Override
    public void logEvent(String message) {
        lastLog = message;
        allLogs.append(message).append("\n");
    }

    @Override public void showDroneOffline(int droneId) {
        logEvent("Drone " + droneId + " offline");
    }

    @Override
    public void updateDroneBatteryFuel(int droneId, double batteryPct, double fuelPct) {
        lastBattery = batteryPct;
        lastFuel = fuelPct;
    }
}
