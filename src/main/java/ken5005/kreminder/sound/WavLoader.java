package ken5005.kreminder.sound;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * wavDir直下の.wav/.WAVファイルを列挙する純関数。
 * 音声名の解決（sound-mapテーブル照合・stem自動採用）はSoundMapBuilderの責務なので、
 * ここでは列挙とファイル名昇順ソートだけを行う（決定的な順序にするため）。
 */
public final class WavLoader {

    private static final String EXT = ".wav";

    private WavLoader() {}

    public static List<File> load(Path wavDir) {
        if (wavDir == null || !Files.isDirectory(wavDir)) {
            return List.of();
        }
        List<File> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(wavDir)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                if (!fileName.toLowerCase().endsWith(EXT)) {
                    continue;
                }
                result.add(entry.toFile());
            }
        } catch (IOException e) {
            return List.of();
        }
        result.sort(Comparator.comparing(File::getName));
        return Collections.unmodifiableList(result);
    }
}
