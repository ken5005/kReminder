package ken5005.kreminder.holiday;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HolidayServiceShouldRefreshTest {

    @Test
    void nullFetchedAt_shouldRefresh() {
        assertTrue(HolidayService.shouldRefresh(null, LocalDateTime.of(2026, 6, 27, 10, 0)));
    }

    @Test
    void withinOneDay_shouldNotRefresh() {
        // fetched 2 hours ago
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 27, 8, 0);
        LocalDateTime now       = LocalDateTime.of(2026, 6, 27, 10, 0);
        assertFalse(HolidayService.shouldRefresh(fetchedAt, now));
    }

    @Test
    void exactlyOneDay_shouldRefresh() {
        // "1日以上前" = at least 1 day ago — boundary is inclusive
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 26, 10, 0);
        LocalDateTime now       = LocalDateTime.of(2026, 6, 27, 10, 0);
        assertTrue(HolidayService.shouldRefresh(fetchedAt, now));
    }

    @Test
    void moreThanOneDay_shouldRefresh() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 25, 8, 0);
        LocalDateTime now       = LocalDateTime.of(2026, 6, 27, 10, 0);
        assertTrue(HolidayService.shouldRefresh(fetchedAt, now));
    }

    @Test
    void oneSecondShortOfOneDay_shouldNotRefresh() {
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 6, 26, 10, 0, 1);
        LocalDateTime now       = LocalDateTime.of(2026, 6, 27, 10, 0, 0);
        assertFalse(HolidayService.shouldRefresh(fetchedAt, now));
    }
}
