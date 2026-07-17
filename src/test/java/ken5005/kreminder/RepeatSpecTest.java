package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.stream.Stream;

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

    // rep=y（毎年）— 数字必須（bareは不可）／day=・kuriageは月次専用のまま
    @Test
    void yearlyUnitRejectsBareAndMonthlyOnlyCommands() {
        // bare の rep=y は不可（他単位と同じくparseInt("")が例外）
        assertThrows(IllegalArgumentException.class,
            () -> RepeatSpec.parse("rep=y"));
        // day=N は月次(M)専用のまま
        assertThrows(IllegalArgumentException.class,
            () -> RepeatSpec.parse("rep=1y;day=25"));
        // kuriage も月次(M)専用のまま
        assertThrows(IllegalArgumentException.class,
            () -> RepeatSpec.parse("rep=1y;kuriage"));
    }

    // rep=1y — 通常年・うるう日clamp・clamp後は留まる（29に戻らない）
    @Test
    void yearlyNext() {
        assertDoesNotThrow(() -> RepeatSpec.parse("rep=1y"));

        RepeatSpec spec = RepeatSpec.parse("rep=1y");

        // 通常年: 2026-03-10 → 2027-03-10
        LocalDateTime normal = LocalDateTime.of(2026, 3, 10, 8, 0);
        assertEquals(LocalDateTime.of(2027, 3, 10, 8, 0), spec.next(normal));

        // うるう日起点: 2024-02-29 → 2025-02-28（plusYearsのclamp）
        LocalDateTime leapDay = LocalDateTime.of(2024, 2, 29, 8, 0);
        LocalDateTime afterClamp = spec.next(leapDay);
        assertEquals(LocalDateTime.of(2025, 2, 28, 8, 0), afterClamp);

        // clamp後さらにnext → 2026-02-28（29に戻らないことを固定）
        assertEquals(LocalDateTime.of(2026, 2, 28, 8, 0), spec.next(afterClamp));
    }

    // rep=1y — nextAfter取りこぼしスキップ
    @Test
    void yearlyCatchUpSkip() {
        RepeatSpec spec = RepeatSpec.parse("rep=1y");
        LocalDateTime from = LocalDateTime.of(2020, 5, 1, 8, 0);
        LocalDateTime now  = LocalDateTime.of(2023, 6, 1, 8, 0);
        assertEquals(LocalDateTime.of(2024, 5, 1, 8, 0), spec.nextAfter(from, now));
    }

    // 回帰: 月次 kuriage は従来どおり parse が通る
    @Test
    void monthlyKuriageStillParses() {
        assertDoesNotThrow(() -> RepeatSpec.parse("rep=1M;day=25;kuriage;ex=0,6"));
    }

    // firstOnOrAfter — 補正が効くケース（曜日限定・第N週限定・月固定日）
    @Test
    void firstOnOrAfterCorrectsToFirstMatchingDay() {
        // rep=1d;in=4（木のみ）: 火曜起点→直後の木曜。時分秒（08:15:30）は据え置き
        RepeatSpec thursdayOnly = RepeatSpec.parse("rep=1d;in=4");
        LocalDateTime tuesday = LocalDateTime.of(2026, 6, 2, 8, 15, 30);
        assertEquals(LocalDateTime.of(2026, 6, 4, 8, 15, 30), thursdayOnly.firstOnOrAfter(tuesday));

        // rep=1d;in=4: 起点自体が木曜なら補正なし（anchorそのまま）
        LocalDateTime thursday = LocalDateTime.of(2026, 6, 4, 8, 15, 30);
        assertEquals(thursday, thursdayOnly.firstOnOrAfter(thursday));

        // rep=1d;in=4;dai=1,3（第1第3木）: 第2週の木曜起点→第3週の木曜へ
        RepeatSpec firstThirdThursday = RepeatSpec.parse("rep=1d;in=4;dai=1,3");
        LocalDateTime secondWeekThursday = LocalDateTime.of(2026, 6, 11, 8, 0);
        assertEquals(LocalDateTime.of(2026, 6, 18, 8, 0), firstThirdThursday.firstOnOrAfter(secondWeekThursday));

        // rep=1M;day=25: 当月分がまだなら当月25日へ、過ぎていれば翌月25日へ
        RepeatSpec day25 = RepeatSpec.parse("rep=1M;day=25");
        assertEquals(LocalDateTime.of(2026, 6, 25, 8, 0),
            day25.firstOnOrAfter(LocalDateTime.of(2026, 6, 10, 8, 0)));
        assertEquals(LocalDateTime.of(2026, 7, 25, 8, 0),
            day25.firstOnOrAfter(LocalDateTime.of(2026, 6, 28, 8, 0)));

        // rep=1M;day=31: 末日clamp（2月は28日）。28は起点10日以降なので補正先はそのまま2月28日
        RepeatSpec day31 = RepeatSpec.parse("rep=1M;day=31");
        assertEquals(LocalDateTime.of(2026, 2, 28, 8, 0),
            day31.firstOnOrAfter(LocalDateTime.of(2026, 2, 10, 8, 0)));
    }

    // firstOnOrAfter — 補正が効かないケース（条件なしの間隔系・年次・ゲート対象外のh/m/s）
    @Test
    void firstOnOrAfterLeavesAnchorUnchangedWhenNoConditionOrGated() {
        LocalDateTime anchor = LocalDateTime.of(2026, 6, 2, 8, 15, 30);

        // rep=1d（毎日・曜日条件なし）
        assertEquals(anchor, RepeatSpec.parse("rep=1d").firstOnOrAfter(anchor));
        // rep=7d（毎週・曜日条件なし）
        assertEquals(anchor, RepeatSpec.parse("rep=7d").firstOnOrAfter(anchor));
        // rep=1y（年次）
        assertEquals(anchor, RepeatSpec.parse("rep=1y").firstOnOrAfter(anchor));
        // ゲート: h/m/s は unit が DAY/MONTH/YEAR ではないため無補正
        assertEquals(anchor, RepeatSpec.parse("rep=30").firstOnOrAfter(anchor));
        assertEquals(anchor, RepeatSpec.parse("rep=6h").firstOnOrAfter(anchor));
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

    // toJapanese() 表駆動テスト（GUI仕様v2 §6.1 対応表をそのままテストデータに）
    @ParameterizedTest
    @MethodSource("toJapaneseCases")
    void toJapanese(String repeat, String expected) {
        assertEquals(expected, RepeatSpec.parse(repeat).toJapanese());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> toJapaneseCases() {
        return Stream.of(
            // 基本
            args("rep=1d", "毎日"),
            args("rep=7d", "毎週"),
            args("rep=1M", "毎月"),
            args("rep=12M", "毎年"),
            args("rep=1y", "毎年"),
            args("rep=2y", "毎2年"),
            // 倍数畳み／端数
            args("rep=14d", "毎2週"),
            args("rep=21d", "毎3週"),
            args("rep=13M", "毎13ヶ月"),
            // 数値+単位
            args("rep=3d", "毎3日"),
            args("rep=6h", "毎6時間"),
            args("rep=30", "毎30分"),
            args("rep=1000m", "毎1000分"),
            // 除外≤3→除く
            args("rep=1d;ex=0,6", "毎日 土日除く"),
            args("rep=1d;ex=0,1,6", "毎日 月土日除く"),
            // bare（毎日＋曜日限定）
            args("rep=1d;in=2", "火"),
            args("rep=1d;in=3,6", "水土"),
            args("rep=1d;in=0,1", "月日"),
            args("rep=1d;in=4;dai=1,3", "第1第3木"),
            // dai 表面化
            args("rep=7d;dai=1,3", "毎週 第1第3週"),
            // kuriage
            args("rep=1M;day=25;kuriage;ex=0,6", "毎月25日(繰上) 土日除く"),
            args("rep=1M;day=19;ex=0,6;kuriage", "毎月19日(繰上) 土日除く"),
            args("rep=1M;ex=0,6;kuriage", "毎月(繰上) 土日除く")
        );
    }

    private static org.junit.jupiter.params.provider.Arguments args(String repeat, String expected) {
        return org.junit.jupiter.params.provider.Arguments.of(repeat, expected);
    }
}
