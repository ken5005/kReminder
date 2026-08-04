package ken5005.kreminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentModeTest {

    @TempDir
    Path tempDir;

    // AppDir.base同様、SilentModeのon/onChangeも静的状態なのでテスト間で毎回リセットする。
    @BeforeEach
    void resetState() throws Exception {
        AppDir.init(tempDir);
        setStaticField("on", false);
        setStaticField("onChange", null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = SilentMode.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @Test
    void turnOnCreatesMarkerFile() {
        assertFalse(SilentMode.isOn());
        SilentMode.turnOn();
        assertTrue(SilentMode.isOn());
        assertTrue(Files.exists(tempDir.resolve(".silentmode")));
    }

    @Test
    void turnOffDeletesMarkerFile() {
        SilentMode.turnOn();
        SilentMode.turnOff();
        assertFalse(SilentMode.isOn());
        assertTrue(Files.notExists(tempDir.resolve(".silentmode")));
    }

    @Test
    void removeMarkerDeletesFileRegardlessOfState() throws Exception {
        Files.createFile(tempDir.resolve(".silentmode"));
        SilentMode.removeMarker();
        assertTrue(Files.notExists(tempDir.resolve(".silentmode")));
    }

    @Test
    void onChangeListenerFiresOnTurnOnAndTurnOff() {
        int[] calls = {0};
        SilentMode.setOnChange(() -> calls[0]++);
        SilentMode.turnOn();
        SilentMode.turnOff();
        assertEquals(2, calls[0]);
    }
}
