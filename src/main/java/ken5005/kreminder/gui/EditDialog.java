package ken5005.kreminder.gui;

import ken5005.kreminder.Reminder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;

/**
 * リマインダー編集ダイアログ（GUI仕様v2 ③-b）。
 * 今回は器のみ：既存値の流し込みと開閉だけを行う。
 * プレビュー配線（EditFormLogic.buildPreview）と保存書き戻しは③-c/③-dで追加する。
 */
public class EditDialog extends JDialog {

    // EditFormLogic と同じ書式（共通化は無理にしない＝重複を許容）
    private static final DateTimeFormatter FIRE_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ③-c/③-dから参照するため各入力欄をフィールドとして保持する
    private final JTextField execTimeField = new JTextField(20);
    private final JTextField repeatField = new JTextField(20);
    private final JComboBox<Reminder.Priority> priorityCombo = new JComboBox<>(Reminder.Priority.values());
    private final JTextField commentField = new JTextField(20);
    private final JTextField cmdField = new JTextField(20);
    private final JTextArea previewArea = new JTextArea(6, 20);

    public EditDialog(Frame owner, Reminder original) {
        super(owner, "リマインダー編集", true);

        // 選択行の既存値を各欄へ流し込む。null許容フィールドは空文字にフォールバック
        execTimeField.setText(original.fireAt == null ? "" : original.fireAt.format(FIRE_AT_FORMAT));
        repeatField.setText(original.repeat == null ? "" : original.repeat);
        priorityCombo.setSelectedItem(original.priority);
        commentField.setText(original.message == null ? "" : original.message);
        cmdField.setText(original.action == null ? "" : original.action);

        // プレビューは③-cで毎打鍵配線するまでは静的な仮テキストにしておく
        previewArea.setEditable(false);
        previewArea.setText("（プレビューは③-cで配線）");

        getContentPane().add(buildForm(), BorderLayout.CENTER);
        getContentPane().add(buildButtons(), BorderLayout.SOUTH);

        // Escでキャンセルボタンと同じdispose（③-dでキャンセル処理が育ったら揃えて呼ぶこと）
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
    }

    /** ラベル＋入力欄をGridBagLayoutで縦に並べ、最終行にプレビュー欄を置く。 */
    private JPanel buildForm() {
        var panel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        addRow(panel, gbc, 0, "実行時刻", execTimeField);
        addRow(panel, gbc, 1, "繰り返し", repeatField);
        addRow(panel, gbc, 2, "優先度", priorityCombo);
        addRow(panel, gbc, 3, "コメント", commentField);
        addRow(panel, gbc, 4, "Cmd", cmdField);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        panel.add(new JScrollPane(previewArea), gbc);

        return panel;
    }

    // ラベル1列＋入力欄1列の1行分をGridBagLayoutに追加する
    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private JPanel buildButtons() {
        var panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        var okButton = new JButton("OK");
        var cancelButton = new JButton("キャンセル");
        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> dispose());
        panel.add(okButton);
        panel.add(cancelButton);
        return panel;
    }

    /**
     * OKボタンの処理。今回はダイアログを閉じるだけ。
     * ③-dで「入力値をReminderへ書き戻してリスト置換＋保存」をここに足す差し込み口。
     */
    private void onOk() {
        dispose();
    }
}
