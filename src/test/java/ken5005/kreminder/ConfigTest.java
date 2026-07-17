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
}
