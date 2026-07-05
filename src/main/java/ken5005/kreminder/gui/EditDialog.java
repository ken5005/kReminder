package ken5005.kreminder.gui;

import ken5005.kreminder.EditFormLogic;
import ken5005.kreminder.HolidayCheck;
import ken5005.kreminder.Reminder;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * リマインダー編集ダイアログ（GUI仕様v2 ③-b/③-c）。
 * 実行時刻・繰り返しの入力から EditFormLogic でプレビューとOK活性を毎打鍵更新する。
 * 保存書き戻し（③-d）はまだ行わない。
 */
public class EditDialog extends JDialog {

    // EditFormLogic と同じ書式（共通化は無理にしない＝重複を許容）
    private static final DateTimeFormatter FIRE_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;

    // ③-c/③-dから参照するため各入力欄をフィールドとして保持する
    private final JTextField execTimeField = new JTextField(20);
    private final JTextField repeatField = new JTextField(20);
    private final JComboBox<Reminder.Priority> priorityCombo = new JComboBox<>(Reminder.Priority.values());
    private final JTextField commentField = new JTextField(20);
    private final JTextField cmdField = new JTextField(20);
    private final JTextArea previewArea = new JTextArea(6, 20);
    private final JButton okButton = new JButton("OK");
    // ダイアログ表示中は1秒ごとにupdatePreview()を呼び、残り時間表示をライブ更新する
    private Timer previewTimer;

    public EditDialog(Frame owner, Reminder original, Clock clock) {
        super(owner, "リマインダー編集", true);
        this.clock = clock;

        // 選択行の既存値を各欄へ流し込む。null許容フィールドは空文字にフォールバック
        execTimeField.setText(original.fireAt == null ? "" : original.fireAt.format(FIRE_AT_FORMAT));
        repeatField.setText(original.repeat == null ? "" : original.repeat);
        priorityCombo.setSelectedItem(original.priority);
        commentField.setText(original.message == null ? "" : original.message);
        cmdField.setText(original.action == null ? "" : original.action);

        previewArea.setEditable(false);
        // 横スクロールを出さず、長い行（Usageヘルプ等）は折り返し表示にする
        previewArea.setLineWrap(true);
        previewArea.setWrapStyleWord(true);

        // プレビューに影響するのは実行時刻・繰り返しの2欄だけ（優先度/コメント/Cmdは不要）
        DocumentListener previewUpdater = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updatePreview(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePreview(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePreview(); }
        };
        execTimeField.getDocument().addDocumentListener(previewUpdater);
        repeatField.getDocument().addDocumentListener(previewUpdater);

        getContentPane().add(buildForm(), BorderLayout.CENTER);
        getContentPane().add(buildButtons(), BorderLayout.SOUTH);

        // Escでキャンセルボタンと同じdispose（③-dでキャンセル処理が育ったら揃えて呼ぶこと）
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        // 初期表示時点のプレビュー・OK活性を既存値に合わせておく
        updatePreview();

        // 残り時間表示（「○○後」）を1秒ごとに再計算してライブ更新する
        previewTimer = new Timer(1000, e -> updatePreview());
        previewTimer.start();

        pack();
    }

    /**
     * ダイアログを閉じる（OK・キャンセル・Esc・×ボタンいずれも最終的にここを通る）。
     * previewTimerを止め忘れるとダイアログを閉じた後も裏で1秒ごとに動き続けてしまうため、
     * dispose()をオーバーライドして一律停止する。
     */
    @Override
    public void dispose() {
        if (previewTimer != null) previewTimer.stop();
        super.dispose();
    }

    /**
     * 実行時刻・繰り返しの現在値からプレビューを再計算し、OKボタンの活性も合わせて更新する。
     * 内容が変わったときだけ setText＋setCaretPosition(0) で先頭固定する。内容が同じなら
     * 何もしない＝ユーザーがスクロール中の位置を保つ（③-c-3の毎秒再計算での引き戻し防止）。
     */
    private void updatePreview() {
        String preview = EditFormLogic.buildPreview(
            execTimeField.getText(),
            repeatField.getText(),
            LocalDateTime.now(clock),
            HolidayCheck.NONE);
        if (!preview.equals(previewArea.getText())) {
            previewArea.setText(preview);
            previewArea.setCaretPosition(0);
        }
        okButton.setEnabled(EditFormLogic.isTotallyValid(execTimeField.getText(), repeatField.getText()));
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
        var previewScroll = new JScrollPane(previewArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(previewScroll, gbc);

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
