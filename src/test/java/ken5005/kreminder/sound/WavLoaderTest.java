package ken5005.kreminder.sound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WavLoaderTest {

    @ParameterizedTest
    @CsvSource({
        "test.wav,   test",
        "test.WAV,   test",
        "ごん.wav,   ごん",
        "カッ.WAV,   カッ",
    })
    void recognizedFileNameBecomesKey(String fileName, String expectedKey) throws IOException {
        Path dir = Files.createTempDirectory("wavloadertest");
        Files.createFile(dir.resolve(fileName));

        Map<String, File> map = WavLoader.load(dir);

        assertEquals(1, map.size());
        assertTrue(map.containsKey(expectedKey));
    }

    @Test
    void nonWavFileIsIgnored() throws IOException {
        Path dir = Files.createTempDirectory("wavloadertest");
        Files.createFile(dir.resolve("test.mp3"));

        Map<String, File> map = WavLoader.load(dir);

        assertTrue(map.isEmpty());
    }

    @Test
    void nonExistentDirReturnsEmptyMap() {
        Path dir = Path.of("C:\\this\\path\\should\\not\\exist\\kreminder-wavloader-test");

        Map<String, File> map = WavLoader.load(dir);

        assertTrue(map.isEmpty());
    }

    @Test
    void emptyDirReturnsEmptyMap() throws IOException {
        Path dir = Files.createTempDirectory("wavloadertest");

        Map<String, File> map = WavLoader.load(dir);

        assertTrue(map.isEmpty());
    }
}
