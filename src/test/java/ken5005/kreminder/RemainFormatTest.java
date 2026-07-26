package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemainFormatTest {

    // §6.2 末尾「具体例（確定）」表 + 境界ケース
    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of(Duration.ofDays(3).plusHours(5), "3日5時間"),
            Arguments.of(Duration.ofDays(3), "3日0時間"),
            Arguments.of(Duration.ofDays(4).plusHours(23), "4日23時間"),
            Arguments.of(Duration.ofDays(1), "1日0時間"),
            Arguments.of(Duration.ofDays(5).plusHours(1), "5日"),          // 境界: 日数5→日のみ
            Arguments.of(Duration.ofDays(1234), "1234日"),
            Arguments.of(Duration.ofHours(2).plusMinutes(5).plusSeconds(45), "2時間05分"),
            Arguments.of(Duration.ofHours(2), "2時間00分"),               // 境界: ちょうど
            Arguments.of(Duration.ofHours(1).plusMinutes(1), "1時間01分"),
            Arguments.of(Duration.ofMinutes(45), "45分"),
            Arguments.of(Duration.ofMinutes(5), "5分"),                   // 境界: 5分ちょうど
            Arguments.of(Duration.ofMinutes(3).plusSeconds(5), "3分5秒"),
            Arguments.of(Duration.ofMinutes(3), "3分0秒"),
            Arguments.of(Duration.ofSeconds(45), "45秒"),
            Arguments.of(Duration.ofSeconds(5), "5秒")
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void formatsRemainingDuration(Duration remain, String expected) {
        assertEquals(expected, RemainFormat.formatRemaining(remain));
    }

    @Test
    void zeroReturnsEmpty() {
        assertEquals("", RemainFormat.formatRemaining(Duration.ZERO));
    }

    @Test
    void negativeReturnsEmpty() {
        assertEquals("", RemainFormat.formatRemaining(Duration.ofSeconds(-5)));
    }
}
