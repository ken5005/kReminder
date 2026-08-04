package ken5005.kreminder.gui;

import ken5005.kreminder.Const;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 消音モード中であることを示す非モーダルダイアログ（GUI仕様v2 §5.6）。
 * 終了ボタンまたは×で閉じると、コンストラクタで受け取ったonClosedが呼ばれる
 * （消音モードの解除自体はMainWindow側の責務・このクラスはSilentModeを直接触らない）。
 */
public class SilentModeDialog extends JDialog {

    public SilentModeDialog(Runnable onClosed) {
        // 発火ポップアップ（Main.showPopup）と同じくowner=null（変更禁止・設計上の厳守事項）
        super((Frame) null, "kReminder", false);

        getContentPane().setBackground(new Color(Const.SILENT_DIALOG_BG_RGB));

        // 前後に半角空白24個ずつ入れて横長に見せ、目立たせる（狙いどおりの表示・誤字ではない）
        JLabel label = new JLabel("                        消音中。。                        ");
        label.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        getContentPane().add(label, BorderLayout.CENTER);

        JButton closeButton = new JButton("終了");
        closeButton.addActionListener(e -> dispose());
        JPanel south = new JPanel();
        south.setOpaque(false); // contentPaneの背景色（緑）を透かして見せるため（Main.showPopupと同じ手筋）
        south.add(closeButton);
        getContentPane().add(south, BorderLayout.SOUTH);

        // 発火ポップアップと同じ手筋（N8）: 最前面だが非アクティブ＝表示してもキーボード入力を奪わない。
        // 代償として終了ボタンはマウス専用になる（仕様どおりの受容済みトレードオフ）
        setAlwaysOnTop(true);
        setFocusableWindowState(false);

        // ×はJDialog既定のHIDE_ON_CLOSEだとdispose()を素通りしてしまうため、
        // windowClosingでdispose()に寄せて終了ボタンと同じ経路（windowClosed）に集約する（EditDialogと同じ流儀）
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                onClosed.run();
            }
        });

        pack();
        setMinimumSize(new Dimension(200, 100));
        placeDialog();
    }

    /**
     * 表示位置を画面中央からConst.SILENT_DIALOG_OFFSET_Xだけ右・SILENT_DIALOG_OFFSET_Yだけ上へ
     * ずらして設定する。使用可能領域（タスクバー等を除く）をはみ出す場合は領域内にクランプする。
     */
    private void placeDialog() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = screen.x + (screen.width - getWidth()) / 2 + Const.SILENT_DIALOG_OFFSET_X;
        int y = screen.y + (screen.height - getHeight()) / 2 - Const.SILENT_DIALOG_OFFSET_Y;
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - getWidth()));
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - getHeight()));
        setLocation(x, y);
    }
}
