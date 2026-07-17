package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderFilterTest {

    // GUI仕様v2 §3.5 対応表 + 境界ケース
    static Stream<Arguments> cases() {
        return Stream.of(
            // 代表例（短期/日次/週次/月次）
            Arguments.of("rep=30", Duration.ofHours(3)),   // 30分・短期
            Arguments.of("rep=6h", Duration.ofHours(3)),   // 6時間・短期
            Arguments.of("rep=1d", Duration.ofHours(6)),   // 1日・日次
            Arguments.of("rep=3d", Duration.ofHours(6)),   // 3日・日次
            Arguments.of("rep=7d", Duration.ofDays(2)),    // 7日・週次
            Arguments.of("rep=15d", Duration.ofDays(2)),   // 15日・週次
            Arguments.of("rep=1M", Duration.ofDays(3)),    // 1ヶ月・月次
            Arguments.of("rep=12M", Duration.ofDays(3)),   // 12ヶ月・月次
            Arguments.of("rep=1y", Duration.ofDays(3)),    // 毎年・月次扱い（例外を投げないことの確認も兼ねる）

            // 境界
            Arguments.of("rep=1d", Duration.ofHours(6)),   // 1日ちょうど → <1日ではない側 → 日次
            Arguments.of("rep=6d", Duration.ofHours(6)),   // 6日 → 日次側
            Arguments.of("rep=7d", Duration.ofDays(2)),    // 7日ちょうど → 週次側
            Arguments.of("rep=29d", Duration.ofDays(2)),   // 29日 → 週次側
            Arguments.of("rep=30d", Duration.ofDays(3))    // 30日ちょうど → 月次側
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void leadWindowMatchesTable(String repeat, Duration expected) {
        RepeatSpec spec = RepeatSpec.parse(repeat);
        assertEquals(expected, ReminderFilter.leadWindowOf(spec));
    }

    // GUI仕様v2 §3.2 時間バケツ境界ケース
    static Stream<Arguments> bucketCases() {
        return Stream.of(
            Arguments.of(Duration.ofSeconds(-1), ReminderFilter.Bucket.終了済),
            Arguments.of(Duration.ZERO, ReminderFilter.Bucket.終了済),
            Arguments.of(Duration.ofSeconds(1), ReminderFilter.Bucket.直近),
            Arguments.of(Duration.ofHours(8).minusSeconds(1), ReminderFilter.Bucket.直近),
            Arguments.of(Duration.ofHours(8), ReminderFilter.Bucket.近日),
            Arguments.of(Duration.ofDays(7).minusSeconds(1), ReminderFilter.Bucket.近日),
            Arguments.of(Duration.ofDays(7), ReminderFilter.Bucket.先),
            Arguments.of(Duration.ofDays(30), ReminderFilter.Bucket.先)
        );
    }

    @ParameterizedTest
    @MethodSource("bucketCases")
    void bucketOfMatchesBoundaries(Duration remain, ReminderFilter.Bucket expected) {
        assertEquals(expected, ReminderFilter.bucketOf(remain));
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0, 0);

    private static Reminder reminder(LocalDateTime fireAt, String message,
                                      Reminder.Priority priority, String repeat) {
        Reminder r = new Reminder();
        r.fireAt = fireAt;
        r.message = message;
        r.priority = priority;
        r.repeat = repeat;
        return r;
    }

    // 全バケツ表示・低優先度表示・繰り返し全表示（isVisibleの土台として使い、狙った軸だけ別途 new する）
    private static FilterState allOpen(String searchText) {
        return new FilterState(true, true, true, true, false, true, true, searchText);
    }

    // GUI仕様v2 §3.2 isVisible 判定順の分岐網羅
    static Stream<Arguments> isVisibleCases() {
        return Stream.of(
            // 上書き層：検索非空はトグルを無視する
            Arguments.of("検索一致→トグル全OFFでも表示",
                new FilterState(false, false, false, false, false, false, false, "hoge"),
                reminder(NOW.plusDays(100), "hogeメッセージ", Reminder.Priority.Pri1, ""),
                true),
            Arguments.of("検索不一致→非表示",
                new FilterState(true, true, true, true, false, true, true, "hoge"),
                reminder(NOW.plusSeconds(1), "別件", Reminder.Priority.Pri3, ""),
                false),

            // 上書き層：showAll はバケツ・優先度を無視する
            Arguments.of("showAll=true→終了済・Pri1でも表示",
                new FilterState(false, false, false, false, true, false, false, null),
                reminder(NOW.minusSeconds(1), "x", Reminder.Priority.Pri1, ""),
                true),

            // ① 時間バケツ
            Arguments.of("終了済・showEnded=false→非表示",
                new FilterState(false, true, true, true, false, true, true, null),
                reminder(NOW.minusSeconds(1), "x", Reminder.Priority.Pri3, ""),
                false),
            Arguments.of("直近・showImminent=true→表示",
                allOpen(null),
                reminder(NOW.plusHours(1), "x", Reminder.Priority.Pri3, ""),
                true),

            // ② 重要度
            Arguments.of("Pri1・showLowPriority=false→非表示",
                new FilterState(true, true, true, true, false, false, true, null),
                reminder(NOW.plusDays(100), "x", Reminder.Priority.Pri1, ""),
                false),
            Arguments.of("Pri3・showLowPriority=false→表示",
                new FilterState(true, true, true, true, false, false, true, null),
                reminder(NOW.plusDays(100), "x", Reminder.Priority.Pri3, ""),
                true),
            Arguments.of("Pri1・showLowPriority=true→表示",
                allOpen(null),
                reminder(NOW.plusDays(100), "x", Reminder.Priority.Pri1, ""),
                true),

            // ③ リードタイム（案Y）
            Arguments.of("繰り返し・窓の外→非表示",
                new FilterState(true, true, true, true, false, true, false, null),
                reminder(NOW.plusDays(2), "x", Reminder.Priority.Pri3, "rep=1d"),
                false),
            Arguments.of("繰り返し・窓の中→表示",
                new FilterState(true, true, true, true, false, true, false, null),
                reminder(NOW.plusHours(3), "x", Reminder.Priority.Pri3, "rep=1d"),
                true),
            Arguments.of("繰り返し・窓の外だがshowAllRepeat=true→表示",
                allOpen(null),
                reminder(NOW.plusDays(2), "x", Reminder.Priority.Pri3, "rep=1d"),
                true),
            Arguments.of("非繰り返しは窓制約を受けない",
                new FilterState(true, true, true, true, false, true, false, null),
                reminder(NOW.plusDays(2), "x", Reminder.Priority.Pri3, ""),
                true),
            Arguments.of("repeatがparse失敗→窓制約スキップで表示",
                new FilterState(true, true, true, true, false, true, false, null),
                reminder(NOW.plusDays(2), "x", Reminder.Priority.Pri3, "rep=1d;kuriage"),
                true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isVisibleCases")
    void isVisibleMatchesDecisionOrder(String label, FilterState state, Reminder r, boolean expected) {
        assertEquals(expected, ReminderFilter.isVisible(r, state, NOW));
    }

    @Test
    void compareTypeOrdersNumerically() {
        assertTrue(ReminderFilter.compareType("1", "2") < 0);
        assertTrue(ReminderFilter.compareType("5", "1") > 0);
        assertEquals(0, ReminderFilter.compareType("3", "3"));
    }

    @Test
    void compareTypeSortsListAscending() {
        List<String> types = new ArrayList<>(List.of("3", "1", "5", "2", "4"));
        types.sort(ReminderFilter::compareType);
        assertEquals(List.of("1", "2", "3", "4", "5"), types);
    }

    @Test
    void compareTypeSendsNonNumericAfterNumeric() {
        assertTrue(ReminderFilter.compareType("x", "1") > 0);
        assertTrue(ReminderFilter.compareType("1", "x") < 0);
    }
}
