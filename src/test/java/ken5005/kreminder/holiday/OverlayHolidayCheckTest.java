package ken5005.kreminder.holiday;

import ken5005.kreminder.HolidayCheck;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OverlayHolidayCheckTest {

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 5, 3);
    private static final LocalDate D3 = LocalDate.of(2026, 7, 20);

    // base with D1 as a holiday
    private static final HolidayCheck BASE = d -> d.equals(D1);

    @Test
    void emptyOverlay_passesThroughToBase() {
        OverlayHolidayCheck oc = new OverlayHolidayCheck(BASE, Map.of(), Set.of());
        assertTrue(oc.isHoliday(D1));
        assertFalse(oc.isHoliday(D2));
    }

    @Test
    void emptyOverlay_isEmpty() {
        assertTrue(new OverlayHolidayCheck(BASE, Map.of(), Set.of()).isEmpty());
        assertFalse(new OverlayHolidayCheck(BASE, Map.of(D2, "x"), Set.of()).isEmpty());
        assertFalse(new OverlayHolidayCheck(BASE, Map.of(), Set.of(D1)).isEmpty());
    }

    @Test
    void addMap_makesNonHolidayIntoHoliday() {
        OverlayHolidayCheck oc = new OverlayHolidayCheck(BASE, Map.of(D2, "extra"), Set.of());
        assertTrue(oc.isHoliday(D2));   // was not in base
        assertTrue(oc.isHoliday(D1));   // still from base
        assertFalse(oc.isHoliday(D3)); // neither base nor add
    }

    @Test
    void removeSet_winsOverBase() {
        // D1 is in base — remove should suppress it
        OverlayHolidayCheck oc = new OverlayHolidayCheck(BASE, Map.of(), Set.of(D1));
        assertFalse(oc.isHoliday(D1));
    }

    @Test
    void removeSet_winsOverAdd() {
        // D2 is both in add and remove — remove must win
        OverlayHolidayCheck oc = new OverlayHolidayCheck(BASE, Map.of(D2, "x"), Set.of(D2));
        assertFalse(oc.isHoliday(D2));
    }

    @Test
    void removeSet_winsOverBaseAndAdd_simultaneously() {
        // D1 in base, D2 in add — both removed
        OverlayHolidayCheck oc = new OverlayHolidayCheck(BASE, Map.of(D2, "x"), Set.of(D1, D2));
        assertFalse(oc.isHoliday(D1));
        assertFalse(oc.isHoliday(D2));
    }

    @Test
    void addLayersOnTopOfBase_noneIsBase() {
        OverlayHolidayCheck oc = new OverlayHolidayCheck(HolidayCheck.NONE, Map.of(D2, "added"), Set.of());
        assertFalse(oc.isHoliday(D1)); // NONE has no holidays
        assertTrue(oc.isHoliday(D2));  // from add
    }
}
