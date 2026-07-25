package ken5005.kreminder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * フィルタトグルの永続化設定（GUI仕様v2 §3.7・案A＝今回はフィルタ6トグルのみ）。
 * AppDir.base()\config.properties に読み書きする。
 * I/O 失敗は握ってデフォルト値のまま継続する（本体を落とさない・ReminderStore と同じ方針）。
 */
public class Config {

    private static final String FILE_NAME = "config.properties";
    private static final String SOUND_MAP_FILE_NAME = "sound-map.properties";

    private static final String KEY_SHOW_ENDED = "filter.showEnded";
    private static final String KEY_SHOW_IMMINENT = "filter.showImminent";
    private static final String KEY_SHOW_SOON = "filter.showSoon";
    private static final String KEY_SHOW_FAR = "filter.showFar";
    private static final String KEY_SHOW_LOW_PRIORITY = "filter.showLowPriority";
    private static final String KEY_SHOW_ALL_REPEAT = "filter.showAllRepeat";
    private static final String KEY_WAV_DIR = "snd.wav.dir";
    private static final String DEFAULT_WAV_DIR = "C:\\tools2\\etc\\wav";

    // ウィンドウ状態の永続化（フェーズ4「き」）。位置・分割位置・ダイアログサイズは
    // 「未設定」を表す UNSET(-1) を既定にし、未設定なら復元せず従来どおりの見た目を使う
    private static final String KEY_MAIN_X = "window.main.x";
    private static final String KEY_MAIN_Y = "window.main.y";
    private static final String KEY_MAIN_WIDTH = "window.main.width";
    private static final String KEY_MAIN_HEIGHT = "window.main.height";
    // 分割位置は絶対pxではなく比率(0.0〜1.0)で持つ。窓高さが変わっても破綻しないようにするため
    private static final String KEY_MAIN_DIVIDER_RATIO = "window.main.dividerRatio";
    private static final String KEY_TABLE_COLUMN_WIDTHS = "table.columnWidths";
    private static final String KEY_EDIT_WIDTH = "window.edit.width";
    private static final String KEY_EDIT_HEIGHT = "window.edit.height";
    private static final String KEY_INSTANT_WIDTH = "window.instant.width";
    private static final String KEY_INSTANT_HEIGHT = "window.instant.height";
    private static final String KEY_DEBUG_ENABLED = "debug.enabled";

    // MainWindowが「一度も保存されていない」を判定する際に同じ値を参照できるようpublicにする
    public static final int UNSET = -1;
    private static final int DEFAULT_MAIN_WIDTH = 800;
    private static final int DEFAULT_MAIN_HEIGHT = 500;

    private final Path configPath;

    private boolean showEnded = false;
    private boolean showImminent = true;
    private boolean showSoon = true;
    private boolean showFar = false;
    private boolean showLowPriority = true;
    private boolean showAllRepeat = false;
    private String wavDir = DEFAULT_WAV_DIR;

    private int mainX = UNSET;
    private int mainY = UNSET;
    private int mainWidth = DEFAULT_MAIN_WIDTH;
    private int mainHeight = DEFAULT_MAIN_HEIGHT;
    // 未設定はUNSETをそのままdoubleへ広げた-1.0。0.0〜1.0の範囲外なので、範囲チェックする側
    // （MainWindow。DEFAULT_DEBUG_DIVIDER_RATIOというGUI固有の既定値を知っているのはそちら）が
    // 「無効値」として自然に検出できる。ここでは壊れた文字列を数値に戻せるかだけを見る
    private double mainDividerRatio = UNSET;
    private String tableColumnWidths = "";
    private int editWidth = UNSET;
    private int editHeight = UNSET;
    private int instantWidth = UNSET;
    private int instantHeight = UNSET;
    private boolean debugEnabled = false;

    public Config() {
        this(buildConfigPath());
    }

    public Config(Path configPath) {
        this.configPath = configPath;
    }

    private static Path buildConfigPath() {
        return configFilePath();
    }

    /** テストや Main から config.properties の実パスを直接参照するための入口。 */
    public static Path configFilePath() {
        return AppDir.base().resolve(FILE_NAME);
    }

    /** config.properties と同じベースフォルダで sound-map.properties を指す。 */
    public Path getSoundMapPath() {
        return configDir().resolve(SOUND_MAP_FILE_NAME);
    }

    private static Path configDir() {
        return AppDir.base();
    }

