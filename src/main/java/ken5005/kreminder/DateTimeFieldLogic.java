package ken5005.kreminder;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * 日時入力ウィジェット（欄分割方式）の状態遷移を担う純関数群（GUI仕様v2 §4.8）。
 * Swing 非依存＝ユニットテストのみで検証できる状態を保つ。clock-free（now()/Clock を呼ばない）。
 */
public final class DateTimeFieldLogic {

    private static final int MIN_YEAR = 2000;

    private DateTimeFieldLogic() {
    }

    /**
     * 初期状態を作る。編集時は既存値、新規時は呼び出し側が用意した現在日時を渡す。
     * カーソルは仕様どおり「日」欄から活性。
     */
    public static DateTimeFieldState initial(LocalDateTime dt) {
        return new DateTimeFieldState(dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
            dt.getHour(), dt.getMinute(), dt.getSecond(), DateField.DAY, null, false);
    }

    /**
     * 数字打鍵。カーソル無活性なら無効（入力無視）。
     * 欄に入った直後の最初の打鍵で旧値をクリアしてバッファを開始する（再進入リセット）。
     * バッファが欄幅に達したら値確定・カーソルは右隣へ（秒欄が満了した場合はカーソル消滅）。
     */
    public static DateTimeFieldState typeDigit(DateTimeFieldState s, int digit) {
        if (s.cursor() == null) return s;
        DateField field = s.cursor();
        String newBuffer = (s.buffer() == null ? "" : s.buffer()) + digit;
        if (newBuffer.length() < field.width) {
            // まだ同じ欄に留まる（欄離脱ではない）ので touched は変化させない
            return withCursorBuffer(s, field, newBuffer, s.touched());
        }
        DateTimeFieldState confirmed = withValue(s, field, Integer.parseInt(newBuffer));
        // 欄幅を満たして隣欄へ自動送り（＝欄離脱）なので touched=false に戻す
        return withCursorBuffer(confirmed, nextField(field), null, false);
    }

    /** ←キー。未完バッファをゼロ埋め確定してから左隣へ（左端では留まる）。カーソル無活性時は無反応。 */
    public static DateTimeFieldState moveLeft(DateTimeFieldState s) {
        return move(s, -1);
    }

    /** →キー。未完バッファをゼロ埋め確定してから右隣へ（右端では留まる）。カーソル無活性時は無反応。 */
    public static DateTimeFieldState moveRight(DateTimeFieldState s) {
        return move(s, 1);
    }

    private static DateTimeFieldState move(DateTimeFieldState s, int dir) {
        if (s.cursor() == null) return s;
        DateTimeFieldState committed = commitBuffer(s);
        DateField[] all = DateField.values();
        int idx = committed.cursor().ordinal() + dir;
        DateField target = (idx < 0 || idx >= all.length) ? committed.cursor() : all[idx];
        // 欄移動コマンドなので touched=false に戻す（端で留まる場合も含め一律）
        return withCursorBuffer(committed, target, null, false);
    }

    /** 欄クリック。現欄をゼロ埋め確定してから指定欄へ移動しバッファを新規化する（無活性からの再活性化も兼ねる）。 */
    public static DateTimeFieldState clickField(DateTimeFieldState s, DateField field) {
        DateTimeFieldState committed = commitBuffer(s);
        return withCursorBuffer(committed, field, null, false);
    }

    /** Enter。現欄の未完バッファをゼロ埋め確定しカーソルを消滅させる（deactivate と同一動作）。 */
    public static DateTimeFieldState pressEnter(DateTimeFieldState s) {
        return deactivate(s);
    }

