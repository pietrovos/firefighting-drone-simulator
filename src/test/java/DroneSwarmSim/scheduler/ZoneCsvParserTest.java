package DroneSwarmSim.scheduler;

import DroneSwarmSim.model.Zone;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

/**
 * Unit tests for the ZoneCsvParser functionality within the Scheduler class.
 * These tests validate the parsing of CSV files containing zone definitions
 * and ensure proper handling of various scenarios such as valid data, malformed
 * rows, missing files, and edge cases.
 * <p>
 * Test cases include:
 * <p>
 * 1. Parsing and loading zones from a valid CSV file.
 * 2. Verifying that fields for a loaded zone match the expected values.
 * 3. Checking the behaviour when an empty file with only a header is provided.
 * 4. Ensuring proper handling when the input CSV file is missing.
 * 5. Skipping rows in the CSV file that contain malformed or invalid data.
 * 6. Confirming accessibility of multiple zones loaded from the CSV file.
 * 7. Validating support for zones with a size of zero located at the origin.
 */
@DisplayName("Zone CSV Parser Tests")
class ZoneCsvParserTest {

    /**
     * Creates a temporary CSV file containing zone data based on the provided lines.
     * The file will be automatically deleted upon JVM termination.
     *
     * @param dataLines Lines of data to be written to the CSV file. Each line is expected to represent a zone record.
     * @return A {@link File} object representing the temporary CSV file.
     * @throws Exception If an error occurs while creating or writing to the file.
     */
    private File createZoneFile(String... dataLines) throws Exception {
        File f = File.createTempFile("zones_test", ".csv");
        f.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("Zone ID,Zone Start,Zone End");
            for (String line : dataLines) pw.println(line);
        }
        return f;
    }

    /**
     * Validates that the system correctly loads and counts valid zones from a CSV file.
     * <p>
     * This test creates a temporary CSV file containing zone data with valid records. Each record specifies
     * a zone ID and the coordinates defining the zone's area. The {@link Scheduler} is initialized with the path
     * to this CSV file and is then verified to have accurately loaded the expected number of zones.
     * <p>
     * The test ensures:
     * - The `createZoneFile` helper method is used to generate a temporary file with valid zone definitions.
     * - The `Scheduler` instance correctly loads and counts the zones from the given file.
     * - The total number of loaded zones matches the expected count (2 in this case).
     * <p>
     * @throws Exception If there is an error during file creation, writing, or initialization of the {@link Scheduler}.
     */
    @Test
    void testLoadsValidZones() throws Exception {
        File f = createZoneFile("1,(0;0),(700;700)", "2,(0;700),(700;1400)");
        Scheduler scheduler = new Scheduler(16010, f.getAbsolutePath());

        assertEquals(2, scheduler.getZoneCount(), "Two valid zones should be loaded");
    }

    /**
     * Verifies that the fields of Zone 1 are correctly parsed and initialized from the input CSV file.
     * <p>
     * This test ensures that:
     * - A temporary CSV file is created using the `createZoneFile` method with data defining Zone 1.
     * - A {@link Scheduler} instance is initialized with the path to the temporary file.
     * - Zone 1 can be retrieved from the scheduler using its zone ID.
     * - All expected fields of Zone 1 (zoneID, xOrigin, yOrigin, xEnd, and yEnd) are correctly populated.
     * - Assertions are made to validate the values of the fields against the input data.
     * <p>
     * @throws Exception If there is an error during file creation, writing, or initialization of the {@link Scheduler}.
     */
    @Test
    void testZoneOneFieldsAreCorrect() throws Exception {
        File f = createZoneFile("1,(10;20),(310;320)");
        Scheduler scheduler = new Scheduler(16011, f.getAbsolutePath());

        Zone z = scheduler.getZone(1);
        assertNotNull(z, "Zone 1 must exist");
        assertEquals(1,   z.zoneID());
        assertEquals(10,  z.xOrigin(), "xOrigin should be 10");
        assertEquals(20,  z.yOrigin(), "yOrigin should be 20");
        assertEquals(310, z.xEnd(),   "xEnd should be 310");
        assertEquals(320, z.yEnd(),  "yEnd should be 320");
    }

    /**
     * Validates that an empty CSV file, containing only the header row, is processed correctly
     * and results in no zones being loaded.
     * <p>
     * This test ensures the following:
     * - A temporary CSV file is created using the `createZoneFile` method, containing only
     *   the header row ("Zone ID, Zone Start, Zone End").
     * - A {@link Scheduler} instance is initialized using the path to the empty file.
     * - The scheduler's zone count is zero, as no data rows are present in the file.
     * <p>
     * Assertions:
     * - `Scheduler#getZoneCount` must return 0 to confirm that no zones are loaded for an
     *   empty file.
     * <p>
     * @throws Exception If there is an error during file creation, writing, or initialization
     *                   of the {@link Scheduler}.
     */
    @Test
    void testEmptyFileProducesZeroZones() throws Exception {
        File f = createZoneFile(); // header only
        Scheduler scheduler = new Scheduler(16012, f.getAbsolutePath());

        assertEquals(0, scheduler.getZoneCount(), "No zones expected for header-only file");
    }

    /**
     * Ensures that a missing CSV file is handled gracefully and results in zero zones being loaded.
     * <p>
     * This test verifies the following:
     * - When a {@link Scheduler} instance is initialized with a path pointing to a non-existent file,
     *   the system does not throw an exception.
     * - The scheduler's zone count is zero, as no zones can be loaded from a missing file.
     * <p>
     * Assertions:
     * - `Scheduler#getZoneCount` must return 0 when initialized with a non-existent file.
     */
    @Test
    void testMissingFileProducesZeroZones() {
        // Non-existent file path
        Scheduler scheduler = new Scheduler(16013, "/tmp/does_not_exist_zone.csv");
        assertEquals(0, scheduler.getZoneCount(), "Missing file should produce 0 zones");
    }

    /**
     * Validates that malformed rows in the CSV file are skipped during processing,
     * while valid rows are still correctly loaded.
     * <p>
     * This test creates a temporary CSV file containing zone data, including
     * - A valid row defining a zone with proper coordinates.
     * - A malformed row missing the end coordinate, which should be skipped by the parser.
     * <p>
     * The test ensures that:
     * - Malformed rows are ignored and do not contribute to the total zone count.
     * - Valid rows are still loaded despite the presence of malformed rows.
     * <p>
     * Assertions:
     * - The total zone count is 1, as only a single valid row is present.
     * - The valid zone (Zone 1) is accessible in the scheduler.
     *
     * @throws Exception If there is an error during file creation, writing, or initialization of the {@link Scheduler}.
     */
    @Test
    void testMalformedRowIsSkipped() throws Exception {
        // Row with missing end coordinate — should be skipped; valid row still loaded
        File f = createZoneFile("1,(0;0),(100;100)", "bad_row_no_coords");
        Scheduler scheduler = new Scheduler(16014, f.getAbsolutePath());

        assertEquals(1, scheduler.getZoneCount(), "Malformed row should be skipped");
        assertNotNull(scheduler.getZone(1), "Valid zone 1 must still be loaded");
    }

    /**
     * Validates that multiple zones with different IDs are correctly loaded, accessible,
     * and retrievable from the {@link Scheduler} after initialization with a valid CSV file.
     * <p>
     * This test creates a temporary CSV file using the `createZoneFile` method,
     * containing three zone definitions with distinct zone IDs and coordinates.
     * The {@link Scheduler} is then initialized with the CSV file path, and
     * its ability to correctly load and retrieve zones is verified.
     * <p>
     * The test ensures:
     * - The total number of loaded zones matches the expected count (3 in this case).
     * - Specific zones with IDs (1, 3, and 7) are accessible
     *   via the scheduler's `getZone` method and are not null.
     * - A non-existent zone with ID 2 is confirmed to be absent.
     * <p>
     * Assertions:
     * - The `Scheduler#getZoneCount` method correctly returns 3.
     * - The `Scheduler#getZone` method does not return null for loaded zone IDs: 1, 3, and 7.
     * - The `Scheduler#getZone` returns null for non-existent zone ID 2.
     *
     * @throws Exception If there is an error during file creation, writing, or initialization of the {@link Scheduler}.
     */
    @Test
    void testMultipleZoneIdsAccessible() throws Exception {
        File f = createZoneFile("1,(0;0),(100;100)", "3,(200;200),(300;300)", "7,(400;0),(500;100)");
        Scheduler scheduler = new Scheduler(16015, f.getAbsolutePath());

        assertEquals(3, scheduler.getZoneCount());
        assertNotNull(scheduler.getZone(1));
        assertNotNull(scheduler.getZone(3));
        assertNotNull(scheduler.getZone(7));
        assertNull(scheduler.getZone(2), "Zone 2 was never loaded");
    }

    @Test
    void testEvenCellCountsStillLoadWhenGridAligned() throws Exception {
        File f = createZoneFile("2,(900;0),(2500;900)", "3,(0;900),(1200;1800)");
        Scheduler scheduler = new Scheduler(16017, f.getAbsolutePath());

        assertEquals(2, scheduler.getZoneCount(), "Grid-aligned zones should load even when a dimension has an even cell count");
        assertNotNull(scheduler.getZone(2));
        assertNotNull(scheduler.getZone(3));
    }

    /**
     * Verifies that a zone defined at the origin with zero dimensions is correctly identified as valid.
     * <p>
     * This test creates a temporary CSV file containing a single zone definition where:
     * - The zone's origin is at (0, 0).
     * - Both the xEnd and yEnd of the zone are 0.
     * <p>
     * The {@link Scheduler} is initialized with the path to this CSV file, and the following validations are performed:
     * - The scheduler accurately reports the total number of zones as 1 after parsing the file.
     * - The zone with ID 1 can be retrieved and is not null.
     * - The retrieved zone's origin is at (0, 0), and both its xEnd and yEnd are set to 0.
     * <p>
     * Assertions:
     * - `Scheduler#getZoneCount` returns 1, confirming that one zone is loaded.
     * - `Scheduler#getZone(1)` returns a non-null zone object.
     * - The retrieved zone's `xOrigin`, `yOrigin`, `xEnd`, and `yEnd` match the expected values.
     *
     * @throws Exception If there is an error during file creation, writing, or initialization of the {@link Scheduler}.
     */
    @Test
    void testZeroSizeZoneAtOriginIsRejected() throws Exception {
        File f = createZoneFile("1,(0;0),(0;0)");
        Scheduler scheduler = new Scheduler(16016, f.getAbsolutePath());

        assertEquals(0, scheduler.getZoneCount());
        assertNull(scheduler.getZone(1));
    }
}
