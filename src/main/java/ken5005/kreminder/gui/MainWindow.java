package ken5005.kreminder.gui;

import javax.swing.*;

/**
 * kReminder のメイン画面。
 * スライス①-a step1: まず空の窓を立てて、EDT 上で Swing を扱う基本形を確認する。
 */
public class MainWindow extends JFrame {

    public MainWindow() {
        super("kReminder");
        setSize(800, 500);
        // 画面中央に配置（null = 自画面基準）
        setLocationRelativeTo(null);
        // ×ボタンで JVM ごと終了（常駐トレイ版とは切り離した学習用 main 前提）
        setDefaultCloseOperation(EXIT_ON_CLOSE);
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
