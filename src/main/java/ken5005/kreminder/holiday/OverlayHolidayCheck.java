package ken5005.kreminder.holiday;

import ken5005.kreminder.HolidayCheck;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Immutable overlay that adds/removes specific dates on top of a base HolidayCheck. */
public final class OverlayHolidayCheck implements HolidayCheck {

    private final HolidayCheck base;
    private final Map<LocalDate, String> addMap;
    private final Set<LocalDate> removeSet;

    public OverlayHolidayCheck(HolidayCheck base, Map<LocalDate, String> addMap, Set<LocalDate> removeSet) {
        this.base = base;
        this.addMap = Collections.unmodifiableMap(new HashMap<>(addMap));
        this.removeSet = Collections.unmodifiableSet(new HashSet<>(removeSet));
    }

    @Override
    public boolean isHoliday(LocalDate date) {
        if (removeSet.contains(date)) return false;
        return base.isHoliday(date) || addMap.containsKey(date);
    }

    public boolean isEmpty() {
        return addMap.isEmpty() && removeSet.isEmpty();
    }
}
