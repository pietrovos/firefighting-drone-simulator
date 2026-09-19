package DroneSwarmSim.model;
/** Fire severity and the corresponding suppression-agent requirement. */
public enum Severity {
    LOW(10,"L"),
    MODERATE(20,"M"),
    HIGH(30, "H");
    private final int neededLitresOfWater;
    private final String label;

    Severity(int neededLitresOfWater, String l) {
        this.neededLitresOfWater = neededLitresOfWater;
        this.label = l;
    }

    public int getReqLitresOfWater() { return neededLitresOfWater; }

    public String getLabel(){
        return label;
    }
}
