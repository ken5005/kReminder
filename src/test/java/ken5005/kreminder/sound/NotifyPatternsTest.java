package ken5005.kreminder.sound;

import ken5005.kreminder.Reminder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NotifyPatterns の表が満たすべき不変条件を検証する（具体的な音声名・ステップ数・Duration の値は
 * 検証しない＝表の数値を変えてもここは黙っている。表の中身が正しいかは目視・実機で確認する）。
 */
class NotifyPatternsTest {

    @ParameterizedTest
    @EnumSource(Reminder.Priority.class)
    void forPriority_returnsNonNullPattern(Reminder.Priority p) {
        assertNotNull(NotifyPatterns.forPriority(p));
    }

    @ParameterizedTest
    @EnumSource(Reminder.Priority.class)
    void steps_isNotEmpty(Reminder.Priority p) {
        // stepsが空だと何も鳴らない＝表として意味を成さない
        assertFalse(NotifyPatterns.forPriority(p).steps().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Reminder.Priority.class)
    void repeatTail_isWithinStepsRange(Reminder.Priority p) {
        NotifyPattern pattern = NotifyPatterns.forPriority(p);
        // repeatTailは「stepsの末尾何個を繰り返すか」なので0以上steps().size()以下でなければ意味を成さない
        // （NotifyPatternのコンストラクタガードと同趣旨だが、表を書き換えたときに壊れていないかをここでも押さえる）
        assertTrue(pattern.repeatTail() >= 0);
        assertTrue(pattern.repeatTail() <= pattern.steps().size());
    }

    @ParameterizedTest
    @EnumSource(Reminder.Priority.class)
    void repeatTailPositive_requiresMaxDuration(Reminder.Priority p) {
        NotifyPattern pattern = NotifyPatterns.forPriority(p);
        // repeatTail>0かつmaxDuration=nullだとstop()されるまで永久に鳴り続ける。
        // 表の側で「無限ループにしていないか」を必ず押さえる。
        if (pattern.repeatTail() > 0) {
            assertNotNull(pattern.maxDuration());
        }
    }

    @ParameterizedTest
    @EnumSource(Reminder.Priority.class)
    void repeatTailPositive_tailHasPositiveDelaySum(Reminder.Priority p) {
        NotifyPattern pattern = NotifyPatterns.forPriority(p);
        // 繰り返し対象（末尾repeatTail個）の待ち時間合計が0だと、待ちゼロで回り続けるホットループになり
        // SoundWorkerのキューを即座に埋め尽くす。NotifyPatternのコンストラクタガードと同趣旨を表側からも確認する。
        if (pattern.repeatTail() > 0) {
            long tailDelaySum = pattern.steps()
                    .subList(pattern.steps().size() - pattern.repeatTail(), pattern.steps().size())
                    .stream()
                    .mapToLong(NotifyStep::delayAfterMs)
                    .sum();
            assertTrue(tailDelaySum > 0);
        }
    }

    @ParameterizedTest
    @EnumSource(Reminder.Priority.class)
    void everyStep_hasValidSoundNameVolumeAndDelay(Reminder.Priority p) {
        for (NotifyStep step : NotifyPatterns.forPriority(p).steps()) {
            // 音声名が空/nullだと再生時に何を鳴らすか解決できない
            assertNotNull(step.soundName());
            assertFalse(step.soundName().isEmpty());
            // 音量は0.0〜1.0の範囲外だとJava Sound側で例外・クリッピングの原因になる
            assertTrue(step.volume() >= 0.0f && step.volume() <= 1.0f);
            // 待ち時間が負だとTimer/Threadのスケジューリングが成立しない
            assertTrue(step.delayAfterMs() >= 0);
        }
    }

    @Test
    void forPriority_nullFallsBackToPri3() {
        // 旧JSON防御＝pがnullでもPri3相当の既定パターンを返す
        assertSame(NotifyPatterns.forPriority(Reminder.Priority.Pri3), NotifyPatterns.forPriority(null));
    }
}
