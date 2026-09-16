package DroneSwarmSim.ui;

import DroneSwarmSim.drone.DroneState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GUI View Demo tests – verify that the {@link StubGUI} correctly tracks and exposes
 * the information that the visual panel must display: DroneId assignment, ZoneId,
 * drone State, position, and water level.
 *
 * <p>Tests run in headless mode using {@link StubGUI}, which records every
 * GUI callback in volatile fields without rendering any Swing components.
 */
@DisplayName("GUI View Demo – state-tracking tests")
class GUITest {

    private StubGUI gui;

    @BeforeEach
    void setUp() {
        gui = new StubGUI();
    }

    /**
     * Verifies that {@code updateDroneState} records the most recent drone state,
     * cycling through a typical mission sequence: IDLE → EN_ROUTE → DROPPING_AGENT → IDLE.
     */
    @Test
    @DisplayName("GUI tracks drone state through a mission cycle")
    void testDroneStateTracking() {
        assertNull(gui.lastDroneState, "Initial state should be null before any update");

        gui.updateDroneState(1, DroneState.IDLE);
        assertEquals(DroneState.IDLE, gui.lastDroneState, "State should reflect IDLE");

        gui.updateDroneState(1, DroneState.EN_ROUTE);
        assertEquals(DroneState.EN_ROUTE, gui.lastDroneState, "State should reflect EN_ROUTE");

        gui.updateDroneState(1, DroneState.DROPPING_AGENT);
        assertEquals(DroneState.DROPPING_AGENT, gui.lastDroneState, "State should reflect DROPPING_AGENT");

        gui.updateDroneState(1, DroneState.IDLE);
        assertEquals(DroneState.IDLE, gui.lastDroneState, "State should return to IDLE after mission");
    }

    /**
     * Verifies that {@code updateDronePosition} records the latest (x, y) coordinates,
     * simulating a drone moving toward its target zone.
     */
    @Test
    @DisplayName("GUI tracks drone position updates")
    void testDronePositionTracking() {
        assertTrue(Double.isNaN(gui.lastX), "Initial X should be NaN");
        assertTrue(Double.isNaN(gui.lastY), "Initial Y should be NaN");

        gui.updateDronePosition(1, 50.0, 75.0);
        assertEquals(50.0, gui.lastX, 0.001, "X position should be updated");
        assertEquals(75.0, gui.lastY, 0.001, "Y position should be updated");

        gui.updateDronePosition(1, 100.0, 200.0);
        assertEquals(100.0, gui.lastX, 0.001, "X position should reflect latest value");
        assertEquals(200.0, gui.lastY, 0.001, "Y position should reflect latest value");
    }

    /**
     * Verifies that {@code updateDroneWater} records the current water level, allowing the
     * panel to display remaining capacity.
     */
    @Test
    @DisplayName("GUI tracks drone water level")
    void testDroneWaterTracking() {
        assertEquals(-1.0, gui.lastWater, 0.001, "Initial water should be -1 (sentinel)");

        gui.updateDroneWater(1, 15.0);
        assertEquals(15.0, gui.lastWater, 0.001, "Water level should be 15 after first update");

        gui.updateDroneWater(1, 0.0);
        assertEquals(0.0, gui.lastWater, 0.001, "Water level should be 0 when depleted");

        gui.updateDroneWater(1, 15.0);
        assertEquals(15.0, gui.lastWater, 0.001, "Water level should be 15 after refill");
    }

    /**
     * Verifies that {@code incrementActiveFires} / {@code decrementActiveFires} maintain
     * an accurate count of ongoing fires, which drives the fire-tile overlay on the panel.
     */
    @Test
    @DisplayName("GUI tracks active fire count")
    void testActiveFireCountTracking() {
        assertEquals(0, gui.activeFireCount, "No fires initially");

        gui.incrementActiveFires();
        gui.incrementActiveFires();
        assertEquals(2, gui.activeFireCount, "Two fires incremented");

        gui.decrementActiveFires();
        assertEquals(1, gui.activeFireCount, "One fire after decrement");

        gui.decrementActiveFires();
        assertEquals(0, gui.activeFireCount, "No fires after second decrement");

        // Guard: count must never go negative.
        gui.decrementActiveFires();
        assertEquals(0, gui.activeFireCount, "Count must not go below zero");
    }

    /**
     * Verifies that {@code logEvent} appends every message to the running log buffer,
     * so the GUI's event-log panel always shows the full history.
     */
    @Test
    @DisplayName("GUI accumulates event log entries")
    void testEventLogging() {
        assertEquals(0, gui.allLogs.length(), "Log buffer empty initially");

        gui.logEvent("Drone 1 assigned to Zone 3");
        gui.logEvent("Drone 1 en route");
        gui.logEvent("Drone 1 returned to IDLE");

        assertTrue(gui.allLogs.toString().contains("Drone 1 assigned to Zone 3"),
                "Log buffer should contain first event");
        assertTrue(gui.allLogs.toString().contains("Drone 1 en route"),
                "Log buffer should contain second event");
        assertTrue(gui.allLogs.toString().contains("Drone 1 returned to IDLE"),
                "Log buffer should contain third event");
        assertEquals("Drone 1 returned to IDLE", gui.lastLog,
                "lastLog should reflect the most recent entry");
    }

    /**
     * Verifies that {@code clearFireTile} increments the clear-fire counter, confirming
     * that the GUI will remove extinguished fire markers from the map grid.
     */
    @Test
    @DisplayName("GUI tracks fire-tile clear events")
    void testClearFireTile() {
        assertEquals(0, gui.clearFireCount, "No tiles cleared initially");

        gui.clearFireTile(5, 10);
        assertEquals(1, gui.clearFireCount, "One tile cleared");

        gui.clearFireTile(6, 11);
        assertEquals(2, gui.clearFireCount, "Two tiles cleared");
    }
}
