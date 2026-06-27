package ken5005.kreminder.holiday;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HolidayCache {

    private static final String FILE_NAME = "holidays.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private HolidayCache() {}

    public record CacheData(LocalDateTime fetchedAt, Map<LocalDate, String> holidays) {}

    // Plain class (not record) for Gson compatibility
    private static class CacheFile {
        String fetchedAt;
        Map<String, String> holidays;
    }

    static Path getCachePath() {
        String appData = System.getenv("APPDATA");
        Path dir = appData != null
            ? Path.of(appData, "kReminder")
            : Path.of(System.getProperty("user.home"), "kReminder");
        return dir.resolve(FILE_NAME);
    }

    /** Returns null if the cache file is absent or unreadable. */
    public static CacheData load() {
        Path path = getCachePath();
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CacheFile cf = GSON.fromJson(reader, CacheFile.class);
            if (cf == null || cf.fetchedAt == null || cf.holidays == null) return null;
            LocalDateTime fetchedAt = LocalDateTime.parse(cf.fetchedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Map<LocalDate, String> holidays = new HashMap<>();
            for (Map.Entry<String, String> e : cf.holidays.entrySet()) {
                try {
                    holidays.put(LocalDate.parse(e.getKey(), DateTimeFormatter.ISO_LOCAL_DATE), e.getValue());
                } catch (Exception ex) {
                    System.err.println("HolidayCache: skipping invalid date key: " + e.getKey());
                }
            }
            return new CacheData(fetchedAt, holidays);
        } catch (Exception e) {
            System.err.println("HolidayCache: load failed: " + e.getMessage());
            return null;
        }
    }

    public static void save(LocalDateTime fetchedAt, Map<LocalDate, String> holidays) {
        Path path = getCachePath();
        Map<String, String> stringMap = new LinkedHashMap<>();
        holidays.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> stringMap.put(
                e.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE), e.getValue()));

        CacheFile cf = new CacheFile();
        cf.fetchedAt = fetchedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        cf.holidays = stringMap;

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(cf, writer);
            }
        } catch (Exception e) {
            System.err.println("HolidayCache: save failed: " + e.getMessage());
        }
    }
}
