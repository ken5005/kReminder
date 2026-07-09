package ken5005.kreminder;

/**
 * 日時入力ウィジェットの状態（GUI仕様v2 §4.8）。イミュータブル・遷移は DateTimeFieldLogic が担う。
 * cursor は null で「カーソル消滅（無活性）」を表す。
 * buffer は null で「入力バッファ非活性（欄は確定値をそのまま表示）」、非null なら入力途中の右詰め数字列を表す。
 */
public record DateTimeFieldState(
    int year, int month, int day, int hour, int minute, int second,
    DateField cursor, String buffer
) {}
