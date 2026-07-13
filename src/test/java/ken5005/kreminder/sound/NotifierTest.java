package ken5005.kreminder.sound;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SNDをinit()しない状態で走らせる＝SND.playはworker==nullでsilent dropするため無音・安全にテストできる。
 */
class NotifierTest {

    @Test
    void singleShotPattern_terminatesQuickly() {
        NotifyPattern pattern = new NotifyPattern(
                List.of(new NotifyStep("Standard", 1.0f, 0)), false, null);

        NotifyHandle handle = Notifier.start(pattern);

        assertTrue(handle.awaitTermination(1000));
    }

    @Test
    void loopingPattern_stopsPromptlyAfterStop() throws InterruptedException {
        NotifyPattern pattern = new NotifyPattern(
                List.of(new NotifyStep("Standard", 1.0f, 1000)), true, Duration.ofMinutes(90));

        NotifyHandle handle = Notifier.start(pattern);
        Thread.sleep(50);
        handle.stop();

        // sleepSlicedは100ms刻みでstopを見るため、1000msの待ち途中でもすぐ終了するはず
        assertTrue(handle.awaitTermination(1000));
    }
}
