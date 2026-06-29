package ken5005.kreminder.holiday;

import ken5005.kreminder.HolidayCheck;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class HolidayService {

    private static final int MIN_BYTES = 1_024;
    private static final int MAX_BYTES = 1_048_576;
    private static final int MIN_COUNT = 10;
    static final int MIN_CURRENT_YEAR_COUNT = 12;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "holiday-fetcher");
        t.setDaemon(true);
        return t;
    });

    private HolidayService() {}

    /**
     * Pure function — testable without I/O.
     * Returns true if holidays contains at least min entries for the given year.
     */
    public static boolean hasEnoughCurrentYearHolidays(Map<LocalDate, String> holidays, int year, int min) {
        long count = holidays.keySet().stream().filter(d -> d.getYear() == year).count();
        return count >= min;
    }

    /**
     * Pure function — testable without I/O.
     * Returns true if the cache is absent or older than 1 day (1日以上前).
     */
    public static boolean shouldRefresh(LocalDateTime fetchedAt, LocalDateTime now) {
        if (fetchedAt == null) return true;
        // refresh when now >= fetchedAt + 1 day
        return !fetchedAt.plusDays(1).isAfter(now);
    }

    /**
     * Synchronous startup load. Returns a HolidayTable if cache exists, NONE otherwise.
     * Always returns immediately.
     */
    public static HolidayCheck loadInitial(Clock clock) {
        HolidayCache.CacheData cache = HolidayCache.load();
        if (cache == null) {
            System.err.println("HolidayService: no cache found, starting with NONE (holidays ignored)");
            return HolidayCheck.NONE;
        }
        System.out.println("HolidayService: loaded " + cache.holidays().size()
            + " holidays from cache (fetched " + cache.fetchedAt() + ")");
        return new HolidayTable(cache.holidays());
    }

    /**
     * Background refresh. Calls onUpdate only on success; on failure keeps existing HolidayCheck.
     * Skips fetch when cache is fresh (< 1 day old).
     */
    public static void refreshAsync(Consumer<HolidayCheck> onUpdate, Clock clock) {
        EXECUTOR.submit(() -> {
            HolidayCache.CacheData existing = HolidayCache.load();
            LocalDateTime fetchedAt = existing != null ? existing.fetchedAt() : null;

            if (!shouldRefresh(fetchedAt, LocalDateTime.now(clock))) {
                System.out.println("HolidayService: cache is fresh, skipping network refresh");
                return;
            }

            byte[] raw;
            try {
                raw = HolidayFetcher.fetch();
            } catch (Exception e) {
                System.err.println("HolidayService: fetch failed: " + e.getMessage());
                return;
            }

            if (raw.length < MIN_BYTES || raw.length >= MAX_BYTES) {
                System.err.println("HolidayService: rejected — invalid size " + raw.length);
                saveFailureCsv(raw);
                return;
            }

            Map<LocalDate, String> holidays;
            try {
                holidays = HolidayCsvParser.parse(raw);
            } catch (IllegalArgumentException e) {
                System.err.println("HolidayService: rejected — parse failed: " + e.getMessage());
                saveFailureCsv(raw);
                return;
            }

            if (holidays.size() < MIN_COUNT) {
                System.err.println("HolidayService: rejected — too few entries: " + holidays.size());
                saveFailureCsv(raw);
                return;
            }
            LocalDate newYear = LocalDate.of(LocalDate.now(clock).getYear(), 1, 1);
            if (!holidays.containsKey(newYear)) {
                System.err.println("HolidayService: rejected — missing " + newYear + " sanity check");
                saveFailureCsv(raw);
                return;
            }

            int currentYear = LocalDate.now(clock).getYear();
            if (!hasEnoughCurrentYearHolidays(holidays, currentYear, MIN_CURRENT_YEAR_COUNT)) {
                long yearCount = holidays.keySet().stream().filter(d -> d.getYear() == currentYear).count();
                System.err.println("HolidayService: rejected — too few holidays for " + currentYear
                    + ": " + yearCount + " (need " + MIN_CURRENT_YEAR_COUNT + ")");
                saveFailureCsv(raw);
                return;
            }

            LocalDateTime now = LocalDateTime.now(clock);
            HolidayCache.save(now, holidays);
            System.out.println("HolidayService: refreshed " + holidays.size() + " holidays");
            onUpdate.accept(new HolidayTable(holidays));
        });
    }

    private static void saveFailureCsv(byte[] raw) {
        try {
            String appData = System.getenv("APPDATA");
            Path dir = appData != null
                ? Path.of(appData, "kReminder")
                : Path.of(System.getProperty("user.home"), "kReminder");
            Files.createDirectories(dir);
            Files.write(dir.resolve("holiday_last_failure.csv"), raw);
            System.err.println("HolidayService: raw CSV saved to holiday_last_failure.csv for diagnosis");
        } catch (Exception e) {
            System.err.println("HolidayService: could not save failure CSV: " + e.getMessage());
        }
    }
}
