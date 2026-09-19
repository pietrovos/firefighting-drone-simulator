package DroneSwarmSim.ui;

import java.awt.Color;

public enum TileTypes {
    NEUTRAL(new Color(55, 60, 70)),
    FIRE(new Color(235, 87, 60)),
    FIRE_EXTINGUISHED(new Color(39, 174, 96)),
    DRONE_LOCATION(new Color(52, 152, 219)),
    DRONE_FAULTED(new Color(233, 30, 99)),
    DRONE_DROPPING(new Color(0, 188, 212));

    private final Color color;

    TileTypes(Color c) {
        this.color = c;
    }

    public Color getColor() {
        return color;
    }
}
