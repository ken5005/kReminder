package ken5005.kreminder.gui;

import ken5005.kreminder.Const;
import ken5005.kreminder.EditFormLogic;
import ken5005.kreminder.HolidayCheck;
import ken5005.kreminder.Reminder;
import ken5005.kreminder.sound.SND;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * リマインダー編集ダイアログ（GUI仕様v2 ③-b/③-c）。
 * 実行時刻・繰り返しの入力から EditFormLogic でプレビューとOK活性を毎打鍵更新する。
 * 保存書き戻し（③-d）はまだ行わない。
 */
public class EditDialog extends JDialog {

    /** 実行時刻欄の開かれ方（GUI仕様v2 §4.1）。NORMAL=欄分割ウィジェット／INSTANT=相対/絶対の1行入力。 */
    public enum Mode { NORMAL, INSTANT }

    private final Clock clock;

    // ③-c/③-dから参照するため各入力欄をフィールドとして保持する
    // 型はExecTimeInput（④・通常/instantモードの差し替えseam）。実体はコンストラクタでmodeにより分岐
    private final ExecTimeInput execTimeField;
    private final JTextField repeatField = new JTextField(20);
    private final JComboBox<Reminder.Priority> priorityCombo = new JComboBox<>(Reminder.Priority.values());
    private final JTextArea commentArea = new JTextArea(3, 20);
    private final JTextField cmdField = new JTextField(20);
    private final JTextArea previewArea = new JTextArea(6, 20);
    private final JButton okButton = new JButton("OK");
    // ダイアログ表示中は1秒ごとにupdatePreview()を呼び、残り時間表示をライブ更新する
    private Timer previewTimer;
    // OKで閉じられたかどうか。キャンセル・Esc・×はfalseのまま（保存はMainWindow側の責務）
    private boolean okPressed = false;

    public EditDialog(Frame owner, Reminder original, Clock clock) {
        this(owner, original, clock, Mode.NORMAL);
    }

    public EditDialog(Frame owner, Reminder original, Clock clock, Mode mode) {
        // モーダル型はAPPLICATION_MODAL（旧: true）ではなくDOCUMENT_MODALにする。
        // APPLICATION_MODALだと「owner=nullの発火ポップアップ（Main.showPopupがnew JDialog((Frame) null, ...)
        // で生成＝Swingが割り当てる共有隠しフレームが根）」までブロック対象にしてしまい、
        // 編集ダイアログを開いている間はポップアップが表示されてもOKが押せなくなる（入力が死ぬ）。
        // DOCUMENT_MODALなら「自分の一族（ownerを辿った先の根＝MainWindow）」だけがブロック対象になるので、
        // MainWindowは従来どおり編集中ブロックされ（編集中の行が消える事故を防ぐ）、
        // 別の根に属する発火ポップアップは生きたまま操作できる。
        // 二度とtrueに戻さないこと（戻すとこの不具合が再発する）。
        super(owner, mode == Mode.INSTANT ? "instant 追加" : "リマインダー編集",
                Dialog.ModalityType.DOCUMENT_MODAL);
        this.clock = clock;
        this.execTimeField = mode == Mode.INSTANT ? new InstantField(clock) : new DateTimeField();
        // IME自動On/Off（N9(c)）。実行時刻欄は各ウィジェット側で自己配線済みのためここでは触らない
        ImeControl.off(repeatField);
        ImeControl.off(cmdField);
        ImeControl.on(commentArea);
        // ダイアログがモーダル表示で活性化された瞬間にも、その時点のフォーカス保持者へ掛け直す（N9(c)）
        ImeControl.installWindowHook(this);

        // 選択行の既存値、または新規/複製でMainWindowが用意したReminderの値を各欄へ流し込む。
        // fireAtは呼び出し側が必ず非nullで渡す（編集=既存値、新規/複製=現在日時・秒0丸め）
        execTimeField.setDateTime(original.fireAt);
        repeatField.setText(original.repeat == null ? "" : original.repeat);
        priorityCombo.setSelectedItem(original.priority);
        commentArea.setText(original.message == null ? "" : original.message);
        cmdField.setText(original.action == null ? "" : original.action);

        // 入力欄フォント拡大（execTimeFieldの内部数字欄はConst.FONT_SIZE_DATETIME_FIELD側で制御済み・ここでは触らない）
        setFontSize(repeatField, Const.FONT_SIZE_EDIT_FIELD);
        setFontSize(commentArea, Const.FONT_SIZE_EDIT_FIELD);
        setFontSize(cmdField, Const.FONT_SIZE_EDIT_FIELD);
        setFontSize(priorityCombo, Const.FONT_SIZE_EDIT_FIELD);

        // 折り返し表示（長文コメントを横スクロールでなく複数行で見せる）
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);

