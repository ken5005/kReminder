package ken5005.kreminder.sound;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * wavファイル一覧とsound-mapテーブルから、最終的な 音声名→File を組み立てる純関数（案X）。
 *
 * 1. テーブルの各 音声名=ファイル名 を大小無視でファイル照合し、その音声名を割り当てる
 * 2. テーブルの値として参照されなかったファイルだけ、stemを音声名として自動採用する
 *    （＝テーブルに載せたファイルはstem名を引退し、書いた音声名でのみ呼べる）
 *
 * dangling（テーブルの値に対応するファイルが無い）・衝突（明示音声名と自動stem名の一致）は例外で落とす。
 */
public final class SoundMapBuilder {

    private SoundMapBuilder() {}

    public static Map<String, File> build(List<File> wavFiles, Map<String, String> table) {
        Map<String, File> byLowerFileName = new HashMap<>();
        for (File file : wavFiles) {
            byLowerFileName.put(file.getName().toLowerCase(), file);
        }

        Map<String, File> result = new LinkedHashMap<>();
        Set<String> consumedLowerFileNames = new HashSet<>();

        for (Map.Entry<String, String> entry : table.entrySet()) {
            String soundName = entry.getKey();
            String fileName = entry.getValue();
            File file = byLowerFileName.get(fileName.toLowerCase());
            if (file == null) {
                throw new IllegalArgumentException(
                    "sound-map: dangling — ファイルが見つからない: " + soundName + "=" + fileName);
            }
            result.put(soundName, file);
            consumedLowerFileNames.add(file.getName().toLowerCase());
        }

        for (File file : wavFiles) {
            String lowerFileName = file.getName().toLowerCase();
            if (consumedLowerFileNames.contains(lowerFileName)) {
                continue;
            }
            String stem = SoundMapParser.stemOf(file.getName());
            if (result.containsKey(stem)) {
                throw new IllegalArgumentException(
                    "sound-map: 衝突 — 明示音声名 '" + stem + "' と自動stem名（ファイル: " + file.getName() + "）が重複");
            }
            result.put(stem, file);
        }

        return result;
    }
}