    /**
     * Space。カーソル欄の入力バッファが活性中（打ちかけがある）、または現欄が↑↓／ホイールで
     * 編集済み（touched）なら、その値をゼロ埋め確定し（現欄は打った/増減した値のまま）、
     * 下位（カーソルより右）の欄だけを最小値にする。
     * どちらでもない（欄に居るがまだ何も編集していない＝再進入直後や自動送り直後）なら、
     * 現欄を含めて最小値にする（例: 時を打ち終えて分へ自動送りされた直後にSpaceを押した場合、
     * 分自身もクリアされないと「23:45:55」から「1」「2」「Space」で「12:00:00」にならない）。
     * いずれも確定後カーソル消滅。
     */
    public static DateTimeFieldState pressSpace(DateTimeFieldState s) {
        if (s.cursor() == null) return s;
        DateField cursorField = s.cursor();
        boolean keepCursorValue = s.buffer() != null || s.touched();
        DateTimeFieldState committed = commitBuffer(s);
        DateTimeFieldState filled = fillFromMinimum(committed, cursorField, keepCursorValue);
        return withCursorBuffer(filled, null, null, false);
    }

    /** 閉店（フォーカスロスト・他コンポーネント操作等）。未完バッファをゼロ埋め確定しカーソルを消滅させる。 */
    public static DateTimeFieldState deactivate(DateTimeFieldState s) {
        DateTimeFieldState committed = commitBuffer(s);
        return withCursorBuffer(committed, null, null, false);
    }

    /**
     * ↑↓キー／マウスホイール。delta は +1 か -1。カーソル無活性時は無反応。
     * まず未完バッファをゼロ埋め確定し、その時点の合成値が不正なら増減自体を行わない
     * （この場合 touched も変化しない）。実際に増減できた場合のみ touched=true にする。
     */
    public static DateTimeFieldState stepUpDown(DateTimeFieldState s, int delta) {
        if (s.cursor() == null) return s;
        DateTimeFieldState committed = commitBuffer(s);
        if (!isValid(committed)) return committed;

        DateTimeFieldState stepped = switch (committed.cursor()) {
            case SECOND -> withValue(committed, DateField.SECOND, wrap(committed.second() + delta, 0, 59));
            case MINUTE -> withValue(committed, DateField.MINUTE, wrap(committed.minute() + delta, 0, 59));
            case HOUR -> withValue(committed, DateField.HOUR, wrap(committed.hour() + delta, 0, 23));
            case MONTH -> adjustMonth(committed, delta);
            case DAY -> adjustDay(committed, delta);
            case YEAR -> adjustYear(committed, delta);
        };
        return withTouched(stepped, true);
    }

    private static DateTimeFieldState adjustMonth(DateTimeFieldState s, int delta) {
        int newMonth = wrap(s.month() + delta, 1, 12);
        int clampedDay = Math.min(s.day(), lastDayOfMonth(s.year(), newMonth));
        return withValue(withValue(s, DateField.MONTH, newMonth), DateField.DAY, clampedDay);
    }

    private static DateTimeFieldState adjustDay(DateTimeFieldState s, int delta) {
        int last = lastDayOfMonth(s.year(), s.month());
        return withValue(s, DateField.DAY, wrap(s.day() + delta, 1, last));
    }

    private static DateTimeFieldState adjustYear(DateTimeFieldState s, int delta) {
        int newYear = Math.max(s.year() + delta, MIN_YEAR);
        int clampedDay = Math.min(s.day(), lastDayOfMonth(newYear, s.month()));
        return withValue(withValue(s, DateField.YEAR, newYear), DateField.DAY, clampedDay);
    }

    /** yyyy-MM-dd HH:mm:ss を合成する。カーソル欄の未完バッファはゼロ埋め解釈で反映する。 */
    public static String composeText(DateTimeFieldState s) {
        return String.format("%04d-%02d-%02d %02d:%02d:%02d",
            effectiveValue(s, DateField.YEAR), effectiveValue(s, DateField.MONTH), effectiveValue(s, DateField.DAY),
            effectiveValue(s, DateField.HOUR), effectiveValue(s, DateField.MINUTE), effectiveValue(s, DateField.SECOND));
    }

    /** 指定欄の表示文字列。バッファ活性中の欄は右詰め・未入力上位桁は空白パディング。 */
    public static String fieldDisplayText(DateTimeFieldState s, DateField field) {
        if (s.cursor() == field && s.buffer() != null) {
            return spacePad(s.buffer(), field.width);
        }
        int v = valueOf(s, field);
        return field == DateField.YEAR ? String.format("%04d", v) : String.format("%02d", v);
    }

