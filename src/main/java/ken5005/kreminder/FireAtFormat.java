package ken5005.kreminder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 日時（fireAt等）を「yyyy/MM/dd(曜) HH:mm[:ss]」の日本語表示に整形する純関数。
 * Swing/io/Gson 非依存。
 */
public final class FireAtFormat {

    private static final DateTimeFormatter WITH_SECONDS =
        DateTimeFormatter.ofPattern("yyyy/MM/dd(E) HH:mm:ss", Locale.JAPANESE);
    private static final DateTimeFormatter WITHOUT_SECONDS =
        DateTimeFormatter.ofPattern("yyyy/MM/dd(E) HH:mm", Locale.JAPANESE);

    private FireAtFormat() {
    }

    public static String withSeconds(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(WITH_SECONDS);
    }

    public static String forList(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.getSecond() == 0 ? dt.format(WITHOUT_SECONDS) : dt.format(WITH_SECONDS);
    }
}
