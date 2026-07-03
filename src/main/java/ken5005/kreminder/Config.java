package ken5005.kreminder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * フィルタトグルの永続化設定（GUI仕様v2 §3.7・案A＝今回はフィルタ6トグルのみ）。
 * %APPDATA%\kReminder\config.properties（APPDATA無しは user.home フォールバック）。
 * I/O 失敗は握ってデフォルト値のまま継続する（本体を落とさない・ReminderStore と同じ方針）。
 */
public class Config {

    private static final String FILE_NAME = "config.properties";

    private static final String KEY_SHOW_ENDED = "filter.showEnded";
    private static final String KEY_SHOW_IMMINENT = "filter.showImminent";
    private static final String KEY_SHOW_SOON = "filter.showSoon";
    private static final String KEY_SHOW_FAR = "filter.showFar";
    private static final String KEY_SHOW_LOW_PRIORITY = "filter.showLowPriority";
    private static final String KEY_SHOW_ALL_REPEAT = "filter.showAllRepeat";

    private final Path configPath;

    private boolean showEnded = false;
    private boolean showImminent = true;
    private boolean showSoon = true;
    private boolean showFar = false;
    private boolean showLowPriority = true;
    private boolean showAllRepeat = false;

    public Config() {
        this(buildConfigPath());
    }

    public Config(Path configPath) {
        this.configPath = configPath;
    }

    private static Path buildConfigPath() {
        String appData = System.getenv("APPDATA");
        Path dir = appData != null
            ? Path.of(appData, "kReminder")
            : Path.of(System.getProperty("user.home"), "kReminder");
        return dir.resolve(FILE_NAME);
    }

    /** ファイル無し・I/O失敗はデフォルト値を維持したまま return する。キー欠けも同様。 */
    public void load() {
        if (!Files.exists(configPath)) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("config load failed: " + e.getMessage());
            return;
        }

        showEnded = parseBool(props, KEY_SHOW_ENDED, showEnded);
        showImminent = parseBool(props, KEY_SHOW_IMMINENT, showImminent);
        showSoon = parseBool(props, KEY_SHOW_SOON, showSoon);
        showFar = parseBool(props, KEY_SHOW_FAR, showFar);
        showLowPriority = parseBool(props, KEY_SHOW_LOW_PRIORITY, showLowPriority);
        showAllRepeat = parseBool(props, KEY_SHOW_ALL_REPEAT, showAllRepeat);
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty(KEY_SHOW_ENDED, Boolean.toString(showEnded));
        props.setProperty(KEY_SHOW_IMMINENT, Boolean.toString(showImminent));
        props.setProperty(KEY_SHOW_SOON, Boolean.toString(showSoon));
        props.setProperty(KEY_SHOW_FAR, Boolean.toString(showFar));
        props.setProperty(KEY_SHOW_LOW_PRIORITY, Boolean.toString(showLowPriority));
        props.setProperty(KEY_SHOW_ALL_REPEAT, Boolean.toString(showAllRepeat));

        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, null);
            }
        } catch (IOException e) {
            System.err.println("config save failed: " + e.getMessage());
        }
    }

    // キー欠け（getProperty==null）はデフォルト維持。Boolean.parseBoolean(null)==false になり
    // デフォルトを潰してしまうため、null チェックを先に行う
    private static boolean parseBool(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public boolean isShowEnded() { return showEnded; }
    public void setShowEnded(boolean showEnded) { this.showEnded = showEnded; }

    public boolean isShowImminent() { return showImminent; }
    public void setShowImminent(boolean showImminent) { this.showImminent = showImminent; }

    public boolean isShowSoon() { return showSoon; }
    public void setShowSoon(boolean showSoon) { this.showSoon = showSoon; }

    public boolean isShowFar() { return showFar; }
    public void setShowFar(boolean showFar) { this.showFar = showFar; }

    public boolean isShowLowPriority() { return showLowPriority; }
    public void setShowLowPriority(boolean showLowPriority) { this.showLowPriority = showLowPriority; }

    public boolean isShowAllRepeat() { return showAllRepeat; }
    public void setShowAllRepeat(boolean showAllRepeat) { this.showAllRepeat = showAllRepeat; }
}
