package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataPathResolverTest {

    @Test
    void nullReturnsDefaultPath() {
        assertEquals(DataPathResolver.defaultPath(), DataPathResolver.resolve(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "C:\\testdata\\reminders_4.json",
        "C:/testdata/reminders_4.json"
    })
    void absolutePathIsReturnedAsIs(String value) {
        assertEquals(Path.of(value), DataPathResolver.resolve(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "testdata\\reminders_4.json",
        "testdata/reminders_4.json",
        "reminders_4.json"
    })
    void relativePathThrows(String value) {
        assertThrows(IllegalArgumentException.class, () -> DataPathResolver.resolve(value));
    }
}
