package ken5005.kreminder.sound;

import ken5005.kreminder.Reminder;

import java.util.List;

/**
 * priority → 鳴らし方（NotifyPattern）の対応表（純関数）。GUI仕様v2 §5.1/5.2、SND仕様v2.1 §11。
 * ⑤時点は全priority共通で "Standard" を volume 1.0 で1回のみ。
 * 将来 Pri-5 を「0.5で鳴らす→500ms休む→1.0で鳴らす→…をループ、最大90分」のように変えるときは
 * この表（forPriorityのswitch）だけ書き換えればよく、Notifierや呼び出し元（Main）は触らずに済む。
 */
public final class NotifyPatterns {

    private static final NotifyPattern STANDARD_ONCE =
            new NotifyPattern(List.of(new NotifyStep("Standard", 1.0f, 0)), 0, null);

    private NotifyPatterns() {
    }

    public static NotifyPattern forPriority(Reminder.Priority p) {
        if (p == null) return STANDARD_ONCE;
        // 5分岐を明示的に書く（default に潰さない＝将来ここだけ差し替えられるようにするため）
        return switch (p) {
            case Pri1 -> STANDARD_ONCE;
            case Pri2 -> STANDARD_ONCE;
            case Pri3 -> STANDARD_ONCE;
            case Pri4 -> STANDARD_ONCE;
            case Pri5 -> STANDARD_ONCE;
        };
    }
}
