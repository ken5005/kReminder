package ken5005.kreminder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 編集ダイアログ向けの純関数群（GUI仕様v2 ③-a）。
 * Swing / java.io / Gson 非依存＝ユニットテストのみで検証できる状態を保つ。
 */
public final class EditFormLogic {

    private EditFormLogic() {
    }

    private static final DateTimeFormatter WITH_SECONDS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter WITHOUT_SECONDS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String TIME_FORMAT_ERROR = "時刻入力エラー";
    private static final String PAST_FIRED_LABEL = "（発火済み）";
    private static final String REP_FORMAT_ERROR =
        "繰り返し書式エラー 例) rep=1d;ex=0,6 / rep=1M;day=25;kuriage";
    private static final int PREVIEW_COUNT = 10;
    private static final int MIN_YEAR = 2000;
    private static final Duration EMPTY_COMMENT_WARN_THRESHOLD = Duration.ofMinutes(5);

    /**
     * 実行時刻文字列をパースする。秒省略時は00秒扱い。
     * 書式不正・null・空文字（trim後）は empty。過去日時でも書式が正しければ valid。
     * 年が MIN_YEAR 未満の場合も empty（日時入力ウィジェットの年欄途中Enterがサイレントに通るのを防ぐ）。
     */
    public static Optional<LocalDateTime> parseExecTime(String s) {
        if (s == null) return Optional.empty();
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return Optional.empty();

        try {
            return withMinYear(LocalDateTime.parse(trimmed, WITH_SECONDS));
        } catch (DateTimeParseException e) {
            // 秒省略形式で再試行
        }
        try {
            return withMinYear(LocalDateTime.parse(trimmed, WITHOUT_SECONDS));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDateTime> withMinYear(LocalDateTime dt) {
        return dt.getYear() < MIN_YEAR ? Optional.empty() : Optional.of(dt);
    }

    /**
     * 繰り返し文字列の妥当性を判定する。trim後空文字は単発扱いでvalid。
     */
    public static boolean isValidRep(String repStr) {
        String trimmed = repStr == null ? "" : repStr.trim();
        if (trimmed.isEmpty()) return true;
        try {
            RepeatSpec.parse(trimmed);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * OKボタンの活性判定。実行時刻・繰り返しの両方がvalidならtrue（Cmdは判定に含めない＝常に可）。
     */
    public static boolean isTotallyValid(String execTimeStr, String repStr) {
        return parseExecTime(execTimeStr).isPresent() && isValidRep(repStr);
    }

    /**
     * 実行時刻・繰り返しの入力からプレビュー文字列（複数行）を組み立てる。
     * now・holiday は外部注入（テストでは HolidayCheck.NONE を渡す）。
     */
    public static String buildPreview(String execTimeStr, String repStr, LocalDateTime now, HolidayCheck holiday) {
        Optional<LocalDateTime> parsed = parseExecTime(execTimeStr);
        if (parsed.isEmpty()) {
            return TIME_FORMAT_ERROR;
        }
        LocalDateTime fireAt = parsed.get();

        String remain = RemainFormat.formatRemaining(Duration.between(now, fireAt));
        String firstLine = remain.isEmpty() ? PAST_FIRED_LABEL : remain;

        String trimmedRep = repStr == null ? "" : repStr.trim();
        if (trimmedRep.isEmpty()) {
            return firstLine + "\n" + fireAt.format(WITH_SECONDS);
        }

        RepeatSpec spec;
        try {
            spec = RepeatSpec.parse(trimmedRep);
        } catch (RuntimeException e) {
            return firstLine + "\n" + REP_FORMAT_ERROR;
        }

        StringBuilder sb = new StringBuilder(firstLine);
        LocalDateTime t = fireAt;
        for (int i = 0; i < PREVIEW_COUNT; i++) {
            sb.append("\n").append(t.format(WITH_SECONDS));
            if (i < PREVIEW_COUNT - 1) {
                t = spec.next(t, holiday);
            }
        }
        return sb.toString();
    }

    /**
     * コメント空の警告要否を判定する（GUI仕様v2 §4.9）。
     * コメントが非空なら常にfalse。コメントが空のときのみ、
     * 「実行時刻がnow+5分より先」または「Type/Rep/Cmdのいずれかがデフォルトから変更」でtrue。
     * fireAtがnull（未確定）なら時間条件は不成立として扱う。
     */
    public static boolean needsEmptyCommentWarning(
            String comment,
            LocalDateTime fireAt,
            Reminder.Priority priority,
            String action,
            String repeat,
            LocalDateTime now) {
        if (!isBlank(comment)) return false;

        boolean timeCondition = fireAt != null && fireAt.isAfter(now.plus(EMPTY_COMMENT_WARN_THRESHOLD));
        boolean nonDefaultCondition = (priority != null && priority != Reminder.Priority.Pri3)
            || !isBlank(action)
            || !isBlank(repeat);

        return timeCondition || nonDefaultCondition;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
