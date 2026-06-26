package ken5005.kreminder;

import java.time.LocalDateTime;
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
                if (last == 'd') {
                    unit = Unit.DAY;
                    repeatVal = Integer.parseInt(val.substring(0, val.length() - 1));
                } else {
                    // other units (h/m/s/M/digit-only) not yet implemented
                    throw new UnsupportedOperationException("unit not yet handled: '" + last + "' in: " + repeat);
                }
            } else {
                // other commands (ex/in/dai/day/kuriage) not yet implemented
                throw new UnsupportedOperationException("command not yet handled: '" + p + "' in: " + repeat);
            }
        }

        if (unit == null) throw new IllegalArgumentException("rep= is required in: " + repeat);
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
