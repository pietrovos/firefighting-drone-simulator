package DroneSwarmSim.scheduler;

import DroneSwarmSim.fire.EventType;
import DroneSwarmSim.messaging.IncidentReport;
import DroneSwarmSim.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimulationMetrics}.
 *
 * <p>Test IDs covered:
 * <ul>
 *   <li>UT-METRICS-DEDUP-01 – duplicate INCIDENT_REPORT for the same zone does not
 *       inflate the {@code totalFiresReported} counter.</li>
 *   <li>UT-METRICS-DEDUP-02 – duplicate COMPLETED updates for the same zone do not
 *       inflate the {@code totalFiresExtinguished} counter.</li>
 *   <li>UT-METRICS-COUNT-01 – distinct zones each contribute exactly one count to
 *       both reported and extinguished totals.</li>
 * </ul>
 */
@DisplayName("SimulationMetrics Unit Tests")
class SimulationMetricsTest {

    private IncidentReport report(String time, int zoneId) {
        return new IncidentReport(time, zoneId, EventType.FIRE_DETECTED, Severity.LOW);
    }

    /**
     * UT-METRICS-DEDUP-01 – recording the same incident more than once must only increment the fires-reported counter
     * once, regardless of how many duplicate calls arrive (e.g. from duplicate UDP packets).
     */
    @Test
    @DisplayName("UT-METRICS-DEDUP-01: duplicate incident reports do not over-count")
    void duplicateFireDetectedDoesNotOverCount() {
        SimulationMetrics metrics = new SimulationMetrics();
        IncidentReport report = report("14:00:00", 1);

        metrics.recordIncidentReported(report);
        metrics.recordIncidentReported(report);
        metrics.recordIncidentReported(report);

        String summary = metrics.getSummaryString();
        assertTrue(summary.contains("Fires reported:          1"),
                "Fires reported must be 1 even after 3 calls for the same zone; summary was:\n" + summary);
    }

    /**
     * UT-METRICS-DEDUP-02 – recording the same extinguished incident more than once must only increment the fires-extinguished counter
     * once, preventing inflation when duplicate COMPLETED packets arrive over UDP.
     */
    @Test
    @DisplayName("UT-METRICS-DEDUP-02: duplicate recordFireExtinguished does not over-count")
    void duplicateFireExtinguishedDoesNotOverCount() {
        SimulationMetrics metrics = new SimulationMetrics();
        IncidentReport report = report("14:05:00", 5);

        metrics.recordFireExtinguished(report);
        metrics.recordFireExtinguished(report);
        metrics.recordFireExtinguished(report);

        String summary = metrics.getSummaryString();
        assertTrue(summary.contains("Fires extinguished:      1"),
                "Fires extinguished must be 1 even after 3 calls for the same zone; summary was:\n" + summary);
    }

    /**
     * UT-METRICS-COUNT-01 – each unique zone ID contributes exactly one unit to the
     * fires-reported and fires-extinguished counters; distinct zones are counted
     * independently and the totals accumulate correctly.
     */
    @Test
    @DisplayName("UT-METRICS-COUNT-01: distinct zones each counted once in totals")
    void distinctZonesCountedSeparately() {
        SimulationMetrics metrics = new SimulationMetrics();

        IncidentReport zone1 = report("14:00:00", 1);
        IncidentReport zone2 = report("14:01:00", 2);
        IncidentReport zone3 = report("14:02:00", 3);
        metrics.recordIncidentReported(zone1);
        metrics.recordIncidentReported(zone2);
        metrics.recordIncidentReported(zone3);
        metrics.recordIncidentReported(zone2);

        metrics.recordFireExtinguished(zone1);
        metrics.recordFireExtinguished(zone3);
        metrics.recordFireExtinguished(zone3);

        String summary = metrics.getSummaryString();
        assertTrue(summary.contains("Fires reported:          3"),
                "Three distinct zones should give fires-reported=3; summary:\n" + summary);
        assertTrue(summary.contains("Fires extinguished:      2"),
                "Two distinct extinguished zones should give fires-extinguished=2; summary:\n" + summary);
    }
}
