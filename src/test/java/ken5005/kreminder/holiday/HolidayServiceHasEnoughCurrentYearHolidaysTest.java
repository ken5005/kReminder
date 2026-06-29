package ken5005.kreminder.holiday;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HolidayServiceHasEnoughCurrentYearHolidaysTest {

    private static Map<LocalDate, String> buildMap(int year, int count) {
        Map<LocalDate, String> map = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            map.put(LocalDate.of(year, 1, i), "holiday" + i);
        }
        return map;
    }

    @Test
    void exactlyMin_returnsTrue() {
        Map<LocalDate, String> m = buildMap(2026, 12);
        assertTrue(HolidayService.hasEnoughCurrentYearHolidays(m, 2026, 12));
    }

    @Test
    void moreThanMin_returnsTrue() {
        Map<LocalDate, String> m = buildMap(2026, 16);
        assertTrue(HolidayService.hasEnoughCurrentYearHolidays(m, 2026, 12));
    }

    @Test
    void lessThanMin_returnsFalse() {
        Map<LocalDate, String> m = buildMap(2026, 11);
        assertFalse(HolidayService.hasEnoughCurrentYearHolidays(m, 2026, 12));
    }

    @Test
    void empty_returnsFalse() {
        assertFalse(HolidayService.hasEnoughCurrentYearHolidays(Map.of(), 2026, 12));
    }

    @Test
    void onlyOtherYear_returnsFalse() {
        Map<LocalDate, String> m = buildMap(2025, 16);
        assertFalse(HolidayService.hasEnoughCurrentYearHolidays(m, 2026, 12));
    }

    @Test
    void mixedYears_countsOnlyTargetYear() {
        Map<LocalDate, String> m = new HashMap<>();
        // 2025: 16 entries, 2026: 11 entries
        for (int i = 1; i <= 16; i++) m.put(LocalDate.of(2025, 1, i), "h" + i);
        for (int i = 1; i <= 11; i++) m.put(LocalDate.of(2026, 1, i), "h" + i);
        assertFalse(HolidayService.hasEnoughCurrentYearHolidays(m, 2026, 12));
        assertTrue(HolidayService.hasEnoughCurrentYearHolidays(m, 2025, 12));
    }

    @Test
    void minZero_alwaysTrue() {
        assertTrue(HolidayService.hasEnoughCurrentYearHolidays(Map.of(), 2026, 0));
    }

    @Test
    void constantValue_is12() {
        assertEquals(12, HolidayService.MIN_CURRENT_YEAR_COUNT);
    }
}
