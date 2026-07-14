package ken5005.kreminder.holiday;

import ken5005.kreminder.AppDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Appends one line per call to holiday.log. Never throws — failures go to stderr. */
public final class HolidayLog {

    private static final String FILE_NAME = "holiday.log";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HolidayLog() {}

    public static void log(Clock clock, String message) {
        try {
            Path dir = AppDir.base();
            Files.createDirectories(dir);
            String line = LocalDateTime.now(clock).format(FMT) + " " + message + System.lineSeparator();
            Files.writeString(dir.resolve(FILE_NAME), line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("HolidayLog: write failed: " + e.getMessage());
        }
    }
}
