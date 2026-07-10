package ken5005.kreminder.sound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WavLoaderTest {

    @ParameterizedTest
    @ValueSource(strings = {"test.wav", "test.WAV", "ごん.wav", "カッ.WAV"})
    void recognizedWavFileIsListed(String fileName) throws IOException {
        Path dir = Files.createTempDirectory("wavloadertest");
        Files.createFile(dir.resolve(fileName));

        List<File> files = WavLoader.load(dir);

        assertEquals(1, files.size());
        assertEquals(fileName, files.get(0).getName());
    }

    @Test
    void nonWavFileIsIgnored() throws IOException {
        Path dir = Files.createTempDirectory("wavloadertest");
        Files.createFile(dir.resolve("test.mp3"));

        List<File> files = WavLoader.load(dir);

        assertTrue(files.isEmpty());
    }

    @Test
    void resultIsSortedByFileNameAscending() throws IOException {
        Path dir = Files.createTempDirectory("wavloadertest");
        Files.createFile(dir.resolve("notify.wav"));
        Files.createFile(dir.resolve("ごん.wav"));
        Files.createFile(dir.resolve("あいう.wav"));

        List<File> files = WavLoader.load(dir);

        List<String> names = files.stream().map(File::getName).toList();
        assertEquals(List.of("notify.wav", "あいう.wav", "ごん.wav"), names);
    }

    @Test
    void nonExistentDirReturnsEmptyList() {
        Path dir = Path.of("C:\\this\\path\\should\\not\\exist\\kreminder-wavloader-test");

        List<File> files = WavLoader.load(dir);

        assertTrue(files.isEmpty());
    }

    @Test
    void emptyDirReturnsEmptyList() throws IOException {
        Path dir = Files.createTempDirectory("wavloadertest");

        List<File> files = WavLoader.load(dir);

        assertTrue(files.isEmpty());
    }
}
