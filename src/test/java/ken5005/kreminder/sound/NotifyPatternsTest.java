package ken5005.kreminder.sound;

import ken5005.kreminder.Reminder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NotifyPatternsTest {

    // 確定シーケンス（GUI仕様v2 §5.1・SND仕様v2.2 §11.2）を表駆動で検証する。
    // ステップ数・先頭数個/末尾の音声名・repeatTail・maxDurationを1行ずつ確認。
    static Stream<Arguments> patterns() {
        return Stream.of(
                Arguments.of(Reminder.Priority.Pri1, 1,
                        List.of("Small"), List.of("Small"), 0, null),
                Arguments.of(Reminder.Priority.Pri2, 1,
                        List.of("Notify"), List.of("Notify"), 1, Duration.ofMinutes(5)),
                Arguments.of(Reminder.Priority.Pri3, 1,
                        List.of("Standard"), List.of("Standard"), 1, Duration.ofMinutes(10)),
                Arguments.of(Reminder.Priority.Pri4, 17,
                        List.of("Standard", "Standard", "Standard"),
                        List.of("Watchout"), 1, Duration.ofHours(2)),
                Arguments.of(Reminder.Priority.Pri5, 16,
                        List.of("Standard", "Standard", "Standard"),
                        List.of("Serious"), 1, Duration.ofHours(12))
        );
    }

    @ParameterizedTest
    @MethodSource("patterns")
    void forPriority_matchesConfirmedSequence(Reminder.Priority p, int expectedStepCount,
                                               List<String> expectedHead, List<String> expectedTail,
                                               int expectedRepeatTail, Duration expectedMaxDuration) {
        NotifyPattern pattern = NotifyPatterns.forPriority(p);

        assertEquals(expectedStepCount, pattern.steps().size());
        for (int i = 0; i < expectedHead.size(); i++) {
            assertEquals(expectedHead.get(i), pattern.steps().get(i).soundName());
        }
        for (int i = 0; i < expectedTail.size(); i++) {
            int idx = pattern.steps().size() - expectedTail.size() + i;
            assertEquals(expectedTail.get(i), pattern.steps().get(idx).soundName());
        }
        assertEquals(expectedRepeatTail, pattern.repeatTail());
        assertEquals(expectedMaxDuration, pattern.maxDuration());
    }

    @Test
    void pri1_repeatTailZero_andNoMaxDuration() {
        NotifyPattern pattern = NotifyPatterns.forPriority(Reminder.Priority.Pri1);

        assertEquals(0, pattern.repeatTail());
        assertNull(pattern.maxDuration());
    }

    @Test
    void forPriority_nullFallsBackToPri3() {
        // 旧JSON防御＝pがnullでもPri3相当の既定パターンを返す
        assertSame(NotifyPatterns.forPriority(Reminder.Priority.Pri3), NotifyPatterns.forPriority(null));
    }
}
