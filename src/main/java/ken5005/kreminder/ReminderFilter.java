package ken5005.kreminder;

import java.time.Duration;

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

    private ReminderFilter() {
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
}
