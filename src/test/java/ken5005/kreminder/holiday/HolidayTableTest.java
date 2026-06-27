package ken5005.kreminder.holiday;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HolidayTableTest {

    @Test
    void isHoliday_knownDates() {
        HolidayTable table = new HolidayTable(Map.of(
            LocalDate.of(2026, 1, 1), "元日",
            LocalDate.of(2026, 1, 12), "成人の日"
        ));
        assertTrue(table.isHoliday(LocalDate.of(2026, 1, 1)));
        assertTrue(table.isHoliday(LocalDate.of(2026, 1, 12)));
        assertFalse(table.isHoliday(LocalDate.of(2026, 1, 2)));
    }

    @Test
    void getName_knownAndUnknown() {
        HolidayTable table = new HolidayTable(Map.of(
            LocalDate.of(2026, 1, 1), "元日"
        ));
        assertEquals("元日", table.getName(LocalDate.of(2026, 1, 1)));
        assertNull(table.getName(LocalDate.of(2026, 1, 2)));
    }
}
