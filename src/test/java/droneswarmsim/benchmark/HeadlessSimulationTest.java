package droneswarmsim.benchmark;

import droneswarmsim.benchmark.HeadlessSimulation.Result;
import droneswarmsim.scheduler.DispatchPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

@DisplayName("Headless Simulation Helper Tests")
class HeadlessSimulationTest {

    @Test
    @DisplayName("percentile uses nearest rank")
    void percentileNearestRank() {
        List<Long> values = List.of(50L, 10L, 40L, 20L, 30L);
        assertEquals(30, HeadlessSimulation.percentile(values, 50));
        assertEquals(50, HeadlessSimulation.percentile(values, 95));
        assertEquals(10, HeadlessSimulation.percentile(values, 1));
        assertEquals(0, HeadlessSimulation.percentile(List.of(), 95));
    }

    @Test
    @DisplayName("parseOptions reads key=value pairs and rejects anything else")
    void parseOptions() {
        Map<String, String> options = HeadlessSimulation.parseOptions(new String[]{"drones=10", "events=a=b.csv"});
        assertEquals("10", options.get("drones"));
        assertEquals("a=b.csv", options.get("events"), "Only the first '=' separates key and value");
        assertThrows(IllegalArgumentException.class, () -> HeadlessSimulation.parseOptions(new String[]{"drones"}));
    }

    @Test
    @DisplayName("Result survives a CSV round trip")
    void resultCsvRoundTrip() {
        Result result = new Result(DispatchPolicy.WATER_MATCHED, 20, true, 600_000, 50, 50,
                1234.5, 2000, 3000, 4567.8, 9000, 12_000, 70, 18, 5, 98765.4);
        assertEquals(result, Result.fromCsv(result.toCsv()));
    }
}
