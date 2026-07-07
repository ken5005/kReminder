package ken5005.kreminder;

import java.nio.file.Path;

/**
 * --data オプションの値からreminders.jsonの実パスを決める純関数。
 * ファイル存在チェック等のI/OはMain側の責務（ここではPath計算のみ）。
 */
public class DataPathResolver {

    private static final String FILE_NAME = "reminders.json";

    public static Path resolve(String dataOpt) {
        if (dataOpt == null) return defaultPath();
        Path path = Path.of(dataOpt);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(
                "kReminder: --data must be an absolute path: \"" + dataOpt + "\"");
        }
        return path;
    }

    static Path defaultPath() {
        String appData = System.getenv("APPDATA");
        Path dir = appData != null
            ? Path.of(appData, "kReminder")
            : Path.of(System.getProperty("user.home"), "kReminder");
        return dir.resolve(FILE_NAME);
    }
}
