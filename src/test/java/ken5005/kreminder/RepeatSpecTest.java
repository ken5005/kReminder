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

    // #2: 単位省略=分、rep=6h
    @Test
    void minuteNoSuffixAndHour() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 1, 8, 0);

        // rep=30 — digit only → MINUTE
        RepeatSpec min30 = RepeatSpec.parse("rep=30");
        assertEquals(LocalDateTime.of(2026, 6, 1, 8, 30), min30.next(base));

        // rep=6h — HOUR
        RepeatSpec hour6 = RepeatSpec.parse("rep=6h");
        assertEquals(LocalDateTime.of(2026, 6, 1, 14, 0), hour6.next(base));
    }

    // #3: rep=1d;ex=0,6 — 週末除外（金曜→月曜へスキップ）
    @Test
    void excludeWeekend() {
        // 2026-06-05 = Friday
        LocalDateTime base = LocalDateTime.of(2026, 6, 5, 8, 0);
        RepeatSpec spec = RepeatSpec.parse("rep=1d;ex=0,6");
        // +1d→Sat(excluded) +1d→Sun(excluded) +1d→Mon = 2026-06-08
        assertEquals(LocalDateTime.of(2026, 6, 8, 8, 0), spec.next(base));
    }

    // #4: rep=1d;in=2 — 指定曜日のみ（火曜→翌火曜）
    @Test
    void inDayOnly() {
        // 2026-06-02 = Tuesday
        LocalDateTime base = LocalDateTime.of(2026, 6, 2, 8, 0);
        RepeatSpec spec = RepeatSpec.parse("rep=1d;in=2");
        // next Tuesday = 2026-06-09
        assertEquals(LocalDateTime.of(2026, 6, 9, 8, 0), spec.next(base));
    }

    // #6: rep=1M;day=25 — 月次＋絶対日（起点は翌月）
    @Test
    void monthlyAbsDay() {
        // 2026-06-25: day already 25; next must be 2026-07-25
        LocalDateTime base = LocalDateTime.of(2026, 6, 25, 8, 0);
        RepeatSpec spec = RepeatSpec.parse("rep=1M;day=25");
        assertEquals(LocalDateTime.of(2026, 7, 25, 8, 0), spec.next(base));
    }

    // #5: rep=1d;in=4;dai=1,3 — 第1・第3木曜のみ
    @Test
    void inDayAndWeekOfMonth() {
        // 2026-06-01 = Monday (week 1)
        LocalDateTime base = LocalDateTime.of(2026, 6, 1, 8, 0);
        RepeatSpec spec = RepeatSpec.parse("rep=1d;in=4;dai=1,3");

        // first hit: 2026-06-04 Thu week-1
        LocalDateTime first = spec.next(base);
        assertEquals(LocalDateTime.of(2026, 6, 4, 8, 0), first);

        // second hit: 2026-06-18 Thu week-3 (week-2 Thu 2026-06-11 skipped)
        assertEquals(LocalDateTime.of(2026, 6, 18, 8, 0), spec.next(first));
    }
}
