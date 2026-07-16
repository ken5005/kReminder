package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstantTimeLogicTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 11, 12, 34, 20);

    private static final String OVER_LIMIT_ERROR = "3日以上は指定出来ません";

    // GUI仕様v2 §4.4「主な例」表
    static Stream<Arguments> successCases() {
        return Stream.of(
            Arguments.of("25", NOW.plusMinutes(25)),
            Arguments.of("+25", NOW.plusMinutes(25)),
            Arguments.of("+1:30", NOW.plusHours(1).plusMinutes(30)),
            Arguments.of("+1:23.45", NOW.plusHours(1).plusMinutes(23).plusSeconds(45)),
            Arguments.of("+23.45", NOW.plusMinutes(23).plusSeconds(45)),
            Arguments.of("0.25", NOW.plusSeconds(25)),
            Arguments.of("+0", NOW),
            Arguments.of("0", NOW),
            Arguments.of("12:34", LocalDateTime.of(2026, 7, 12, 12, 34, 0)),   // 過ぎている→翌日
            Arguments.of("1:02.03", LocalDateTime.of(2026, 7, 12, 1, 2, 3)),  // 過ぎている→翌日
            Arguments.of("1230", NOW.plusMinutes(1230)),

            // 相対の境界
            Arguments.of("+05", NOW.plusMinutes(5)),
            Arguments.of("+72:00", NOW.plusHours(72)),
            Arguments.of("+4320", NOW.plusMinutes(4320)),

            // 絶対の境界（now=12:34:20）
            Arguments.of("12:35", LocalDateTime.of(2026, 7, 11, 12, 35, 0)),        // 今日
            Arguments.of("12:34.21", LocalDateTime.of(2026, 7, 11, 12, 34, 21)),    // 今日（nowより後）
            Arguments.of("12:34.20", LocalDateTime.of(2026, 7, 12, 12, 34, 20)),    // ちょうど同時刻→翌日
            Arguments.of("00:00", LocalDateTime.of(2026, 7, 12, 0, 0, 0)),          // 翌日
            Arguments.of("23:59.59", LocalDateTime.of(2026, 7, 11, 23, 59, 59)),    // 今日

            // 全角
            Arguments.of("＋１．２３", NOW.plusMinutes(1).plusSeconds(23)),
            Arguments.of("１２：３４", LocalDateTime.of(2026, 7, 12, 12, 34, 0)),
            Arguments.of("１２。３４", NOW.plusMinutes(12).plusSeconds(34)),

            // 単位サフィックス（s/m/h、小文字のみ・常に相対）
            Arguments.of("15m", NOW.plusMinutes(15)),
            Arguments.of("+15m", NOW.plusMinutes(15)),
            Arguments.of("30s", NOW.plusSeconds(30)),
            Arguments.of("2h", NOW.plusHours(2)),
            Arguments.of("90m", NOW.plusMinutes(90)),
            Arguments.of("72h", NOW.plusHours(72))   // 境界＝72時間ちょうどはOK
        );
    }

    @ParameterizedTest
    @MethodSource("successCases")
    void parsesToExpectedFireAt(String input, LocalDateTime expected) {
        InstantTimeLogic.Result result = InstantTimeLogic.parse(input, NOW);
        assertEquals(expected, result.fireAt());
        assertNull(result.error());
    }

    static Stream<Arguments> overLimitCases() {
        return Stream.of(
            Arguments.of("+72:01"),
            Arguments.of("+4321"),
            Arguments.of("12345"),
            Arguments.of("+123:45"),
            // 最上位フィールドの桁あふれ（相対）：long に収まる/収まらない両方
            Arguments.of("99999999999"),
            Arguments.of("+99999999999999999999"),
            // 単位サフィックスの上限超過
            Arguments.of("73h"),
            Arguments.of("4321m")
        );
    }

    @ParameterizedTest
    @MethodSource("overLimitCases")
    void rejectsOverThreeDays(String input) {
        InstantTimeLogic.Result result = InstantTimeLogic.parse(input, NOW);
        assertNull(result.fireAt());
        assertEquals(OVER_LIMIT_ERROR, result.error());
    }

    static Stream<Arguments> grammarErrorCases() {
        return Stream.of(
            Arguments.of("1:2.03"),
            Arguments.of("1:02.3"),
            Arguments.of("1.60"),
            Arguments.of("1:60"),
            Arguments.of("24:00"),
            Arguments.of("+2:"),
            Arguments.of("+:30"),
            Arguments.of(":30"),
            Arguments.of(".30"),
            Arguments.of("。30"),
            Arguments.of("1.2.3"),
            Arguments.of("1:2:3"),
            Arguments.of("abc"),
            Arguments.of("1 2"),
            Arguments.of(""),
            Arguments.of((String) null),
            // 最上位フィールドの桁あふれ（絶対＝時は0〜23なので文法エラー扱い）
            Arguments.of("99999999999:00"),
            // 漏れていた境界
            Arguments.of("+1.60"),   // 相対の秒60
            Arguments.of("+1:60"),   // 相対の分60
            Arguments.of("1.2:3"),   // '.' の後に ':' が来る
            // 単位サフィックスの文法エラー
            Arguments.of("1:30m"),   // コロンとの併用
            Arguments.of("1.30s"),   // 小数点との併用
            Arguments.of("15M"),     // 大文字は対象外
            Arguments.of("1.5h"),    // 数値部が小数
            Arguments.of("15x")      // 未知の単位
        );
    }

    @ParameterizedTest
    @MethodSource("grammarErrorCases")
    void rejectsGrammarErrors(String input) {
        InstantTimeLogic.Result result = InstantTimeLogic.parse(input, NOW);
        assertNull(result.fireAt());
        assertNotNull(result.error());
    }

    @Test
    void grammarErrorMessageIsTheSpecifiedHelpText() {
        InstantTimeLogic.Result result = InstantTimeLogic.parse("abc", NOW);
        assertEquals(
            "時刻入力エラー  例) 25=25分後 / +1:30=1時間30分後 / 0.25=25秒後 / 15m=15分後 / "
                + "12:34=今日の12:34（過ぎたら翌日） ※最上位以外の桁は2桁必須",
            result.error());
    }
}
