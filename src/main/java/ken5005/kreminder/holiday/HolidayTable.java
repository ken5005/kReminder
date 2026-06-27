package ken5005.kreminder.holiday;

import ken5005.kreminder.HolidayCheck;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class HolidayTable implements HolidayCheck {

    private final Map<LocalDate, String> holidays;

    public HolidayTable(Map<LocalDate, String> holidays) {
        this.holidays = Collections.unmodifiableMap(new HashMap<>(holidays));
    }

    @Override
    public boolean isHoliday(LocalDate date) {
        return holidays.containsKey(date);
    }

    /** Returns the holiday name, or null if not a holiday. */
    public String getName(LocalDate date) {
        return holidays.get(date);
    }
}
