package ken5005.kreminder.gui;

import ken5005.kreminder.DateField;
import ken5005.kreminder.DateTimeFieldLogic;
import ken5005.kreminder.DateTimeFieldState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 実行時刻の日時入力ウィジェット（欄分割方式・GUI仕様v2 §4.8）。
 * DateTimeFieldLogic（純関数コア）の薄いSwing配線。業務判断（打鍵解釈・確定・クランプ等）は
 * 一切ここに置かず、すべてLogic側の遷移関数に委譲する。
 */
public class DateTimeField extends JPanel implements ExecTimeInput {

    private static final Color ACTIVE_BG = new Color(200, 220, 255);

    // 欄の見た目サイズはここにまとめる（3つ独立して調整可）
    private static final int FIELD_FONT_SIZE = 18;      // JTextField（数字欄）のフォントサイズ
    private static final int SEPARATOR_FONT_SIZE = 18;  // 区切りJLabel（-, :, 空白）のフォントサイズ
    private static final int FIELD_EXTRA_COLUMNS = 1;   // JTextFieldの桁数(欄幅)に足す余白。欄そのものの横幅を広げる

    private final EnumMap<DateField, JTextField> fields = new EnumMap<>(DateField.class);
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Runnable> enterListeners = new ArrayList<>();
    private Color defaultBg;
    // setDateTime呼び出しまでは値未確定。コンストラクタではLocalDateTime.now()等を一切呼ばない
    private DateTimeFieldState state;

    public DateTimeField() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setFocusable(true);

        addField(DateField.YEAR);
        addSeparator("-");
        addField(DateField.MONTH);
        addSeparator("-");
        addField(DateField.DAY);
        addSeparator(" ");
        addField(DateField.HOUR);
        addSeparator(":");
        addField(DateField.MINUTE);
        addSeparator(":");
        addField(DateField.SECOND);

        setupKeyBindings();
        setupMouseWheel();

