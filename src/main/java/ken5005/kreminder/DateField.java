package ken5005.kreminder;

/**
 * 日時入力ウィジェットの6欄（GUI仕様v2 §4.8）。
 * 宣言順がそのままカーソル移動順（YEAR→…→SECOND）＝ordinal()で前後関係を判定する。
 */
public enum DateField {
    YEAR(4), MONTH(2), DAY(2), HOUR(2), MINUTE(2), SECOND(2);

    public final int width;

    DateField(int width) {
        this.width = width;
    }
}
