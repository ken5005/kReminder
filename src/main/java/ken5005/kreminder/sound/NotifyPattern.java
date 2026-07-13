package ken5005.kreminder.sound;

import java.time.Duration;
import java.util.List;

/** 「鳴らし方」の手順書＝ステップ列＋ループするか＋ループ時の最大時間。loop=falseならmaxDurationは無視（null可）。 */
public record NotifyPattern(List<NotifyStep> steps, boolean loop, Duration maxDuration) {
}
