package droneswarmsim.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MapLabelTextTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "<html><center>D5<br>15L B96 F85</center></html>|D5",
            "<html><center>D12<br>0L</center></html>|D12",
            "D3: DRONE_STUCK|D3",
            "D7|D7",
            "H|H",
            "''|''",
    })
    void mapCellsShowPlainText(String label, String expected) {
        assertEquals(expected, GUI.compactTileText(label));
    }
}
