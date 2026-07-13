package ken5005.kreminder.sound;

import ken5005.kreminder.Reminder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotifyPatternsTest {

    // ⑤時点は全priority（nullも含む）で同じ「Standardを1.0で1回」を返す
    static Stream<Arguments> priorities() {
        return Stream.of(
            Arguments.of((Object) null),
            Arguments.of(Reminder.Priority.Pri1),
            Arguments.of(Reminder.Priority.Pri2),
            Arguments.of(Reminder.Priority.Pri3),
            Arguments.of(Reminder.Priority.Pri4),
            Arguments.of(Reminder.Priority.Pri5)
        );
    }

    @ParameterizedTest
    @MethodSource("priorities")
    void forPriority_returnsStandardOnce(Reminder.Priority p) {
        NotifyPattern pattern = NotifyPatterns.forPriority(p);

        assertEquals(1, pattern.steps().size());
        assertEquals("Standard", pattern.steps().get(0).soundName());
        assertEquals(1.0f, pattern.steps().get(0).volume());
        assertEquals(0, pattern.repeatTail());
    }
}
