package ken5005.kreminder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
