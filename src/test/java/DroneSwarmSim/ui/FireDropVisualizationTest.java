package DroneSwarmSim.ui;

import DroneSwarmSim.model.Zone;
import java.awt.Point;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FireDropVisualizationTest {
    @SuppressWarnings("unchecked")
    private Map<Integer, List<Point>> cells(GUI gui, String name) throws Exception {
        Field field = GUI.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Integer, List<Point>>) field.get(gui);
    }

    private StubGUI fire(String severity) {
        StubGUI gui = new StubGUI();
        gui.zones.put(1, new Zone(1, 0, 0, 900, 900));
        gui.showFireIncident(1, severity);
        gui.showDronePosition(7, 450, 450);
        return gui;
    }

    private void assertDropMatchesRemoval(String severity, int totalWater, int expectedRemoval) throws Exception {
        StubGUI gui = fire(severity);
        List<Point> before = new ArrayList<>(cells(gui, "fireSpreadCells").get(1));
        gui.showDroneDropping(7, 1, totalWater, totalWater, 15);
        List<Point> targets = cells(gui, "waterDropTargets").get(7);
        assertEquals(expectedRemoval, targets.size());
        assertFalse(targets.contains(gui.toGridCell(450, 450)), "Partial drops must leave the center burning");
        gui.shrinkFireSpread(1, totalWater - 15, totalWater);
        before.removeAll(cells(gui, "fireSpreadCells").get(1));
        assertEquals(before, targets, "Splash targets must exactly match the cells cleared by this drop");
        gui.clearDroneDropping(7);
        assertFalse(cells(gui, "waterDropTargets").containsKey(7));
    }

    @Test void moderateDropSplashesOnlyThreeOuterCells() throws Exception {
        assertDropMatchesRemoval("M", 20, 3);
    }

    @Test void highDropSplashesOnlyFourOuterCells() throws Exception {
        assertDropMatchesRemoval("H", 30, 4);
    }

    @Test void followUpDropClearsAllRemainingCells() throws Exception {
        StubGUI gui = fire("H");
        gui.shrinkFireSpread(1, 15, 30);
        List<Point> remaining = new ArrayList<>(cells(gui, "fireSpreadCells").get(1));
        assertEquals(5, remaining.size());
        gui.showDroneDropping(7, 1, 15, 30, 15);
        assertEquals(remaining, cells(gui, "waterDropTargets").get(7));
        gui.clearDroneDropping(7);
    }

    @Test void emptyTankDoesNotAnimateAnyFireCells() throws Exception {
        StubGUI gui = fire("M");
        gui.showDroneDropping(7, 1, 20, 20, 0);
        assertTrue(cells(gui, "waterDropTargets").get(7).isEmpty());
        gui.clearDroneDropping(7);
    }
}
