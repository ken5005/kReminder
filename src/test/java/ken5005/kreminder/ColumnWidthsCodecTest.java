package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnWidthsCodecTest {

    static Stream<Arguments> parseCases() {
        return Stream.of(
            Arguments.of("nullは空配列", null, new int[0]),
            Arguments.of("空文字は空配列", "", new int[0]),
            Arguments.of("空白のみも空配列", "   ", new int[0]),
            Arguments.of("通常の3列", "40,120,80", new int[]{40, 120, 80}),
            Arguments.of("トークン前後の空白は無視", "40, 120 ,80", new int[]{40, 120, 80}),
            Arguments.of("0は有効な幅として扱う", "0,100", new int[]{0, 100}),
            Arguments.of("数値でないトークンがあれば全体を諦める", "40,abc,80", new int[0]),
            Arguments.of("負数があれば全体を諦める", "40,-5,80", new int[0]),
            Arguments.of("空トークン(カンマの連続)も不正として全体を諦める", "40,,80", new int[0])
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parseCases")
    void parseMatchesTable(String label, String input, int[] expected) {
        assertArrayEquals(expected, ColumnWidthsCodec.parse(input));
    }

    @Test
    void formatJoinsWithComma() {
        assertEquals("40,120,80", ColumnWidthsCodec.format(new int[]{40, 120, 80}));
    }

    @Test
    void formatOfEmptyArrayIsEmptyString() {
        assertEquals("", ColumnWidthsCodec.format(new int[0]));
    }

    @Test
    void formatThenParseRoundTrips() {
        int[] original = {40, 120, 80, 100, 300};
        assertArrayEquals(original, ColumnWidthsCodec.parse(ColumnWidthsCodec.format(original)));
    }
}
