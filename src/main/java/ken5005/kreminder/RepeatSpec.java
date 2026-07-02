package ken5005.kreminder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class RepeatSpec {

    private enum Unit { MONTH, DAY, HOUR, MINUTE, SECOND }

    private final String raw;
    private final int repeatVal;
    private final Unit unit;
    private final boolean[] excluded;   // [0]=Sun .. [6]=Sat
    private final Set<Integer> allowedWeeks; // null=no restriction; values are week-of-month (1..5)
    private final int absDay;           // 0 = not set
    private final boolean kuriage;

    private static final int CAP = 100_000;

    // 曜日: excluded[] のインデックス（0=日..6=土）に対応する表示文字
    private static final String[] WEEKDAY_CHARS = {"日", "月", "火", "水", "木", "金", "土"};
    // 表示順（月火水木金土日）を excluded[] インデックスの並びで表す
    private static final int[] WEEKDAY_DISPLAY_ORDER = {1, 2, 3, 4, 5, 6, 0};

    private RepeatSpec(String raw, int repeatVal, Unit unit,
                       boolean[] excluded, Set<Integer> allowedWeeks,
                       int absDay, boolean kuriage) {
        this.raw = raw;
        this.repeatVal = repeatVal;
        this.unit = unit;
        this.excluded = excluded;
        this.allowedWeeks = allowedWeeks;
        this.absDay = absDay;
        this.kuriage = kuriage;
    }

    public static RepeatSpec parse(String repeat) {
        int repeatVal = 0;
        Unit unit = null;
        boolean[] excluded = new boolean[7];
        Set<Integer> allowedWeeks = null;
        int absDay = 0;
        boolean kuriage = false;

        for (String part : repeat.split(";")) {
            String p = part.trim();
            if (p.startsWith("rep=")) {
                String val = p.substring(4);
                char last = val.charAt(val.length() - 1);
                if (last == 'M') {
                    unit = Unit.MONTH;
                    repeatVal = Integer.parseInt(val.substring(0, val.length() - 1));
                } else if (last == 'd') {
                    unit = Unit.DAY;
                    repeatVal = Integer.parseInt(val.substring(0, val.length() - 1));
                } else if (last == 'h') {
                    unit = Unit.HOUR;
                    repeatVal = Integer.parseInt(val.substring(0, val.length() - 1));
                } else if (last == 'm') {
                    unit = Unit.MINUTE;
                    repeatVal = Integer.parseInt(val.substring(0, val.length() - 1));
                } else if (last == 's') {
                    unit = Unit.SECOND;
                    repeatVal = Integer.parseInt(val.substring(0, val.length() - 1));
                } else if (Character.isDigit(last)) {
                    unit = Unit.MINUTE;
                    repeatVal = Integer.parseInt(val);
                } else {
                    throw new IllegalArgumentException("unknown unit: '" + last + "' in: " + repeat);
                }
            } else if (p.startsWith("ex=")) {
                for (String d : p.substring(3).split(",")) {
                    excluded[Integer.parseInt(d.trim())] = true;
                }
            } else if (p.startsWith("in=")) {
                Arrays.fill(excluded, true);  // exclude all, then open specified
                for (String d : p.substring(3).split(",")) {
                    excluded[Integer.parseInt(d.trim())] = false;
                }
            } else if (p.startsWith("dai=")) {
                allowedWeeks = new HashSet<>();
                for (String w : p.substring(4).split(",")) {
                    allowedWeeks.add(Integer.parseInt(w.trim()));
                }
            } else if (p.startsWith("day=")) {
                absDay = Integer.parseInt(p.substring(4).trim());
            } else if (p.equals("kuriage")) {
                kuriage = true;
            } else {
                throw new IllegalArgumentException("unknown command: '" + p + "' in: " + repeat);
            }
        }

        if (unit == null) throw new IllegalArgumentException("rep= is required in: " + repeat);
        if (absDay != 0 && unit != Unit.MONTH)
            throw new IllegalArgumentException("day=N requires rep unit M in: " + repeat);
        if (kuriage && unit != Unit.MONTH)
            throw new IllegalArgumentException("kuriage は月次(M)専用です: " + repeat);
        return new RepeatSpec(repeat, repeatVal, unit, excluded, allowedWeeks, absDay, kuriage);
    }

    public LocalDateTime next(LocalDateTime from) {
        return next(from, HolidayCheck.NONE);
    }

    public LocalDateTime next(LocalDateTime from, HolidayCheck holiday) {
        LocalDateTime cal = from;
        if (absDay != 0) {
            int clamped = Math.min(absDay, cal.toLocalDate().lengthOfMonth());
            cal = cal.withDayOfMonth(clamped);
        }

        Unit currentUnit = this.unit;
        int currentVal = this.repeatVal;

        for (int i = 0; i < CAP; i++) {
            cal = advance(cal, currentUnit, currentVal);
            int idx = holiday.isHoliday(cal.toLocalDate()) ? 0
                : (cal.getDayOfWeek().getValue() % 7);
            int wom = (cal.getDayOfMonth() - 1) / 7 + 1;

            boolean dayOk  = !excluded[idx];
            boolean weekOk = (allowedWeeks == null) || allowedWeeks.contains(wom);

            if (dayOk && weekOk) return cal;

            if (currentUnit == Unit.MONTH) {
                currentUnit = Unit.DAY;
                currentVal  = kuriage ? -1 : 1;
            }
        }
        throw new IllegalStateException("no next date found for: " + raw);
    }

    public LocalDateTime nextAfter(LocalDateTime from, LocalDateTime now) {
        return nextAfter(from, now, HolidayCheck.NONE);
    }

    public LocalDateTime nextAfter(LocalDateTime from, LocalDateTime now, HolidayCheck holiday) {
        LocalDateTime t = next(from, holiday);
        while (!t.isAfter(now)) t = next(t, holiday);
        return t;
    }

    /**
     * repeat 生文字列を人間可読な1行に組み立てる（GUI仕様 v2 §6.1）。
     * valid な spec のみを対象とする（不正 repeat は parse() で弾かれている前提）。
     */
    public String toJapanese() {
        int excludedCount = 0;
        for (boolean b : excluded) if (b) excludedCount++;
        if (excludedCount == 7) {
            throw new IllegalStateException("有効な曜日がありません: " + raw);
        }

        // bare形: 毎日(1d)＋曜日限定(許可1〜3＝除外4〜6)のとき「毎日」を落として曜日だけにする
        boolean bare = unit == Unit.DAY && repeatVal == 1 && excludedCount >= 4;
        if (bare) {
            String daiPrefix = allowedWeeks == null ? "" : daiWeeksJoined();
            return daiPrefix + weekdayString(true);
        }

        StringBuilder sb = new StringBuilder("毎").append(intervalWord());
        if (absDay != 0) sb.append(absDay).append("日");
        if (kuriage) sb.append("(繰上)");
        if (allowedWeeks != null) {
            sb.append(" ").append(daiWeeksJoined()).append("週");
        }
        if (excludedCount >= 1 && excludedCount <= 3) {
            sb.append(" ").append(weekdayString(false)).append("除く");
        } else if (excludedCount >= 4) {
            sb.append(" ").append(weekdayString(true)).append("のみ");
        }
        return sb.toString();
    }

    // rep= の間隔部分を日本語化。月は12の倍数→年、日は7の倍数→週に畳む（端数は据え置き）
    private String intervalWord() {
        switch (unit) {
            case MONTH:
                if (repeatVal == 1) return "月";
                if (repeatVal % 12 == 0) {
                    int n = repeatVal / 12;
                    return n == 1 ? "年" : n + "年";
                }
                return repeatVal + "ヶ月";
            case DAY:
                if (repeatVal == 1) return "日";
                if (repeatVal % 7 == 0) {
                    int n = repeatVal / 7;
                    return n == 1 ? "週" : n + "週";
                }
                return repeatVal + "日";
            case HOUR:
                return repeatVal == 1 ? "時間" : repeatVal + "時間";
            case MINUTE:
                return repeatVal == 1 ? "分" : repeatVal + "分";
            case SECOND:
                return repeatVal == 1 ? "秒" : repeatVal + "秒";
            default:
                throw new IllegalStateException("unknown unit: " + unit);
        }
    }

    // dai=（許可週）を「第1第3」のように連結。週サフィックスは呼び出し側で付与する
    private String daiWeeksJoined() {
        StringBuilder sb = new StringBuilder();
        allowedWeeks.stream().sorted().forEach(w -> sb.append("第").append(w));
        return sb.toString();
    }

    // allowed=true: 許可曜日（excluded=false）、allowed=false: 除外曜日（excluded=true）を
    // 表示順（月火水木金土日）で連結する
    private String weekdayString(boolean allowed) {
        StringBuilder sb = new StringBuilder();
        for (int idx : WEEKDAY_DISPLAY_ORDER) {
            boolean flag = allowed ? !excluded[idx] : excluded[idx];
            if (flag) sb.append(WEEKDAY_CHARS[idx]);
        }
        return sb.toString();
    }

    private static LocalDateTime advance(LocalDateTime cal, Unit unit, int val) {
        switch (unit) {
            case MONTH:  return cal.plusMonths(val);
            case DAY:    return cal.plusDays(val);
            case HOUR:   return cal.plusHours(val);
            case MINUTE: return cal.plusMinutes(val);
            case SECOND: return cal.plusSeconds(val);
            default: throw new IllegalStateException("unknown unit: " + unit);
        }
    }
}