        // JTextAreaは既定でEnterを改行として消費し、rootPaneのokOrGonまで届かない。
        // WHEN_FOCUSEDで握り直し、Enter=登録・Shift+Enter=改行にする（A案）
        commentArea.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "kreminder.submit");
        commentArea.getActionMap().put("kreminder.submit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onEnterPressed(); }
        });
        commentArea.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                 DefaultEditorKit.insertBreakAction);

        // Tabはタブ文字を入れず、他の欄と同じくフォーカス移動として扱う
        commentArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
            Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)));
        commentArea.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
            Set.of(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)));

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
        // 実行時刻欄は打鍵・移動・確定・閉店を含む状態変化のたびにChangeListenerで通知される
        execTimeField.addChangeListener(this::updatePreview);
        repeatField.getDocument().addDocumentListener(previewUpdater);
        // 実行時刻欄でのEnterはDateTimeField内で欄確定まで済ませてから通知される（v1.2・日時入力ウィジェット仕様§3.5）
        execTimeField.addEnterListener(this::onEnterPressed);

        getContentPane().add(buildForm(), BorderLayout.CENTER);
        getContentPane().add(buildButtons(), BorderLayout.SOUTH);

        // Escでキャンセルボタンと同じdispose（③-dでキャンセル処理が育ったら揃えて呼ぶこと）
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        // 実行時刻欄以外（繰り返し・コメント・Cmd等）でのEnterもここで一元的に拾う（v1.2）。
        // DateTimeFieldはWHEN_FOCUSEDでEnterを握っており、InputMapの優先順位はWHEN_FOCUSED >
        // WHEN_IN_FOCUSED_WINDOWなので、実行時刻欄にフォーカスがある間はDateTimeField側だけが動く（二重発火しない）
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "okOrGon");
        getRootPane().getActionMap().put("okOrGon", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onEnterPressed(); }
        });

        // OKボタンの見た目（太枠）のために残す。Enterの実処理は上のokOrGonバインドが担う（v1.2）
        getRootPane().setDefaultButton(okButton);

        // ×ボタンはJDialogの既定(HIDE_ON_CLOSE)だとsetVisible(false)だけでdispose()を素通りしてしまう。
        // OK・キャンセル・Escと同じくdispose()に集約させ、previewTimer停止・IME復帰(N9(c))を確実に効かせる
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // ダイアログを開いた時点でカーソルは日欄で活性（§4.8）＝実キーボードフォーカスもそこへ当てる
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                execTimeField.getComponent().requestFocusInWindow();
            }

            @Override public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        // 初期表示時点のプレビュー・OK活性を既存値に合わせておく
        updatePreview();

        // 残り時間表示（「○○後」）を1秒ごとに再計算してライブ更新する。
        // tick()が先＝相対指定（instantの+15m等）をnow基準で再解決してからプレビューに反映する
        previewTimer = new Timer(1000, e -> { execTimeField.tick(); updatePreview(); });
        previewTimer.start();

        pack();
    }

    /**
     * ダイアログを閉じる（OK・キャンセル・Esc・×ボタンいずれも最終的にここを通る。
     * ×はwindowClosing→dispose()で集約している）。
     * previewTimerを止め忘れるとダイアログを閉じた後も裏で1秒ごとに動き続けてしまうため、
     * dispose()をオーバーライドして一律停止する。
     * IMEを全角のまま閉じるとMainWindowへ戻ってからの掛け直し（installWindowHook）が
     * フォーカス移譲のタイミング次第で空振りすることがあるため（N9(c)）、
     * まだ表示中＝InputContextが取れるうちにここで自ら半角へ倒しておく
     */
    @Override
    public void dispose() {
        ImeControl.applyOff(this);
        if (previewTimer != null) previewTimer.stop();
        super.dispose();
    }

    /**
     * 実行時刻・繰り返しの現在値からプレビューを再計算し、OKボタンの活性も合わせて更新する。
     * 内容が変わったときだけ setText＋setCaretPosition(0) で先頭固定する。内容が同じなら
     * 何もしない＝ユーザーがスクロール中の位置を保つ（③-c-3の毎秒再計算での引き戻し防止）。
     */
    private void updatePreview() {
        String help = execTimeField.getErrorHelp();
        String preview = (help != null)
            ? help
            : EditFormLogic.buildPreview(
                execTimeField.getExecTimeText(),
                repeatField.getText(),
                LocalDateTime.now(clock),
                HolidayCheck.NONE);
        if (!preview.equals(previewArea.getText())) {
            previewArea.setText(preview);
            previewArea.setCaretPosition(0);
        }
        // OK活性はisTotallyValidのみで判定する（カーソル活性中かは問わない・N10）。
        // getExecTimeTextはカーソル活性中もゼロ埋め解釈で常に完全な日時を合成するためgate不要
        okButton.setEnabled(
            EditFormLogic.isTotallyValid(execTimeField.getExecTimeText(), repeatField.getText()));
    }

    /** ラベル＋入力欄をGridBagLayoutで縦に並べ、最終行にプレビュー欄を置く。 */
    private JPanel buildForm() {
        var panel = new JPanel(new GridBagLayout());
        var gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        var commentScroll = new JScrollPane(commentArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // GridBagLayoutはJScrollPaneの既定最小サイズ（極小）まで潰しうるため、3行ぶんの高さを下限に固定する。
        // 幅は0のままにしてfill=HORIZONTALでの横方向の伸縮を妨げない
        commentScroll.setMinimumSize(new Dimension(0, commentScroll.getPreferredSize().height));

        addRow(panel, gbc, 0, "実行時刻", execTimeField.getComponent());
        addRow(panel, gbc, 1, "コメント", commentScroll, GridBagConstraints.NORTHWEST);
        addRow(panel, gbc, 2, "優先度", priorityCombo);
        addRow(panel, gbc, 3, "繰り返し", repeatField);
        addRow(panel, gbc, 4, "Cmd", cmdField);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        var previewScroll = new JScrollPane(previewArea,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // 同様に6行ぶんの高さを下限に固定する
        previewScroll.setMinimumSize(new Dimension(0, previewScroll.getPreferredSize().height));
        panel.add(previewScroll, gbc);

        return panel;
    }

    // ラベル1列＋入力欄1列の1行分をGridBagLayoutに追加する（ラベルは既定でCENTER縦位置）
    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        addRow(panel, gbc, row, label, field, GridBagConstraints.CENTER);
    }

    // labelAnchor違いのオーバーロード。コメント行のように背の高い入力欄でラベルを上寄せにしたい場合に使う。
    // gbcはメソッド間で使い回すため、次の行に影響しないよう最後に既定のCENTERへ戻す
    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field, int labelAnchor) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.anchor = labelAnchor;
        JLabel labelComponent = new JLabel(label);
        setFontSize(labelComponent, Const.FONT_SIZE_EDIT_LABEL);
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        gbc.anchor = GridBagConstraints.CENTER;
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
     * Enter押下の共通処理（日時入力ウィジェット仕様 v1.2 §3.5）。
     * DateTimeField.handleEnter()は欄確定→ChangeListener→updatePreview()→okButton.setEnabled(...)を
     * 同期で走らせてからこのリスナを呼ぶので、ここでのisEnabled()は最新状態。
     * OKが活性なら登録完了、非活性（入力不正）なら警告音を鳴らして気づかせる。
     */
    private void onEnterPressed() {
        if (okButton.isEnabled()) {
            okButton.doClick();
        } else {
            SND.play("Oops");
        }
    }

    /**
     * OKボタンの処理。コメント空の警告（GUI仕様v2 §4.9）を確認してからokPressedをtrueにして閉じる。
     * 入力値のReminderへの書き戻し・保存はEditDialogでは行わず、MainWindow側の責務とする。
     */
    private void onOk() {
        var fireAt = EditFormLogic.parseExecTime(execTimeField.getExecTimeText());
        if (fireAt.isPresent()
                && EditFormLogic.needsEmptyCommentWarning(
                    commentArea.getText(), fireAt.get(), getSelectedPriority(), cmdField.getText(), repeatField.getText(),
                    LocalDateTime.now(clock))) {
            int result = JOptionPane.showConfirmDialog(
                this, "<<注意：コメントが空です>>", "確認", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
        }
        okPressed = true;
        dispose();
    }

    /** OKで閉じられたか（キャンセル・Esc・×はfalseのまま）。 */
    public boolean isOkPressed() {
        return okPressed;
    }

    public String getExecTimeText() {
        return execTimeField.getExecTimeText();
    }

    public String getRepeatText() {
        return repeatField.getText();
    }

    public Reminder.Priority getSelectedPriority() {
        return (Reminder.Priority) priorityCombo.getSelectedItem();
    }

    public String getCommentText() {
        return commentArea.getText();
    }

    public String getCmdText() {
        return cmdField.getText();
    }

    /** フォントサイズだけを差し替える（ファミリ・スタイルはderiveFontで維持）。 */
    private static void setFontSize(JComponent c, int size) {
        c.setFont(c.getFont().deriveFont((float) size));
    }
}
