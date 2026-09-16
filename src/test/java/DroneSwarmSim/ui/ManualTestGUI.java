package DroneSwarmSim.ui;

import DroneSwarmSim.drone.DroneState;
import DroneSwarmSim.model.Severity;
import DroneSwarmSim.model.Zone;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A GUI class designed specifically for manual testing. It extends the {@link GUI} base class and provides
 * implementations for updating drone states, fire tile management, event logging, and water level tracking.
 * Additionally, it allows for testing zone boundary visualization.
 * <p>
 * This class serves as a test harness for the {@link GUI} functionalities, including simulating fire tile updates,
 * drone position updates, and zone representation on the grid.
 */
public class ManualTestGUI extends GUI{

    /**
     * Constructs a new instance of the ManualTestGUI class and initializes the graphical user interface
     * specific to manual testing. This class serves as a specialized extension of the GUI for testing
     * purposes, retaining all standard GUI functionality while focusing on manual interventions
     * and debugging.
     *
     * @param zones map of zone IDs to {@link Zone} objects, representing the zones to be visualized and managed
     *              in the simulation. Each zone is drawn as an outline on the grid, with the map providing
     *              the zone definitions. If the map is {@code null}, it is treated as an empty map, and no
     *              zones will be displayed.
     */
    public ManualTestGUI(Map<Integer, Zone> zones) {
        super(zones);
    }

    /**
     * Clears the fire tile at the specified grid location, marking it as no longer on fire.
     * This method is typically used to update the state of a simulation or GUI when a fire
     * has been extinguished at a specific row and column of the grid.
     *
     * @param row The row index of the tile to be cleared.
     * @param col The column index of the tile to be cleared.
     */
    @Override public void clearFireTile(int row, int col) { }

    /**
     * Updates the current state of the drone in the simulation.
     * This method is typically invoked to reflect changes in the drone's
     * operational state, such as transitions between tasks or fault conditions,
     * and is used to synchronize the visual representation or internal logic
     * with the drone's actual status.
     *
     * @param droneId The ID of the drone being updated.
     * @param state The new operational state of the drone, as represented by
     *              the {@link DroneState} enumeration. This parameter defines
     *              the specific lifecycle stage of the drone, such as IDLE, EN_ROUTE,
     *              ARRIVED, DROPPING_AGENT, COMPLETED, RETURNING, REFILLING, or FAULTED.
     */
    @Override public void updateDroneState(int droneId, DroneState state) { }

    /**
     * Updates the current water level of the drone in the simulation.
     * This method is typically invoked when the water capacity of the drone changes,
     * such as during refilling or after releasing water during a fire suppression task.
     *
     * @param droneId The ID of the drone being updated.
     * @param water The new water level of the drone, represented as a double. This
     *              value indicates the current remaining water capacity of the drone.
     */
    @Override public void updateDroneWater(int droneId, double water) { }

    /**
     * Updates the current position of the drone in the simulation.
     * This method is typically used to reflect updates in the drone's
     * location, ensuring synchronization between the visual representation
     * or internal logic and the actual position of the drone.
     *
     * @param droneId The ID of the drone being updated.
     * @param x The new x-coordinate of the drone's position.
     * @param y The new y-coordinate of the drone's position.
     */
    @Override public void updateDronePosition(int droneId, double x, double y) { }

    /**
     * Increments the count of active fires in the simulation or GUI. This method
     * is typically called when a new fire is detected or manually added to the
     * simulation. It ensures that the internal counter tracking the number of
     * active fires is updated, which may affect visual representations, logical
     * operations, or system alerts related to fire events.
     */
    @Override public void incrementActiveFires() { }

    /**
     * Decrements the count of active fires in the simulation or GUI.
     * This method is typically invoked when a fire has been extinguished or manually removed from the simulation.
     * It ensures that the internal counter tracking of the number of active fires is updated, allowing for accurate
     * synchronization of visual representations, logical processes, and system monitoring related to fire events.
     */
    @Override public void decrementActiveFires() { }

    /**
     * Logs an event with the specified message. This method is typically used to record
     * significant actions, errors, or status updates during the operation of the GUI
     * or simulation. It may also serve as a mechanism for debugging or tracking system behavior.
     *
     * @param message The message detailing the event to be logged. This parameter provides
     *                context or description about the event being recorded, aiding in
     *                monitoring or diagnostics.
     */
    @Override public void logEvent(String message) { }

    /**
     * Entry point for the application. This method initializes and configures the GUI,
     * defines test zones, and performs various updates on the simulation for manual testing purposes.
     * <p>
     * The following actions are performed:
     * - Defines zones with specific identifiers and coordinates.
     * - Initializes a manual testing GUI with the defined zones.
     * - Tests various functionalities of the GUI by updating tile states with different types and severities.
     * - Simulates transitions or interactions between different components of the simulation.
     *
     * @param args Command-line arguments passed to the program. These arguments are not utilized in this method.
     */
    public static void main(String[] args) {

        /*
          zones used
          Zone ID,Zone Start,Zone End
          1,(0;0),(700;700)
          2,(0;700),(700;1600)
          3,(700;0),(1400;700)
          4,(700;700),(1400;1600)
         */

        Zone a = new Zone(1,0,0,700,700);
        Zone b = new Zone(2,0,700,700,1600);
        Zone c = new Zone(3,700,0,1400,700);
        Zone d = new Zone(4,700,700,1400,1600);

        //map of test zones to test border drawing
        Map<Integer, Zone> testZones = new ConcurrentHashMap<>();
        testZones.put(a.zoneID(), a);
        testZones.put(b.zoneID(), b);
        testZones.put(c.zoneID(), c);
        testZones.put(d.zoneID(), d);


        GUI g = new ManualTestGUI(testZones);

        //severity objects to test fire labels
        Severity low = Severity.LOW;
        Severity mid = Severity.MODERATE;
        Severity high = Severity.HIGH;

        g.start();

        //fire tiles test
        g.updateTile(TileTypes.FIRE, 2,2, low.getLabel());
        g.updateTile(TileTypes.FIRE, 3,3, mid.getLabel());
        g.updateTile(TileTypes.FIRE, 4,4, high.getLabel());
        g.updateTile(TileTypes.FIRE, 5,5);


        //update tile with and without labelData
        g.updateTile(TileTypes.DRONE_LOCATION, 1,1, "D"+1);
        g.updateTile(TileTypes.DRONE_LOCATION, 1,2);

        //make sure tiles can still update
        try { Thread.sleep(3000); }
        catch (InterruptedException e) { throw new RuntimeException(e); }

        //see what happens when a drone is drawn below a zone id
        g.updateTile(TileTypes.DRONE_LOCATION, 0,0, "D"+1);
        try { Thread.sleep(3000); }
        catch (InterruptedException e) { throw new RuntimeException(e); }

        //make sure the zone id isn't erased
        g.updateTile(TileTypes.NEUTRAL, 0,0, "");
    }
}
