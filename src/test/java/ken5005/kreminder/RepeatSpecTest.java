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

    // #11: rep=1M;day=31 — 末日clamp（2月に31日は無い）
    @Test
    void endOfMonthClamp() {
        // from=2026-01-15: set to Jan-31, +1M → Feb-28 (2026 is not a leap year)
        LocalDateTime base = LocalDateTime.of(2026, 1, 15, 8, 0);
        RepeatSpec spec = RepeatSpec.parse("rep=1M;day=31");
        assertEquals(LocalDateTime.of(2026, 2, 28, 8, 0), spec.next(base));
    }

    // #10: parse 例外 — 未知命令 / day=N を月次以外で使う / kuriage を月次以外で使う
    @Test
    void parseExceptions() {
        // unknown command
        assertThrows(IllegalArgumentException.class,
            () -> RepeatSpec.parse("rep=1d;foo=3"));
        // day=N without monthly unit
        assertThrows(IllegalArgumentException.class,
            () -> RepeatSpec.parse("rep=1d;day=25"));
        // kuriage without monthly unit
        assertThrows(IllegalArgumentException.class,
            () -> RepeatSpec.parse("rep=1d;kuriage;ex=0"));
    }

    // 回帰: 月次 kuriage は従来どおり parse が通る
    @Test
    void monthlyKuriageStillParses() {
        assertDoesNotThrow(() -> RepeatSpec.parse("rep=1M;day=25;kuriage;ex=0,6"));
    }

    // #9: nextAfter — 取りこぼしスキップ
    @Test
    void catchUpSkip() {
        // rep=1d, from=06-01 08:00, now=06-05 10:00 → skip 06-02〜06-05 → 06-06 08:00
        RepeatSpec spec = RepeatSpec.parse("rep=1d");
        LocalDateTime from = LocalDateTime.of(2026, 6, 1, 8, 0);
        LocalDateTime now  = LocalDateTime.of(2026, 6, 5, 10, 0);
        assertEquals(LocalDateTime.of(2026, 6, 6, 8, 0), spec.nextAfter(from, now));
    }

    // #8: rep=1M;day=25;kuriage;ex=0,6 — 7/25は土→繰り上げで金曜へ
    @Test
    void monthlyRollUp() {
        // 2026-07-25 = Saturday → kuriage → 2026-07-24 Fri
        LocalDateTime base = LocalDateTime.of(2026, 6, 25, 8, 0);
        RepeatSpec spec = RepeatSpec.parse("rep=1M;day=25;kuriage;ex=0,6");
        assertEquals(LocalDateTime.of(2026, 7, 24, 8, 0), spec.next(base));
    }

    // #7: rep=1M;day=25;ex=0,6 — 7/25は土→繰り下げで月曜へ
    @Test
    void monthlyRollDown() {
        // 2026-07-25 = Saturday → skip Sat, skip Sun → 2026-07-27 Mon
        LocalDateTime base = LocalDateTime.of(2026, 6, 25, 8, 0);
        RepeatSpec spec = RepeatSpec.parse("rep=1M;day=25;ex=0,6");
        assertEquals(LocalDateTime.of(2026, 7, 27, 8, 0), spec.next(base));
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
