package DroneSwarmSim.model;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for mapping severity levels to their corresponding water requirements.
 * The SeverityMappingTest class verifies the correctness of the Severity enum
 * implementation by testing the associated water requirements for each severity level
 * and ensuring the integrity of the enum structure.
 * <p>
 * Test cases include:
 * - Validation of water requirements for LOW, MODERATE, and HIGH severities.
 * - Verification that all defined severity levels are accounted for.
 * - Enforcement of strict ordering of water requirements across severity levels.
 */
@DisplayName("Severity Mapping Tests")
class SeverityMappingTest {

    /**
     * Verifies that the LOW severity level corresponds to a required water allocation of 10 litres.
     * <p>
     * This test ensures that the getReqLitresOfWater method in the Severity enum returns 10 litres
     * for the LOW severity level, in alignment with the expected behaviour as defined in the system design.
     * The assertion confirms the correctness of the mapping for the LOW severity level.
     */
    @Test
    void testLowRequiresTenLitres() {
        assertEquals(10, Severity.LOW.getReqLitresOfWater(),
                "LOW severity should require 10 L");
    }

    /**
     * Validates that the MODERATE severity level is correctly associated with a required water allocation
     * of 20 litres as specified in the Severity enum.
     * <p>
     * This test ensures that the getReqLitresOfWater method in the Severity enum returns 20 litres
     * for the MODERATE severity level. The assertion provides verification of the expected behaviour,
     * ensuring that the mapping for MODERATE severity is accurately implemented.
     */
    @Test
    void testModerateRequiresTwentyLitres() {
        assertEquals(20, Severity.MODERATE.getReqLitresOfWater(),
                "MODERATE severity should require 20 L");
    }

    /**
     * Ensures that the HIGH severity level is accurately associated with a water requirement of 30 litres.
     * <p>
     * This test verifies that the getReqLitresOfWater method in the Severity enum correctly returns 30 litres
     * for the HIGH severity level. The assertion confirms the expected mapping for the HIGH severity to its
     * corresponding water requirement, ensuring consistency with the system design.
     */
    @Test
    void testHighRequiresThirtyLitres() {
        assertEquals(30, Severity.HIGH.getReqLitresOfWater(),
                "HIGH severity should require 30 L");
    }

    /**
     * Verifies that all expected severity levels are defined within the Severity enum.
     * <p>
     * This test ensures that the Severity enum contains exactly three values: LOW, MODERATE, and HIGH.
     * The assertion confirms that the number of defined severity levels matches the expected count,
     * ensuring alignment with the system's design requirements.
     */
    @Test
    void testAllSeverityValuesPresent() {
        assertEquals(3, Severity.values().length, "Severity enum must have exactly 3 values");
    }

    /**
     * Validates that the required litres of water for different severity levels are strictly ordered.
     * <p>
     * This test ensures that the Severity levels are ranked in ascending order based on the amount
     * of water required for extinguishing a fire. Specifically:
     * - The LOW severity level must require less water than the MODERATE severity level.
     * - The MODERATE severity level must require less water than the HIGH severity level.
     * <p>
     * The assertions in this test verify the correctness of the ordering, ensuring consistency
     * with the intended design and behaviour of the Severity enum.
     */
    @Test
    void testRequiredLitresAreStrictlyOrdered() {
        assertTrue(Severity.LOW.getReqLitresOfWater() < Severity.MODERATE.getReqLitresOfWater(),
                "LOW should need fewer litres than MODERATE");
        assertTrue(Severity.MODERATE.getReqLitresOfWater() < Severity.HIGH.getReqLitresOfWater(),
                "MODERATE should need fewer litres than HIGH");
    }
}
