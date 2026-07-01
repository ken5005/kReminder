package ken5005.kreminder.gui;

import ken5005.kreminder.ReminderStore;

import javax.swing.*;
import java.awt.*;

/**
 * kReminder のメイン画面。
 * スライス①-a step3: ツールバー＋ダミーボタン＋ステータスバーを追加。
 */
public class MainWindow extends JFrame {

    // ステータスバーは ActionListener から更新するためフィールドに持つ
    private final JLabel statusBar = new JLabel(" ");

    public MainWindow() {
        super("kReminder");
        setSize(800, 500);
        // 画面中央に配置（null = 自画面基準）
        setLocationRelativeTo(null);
        // ×ボタンで JVM ごと終了（常駐トレイ版とは切り離した学習用 main 前提）
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        getContentPane().add(buildToolBar(), BorderLayout.NORTH);
        getContentPane().add(buildTable(),   BorderLayout.CENTER);
        getContentPane().add(statusBar,      BorderLayout.SOUTH);
    }

    /** ツールバーを組み立てる。ボタン機能は seam のみ——将来コントローラへ委譲する。 */
    private JToolBar buildToolBar() {
        var bar = new JToolBar();
        bar.setFloatable(false); // ドラッグで切り離せないようにする（常設ツールバーの慣用）

        // 各ボタンの ActionListener はステータスバーを更新するだけ（業務ロジックなし）
        for (String name : new String[]{"新規", "編集", "複製", "削除", "更新", "デバッグログ"}) {
            var btn = new JButton(name);
            btn.addActionListener(e -> statusBar.setText(name + " が押されました"));
            bar.add(btn);
        }
        return bar;
    }

    /** テーブルを組み立てる。読込失敗時は空リストで起動（ReminderStore.load() が保証）。 */
    private JScrollPane buildTable() {
        var reminders = ReminderStore.load();
        var model = new ReminderTableModel(reminders);
        var table = new JTable(model);
        // JScrollPane に載せないとヘッダ（列名）が表示されない — これは JTable の仕様
        return new JScrollPane(table);
    }

    /**
     * 学習用の単独起動エントリポイント。
     * Swing のコンポーネントはすべて EDT（Event Dispatch Thread）上で操作する決まりがあるため、
     * invokeLater でウィンドウ生成も EDT に委ねる。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
