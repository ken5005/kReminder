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

    /**
     * 1秒ごとに Main の Timer から呼ばれる。残り時間列は now 依存なので再描画で追従させる。
     * fireTableDataChanged() ではなく fireTableRowsUpdated で「値だけ更新」と通知する。
     * fireTableDataChanged はモデル全体差し替え相当の通知になり、JTable が選択行を破棄してしまう
     * （毎秒選択が解除され「編集」ボタンが常に未選択扱いになるバグの原因だった）。
     *
     * 不変条件: tick() は値の更新のみを行い、行の追加・削除・並び替えは一切しない。
     * 行数が変わらないからこそ fireTableRowsUpdated(0, getRowCount()-1) で範囲を固定してよい。
     * 将来 tick() 内で行を増減させる処理（例: 発火済みリマインダーの自動削除）を足す場合は、
     * その増減側で fireTableRowsInserted/Deleted 等の専用通知を出すこと。tick() の役割を
     * 値更新に限定したまま、行数変化の通知と混同しないこと。
     */
    public void tick() {
        if (getRowCount() > 0) {
            fireTableRowsUpdated(0, getRowCount() - 1);
        }
    }

    /** RowFilter が行番号から Reminder を引くために使う（フィルタ判定は ReminderFilter.isVisible に委譲）。 */
    public Reminder getReminderAt(int row) {
        return reminders.get(row);
    }

    /** 編集ダイアログでの書き戻し後、指定行の値が変わったことをJTableに通知する（GUI仕様v2 ③-d）。 */
    public void reminderUpdatedAt(int modelRow) {
        fireTableRowsUpdated(modelRow, modelRow);
    }

    /**
     * 新規/複製で作成したReminderをリスト末尾に追加し、行追加をJTableに通知する（GUI仕様v2 §2.5.1/2.5.2）。
     * 戻り値は追加された行のモデル行インデックス（呼び出し側がsorter経由でビュー行へ変換して選択・スクロールに使う）。
     */
    public int addReminder(Reminder r) {
        reminders.add(r);
        int modelRow = reminders.size() - 1;
        fireTableRowsInserted(modelRow, modelRow);
        return modelRow;
    }

    /** 削除操作でモデルから該当行を除去し、行削除をJTableに通知する（GUI仕様v2 §2.5.4）。 */
    public void removeReminderAt(int modelRow) {
        reminders.remove(modelRow);
        fireTableRowsDeleted(modelRow, modelRow);
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
