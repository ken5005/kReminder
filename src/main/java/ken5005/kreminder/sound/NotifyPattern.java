package ken5005.kreminder.sound;

import java.time.Duration;
import java.util.List;

/**
 * 「鳴らし方」の手順書。steps を頭から順に1回流す→ repeatTail&gt;0 なら steps の末尾 repeatTail 個の
 * サイクルを、以降 stop() されるか maxDuration に達するまで繰り返す。repeatTail=0 は繰り返さない。
 * maxDuration が null かつ repeatTail&gt;0 は stop() されるまで永久に鳴り続ける意味。
 */
public record NotifyPattern(List<NotifyStep> steps, int repeatTail, Duration maxDuration) {

    public NotifyPattern {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("steps は必須（null/空不可）");
        }
        if (repeatTail < 0) {
            throw new IllegalArgumentException("repeatTail は0以上でなければならない: " + repeatTail);
        }
        if (repeatTail > steps.size()) {
            throw new IllegalArgumentException(
                    "repeatTail は steps.size() 以下でなければならない: " + repeatTail + " > " + steps.size());
        }
        steps = List.copyOf(steps);
    }
}
