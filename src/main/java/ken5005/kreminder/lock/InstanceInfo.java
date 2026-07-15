package ken5005.kreminder.lock;

import java.util.List;

/**
 * ロック保持プロセスの情報。.instance.info の内容をそのまま表す不変データ。
 */
public record InstanceInfo(long pid, String startedAt, String base) {

    private static final String KEY_PID = "pid";
    private static final String KEY_STARTED_AT = "startedAt";
    private static final String KEY_BASE = "base";

    /** key=value 形式・UTF-8想定・改行区切りのプレーンテキストへ変換する。 */
    public String render() {
        return KEY_PID + "=" + pid + "\n"
                + KEY_STARTED_AT + "=" + startedAt + "\n"
                + KEY_BASE + "=" + base + "\n";
    }

    /**
     * render() の逆変換。未知の行・空行は無視する。
     * 必須キー（pid/startedAt/base）が欠けている場合、または pid が数値でない場合は
     * IllegalArgumentException を投げる。
     */
    public static InstanceInfo parse(List<String> lines) {
        Long pid = null;
        String startedAt = null;
        String base = null;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            switch (key) {
                case KEY_PID -> {
                    try {
                        pid = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("pid が数値ではない: " + value, e);
                    }
                }
                case KEY_STARTED_AT -> startedAt = value;
                case KEY_BASE -> base = value;
                default -> { /* 未知の行は無視 */ }
            }
        }

        if (pid == null || startedAt == null || base == null) {
            throw new IllegalArgumentException(
                    "必須キーが欠落している（pid/startedAt/base が必要）");
        }
        return new InstanceInfo(pid, startedAt, base);
    }
}
