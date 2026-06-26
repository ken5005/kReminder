package ken5005.kreminder;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RepeatSpecTest {

    // #1: rep=1d / rep=7d — 最小（毎日・週次）
    @Test
    void dailyAndWeekly() {
        // 2026-06-01 = Monday
        LocalDateTime base = LocalDateTime.of(2026, 6, 1, 8, 0);

        RepeatSpec daily = RepeatSpec.parse("rep=1d");
        assertEquals(LocalDateTime.of(2026, 6, 2, 8, 0), daily.next(base));

        RepeatSpec weekly = RepeatSpec.parse("rep=7d");
        assertEquals(LocalDateTime.of(2026, 6, 8, 8, 0), weekly.next(base));
    }
}
