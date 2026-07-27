package ken5005.kreminder.gui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * フォーカスが当たった瞬間にIMEをOn/Offするユーティリティ（N9(c)）。
 * enableInputMethods(false)は使わない（IMEを禁止せず、デフォルトを傾けるだけの方針）。
 * IME切替はあくまで利便機能なので、InputContextが取れない・例外が飛ぶ場合も黙って何もしない。
 *
 * 起動直後やダイアログから戻った直後は、そのコンポーネントへのfocusGainedが
 * ウィンドウ自体のアクティブ化より先に飛んでしまい、その時点でのsetCompositionEnabledが
 * 空振りすることがある。installWindowHookはウィンドウがフォーカスを得た瞬間に
 * その時点のフォーカス保持者へ改めて掛け直すための保険。
 */
public class ImeControl {

    private static final String KEY = "kreminder.ime.enabled";

    private ImeControl() {
    }

    /** cにフォーカスが入るたびIMEを半角（合成無効）側へ倒す。 */
    public static void off(JComponent c) {
        attach(c, false);
    }

    /** cにフォーカスが入るたびIMEを全角（合成有効）側へ倒す。 */
    public static void on(JComponent c) {
        attach(c, true);
    }

    private static void attach(JComponent c, boolean enabled) {
        // installWindowHookが後から読み直せるよう、望みのIME状態をコンポーネント自身に記録しておく
        c.putClientProperty(KEY, enabled);
        c.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                apply(c, enabled);
            }
        });
    }

    /**
     * ウィンドウがアクティブ化された瞬間、その時点のフォーカス保持者へIME状態を掛け直す。
     * MainWindow・EditDialogの組み立て時にそれぞれ1回ずつ呼ぶ想定。
     */
    public static void installWindowHook(Window w) {
        w.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                // windowGainedFocusの発火時点ではフォーカス移譲が未完了で、getPermanentFocusOwnerが
                // まだ移譲元を指していることがある（EditDialogをEnter/Escで閉じた直後等）。
                // イベントキュー上のフォーカス移動が完了してから読み直すため丸ごと遅延させる
                SwingUtilities.invokeLater(() -> {
                    var owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
                    if (!(owner instanceof JComponent jc)) return;
                    Object enabled = jc.getClientProperty(KEY);
                    if (enabled instanceof Boolean b) apply(jc, b);
                });
            }
        });
    }

    /**
     * cのIMEを即時に半角（合成無効）へ倒す。リスナもプロパティも付けず、その場で1回だけ呼ぶ。
     * ウィンドウが閉じる（dispose等でpeerが失われる）直前、まだ表示中のうちに呼ぶ用途を想定する。
     */
    public static void applyOff(Component c) {
        apply(c, false);
    }

    private static void apply(Component c, boolean enabled) {
        try {
            var ic = c.getInputContext();
            if (ic != null) ic.setCompositionEnabled(enabled);
        } catch (RuntimeException ex) {
            // 利便機能のため失敗は無視する（ログも出さない）
        }
    }
}
