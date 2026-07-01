package ken5005.kreminder.debug;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * DEB用の純粋な文字列整形。Swingもjava.ioも使わない（stack trace整形の
 * 一時バッファを除く）＝ユニットテストで縛れる。
 */
public final class DebFormat {

    // DateTimeFormatterはスレッド安全（SimpleDateFormatと違う）なので共有インスタンスでよい。
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");

    private DebFormat() {}

    /** 元のprMul相当：null→"(null) "、Double→小数4桁、それ以外はtoString()。 */
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

    /** Throwableのstack traceを文字列化する。UTF-8であることを明示的にデコードする。 */
    public static String formatStackTrace(Throwable t) {
        if (t == null) return "(null)";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            t.printStackTrace(ps);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /** 1行分の最終整形：時刻プレフィックス＋本文。 */
    public static String formatLine(LocalDateTime time, String body) {
        return formatTime(time) + " " + body;
    }
}
