package DroneSwarmSim.model;
/**
 * Represents a rectangular zone in a 2D coordinate system, defined by an origin
 * point (xOrigin, yOrigin) and an end point (xEnd, yEnd). A zone ID uniquely
 * identifies each zone.
 * <p>
 * This class provides methods to calculate the dimensions of the zone, determine
 * its centre position, and check whether it aligns to the 100m display grid.
 */
public record Zone(int zoneID, int xOrigin, int yOrigin, int xEnd, int yEnd) {

    /**
     * Calculates the width of the rectangular zone in meters.
     * The width is determined as the difference between the x-coordinate
     * of the end point (xEnd) and the x-coordinate of the origin point (xOrigin).
     *
     * @return The width of the zone in meters, as an integer.
     *         A positive value indicates a valid width, while a non-positive
     *         value signifies an invalid or degenerate zone definition.
     */
    public int widthMeters() { return xEnd - xOrigin; }

    /**
     * Calculates the height of the rectangular zone in meters.
     * The height is determined as the difference between the y-coordinate
     * of the end point (yEnd) and the y-coordinate of the origin point (yOrigin).
     *
     * @return The height of the zone in meters, as an integer.
     *         A positive value indicates a valid height, while a non-positive
     *         value signifies an invalid or degenerate zone definition.
     */
    public int heightMeters() { return yEnd - yOrigin; }

    /**
     * Calculates the x-coordinate of the centre of the rectangular zone.
     * The centre x-coordinate is determined by adding half the zone's width
     * to the x-coordinate of the origin point.
     *
     * @return The x-coordinate of the zone's centre as a double, representing
     *         the midpoint of the zone along the x-axis.
     */
    public double centerX() { return xOrigin + (widthMeters() / 2.0); }

    /**
     * Calculates the y-coordinate of the centre of the rectangular zone.
     * The centre y-coordinate is determined by adding half the height of the zone
     * to the y-coordinate of the origin point.
     *
     * @return The y-coordinate of the zone's centre as a double, representing
     *         the midpoint of the zone along the y-axis.
     */
    public double centerY() { return yOrigin + (heightMeters() / 2.0); }

    /**
     * Determines whether the zone aligns to the simulation's 100m cell grid.
     * <p>
     * A valid zone requires:
     * 1. Positive width and height of the zone.
     * 2. Both width and height are divisible by 100.
     *
     * @return {@code true} if the zone can be rendered and targeted on the grid;
     *         {@code false} otherwise.
     */
    public boolean isGridAligned() {
        int width = widthMeters();
        int height = heightMeters();
        if (width <= 0 || height <= 0) { return false; }
        return width % 100 == 0 && height % 100 == 0;
    }
}
