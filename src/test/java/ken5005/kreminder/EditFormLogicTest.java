package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditFormLogicTest {

    // parseExecTime: 秒あり/秒なし/書式不正/空/日付だけ/時刻だけ を表駆動で確認
    static Stream<Arguments> parseExecTimeCases() {
        return Stream.of(
            Arguments.of("2026-06-24 15:30:00", Optional.of(LocalDateTime.of(2026, 6, 24, 15, 30, 0))),
            Arguments.of("2026-06-24 15:30", Optional.of(LocalDateTime.of(2026, 6, 24, 15, 30, 0))),
            Arguments.of("  2026-06-24 15:30:00  ", Optional.of(LocalDateTime.of(2026, 6, 24, 15, 30, 0))),
            Arguments.of("2020-01-01 00:00", Optional.of(LocalDateTime.of(2020, 1, 1, 0, 0, 0))), // 過去日時でもvalid
            Arguments.of("2026-06-24", Optional.empty()),      // 日付だけ
            Arguments.of("15:30:00", Optional.empty()),        // 時刻だけ
            Arguments.of("not-a-date", Optional.empty()),      // 書式不正
            Arguments.of("2026-13-01 10:00", Optional.empty()), // 不正な月
            Arguments.of("", Optional.empty()),                // 空文字
            Arguments.of("   ", Optional.empty()),              // 空白のみ
            Arguments.of(null, Optional.empty()),               // null
            Arguments.of("1999-12-31 23:59:59", Optional.empty()), // 年下限未満
            Arguments.of("2000-01-01 00:00:00", Optional.of(LocalDateTime.of(2000, 1, 1, 0, 0, 0))), // 年下限ちょうど
            Arguments.of("0026-07-24 12:00:00", Optional.empty()) // 年欄途中Enter相当（0026年）
        );
    }

    @ParameterizedTest
    @MethodSource("parseExecTimeCases")
    void parseExecTime(String input, Optional<LocalDateTime> expected) {
        assertEquals(expected, EditFormLogic.parseExecTime(input));
    }

    // isValidRep: 空/正常/不正 を表駆動で確認
    static Stream<Arguments> isValidRepCases() {
        return Stream.of(
            Arguments.of("", true),                       // 空文字＝単発
            Arguments.of("   ", true),                     // 空白のみ＝単発
            Arguments.of(null, true),                      // null＝単発
            Arguments.of("rep=1d;ex=0,6", true),           // 正常
            Arguments.of("rep=1M;day=25;kuriage", true),   // 正常
            Arguments.of("rep=1d;foo=3", false),           // 未知命令
            Arguments.of("rep=1d;day=25", false)            // day=N を月次以外で使用
        );
    }

    @ParameterizedTest
    @MethodSource("isValidRepCases")
    void isValidRep(String input, boolean expected) {
        assertEquals(expected, EditFormLogic.isValidRep(input));
    }

    // isTotallyValid: execTime・repのvalid/invalidの組み合わせを表駆動で確認
    static Stream<Arguments> isTotallyValidCases() {
        return Stream.of(
            Arguments.of("2026-06-24 15:30:00", "", true),              // execTime正 × rep空＝単発
            Arguments.of("2026-06-24 15:30:00", "rep=1d;ex=0,6", true), // execTime正 × rep正常
            Arguments.of("2026-06-24 15:30:00", "rep=1d;foo=3", false), // execTime正 × rep不正
            Arguments.of("not-a-date", "", false),                       // execTime不正 × rep空
            Arguments.of("not-a-date", "rep=1d;foo=3", false),          // execTime不正 × rep不正
            Arguments.of("", "", false),                                 // execTime空 × 任意
            Arguments.of(null, "rep=1d", false)                          // execTime null × 任意
        );
    }

    @ParameterizedTest
    @MethodSource("isTotallyValidCases")
    void isTotallyValid(String execTimeStr, String repStr, boolean expected) {
        assertEquals(expected, EditFormLogic.isTotallyValid(execTimeStr, repStr));
    }

    @Test
    void buildPreview_execTimeInvalid_returnsErrorLine() {
        String result = EditFormLogic.buildPreview("not-a-date", "", LocalDateTime.of(2026, 6, 24, 10, 0), HolidayCheck.NONE);
        assertEquals("時刻入力エラー", result);
    }

    @Test
    void buildPreview_repEmpty_singleShot() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        String result = EditFormLogic.buildPreview("2026-06-24 15:30:00", "", now, HolidayCheck.NONE);
        assertEquals("5時間30分\n2026/06/24(水) 15:30:00", result);
    }

    @Test
    void buildPreview_repValid_showsTenOccurrences() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 7, 0);
        String result = EditFormLogic.buildPreview("2026-06-01 08:00:00", "rep=1d", now, HolidayCheck.NONE);

        StringBuilder expected = new StringBuilder("1時間00分");
        LocalDateTime t = LocalDateTime.of(2026, 6, 1, 8, 0, 0);
        for (int i = 0; i < 10; i++) {
            expected.append("\n").append(FireAtFormat.withSeconds(t));
            t = t.plusDays(1);
        }
        assertEquals(expected.toString(), result);
    }

    @Test
    void buildPreview_repValid_correctsFirstOccurrenceToMatchingCondition() {
        // rep=1d;in=4（木のみ）: 入力が火曜(2026-06-02)でも、初回は直後の木曜(2026-06-04)に補正される
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 7, 0);
        String result = EditFormLogic.buildPreview("2026-06-02 08:00:00", "rep=1d;in=4", now, HolidayCheck.NONE);

        StringBuilder expected = new StringBuilder("3日1時間");
        LocalDateTime t = LocalDateTime.of(2026, 6, 4, 8, 0, 0);
        for (int i = 0; i < 10; i++) {
            expected.append("\n").append(FireAtFormat.withSeconds(t));
            t = t.plusDays(7);
        }
        assertEquals(expected.toString(), result);
    }

    @Test
    void buildPreview_repInvalid_showsHelp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        String result = EditFormLogic.buildPreview("2026-06-24 15:30:00", "rep=1d;foo=3", now, HolidayCheck.NONE);
        assertEquals("5時間30分\n繰り返し書式エラー 例) rep=1d;ex=0,6 / rep=1M;day=25;kuriage", result);
    }

    @Test
    void buildPreview_pastFireAt_showsAlternateFirstLine() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 16, 0);
        String result = EditFormLogic.buildPreview("2026-06-24 15:30:00", "", now, HolidayCheck.NONE);
        assertEquals("（発火済み）\n" + FireAtFormat.withSeconds(LocalDateTime.of(2026, 6, 24, 15, 30, 0)), result);
    }

    // needsEmptyCommentWarning: コメント非空/空・時間境界・非デフォルト条件・null安全 を表駆動で確認
    private static final LocalDateTime WARN_NOW = LocalDateTime.of(2026, 6, 24, 10, 0);

    static Stream<Arguments> needsEmptyCommentWarningCases() {
        return Stream.of(
            Arguments.of("買い物", WARN_NOW.plusYears(1), Reminder.Priority.Pri1, "cmd", "rep=1d", false), // コメント非空なら常にfalse
            Arguments.of("", WARN_NOW.plusMinutes(5), Reminder.Priority.Pri3, "", "", false),               // 境界＝ちょうど5分後は素通し
            Arguments.of("", WARN_NOW.plusMinutes(5).plusSeconds(1), Reminder.Priority.Pri3, "", "", true), // 5分1秒後は警告
            Arguments.of("", WARN_NOW.plusMinutes(1), Reminder.Priority.Pri3, "", "", false),               // ラーメンタイマー
            Arguments.of("", WARN_NOW.minusMinutes(1), Reminder.Priority.Pri3, "", "", false),              // 過去
            Arguments.of("   ", WARN_NOW.plusYears(1), Reminder.Priority.Pri3, "", "", true),               // 空白のみも空扱い
            Arguments.of(null, WARN_NOW.plusMinutes(1), Reminder.Priority.Pri3, "", "", false),             // コメントnull・全デフォルト
            Arguments.of("", WARN_NOW.plusMinutes(1), Reminder.Priority.Pri1, "", "", true),                // 非デフォルト＝優先度
            Arguments.of("", WARN_NOW.plusMinutes(1), Reminder.Priority.Pri3, "notepad", "", true),         // 非デフォルト＝Cmd
            Arguments.of("", WARN_NOW.plusMinutes(1), Reminder.Priority.Pri3, "", "rep=1d", true),          // 非デフォルト＝繰り返し
            Arguments.of("", WARN_NOW.plusMinutes(1), null, null, null, false),                              // null＝全部デフォルト扱い
            Arguments.of("", WARN_NOW.plusMinutes(1), Reminder.Priority.Pri3, "  ", "  ", false),            // 空白のみはデフォルト扱い
            Arguments.of("", null, Reminder.Priority.Pri3, "", "", false),                                   // fireAt null＝時間条件は不成立
            Arguments.of("", null, Reminder.Priority.Pri1, "", "", true)                                     // fireAt nullでも非デフォルト条件は成立
        );
    }

    @ParameterizedTest
    @MethodSource("needsEmptyCommentWarningCases")
    void needsEmptyCommentWarning(
            String comment, LocalDateTime fireAt, Reminder.Priority priority, String action, String repeat,
            boolean expected) {
        assertEquals(expected, EditFormLogic.needsEmptyCommentWarning(comment, fireAt, priority, action, repeat, WARN_NOW));
    }
}