    private static boolean isValid(DateTimeFieldState s) {
        return EditFormLogic.parseExecTime(composeText(s)).isPresent();
    }

    private static int effectiveValue(DateTimeFieldState s, DateField field) {
        if (s.cursor() == field && s.buffer() != null) {
            return Integer.parseInt(zeroPad(s.buffer(), field.width));
        }
        return valueOf(s, field);
    }

    /**
     * cursorField 以降の欄を最小値にする。excludeCursor=true ならカーソル欄自身は対象外（下位のみ）、
     * false ならカーソル欄自身も含める。
     */
    private static DateTimeFieldState fillFromMinimum(DateTimeFieldState s, DateField cursorField, boolean excludeCursor) {
        DateTimeFieldState result = s;
        for (DateField f : DateField.values()) {
            boolean shouldMinimize = excludeCursor
                ? f.ordinal() > cursorField.ordinal()
                : f.ordinal() >= cursorField.ordinal();
            if (shouldMinimize) {
                int min = (f == DateField.MONTH || f == DateField.DAY) ? 1 : 0;
                result = withValue(result, f, min);
            }
        }
        return result;
    }

    /** 未完バッファをゼロ埋め確定して欄の値に反映し、バッファを空にする（カーソルは維持）。バッファ非活性なら何もしない。 */
    private static DateTimeFieldState commitBuffer(DateTimeFieldState s) {
        if (s.cursor() == null || s.buffer() == null) return s;
        int value = Integer.parseInt(zeroPad(s.buffer(), s.cursor().width));
        DateTimeFieldState updated = withValue(s, s.cursor(), value);
        // 確定するだけで欄離脱ではないので touched は維持する
        return withCursorBuffer(updated, updated.cursor(), null, updated.touched());
    }

    private static DateField nextField(DateField f) {
        int idx = f.ordinal() + 1;
        DateField[] all = DateField.values();
        return idx < all.length ? all[idx] : null;
    }

    private static int wrap(int v, int lo, int hi) {
        int range = hi - lo + 1;
        return Math.floorMod(v - lo, range) + lo;
    }

    private static int lastDayOfMonth(int year, int month) {
        return YearMonth.of(year, month).lengthOfMonth();
    }

    private static String zeroPad(String buf, int width) {
        return "0".repeat(width - buf.length()) + buf;
    }

    private static String spacePad(String buf, int width) {
        return " ".repeat(width - buf.length()) + buf;
    }

    private static int valueOf(DateTimeFieldState s, DateField f) {
        return switch (f) {
            case YEAR -> s.year();
            case MONTH -> s.month();
            case DAY -> s.day();
            case HOUR -> s.hour();
            case MINUTE -> s.minute();
            case SECOND -> s.second();
        };
    }

    private static DateTimeFieldState withValue(DateTimeFieldState s, DateField f, int v) {
        return switch (f) {
            case YEAR -> new DateTimeFieldState(v, s.month(), s.day(), s.hour(), s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case MONTH -> new DateTimeFieldState(s.year(), v, s.day(), s.hour(), s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case DAY -> new DateTimeFieldState(s.year(), s.month(), v, s.hour(), s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case HOUR -> new DateTimeFieldState(s.year(), s.month(), s.day(), v, s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case MINUTE -> new DateTimeFieldState(s.year(), s.month(), s.day(), s.hour(), v, s.second(), s.cursor(), s.buffer(), s.touched());
            case SECOND -> new DateTimeFieldState(s.year(), s.month(), s.day(), s.hour(), s.minute(), v, s.cursor(), s.buffer(), s.touched());
        };
    }

    private static DateTimeFieldState withCursorBuffer(DateTimeFieldState s, DateField cursor, String buffer, boolean touched) {
        return new DateTimeFieldState(s.year(), s.month(), s.day(), s.hour(), s.minute(), s.second(), cursor, buffer, touched);
    }

    private static DateTimeFieldState withTouched(DateTimeFieldState s, boolean touched) {
        return new DateTimeFieldState(s.year(), s.month(), s.day(), s.hour(), s.minute(), s.second(), s.cursor(), s.buffer(), touched);
    }
}
