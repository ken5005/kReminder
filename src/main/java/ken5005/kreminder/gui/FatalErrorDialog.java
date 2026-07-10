package ken5005.kreminder.gui;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * 起動時・実行時の致命的エラーをloudに知らせて即終了する汎用ヘルパー（今回の配線先はsound-mapのみ）。
 */
public final class FatalErrorDialog {

    private FatalErrorDialog() {}

    /**
     * どのスレッドから呼んでも安全：EDT上ならそのまま、非EDTならinvokeAndWaitで包んでからexit(1)する。
     * 一時的な最前面フレームを親にするのは常駐窓がまだ無い起動時専用の割り切り。実行中の致命エラーに
     * 横展開する場合は、常駐窓（MainWindow等）が既にあるのでそれを親に渡せばowner生成は不要になる。
     */
    public static void showAndExit(String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            showDialog(message);
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> showDialog(message));
            } catch (Exception e) {
                // どうせ直後にexitするので、invokeAndWait自体の失敗は握りつぶしてよい
            }
        }
        System.exit(1);
    }

    /**
     * 親にnullを渡すと他アプリの背後に隠れることがある（＝無言の起動失敗と同じになる）ため、
     * alwaysOnTopな一時フレームを親にしてダイアログを強制的に最前面へ出す。
     */
    private static void showDialog(String message) {
        JFrame owner = new JFrame();
        owner.setAlwaysOnTop(true);
        owner.setUndecorated(true);
        owner.setLocationRelativeTo(null);
        owner.setVisible(true); // alwaysOnTopを効かせるにはownerが表示されている必要がある
        JOptionPane.showMessageDialog(owner, message, "kReminder 起動エラー", JOptionPane.ERROR_MESSAGE);
        owner.dispose();
    }
}
