package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FireAtFormatTest {

    @Test
    void withSeconds_nullReturnsEmpty() {
        assertEquals("", FireAtFormat.withSeconds(null));
    }

    @Test
    void forList_nullReturnsEmpty() {
        assertEquals("", FireAtFormat.forList(null));
    }

    @Test
    void secondsZero_forListOmitsSeconds_withSecondsKeepsThem() {
        LocalDateTime dt = LocalDateTime.of(2026, 7, 17, 12, 34, 0);
        assertEquals("2026/07/17(金) 12:34", FireAtFormat.forList(dt));
        assertEquals("2026/07/17(金) 12:34:00", FireAtFormat.withSeconds(dt));
    }

    // 秒が非0のときはforList/withSecondsとも同じ結果になる（秒を省く分岐に入らない）
    static Stream<Arguments> nonZeroSecondsCases() {
        return Stream.of(
            Arguments.of(LocalDateTime.of(2026, 7, 17, 12, 34, 56), "2026/07/17(金) 12:34:56"),
            Arguments.of(LocalDateTime.of(2026, 1, 5, 3, 4, 1), "2026/01/05(月) 03:04:01")
        );
    }

    @ParameterizedTest
    @MethodSource("nonZeroSecondsCases")
    void secondsNonZero_bothMethodsMatch(LocalDateTime dt, String expected) {
        assertEquals(expected, FireAtFormat.forList(dt));
        assertEquals(expected, FireAtFormat.withSeconds(dt));
    }

    // 2026/01/01(木)始まりの1週間で曜日7種＝日本語1文字化を一通り確認
    static Stream<Arguments> weekdayCases() {
        return Stream.of(
            Arguments.of(LocalDateTime.of(2026, 1, 1, 0, 0, 0), "木"),
            Arguments.of(LocalDateTime.of(2026, 1, 2, 0, 0, 0), "金"),
            Arguments.of(LocalDateTime.of(2026, 1, 3, 0, 0, 0), "土"),
            Arguments.of(LocalDateTime.of(2026, 1, 4, 0, 0, 0), "日"),
            Arguments.of(LocalDateTime.of(2026, 1, 5, 0, 0, 0), "月"),
            Arguments.of(LocalDateTime.of(2026, 1, 6, 0, 0, 0), "火"),
            Arguments.of(LocalDateTime.of(2026, 1, 7, 0, 0, 0), "水")
        );
    }

    @ParameterizedTest
    @MethodSource("weekdayCases")
    void weekdayIsSingleJapaneseCharacter(LocalDateTime dt, String expectedWeekday) {
        String expected = String.format("2026/01/%02d(%s) 00:00:00", dt.getDayOfMonth(), expectedWeekday);
        assertEquals(expected, FireAtFormat.withSeconds(dt));
    }

    // 月・日・時・分が1桁になる組み合わせでゼロ埋め2桁を確認
    static Stream<Arguments> zeroPaddingCases() {
        return Stream.of(
            Arguments.of(LocalDateTime.of(2026, 1, 5, 3, 4, 0), "2026/01/05(月) 03:04"),
            Arguments.of(LocalDateTime.of(2026, 9, 9, 9, 9, 0), "2026/09/09(水) 09:09")
        );
    }

    @ParameterizedTest
    @MethodSource("zeroPaddingCases")
    void singleDigitFieldsAreZeroPadded(LocalDateTime dt, String expected) {
        assertEquals(expected, FireAtFormat.forList(dt));
    }
}
