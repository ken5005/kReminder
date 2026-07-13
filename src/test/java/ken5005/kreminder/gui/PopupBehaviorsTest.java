package ken5005.kreminder.gui;

import ken5005.kreminder.Reminder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopupBehaviorsTest {

    // GUI仕様v2 §5.1：Pri-1のみ5秒自動消滅・Extend非表示。Pri-2〜Pri-5・null（旧JSON防御）は標準挙動。
    static Stream<Arguments> priorities() {
        return Stream.of(
                Arguments.of(Reminder.Priority.Pri1, Duration.ofSeconds(5), false),
                Arguments.of(Reminder.Priority.Pri2, null, true),
                Arguments.of(Reminder.Priority.Pri3, null, true),
                Arguments.of(Reminder.Priority.Pri4, null, true),
                Arguments.of(Reminder.Priority.Pri5, null, true),
                Arguments.of((Reminder.Priority) null, null, true)
        );
    }

    @ParameterizedTest
    @MethodSource("priorities")
    void forPriority_matchesTable(Reminder.Priority p, Duration expectedAutoClose, boolean expectedShowExtend) {
        PopupBehavior behavior = PopupBehaviors.forPriority(p);

        assertEquals(expectedAutoClose, behavior.autoCloseAfter());
        assertEquals(expectedShowExtend, behavior.showExtend());
    }
}
