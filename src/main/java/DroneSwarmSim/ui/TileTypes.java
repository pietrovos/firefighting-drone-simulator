package DroneSwarmSim.ui;

import java.awt.*;

/**
 * The TileTypes enum represents the various classification types of tiles
 * that can be used within the application's graphical interface.
 * These tile types influence the visual appearance and behaviour
 * of grid elements in the GUI. Each type corresponds to a distinct
 * visual styling or functional purpose.
 * <p>
 * Enum constants:
 * - {@code NEUTRAL}: Represents a default, inactive state of a tile (light gray background).
 * - {@code FIRE}: Represents a tile that is affected by fire (red background).
 * - {@code FIRE_EXTINGUISHED}: Represents a tile where fire has been extinguished (green background).
 * - {@code DRONE_LOCATION}: Represents a tile that indicates the presence
 *   of a drone (orange background).
 * <p>
 * This enum is used in methods such as {@code GUI.updateTile(TileTypes, int, int)}
 * to dynamically change the appearance of tiles based on their type.
 */

public enum TileTypes {
    // Dark neutral that blends with grid background
    NEUTRAL(new Color(55, 60, 70)),
    // Bright orange-red for active fires - highly visible
    FIRE(new Color(235, 87, 60)),
    // Muted teal for extinguished fires
    FIRE_EXTINGUISHED(new Color(39, 174, 96)),
    // Bright blue for drones - easy to track
    DRONE_LOCATION(new Color(52, 152, 219)),
    // Pink/magenta for faulted drones - matches FAULT_STUCK
    DRONE_FAULTED(new Color(233, 30, 99)),
    // Cyan for drones actively dropping water - pulses with DRONE_LOCATION
    DRONE_DROPPING(new Color(0, 188, 212));

    private final Color color;

    TileTypes(Color c){
        this.color = c;
    }

    public Color getColor(){
        return color;
    }
}
