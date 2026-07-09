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
public class DateTimeField extends JPanel {

    private static final Color ACTIVE_BG = new Color(200, 220, 255);

    // 欄の見た目サイズはここにまとめる（3つ独立して調整可）
    private static final int FIELD_FONT_SIZE = 18;      // JTextField（数字欄）のフォントサイズ
    private static final int SEPARATOR_FONT_SIZE = 18;  // 区切りJLabel（-, :, 空白）のフォントサイズ
    private static final int FIELD_EXTRA_COLUMNS = 1;   // JTextFieldの桁数(欄幅)に足す余白。欄そのものの横幅を広げる

    private final EnumMap<DateField, JTextField> fields = new EnumMap<>(DateField.class);
    private final List<Runnable> listeners = new ArrayList<>();
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
            bindKey(im, am, KeyEvent.VK_0 + d, name, () -> applyTransition(s -> DateTimeFieldLogic.typeDigit(s, digit)));
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + d, 0), name);
        }
        bindKey(im, am, KeyEvent.VK_LEFT, "moveLeft", () -> applyTransition(DateTimeFieldLogic::moveLeft));
        bindKey(im, am, KeyEvent.VK_RIGHT, "moveRight", () -> applyTransition(DateTimeFieldLogic::moveRight));
        bindKey(im, am, KeyEvent.VK_UP, "stepUp", () -> applyTransition(s -> DateTimeFieldLogic.stepUpDown(s, 1)));
        bindKey(im, am, KeyEvent.VK_DOWN, "stepDown", () -> applyTransition(s -> DateTimeFieldLogic.stepUpDown(s, -1)));
        bindKey(im, am, KeyEvent.VK_SPACE, "space", () -> applyTransition(DateTimeFieldLogic::pressSpace));
        bindKey(im, am, KeyEvent.VK_ENTER, "enter", this::handleEnter);
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

    // カーソル活性中はEnterを消費して現欄を確定・カーソル消滅（OKは発火しない）。
    // カーソル無活性中はデフォルトボタン（OK）へ委譲する＝Enter2回で登録完了
    private void handleEnter() {
        if (state != null && state.cursor() != null) {
            applyTransition(DateTimeFieldLogic::pressEnter);
            return;
        }
        JRootPane root = getRootPane();
        JButton defaultButton = root == null ? null : root.getDefaultButton();
        if (defaultButton != null) {
            defaultButton.doClick();
        }
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
