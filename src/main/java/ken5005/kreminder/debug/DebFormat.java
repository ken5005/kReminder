package ken5005.kreminder.debug;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Pure string formatting for DEB. No Swing, no java.io side effects beyond the
 * in-memory buffer used to render a stack trace — safe to unit test directly.
 */
public final class DebFormat {

    // DateTimeFormatter is thread-safe (unlike SimpleDateFormat) — one shared instance is fine.
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");

    private DebFormat() {}

    /** Formats varargs like the original prMul: null -> "(null) ", Double -> 4 decimals, else toString(). */
    public static String formatArgs(Object... args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(formatOne(arg));
        }
        return sb.toString();
    }

    private static String formatOne(Object arg) {
        if (arg == null) return "(null) ";
        if (arg instanceof Double d) return String.format("%.4f ", d);
        return arg + " ";
    }

    public static String formatTime(LocalDateTime time) {
        return time.format(TIME_FMT);
    }

    public static String formatTime(Instant instant, ZoneId zone) {
        return formatTime(LocalDateTime.ofInstant(instant, zone));
    }

    /** Renders a Throwable's stack trace to a String, decoding explicitly as UTF-8. */
    public static String formatStackTrace(Throwable t) {
        if (t == null) return "(null)";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            t.printStackTrace(ps);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /** Final one-line formatting: time prefix + body. */
    public static String formatLine(LocalDateTime time, String body) {
        return formatTime(time) + " " + body;
    }
}
