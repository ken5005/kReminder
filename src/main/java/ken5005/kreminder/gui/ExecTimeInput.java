package ken5005.kreminder.gui;

import javax.swing.JComponent;
import java.time.LocalDateTime;

/**
 * 編集ダイアログの「実行時刻」欄が満たすべき契約（GUI仕様v2 ④）。
 * 通常モードの DateTimeField（欄分割ウィジェット）と instant モードの InstantField（相対/絶対の1行入力）を
 * EditDialog から同じ扱いで差し替えるための seam。
 */
public interface ExecTimeInput {

    /** EditDialog がレイアウトに置く実体。 */
    JComponent getComponent();

    /** 初期値の流し込み。 */
    void setDateTime(LocalDateTime dt);

    /** 正規の絶対日時文字列（"yyyy-MM-dd HH:mm:ss"）。不正入力時は不正な文字列でよい（EditFormLogic側でemptyになる）。 */
    String getExecTimeText();

    /** 未確定の編集中か（OK活性のgateに使う）。 */
    boolean isEditing();

    /** 状態変化のたびに呼ばれる。 */
    void addChangeListener(Runnable r);

    /** Enter押下（欄確定後）に呼ばれる。 */
    void addEnterListener(Runnable r);

    /** 入力不正時の説明。正常時は null。 */
    String getErrorHelp();

    /**
     * ダイアログ表示中に定期的（1秒ごと）に呼ばれる。相対時刻を now 基準で再解決するためのフック。
     * 表示更新は呼び出し側（EditDialog.updatePreview）が担うので、この中で ChangeListener を発火してはならない。
     */
    void tick();
}
