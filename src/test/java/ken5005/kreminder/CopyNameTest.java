package ken5005.kreminder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopyNameTest {

    // GUI仕様v2 §2.5.3 の確定仕様を全ケース網羅
    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of(null, "(copy)"),
            Arguments.of("", "(copy)"),
            Arguments.of("ゴミ出し", "(copy)ゴミ出し"),               // 非マッチ→先頭付与
            Arguments.of("(copy)ゴミ出し", "(copy2)ゴミ出し"),        // 番号省略=1とみなし+1
            Arguments.of("(copy2)ゴミ出し", "(copy3)ゴミ出し"),
            Arguments.of("(copy9)x", "(copy10)x"),                    // 桁上がり
            Arguments.of("(copy)", "(copy2)"),                        // マーカー単体
            Arguments.of("(COPY)", "(copy)(COPY)"),                   // 大文字は非マッチ
            Arguments.of("( copy )", "(copy)( copy )")                // 空白入りも非マッチ
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void nextCopyComment(String input, String expected) {
        assertEquals(expected, CopyName.nextCopyComment(input));
    }
}
