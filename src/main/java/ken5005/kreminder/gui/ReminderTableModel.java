package ken5005.kreminder.gui;

import ken5005.kreminder.Reminder;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * JTable と List<Reminder> を橋渡しする TableModel。
 *
 * AbstractTableModel を継承することで、DefaultTableModel のように
 * 内部を Object[][] で持つ必要がなくなり、ドメインオブジェクトを直接保持できる。
 * 必須メソッド3つ（getRowCount / getColumnCount / getValueAt）＋列名を実装するだけでよい。
 *
 * このクラスは「Reminder の値をそのまま返す」だけ。整形・判断は別クラスへ。
 */
public class ReminderTableModel extends AbstractTableModel {

    // 列名は仕様 §2.3 の「生6列」に対応
    private static final String[] COLUMN_NAMES = {
        "発火日時", "メッセージ", "優先度", "アクション", "通知済", "繰り返し"
    };

    private final List<Reminder> reminders;

    public ReminderTableModel(List<Reminder> reminders) {
        this.reminders = reminders;
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
     * 指定セルの値を返す。Reminder の各フィールドをそのまま返す（整形なし）。
     * JTable はこの戻り値に対して toString() を呼んで表示する。
     */
    @Override
    public Object getValueAt(int row, int col) {
        Reminder r = reminders.get(row);
        return switch (col) {
            case 0 -> r.fireAt;
            case 1 -> r.message;
            case 2 -> r.priority;
            case 3 -> r.action;
            case 4 -> r.noticed;
            case 5 -> r.repeat;
            default -> null;
        };
    }
}
