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
            Arguments.of(null, Optional.empty())                // null
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
        assertEquals("5時間30分後\n2026-06-24 15:30:00", result);
    }

    @Test
    void buildPreview_repValid_showsTenOccurrences() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 7, 0);
        String result = EditFormLogic.buildPreview("2026-06-01 08:00:00", "rep=1d", now, HolidayCheck.NONE);

        StringBuilder expected = new StringBuilder("1時間00分後");
        LocalDateTime t = LocalDateTime.of(2026, 6, 1, 8, 0, 0);
        for (int i = 0; i < 10; i++) {
            expected.append("\n").append(t.toLocalDate()).append(" ")
                .append(String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()));
            t = t.plusDays(1);
        }
        assertEquals(expected.toString(), result);
    }

    @Test
    void buildPreview_repInvalid_showsHelp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        String result = EditFormLogic.buildPreview("2026-06-24 15:30:00", "rep=1d;foo=3", now, HolidayCheck.NONE);
        assertEquals("5時間30分後\n繰り返し書式エラー 例) rep=1d;ex=0,6 / rep=1M;day=25;kuriage", result);
    }

    @Test
    void buildPreview_pastFireAt_showsAlternateFirstLine() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 16, 0);
        String result = EditFormLogic.buildPreview("2026-06-24 15:30:00", "", now, HolidayCheck.NONE);
        assertEquals("（発火済み）\n2026-06-24 15:30:00", result);
    }
}
