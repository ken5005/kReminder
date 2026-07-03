package ken5005.kreminder.gui;

import ken5005.kreminder.Reminder;
import ken5005.kreminder.RemainFormat;
import ken5005.kreminder.RepeatSpec;

import javax.swing.table.AbstractTableModel;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JTable と List<Reminder> を橋渡しする TableModel。
 *
 * AbstractTableModel を継承することで、DefaultTableModel のように
 * 内部を Object[][] で持つ必要がなくなり、ドメインオブジェクトを直接保持できる。
 * 必須メソッド3つ（getRowCount / getColumnCount / getValueAt）＋列名を実装するだけでよい。
 *
 * リピート列・残り時間列は純関数（RepeatSpec.toJapanese / RemainFormat.formatRemaining）で
 * 整形して返す。それ以外は Reminder の値をそのまま返す。
 */
public class ReminderTableModel extends AbstractTableModel {

    // 列名・並びは仕様 §2.3 の最終形（Type/リピート/次回実行/残り時間/コメント/Cmd）
    private static final String[] COLUMN_NAMES = {
        "Type", "リピート", "次回実行", "残り時間", "コメント", "Cmd"
    };

    private final List<Reminder> reminders;
    private final Clock clock;

    public ReminderTableModel(List<Reminder> reminders, Clock clock) {
        this.reminders = reminders;
        this.clock = clock;
    }

    /** 1秒ごとに Main の Timer から呼ばれる。残り時間列は now 依存なので再描画で追従させる。 */
    public void tick() {
        fireTableDataChanged();
    }

    /** RowFilter が行番号から Reminder を引くために使う（フィルタ判定は ReminderFilter.isVisible に委譲）。 */
    public Reminder getReminderAt(int row) {
        return reminders.get(row);
    }

    @Override
    public int getRowCount() {
        return reminders.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int col) {
        return COLUMN_NAMES[col];
    }

    /**
     * 指定セルの値を返す。列1（リピート）・列3（残り時間）は純関数で整形する。
     * JTable はこの戻り値に対して toString() を呼んで表示する。
     */
    @Override
    public Object getValueAt(int row, int col) {
        Reminder r = reminders.get(row);
        return switch (col) {
            case 0 -> formatType(r.priority);
            case 1 -> formatRepeat(r.repeat);
            case 2 -> r.fireAt;
            case 3 -> formatRemain(r.fireAt);
            case 4 -> r.message;
            case 5 -> r.action;
            default -> null;
        };
    }

    // 優先度を「Pri-」を落とした数字だけの表示にする（例: Pri3 → "3"）。ソート比較器は次スライスで別途。
    private static String formatType(Reminder.Priority priority) {
        if (priority == null) return "";
        return priority.name().substring("Pri".length());
    }

    // repeat 生文字列を日本語化する。空なら空欄（単発予定）。
    // 不正な repeat で parse が例外を投げても、テーブル描画を落とさず生文字列にフォールバックする（原則5）。
    private static String formatRepeat(String repeat) {
        if (repeat == null || repeat.isEmpty()) return "";
        try {
            return RepeatSpec.parse(repeat).toJapanese();
        } catch (Exception e) {
            return repeat;
        }
    }

    // fireAt - now を残り時間表示に整形する。fireAt 未設定なら空欄。
    private String formatRemain(LocalDateTime fireAt) {
        if (fireAt == null) return "";
        Duration remain = Duration.between(LocalDateTime.now(clock), fireAt);
        return RemainFormat.formatRemaining(remain);
    }
}
