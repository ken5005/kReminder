package ken5005.kreminder.gui;

import ken5005.kreminder.ReminderStore;

import javax.swing.*;
import java.awt.*;

/**
 * kReminder のメイン画面。
 * スライス①-a step2: reminders.json を読み、生の6列テーブルとして表示する。
 */
public class MainWindow extends JFrame {

    public MainWindow() {
        super("kReminder");
        setSize(800, 500);
        // 画面中央に配置（null = 自画面基準）
        setLocationRelativeTo(null);
        // ×ボタンで JVM ごと終了（常駐トレイ版とは切り離した学習用 main 前提）
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // reminders.json を読む。失敗時は ReminderStore.load() が空リストを返すので here は常に非 null
        var reminders = ReminderStore.load();

        // TableModel にドメインリストを渡す。整形・判断は model 外へ（今回は raw のまま）
        var model = new ReminderTableModel(reminders);
        var table = new JTable(model);

        // JScrollPane に載せないとヘッダ（列名）が表示されない — これは JTable の仕様
        var scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
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
