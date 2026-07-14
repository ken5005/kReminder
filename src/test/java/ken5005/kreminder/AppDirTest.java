package ken5005.kreminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppDirTest {

    // 他のテストクラスがAppDir.init()を呼んだ状態が残っていても、このテストが未init状態から
    // 独立して検証できるよう、静的フィールドをリフレクションで毎回nullに戻す。
    @BeforeEach
    void resetAppDirState() throws Exception {
        Field field = AppDir.class.getDeclaredField("base");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void baseThrowsWhenNotInitialized() {
        IllegalStateException e = assertThrows(IllegalStateException.class, AppDir::base);
        assertEquals("AppDir.init()が呼ばれていません", e.getMessage());
    }

    @Test
    void initMakesRelativePathAbsolute() {
        Path relative = Paths.get("testdata");
        AppDir.init(relative);
        assertEquals(relative.toAbsolutePath().normalize(), AppDir.base());
    }

    @Test
    void resolveJoinsBaseAndName() {
        Path base = Paths.get("testdata").toAbsolutePath().normalize();
        AppDir.init(base);
        assertEquals(base.resolve("reminders.json"), AppDir.resolve("reminders.json"));
    }
}
