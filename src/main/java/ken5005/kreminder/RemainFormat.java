package ken5005.kreminder;

import java.time.Duration;

/**
 * 残り時間（fireAt - now）を1行の日本語表示に整形する純関数（GUI仕様v2 §6.2）。
 * Swing/io/Gson 非依存。
 */
public final class RemainFormat {

    private RemainFormat() {
    }

    public static String formatRemaining(Duration remain) {
        if (remain.isZero() || remain.isNegative()) return "";

        long totalSeconds = remain.getSeconds();
        long days = totalSeconds / 86400;

        if (days >= 5) {
            return days + "日";
        }
        if (days >= 1) {
            long hours = (totalSeconds % 86400) / 3600;
            return days + "日" + hours + "時間";
        }

        // ここから日数0
        if (totalSeconds >= 300) {
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            if (hours >= 1) {
                return hours + "時間" + String.format("%02d", minutes) + "分";
            }
            return minutes + "分";
        }

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 1) {
            return minutes + "分" + seconds + "秒";
        }
        return seconds + "秒";
    }
}
