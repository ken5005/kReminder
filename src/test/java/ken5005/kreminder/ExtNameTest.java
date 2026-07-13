package ken5005.kreminder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtNameTest {

    // GUI仕様v2 §5.3/5.4 の確定仕様を全ケース網羅。hasExtPrefix / withExtPrefix を同じ表で検証
    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of(null, false, "(Ext) "),
            Arguments.of("", false, "(Ext) "),
            Arguments.of("会議", false, "(Ext) 会議"),
            Arguments.of("(Ext) 会議", true, "(Ext) 会議"),           // 既に前置済み→不変
            Arguments.of("(Ext)会議", false, "(Ext) (Ext)会議"),      // 空白なし→非マッチ・重ねて前置
            Arguments.of("(Ext) ", true, "(Ext) "),                   // マーカー単体→不変
            Arguments.of("(ext) 会議", false, "(Ext) (ext) 会議"),    // 小文字→非マッチ
            Arguments.of(" (Ext) 会議", false, "(Ext)  (Ext) 会議")   // 先頭に空白→非マッチ
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void hasExtPrefix(String input, boolean expectedHasPrefix, String expectedWithPrefix) {
        assertEquals(expectedHasPrefix, ExtName.hasExtPrefix(input));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void withExtPrefix(String input, boolean expectedHasPrefix, String expectedWithPrefix) {
        assertEquals(expectedWithPrefix, ExtName.withExtPrefix(input));
    }
}
