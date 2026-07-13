package ken5005.kreminder.sound;

import ken5005.kreminder.Reminder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * priority → 鳴らし方（NotifyPattern）の対応表（純関数）。GUI仕様v2 §5.1、SND仕様v2.2 §11。
 * 通知拡張PRで確定したPri1〜Pri5の鳴らし方をここに1箇所に集約する。表だけ書き換えれば
 * Notifierも呼び出し元（Main）も触らずに済む。
 */
public final class NotifyPatterns {

    // 音量はすべて1.0固定（音の大小はwav自体で表現する・GUI仕様v2 §5.1）
    private static final float VOLUME = 1.0f;

    // 各Priの最大鳴動時間（NotifyPattern.maxDuration）
    private static final Duration MAX_PRI2 = Duration.ofMinutes(5);
    private static final Duration MAX_PRI3 = Duration.ofMinutes(10);
    private static final Duration MAX_PRI4 = Duration.ofHours(2);
    private static final Duration MAX_PRI5 = Duration.ofHours(12);

    private static final NotifyPattern PRI1 =
            new NotifyPattern(List.of(new NotifyStep("Small", VOLUME, 0)), 0, null);

    private static final NotifyPattern PRI2 = new NotifyPattern(
            List.of(new NotifyStep("Notify", VOLUME, 10_000)), 1, MAX_PRI2);

    private static final NotifyPattern PRI3 = new NotifyPattern(
            List.of(new NotifyStep("Standard", VOLUME, 5_000)), 1, MAX_PRI3);

    private static final NotifyPattern PRI4 = new NotifyPattern(
            concat(
                    rep(new NotifyStep("Standard", VOLUME, 500), 3),
                    rep(new NotifyStep("Standard", VOLUME, 5_000), 3),
                    rep(new NotifyStep("Watchout", VOLUME, 5_000), 10),
                    rep(new NotifyStep("Watchout", VOLUME, 60_000), 1)
            ), 1, MAX_PRI4);

    private static final NotifyPattern PRI5 = new NotifyPattern(
            concat(
                    rep(new NotifyStep("Standard", VOLUME, 500), 5),
                    rep(new NotifyStep("Watchout", VOLUME, 5_000), 10),
                    rep(new NotifyStep("Serious", VOLUME, 60_000), 1)
            ), 1, MAX_PRI5);

    private NotifyPatterns() {
    }

    public static NotifyPattern forPriority(Reminder.Priority p) {
        if (p == null) return PRI3;
        // 5分岐を明示的に書く（default に潰さない＝将来ここだけ差し替えられるようにするため）
        return switch (p) {
            case Pri1 -> PRI1;
            case Pri2 -> PRI2;
            case Pri3 -> PRI3;
            case Pri4 -> PRI4;
            case Pri5 -> PRI5;
        };
    }

    /** 同じstepをtimes回繰り返したステップ列を作る（羅列すると冗長になるための読みやすさ用ヘルパ）。 */
    private static List<NotifyStep> rep(NotifyStep step, int times) {
        return Collections.nCopies(times, step);
    }

    /** 複数のステップ列を1本に連結する。 */
    @SafeVarargs
    private static List<NotifyStep> concat(List<NotifyStep>... parts) {
        List<NotifyStep> result = new ArrayList<>();
        for (List<NotifyStep> part : parts) {
            result.addAll(part);
        }
        return result;
    }
}
