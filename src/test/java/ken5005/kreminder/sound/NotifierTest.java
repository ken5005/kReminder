package ken5005.kreminder.sound;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SNDをinit()しない状態で走らせる＝SND.playはworker==nullでsilent dropするため無音・安全にテストできる。
 * 時間依存テストは脆いので最小限にとどめる。
 */
class NotifierTest {

    @Test
    void repeatTailZero_terminatesAfterStepsOnce() {
        NotifyPattern pattern = new NotifyPattern(
                List.of(new NotifyStep("Standard", 1.0f, 0)), 0, null);

        NotifyHandle handle = Notifier.start(pattern);

        assertTrue(handle.awaitTermination(1000));
    }

    @Test
    void repeatTailPositive_terminatesNaturallyAtMaxDuration() {
        NotifyPattern pattern = new NotifyPattern(
                List.of(new NotifyStep("Standard", 1.0f, 50)), 1, Duration.ofMillis(200));

        NotifyHandle handle = Notifier.start(pattern);

        // stop()を呼ばなくてもmaxDuration経過後に自然終了するはず
        assertTrue(handle.awaitTermination(1000));
    }

    @Test
    void stop_terminatesPromptly() throws InterruptedException {
        NotifyPattern pattern = new NotifyPattern(
                List.of(new NotifyStep("Standard", 1.0f, 1000)), 1, null);

        NotifyHandle handle = Notifier.start(pattern);
        Thread.sleep(50);
        handle.stop();

        // sleepSlicedは100ms刻みでstopを見るため、300ms以内に終了するはず
        assertTrue(handle.awaitTermination(300));
    }

    @Test
    void constructor_repeatTailEqualsStepsSize_ok() {
        NotifyPattern pattern = new NotifyPattern(
                List.of(new NotifyStep("Standard", 1.0f, 0)), 1, null);

        assertTrue(pattern.steps().size() == 1);
    }

    @Test
    void constructor_repeatTailTooLarge_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new NotifyPattern(List.of(new NotifyStep("Standard", 1.0f, 0)), 2, null));
    }

    @Test
    void constructor_negativeRepeatTail_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new NotifyPattern(List.of(new NotifyStep("Standard", 1.0f, 0)), -1, null));
    }

    @Test
    void constructor_emptySteps_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new NotifyPattern(List.of(), 0, null));
    }

    @Test
    void constructor_nullSteps_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new NotifyPattern(null, 0, null));
    }
}