        // 他コンポーネントへフォーカスが移った瞬間＝閉店（未完バッファはゼロ埋め確定）。
        // ウィンドウの一時的なフォーカス喪失（isTemporary）では閉店させない
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (e.isTemporary()) return;
                applyTransition(DateTimeFieldLogic::deactivate);
            }
        });
    }

    /** 値の入口はこの1本のみ。呼ぶたびに日欄カーソル活性の初期状態へリセットする。 */
    public void setDateTime(LocalDateTime dt) {
        state = DateTimeFieldLogic.initial(dt);
        refresh();
        notifyListeners();
    }

    /** 常に "yyyy-MM-dd HH:mm:ss" を返す（既存 EditFormLogic 契約維持）。未設定時は空文字。 */
    public String getExecTimeText() {
        return state == null ? "" : DateTimeFieldLogic.composeText(state);
    }

    /** カーソルが活性中（＝未確定の編集中）かどうか。OK活性判定に使う。 */
    public boolean isEditing() {
        return state != null && state.cursor() != null;
    }

    /** 状態変化（打鍵・移動・確定・閉店を含む）のたびに呼ばれる。 */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Enter押下のたびに呼ばれる（カーソル活性/無活性を問わない）。OK発火・警告音判断はEditDialog側の責務。 */
    public void addEnterListener(Runnable listener) {
        enterListeners.add(listener);
    }

    /** ExecTimeInput契約：EditDialogがレイアウトに置く実体は自分自身。 */
    @Override
    public JComponent getComponent() {
        return this;
    }

    /** ExecTimeInput契約：DateTimeFieldは不正値もそのまま保持・表示するだけで文法エラー説明を持たない。 */
    @Override
    public String getErrorHelp() {
        return null;
    }

    private void addField(DateField field) {
        JTextField tf = new JTextField(field.width + FIELD_EXTRA_COLUMNS);
        tf.setEditable(false);
        tf.setFocusable(false); // キャレット編集させない。フォーカスはパネル代表で受ける
        tf.setFont(new Font(Font.MONOSPACED, Font.PLAIN, FIELD_FONT_SIZE));
        tf.setHorizontalAlignment(JTextField.CENTER);
        tf.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                applyTransition(s -> DateTimeFieldLogic.clickField(s, field));
            }
        });
        if (defaultBg == null) defaultBg = tf.getBackground();
        fields.put(field, tf);
        add(tf);
    }

    private void addSeparator(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont((float) SEPARATOR_FONT_SIZE));
        add(label);
    }

    // 数字0-9（テンキー含む）・矢印・Enter・Spaceをパネル自身のフォーカス時のみ捕まえる
    private void setupKeyBindings() {
        InputMap im = getInputMap(WHEN_FOCUSED);
        ActionMap am = getActionMap();

        for (int d = 0; d <= 9; d++) {
            int digit = d;
            String name = "typeDigit" + d;
            bindKey(im, am, KeyEvent.VK_0 + d, name, () -> typeDigitAndMaybeAdvance(digit));
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + d, 0), name);
        }
        bindKey(im, am, KeyEvent.VK_LEFT, "moveLeft", () -> applyTransition(DateTimeFieldLogic::moveLeft));
        bindKey(im, am, KeyEvent.VK_RIGHT, "moveRight", () -> applyTransition(DateTimeFieldLogic::moveRight));
        bindKey(im, am, KeyEvent.VK_UP, "stepUp", () -> applyTransition(s -> DateTimeFieldLogic.stepUpDown(s, 1)));
        bindKey(im, am, KeyEvent.VK_DOWN, "stepDown", () -> applyTransition(s -> DateTimeFieldLogic.stepUpDown(s, -1)));
        bindKey(im, am, KeyEvent.VK_SPACE, "space", () -> {
            applyTransition(DateTimeFieldLogic::pressSpace);
            // 確定後は次のコンポーネント（＝繰り返し欄）へフォーカスを送る（v1.2）。
            // 遷移先が誰かはウィジェットが知る必要はない
            KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(this);
        });
        bindKey(im, am, KeyEvent.VK_ENTER, "enter", this::handleEnter);
    }

    /**
     * 数字打鍵。秒欄（右端）が満了するとカーソルが消滅する（DateTimeFieldLogic.typeDigit の
     * nextField(SECOND) == null）。その場合は Space / Tab で抜けたときと同じく、
     * 次のコンポーネント（＝繰り返し欄）へフォーカスを送って操作感を揃える（v1.2）。
     * wasEditingを見るのは、カーソル無活性中の打鍵（typeDigitが無反応で返る）でフォーカスを飛ばさないため。
     */
    private void typeDigitAndMaybeAdvance(int digit) {
        boolean wasEditing = isEditing();
        applyTransition(s -> DateTimeFieldLogic.typeDigit(s, digit));
        if (wasEditing && !isEditing()) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().focusNextComponent(this);
        }
    }

    private void bindKey(InputMap im, ActionMap am, int keyCode, String name, Runnable action) {
        im.put(KeyStroke.getKeyStroke(keyCode, 0), name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // カーソル活性中はまず現欄を確定・カーソル消滅させる（→ChangeListener経由でOK活性が最新化される）。
    // カーソルの活性/無活性を問わず、確定処理のあと必ずenterListenersへ通知する（v1.2・Enter1回化）。
    // OK発火するか警告音を鳴らすかの判断はEditDialog側の責務＝ここではrootPane/defaultButtonに触れない
    private void handleEnter() {
        if (state != null && state.cursor() != null) {
            applyTransition(DateTimeFieldLogic::pressEnter);
        }
        for (Runnable l : enterListeners) l.run();
    }

    private void setupMouseWheel() {
        addMouseWheelListener(e -> {
            int delta = e.getWheelRotation() < 0 ? 1 : -1;
            applyTransition(s -> DateTimeFieldLogic.stepUpDown(s, delta));
        });
    }

    private void applyTransition(UnaryOperator<DateTimeFieldState> transition) {
        if (state == null) return;
        state = transition.apply(state);
        refresh();
        notifyListeners();
    }

    private void refresh() {
        if (state == null) return;
        for (DateField f : DateField.values()) {
            JTextField tf = fields.get(f);
            tf.setText(DateTimeFieldLogic.fieldDisplayText(state, f));
            tf.setBackground(f == state.cursor() ? ACTIVE_BG : defaultBg);
        }
    }

    private void notifyListeners() {
        for (Runnable l : listeners) l.run();
    }
}
