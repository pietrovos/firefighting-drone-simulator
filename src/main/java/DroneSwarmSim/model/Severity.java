package DroneSwarmSim.model;
/**
 * Enumeration representing the severity levels of a fire and the corresponding amount of water needed
 * to extinguish it in a drone swarm simulation system.
 * <p>
 * The Severity enum defines three levels:
 * - LOW: Indicates a fire of low severity, requiring fewer resources to extinguish.
 * - MODERATE: Indicates a fire of moderate severity, requiring a moderate amount of resources to extinguish.
 * - HIGH: Indicates a fire of high severity, demanding significant resources to extinguish.
 * <p>
 * Each severity level is associated with the number of litres of water needed to handle the fire.
 * This information can be used to assess and allocate the necessary resources during fire suppression operations.
 */
public enum Severity {
    LOW(10,"L"),
    MODERATE(20,"M"),
    HIGH(30, "H");
    private final int neededLitresOfWater;
    private final String label;

    /**
     * Constructs an instance of the Severity enum with the specified amount of water needed.
     * Parameterized for dynamic initialization of the Severity.
     *
     * @param neededLitresOfWater the number of litres of water required to extinguish a fire of this severity level.
     * @param l label for updating tiles
     */
    Severity(int neededLitresOfWater, String l) {
        this.neededLitresOfWater = neededLitresOfWater;
        this.label = l;
    }

    /**
     * Retrieves the amount of water, in litres, required to extinguish a fire of this severity level.
     *
     * @return the number of litres of water needed for this severity level.
     */
    public int getReqLitresOfWater() { return neededLitresOfWater; }

    /**
     * Retrieves the label associated with this severity level.
     * The label is a shorthand representation of the severity level
     * used in the simulation, such as for updating tile states in the GUI.
     *
     * @return a string representing the label of this severity level.
     */
    public String getLabel(){
        return label;
    }
}
