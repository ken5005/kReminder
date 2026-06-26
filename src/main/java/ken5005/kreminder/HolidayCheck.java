package ken5005.kreminder;

import java.time.LocalDate;

@FunctionalInterface
public interface HolidayCheck {
    boolean isHoliday(LocalDate date);
    HolidayCheck NONE = d -> false;
}
