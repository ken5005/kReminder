package ken5005.kreminder.gui;

import ken5005.kreminder.Reminder;

import java.time.Duration;

/**
 * priority → ポップアップ挙動（PopupBehavior）の対応表（純関数）。GUI仕様v2 §5.1。
 * Pri-1のみ5秒で自動消滅・Extend非表示。Pri-2〜Pri-5は自動消滅なし・OK/Extendの2ボタン。
 */
public final class PopupBehaviors {

    private static final PopupBehavior PRI1 = new PopupBehavior(Duration.ofSeconds(5), false);
    private static final PopupBehavior STANDARD = new PopupBehavior(null, true);

    private PopupBehaviors() {
    }

    public static PopupBehavior forPriority(Reminder.Priority p) {
        if (p == null) return STANDARD;
        // 5分岐を明示的に書く（default に潰さない＝NotifyPatterns.forPriorityと同じ流儀）
        return switch (p) {
            case Pri1 -> PRI1;
            case Pri2 -> STANDARD;
            case Pri3 -> STANDARD;
            case Pri4 -> STANDARD;
            case Pri5 -> STANDARD;
        };
    }
}
