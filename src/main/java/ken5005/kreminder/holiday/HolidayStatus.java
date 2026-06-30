package ken5005.kreminder.holiday;

public enum HolidayStatus {
    /** CSV from network (or fresh cache) was adopted successfully. */
    OK,
    /** Previous cache is in use because the latest fetch/validation failed. */
    DEGRADED,
    /** No cache at all — holidays are ignored (only weekends excluded). */
    NONE
}
