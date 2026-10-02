package droneswarmsim.scheduler;

/**
 * Controls how many drones the {@link Scheduler} sends to a burning zone at once.
 * <p>
 * Each drone carries a fixed load of agent and drops all of it on arrival, so a fire
 * that needs more water than one drone carries takes several drops.
 */
public enum DispatchPolicy {
    /**
     * One drone per zone at a time. A fire that needs more water than one load waits for
     * the drone to start returning, then queues for another drone. This was the original
     * scheduler behaviour.
     */
    SINGLE_DRONE,

    /**
     * Sends as many idle drones as the fire's remaining water requires, counting water
     * already carried by drones flying to the zone. Large fires get their drops in
     * parallel instead of one trip after another.
     */
    WATER_MATCHED;

    /**
     * Parses a policy name case-insensitively, accepting either {@code -} or {@code _}
     * as the word separator.
     *
     * @param name the policy name, for example {@code "water-matched"}
     * @return the matching policy
     * @throws IllegalArgumentException if no policy has that name
     */
    public static DispatchPolicy parse(String name) {
        return valueOf(name.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }
}
