package ken5005.kreminder.gui;

import ken5005.kreminder.InstantTimeLogic;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * instant 入力欄（時刻のみ・相対/絶対、GUI仕様v2 §4.4/④）。
 * InstantTimeLogic（純関数コア）の薄いSwing配線。DateTimeFieldと同じくExecTimeInputを実装し、
 * EditDialogから同じ扱いで差し替えられるようにする。
 */
public class InstantField extends JPanel implements ExecTimeInput {

    private static final DateTimeFormatter EXEC_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;
    private final JTextField textField = new JTextField(20);
    private final List<Runnable> listeners = new ArrayList<>();
    private InstantTimeLogic.Result result;

    public InstantField(Clock clock) {
        this.clock = clock;
        setLayout(new BorderLayout());
        add(textField, BorderLayout.CENTER);

        // 空欄からスタート＝§4.4「空欄はエラー」を初手から反映しておく
        reparse();

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { reparse(); }
            @Override public void removeUpdate(DocumentEvent e) { reparse(); }
            @Override public void changedUpdate(DocumentEvent e) { reparse(); }
        });
    }

    /** 打鍵のたびに現在のテキストをパースし直し、結果を保持してから変化を通知する。 */
    private void reparse() {
        result = InstantTimeLogic.parse(textField.getText(), LocalDateTime.now(clock));
        for (Runnable l : listeners) l.run();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    /**
     * JPanel（自分自身）は既定でfocusableでないため、そのままではEditDialogのwindowOpenedからの
     * requestFocusInWindow()が内部のtextFieldに届く保証がない。ここでオーバーライドして明示的に委譲する。
     */
    @Override
    public boolean requestFocusInWindow() {
        return textField.requestFocusInWindow();
    }

    /** instant は空欄から始まる仕様。呼ばれても無視する。 */
    @Override
    public void setDateTime(LocalDateTime dt) {
    }

    /** 成功時は確定済みfireAtを正規の文字列で返す。失敗時は生の入力文字列（EditFormLogic側でempty化される）。 */
    @Override
    public String getExecTimeText() {
        return result.fireAt() != null ? result.fireAt().format(EXEC_TIME_FORMAT) : textField.getText();
    }

    /** instant に未確定バッファの概念は無い。常にfalse。 */
    @Override
    public boolean isEditing() {
        return false;
    }

    @Override
    public void addChangeListener(Runnable r) {
        listeners.add(r);
    }

    /**
     * no-op。JTextFieldはActionListenerを持たなければEnterを消費せず、EditDialogのrootPane
     * （WHEN_IN_FOCUSED_WINDOW）のokOrGonバインドがそのまま拾う想定。実機での動作確認はstep5で行う。
     */
    @Override
    public void addEnterListener(Runnable r) {
    }

    @Override
    public String getErrorHelp() {
        return result.error();
    }

    /**
     * 毎秒呼ばれ、現在のテキストを最新の now で再パースする（listener は発火しない）。
     * 相対入力（+15m等）はこれで now に追従し続け、絶対入力（12:34等）は結果が変わらず自然に固定される。
     */
    @Override
    public void tick() {
        result = InstantTimeLogic.parse(textField.getText(), LocalDateTime.now(clock));
    }
}
