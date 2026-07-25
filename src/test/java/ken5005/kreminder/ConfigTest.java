package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void saveThenLoadRoundTripsNonDefaultValues(@TempDir Path tmp) {
        Path configPath = tmp.resolve("config.properties");

        Config written = new Config(configPath);
        // 全項目をデフォルトから反転させて保存する（デフォルト値のまま読めてしまう見落としを防ぐ）
        written.setShowEnded(true);
        written.setShowImminent(false);
        written.setShowSoon(false);
        written.setShowFar(true);
        written.setShowLowPriority(false);
        written.setShowAllRepeat(true);
        written.save();

        Config read = new Config(configPath);
        read.load();

        assertTrue(read.isShowEnded());
        assertFalse(read.isShowImminent());
        assertFalse(read.isShowSoon());
        assertTrue(read.isShowFar());
        assertFalse(read.isShowLowPriority());
        assertTrue(read.isShowAllRepeat());
    }

    @Test
    void loadWithoutFileKeepsDefaultsAndMaterializesFile(@TempDir Path tmp) throws IOException {
        Path configPath = tmp.resolve("nonexistent.properties");

        Config config = new Config(configPath);
        config.load();

        assertFalse(config.isShowEnded());
        assertTrue(config.isShowImminent());
        assertTrue(config.isShowSoon());
        assertFalse(config.isShowFar());
        assertTrue(config.isShowLowPriority());
        assertFalse(config.isShowAllRepeat());

        // 不在だったファイルが load() 時点でデフォルト値のまま実体化されていること
        assertTrue(Files.exists(configPath));
        Properties saved = new Properties();
        try (var in = Files.newInputStream(configPath)) {
            saved.load(in);
        }
        assertEquals("false", saved.getProperty("filter.showEnded"));
        assertEquals("true", saved.getProperty("filter.showImminent"));
        assertEquals("true", saved.getProperty("filter.showSoon"));
        assertEquals("false", saved.getProperty("filter.showFar"));
        assertEquals("true", saved.getProperty("filter.showLowPriority"));
        assertEquals("false", saved.getProperty("filter.showAllRepeat"));
        assertEquals("C:\\tools2\\etc\\wav", saved.getProperty("snd.wav.dir"));
    }

    @Test
    void loadWithMissingKeysKeepsDefaultsForThem(@TempDir Path tmp) throws IOException {
        Path configPath = tmp.resolve("partial.properties");
        // showEnded だけを書いたファイル。他5キーは欠けたまま
        Files.writeString(configPath, "filter.showEnded=true\n", StandardCharsets.UTF_8);

        Config config = new Config(configPath);
        config.load();

        assertTrue(config.isShowEnded());
        assertTrue(config.isShowImminent());
        assertTrue(config.isShowSoon());
        assertFalse(config.isShowFar());
        assertTrue(config.isShowLowPriority());
        assertFalse(config.isShowAllRepeat());
    }

    @Test
    void windowStateRoundTripsNonDefaultValues(@TempDir Path tmp) {
        Path configPath = tmp.resolve("config.properties");

        Config written = new Config(configPath);
        written.setMainX(120);
        written.setMainY(80);
        written.setMainWidth(1000);
        written.setMainHeight(700);
        written.setMainDividerRatio(0.6);
        written.setTableColumnWidths("40,120,80,100,300");
        written.setEditWidth(500);
        written.setEditHeight(400);
        written.setInstantWidth(450);
        written.setInstantHeight(200);
        written.save();

        Config read = new Config(configPath);
        read.load();

        assertEquals(120, read.getMainX());
        assertEquals(80, read.getMainY());
        assertEquals(1000, read.getMainWidth());
        assertEquals(700, read.getMainHeight());
        assertEquals(0.6, read.getMainDividerRatio());
        assertEquals("40,120,80,100,300", read.getTableColumnWidths());
        assertEquals(500, read.getEditWidth());
        assertEquals(400, read.getEditHeight());
        assertEquals(450, read.getInstantWidth());
        assertEquals(200, read.getInstantHeight());
    }

    @Test
    void windowStateWithMissingKeysKeepsDefaults(@TempDir Path tmp) throws IOException {
        Path configPath = tmp.resolve("nowindow.properties");
        // ウィンドウ関連キーを一切書かないファイル
        Files.writeString(configPath, "filter.showEnded=true\n", StandardCharsets.UTF_8);

        Config config = new Config(configPath);
        config.load();

        assertEquals(-1, config.getMainX());
        assertEquals(-1, config.getMainY());
        assertEquals(800, config.getMainWidth());
        assertEquals(500, config.getMainHeight());
        assertEquals(-1.0, config.getMainDividerRatio());
        assertEquals("", config.getTableColumnWidths());
        assertEquals(-1, config.getEditWidth());
        assertEquals(-1, config.getEditHeight());
        assertEquals(-1, config.getInstantWidth());
        assertEquals(-1, config.getInstantHeight());
        assertFalse(config.isDebugEnabled());
    }

    @Test
    void windowStateWithMalformedValuesFallsBackToDefaults(@TempDir Path tmp) throws IOException {
        Path configPath = tmp.resolve("broken.properties");
        Files.writeString(configPath, String.join("\n",
            "window.main.x=abc",
            "window.main.y=",
            "window.main.width=notanumber",
            "window.main.height=1.5",
            "window.main.dividerRatio=abc",
            "window.edit.width=abc",
            "window.edit.height=abc",
            "window.instant.width=abc",
            "window.instant.height=abc",
            "debug.enabled=abc",
            ""), StandardCharsets.UTF_8);

        Config config = new Config(configPath);
        config.load();

        assertEquals(-1, config.getMainX());
        assertEquals(-1, config.getMainY());
        assertEquals(800, config.getMainWidth());
        assertEquals(500, config.getMainHeight());
        assertEquals(-1.0, config.getMainDividerRatio());
        assertEquals(-1, config.getEditWidth());
        assertEquals(-1, config.getEditHeight());
        assertEquals(-1, config.getInstantWidth());
        assertEquals(-1, config.getInstantHeight());
        // debug.enabled=abc は Boolean.parseBoolean で例外にならず単に false 扱いになる（デフォルトと同じ）
        assertFalse(config.isDebugEnabled());
    }

    @Test
    void mainDividerRatioOutOfRangeIsNotRejectedByConfig(@TempDir Path tmp) throws IOException {
        // 範囲(0.0〜1.0)の妥当性はConfigの責務ではなくMainWindow側で見る設計（DEFAULT_DEBUG_DIVIDER_RATIOという
        // GUI固有の既定値を知っているのがMainWindowのため）。Configは数値として読めればそのまま返してよい
        Path configPath = tmp.resolve("outofrange.properties");
        Files.writeString(configPath, "window.main.dividerRatio=5.0\n", StandardCharsets.UTF_8);

        Config config = new Config(configPath);
        config.load();

        assertEquals(5.0, config.getMainDividerRatio());
    }
}
