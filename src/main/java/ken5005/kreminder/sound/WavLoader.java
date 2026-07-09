package ken5005.kreminder.sound;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * wavDir直下の.wavファイルを列挙し、音声名（拡張子抜きファイル名）→Fileのマップを作る純関数。
 * I/Oは行うが副作用はない（読み取り専用・例外を投げない）。
 */
public final class WavLoader {

    private static final String EXT = ".wav";

    private WavLoader() {}

    public static Map<String, File> load(Path wavDir) {
        Map<String, File> result = new HashMap<>();
        if (wavDir == null || !Files.isDirectory(wavDir)) {
            return Collections.unmodifiableMap(result);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(wavDir)) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                if (!fileName.toLowerCase().endsWith(EXT)) {
                    continue;
                }
                String name = fileName.substring(0, fileName.length() - EXT.length());
                result.put(name, entry.toFile());
            }
        } catch (IOException e) {
            return Collections.unmodifiableMap(new HashMap<>());
        }
        return Collections.unmodifiableMap(result);
    }
}
