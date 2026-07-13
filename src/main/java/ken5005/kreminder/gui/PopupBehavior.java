package ken5005.kreminder.gui;

import java.time.Duration;

/**
 * 発火ポップアップの挙動＝自動消滅までの時間（null可・自動消滅しない）とExtendボタンを見せるか。
 * GUI仕様v2 §5.1。Swingは import しない純粋なrecord（表駆動テスト対象）。
 */
public record PopupBehavior(Duration autoCloseAfter, boolean showExtend) {
}
