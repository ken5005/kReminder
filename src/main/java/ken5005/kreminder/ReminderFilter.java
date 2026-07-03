package ken5005.kreminder;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 一覧のフィルタ判定に使う純関数群（GUI仕様v2 §3）。
 * Swing/io/Gson 非依存。
 */
public final class ReminderFilter {

    // 間隔クラスの分類閾値（日数・GUI仕様v2 §3.5）
    private static final long SHORT_TERM_THRESHOLD_DAYS = 1;
    private static final long DAILY_THRESHOLD_DAYS = 7;
    private static final long WEEKLY_THRESHOLD_DAYS = 30;

    // 間隔クラスごとの先読み窓（GUI仕様v2 §3.5）
    private static final Duration LEAD_WINDOW_SHORT_TERM = Duration.ofHours(3);
    private static final Duration LEAD_WINDOW_DAILY = Duration.ofHours(6);
    private static final Duration LEAD_WINDOW_WEEKLY = Duration.ofDays(2);
    private static final Duration LEAD_WINDOW_MONTHLY = Duration.ofDays(3);

    // 時間バケツの分類閾値（GUI仕様v2 §3.2）
    private static final Duration BUCKET_IMMINENT_MAX = Duration.ofHours(8);
    private static final Duration BUCKET_SOON_MAX = Duration.ofDays(7);

    private ReminderFilter() {
    }

    // 一覧の時間バケツ分類（表示用ではなく内部分類キー）
    public enum Bucket { 終了済, 直近, 近日, 先 }

    /**
     * 残り時間（fireAt - now）を時間バケツへ分類する純関数（GUI仕様v2 §3.2）。
     */
    public static Bucket bucketOf(Duration remain) {
        if (remain.isZero() || remain.isNegative()) return Bucket.終了済;
        if (remain.compareTo(BUCKET_IMMINENT_MAX) < 0) return Bucket.直近;
        if (remain.compareTo(BUCKET_SOON_MAX) < 0) return Bucket.近日;
        return Bucket.先;
    }

    // repeatVal/unit から概算日数を出す。分類の閾値が30日境界なので月=30日概算で足りる（整数除算切り捨て）
    private static long approxIntervalDays(RepeatSpec spec) {
        switch (spec.getUnit()) {
            case SECOND: return spec.getRepeatVal() / 86400;
            case MINUTE: return spec.getRepeatVal() / 1440;
            case HOUR:   return spec.getRepeatVal() / 24;
            case DAY:    return spec.getRepeatVal();
            case MONTH:  return spec.getRepeatVal() * 30;
            default: throw new IllegalStateException("unknown unit: " + spec.getUnit());
        }
    }

    /**
     * 繰り返し予定の先読み窓（GUI仕様v2 §3.5）。間隔クラスが短いほど窓を狭くする。
     */
    public static Duration leadWindowOf(RepeatSpec spec) {
        long d = approxIntervalDays(spec);
        if (d < SHORT_TERM_THRESHOLD_DAYS) return LEAD_WINDOW_SHORT_TERM;
        if (d < DAILY_THRESHOLD_DAYS)       return LEAD_WINDOW_DAILY;
        if (d < WEEKLY_THRESHOLD_DAYS)      return LEAD_WINDOW_WEEKLY;
        return LEAD_WINDOW_MONTHLY;
    }

    /**
     * 一覧の表示判定（GUI仕様v2 §3.2）。上書き層（検索・全表示）→独立フィルタANDの順で短絡評価する。
     */
    public static boolean isVisible(Reminder r, FilterState f, LocalDateTime now) {
        Duration remain = Duration.between(now, r.fireAt);

        // 上書き層：検索非空なら他トグルを無視してコメント一致のみで判定
        if (f.searchText() != null && !f.searchText().isEmpty()) {
            return r.message.contains(f.searchText());
        }
        if (f.showAll()) return true;

        // 独立フィルタAND
        if (!bucketVisible(remain, f)) return false;
        if (!priorityVisible(r, f)) return false;
        if (isRepeating(r) && !f.showAllRepeat()) {
            if (exceedsLeadWindow(r, remain)) return false;
        }
        return true;
    }

    // バケツ分類結果を対応トグルへマップする
    private static boolean bucketVisible(Duration remain, FilterState f) {
        switch (bucketOf(remain)) {
            case 終了済: return f.showEnded();
            case 直近:   return f.showImminent();
            case 近日:   return f.showSoon();
            case 先:     return f.showFar();
            default: throw new IllegalStateException("unknown bucket");
        }
    }

    // showLowPriority=false のとき Pri1/Pri2 のみ隠す（ordinal に頼らず == で明示比較）
    private static boolean priorityVisible(Reminder r, FilterState f) {
        if (f.showLowPriority()) return true;
        return r.priority != Reminder.Priority.Pri1 && r.priority != Reminder.Priority.Pri2;
    }

    private static boolean isRepeating(Reminder r) {
        return r.repeat != null && !r.repeat.isEmpty();
    }

    // parse失敗（不正repeat）はリードタイム制約を課さない側に倒す（原則5・表示を落とさない）
    private static boolean exceedsLeadWindow(Reminder r, Duration remain) {
        RepeatSpec spec;
        try {
            spec = RepeatSpec.parse(r.repeat);
        } catch (RuntimeException e) {
            return false;
        }
        return remain.compareTo(leadWindowOf(spec)) > 0;
    }
}