    /**
     * ファイル無しはデフォルト値のまま save() して実体化してから return する
     * （初回起動時に config.properties を生えさせるため）。I/O失敗・キー欠けはデフォルト値を維持する。
     */
    public void load() {
        if (!Files.exists(configPath)) {
            save();
            return;
        }

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

        // wavDirキーが無い設定ファイルにはデフォルト値を書き戻す（設定ファイルが説明的であることを優先）
        if (props.getProperty(KEY_WAV_DIR) == null) {
            wavDir = DEFAULT_WAV_DIR;
            save();
        } else {
            wavDir = props.getProperty(KEY_WAV_DIR);
        }

        mainX = parseInt(props, KEY_MAIN_X, mainX);
        mainY = parseInt(props, KEY_MAIN_Y, mainY);
        mainWidth = parseInt(props, KEY_MAIN_WIDTH, mainWidth);
        mainHeight = parseInt(props, KEY_MAIN_HEIGHT, mainHeight);
        mainDividerRatio = parseDouble(props, KEY_MAIN_DIVIDER_RATIO, mainDividerRatio);
        tableColumnWidths = props.getProperty(KEY_TABLE_COLUMN_WIDTHS, tableColumnWidths);
        editWidth = parseInt(props, KEY_EDIT_WIDTH, editWidth);
        editHeight = parseInt(props, KEY_EDIT_HEIGHT, editHeight);
        instantWidth = parseInt(props, KEY_INSTANT_WIDTH, instantWidth);
        instantHeight = parseInt(props, KEY_INSTANT_HEIGHT, instantHeight);
        debugEnabled = parseBool(props, KEY_DEBUG_ENABLED, debugEnabled);
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty(KEY_SHOW_ENDED, Boolean.toString(showEnded));
        props.setProperty(KEY_SHOW_IMMINENT, Boolean.toString(showImminent));
        props.setProperty(KEY_SHOW_SOON, Boolean.toString(showSoon));
        props.setProperty(KEY_SHOW_FAR, Boolean.toString(showFar));
        props.setProperty(KEY_SHOW_LOW_PRIORITY, Boolean.toString(showLowPriority));
        props.setProperty(KEY_SHOW_ALL_REPEAT, Boolean.toString(showAllRepeat));
        props.setProperty(KEY_WAV_DIR, wavDir);

        props.setProperty(KEY_MAIN_X, Integer.toString(mainX));
        props.setProperty(KEY_MAIN_Y, Integer.toString(mainY));
        props.setProperty(KEY_MAIN_WIDTH, Integer.toString(mainWidth));
        props.setProperty(KEY_MAIN_HEIGHT, Integer.toString(mainHeight));
        props.setProperty(KEY_MAIN_DIVIDER_RATIO, Double.toString(mainDividerRatio));
        props.setProperty(KEY_TABLE_COLUMN_WIDTHS, tableColumnWidths);
        props.setProperty(KEY_EDIT_WIDTH, Integer.toString(editWidth));
        props.setProperty(KEY_EDIT_HEIGHT, Integer.toString(editHeight));
        props.setProperty(KEY_INSTANT_WIDTH, Integer.toString(instantWidth));
        props.setProperty(KEY_INSTANT_HEIGHT, Integer.toString(instantHeight));
        props.setProperty(KEY_DEBUG_ENABLED, Boolean.toString(debugEnabled));

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

    // キー欠け・数値でない壊れた値の両方でデフォルト維持(Integer.parseIntは例外を投げるためcatchする)
    private static int parseInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // parseIntのdouble版。範囲(0.0〜1.0等)の妥当性はここでは見ない＝数値として読めるかどうかだけ
    private static double parseDouble(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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

    public Path getWavDir() { return Path.of(wavDir); }

    public int getMainX() { return mainX; }
    public void setMainX(int mainX) { this.mainX = mainX; }

    public int getMainY() { return mainY; }
    public void setMainY(int mainY) { this.mainY = mainY; }

    public int getMainWidth() { return mainWidth; }
    public void setMainWidth(int mainWidth) { this.mainWidth = mainWidth; }

    public int getMainHeight() { return mainHeight; }
    public void setMainHeight(int mainHeight) { this.mainHeight = mainHeight; }

    public double getMainDividerRatio() { return mainDividerRatio; }
    public void setMainDividerRatio(double mainDividerRatio) { this.mainDividerRatio = mainDividerRatio; }

    public String getTableColumnWidths() { return tableColumnWidths; }
    public void setTableColumnWidths(String tableColumnWidths) { this.tableColumnWidths = tableColumnWidths; }

    public int getEditWidth() { return editWidth; }
    public void setEditWidth(int editWidth) { this.editWidth = editWidth; }

    public int getEditHeight() { return editHeight; }
    public void setEditHeight(int editHeight) { this.editHeight = editHeight; }

    public int getInstantWidth() { return instantWidth; }
    public void setInstantWidth(int instantWidth) { this.instantWidth = instantWidth; }

    public int getInstantHeight() { return instantHeight; }
    public void setInstantHeight(int instantHeight) { this.instantHeight = instantHeight; }

    // 読むだけで書き換えないので setter は用意しない
    public boolean isDebugEnabled() { return debugEnabled; }
}
