package ken5005.kreminder.sound;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * sound-map.properties の行データ⇔テーブル(音声名→ファイル名)を変換する純関数。
 * PropertiesクラスはISO-8859-1読み込み・重複キー握り潰しの問題があるため使わず、
 * 自前で1行ずつ処理する（UTF-8前提の読み込み自体はMain側の責務）。
 *
 * コメントは行内のどこに # があってもそこから行末までがコメント扱い（行頭 # 限定ではない）。
 * そのためキー・値そのものに # を含めることはできない。
 */
public final class SoundMapParser {

    private static final String EXT_WAV = ".wav";

    private SoundMapParser() {}

    /** 音声名=ファイル名 の行を挿入順を保った LinkedHashMap にする。不正入力は例外で落とす。 */
    public static LinkedHashMap<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String rawLine : lines) {
            int hashIdx = rawLine.indexOf('#');
            String beforeComment = hashIdx >= 0 ? rawLine.substring(0, hashIdx) : rawLine;
            String line = beforeComment.trim();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("sound-map: '=' の無い行: " + rawLine);
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("sound-map: キーまたは値が空の行: " + rawLine);
            }
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("sound-map: 重複した音声名: " + key);
            }
            result.put(key, value);
        }
        return result;
    }

    /** sound-map.properties が無いときの雛形を生成する。全ファイルを stem=ファイル名 で列挙する。 */
    public static String renderTemplate(List<File> files) {
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        sb.append("# kReminder sound-map.properties").append(nl);
        sb.append("# 書式: 1行1件で「音声名」の次に等号、その次に拡張子アリのファイル名を書く。").append(nl);
        sb.append("# # より後ろは行内のどこにあってもコメント扱い（音声名・ファイル名に # は使えない）。").append(nl);
        sb.append("# ここに書いたファイルは、以後この音声名でのみ SND.play() から呼べる").append(nl);
        sb.append("# （元のファイル名(stem)では呼べなくなる）。").append(nl);
        sb.append("# 書かなかったファイルは今まで通りファイル名（拡張子抜き）で呼べる。").append(nl);
        for (File file : files) {
            String fileName = file.getName();
            sb.append(stemOf(fileName)).append("=").append(fileName).append(nl);
        }
        return sb.toString();
    }

    private static String stemOf(String fileName) {
        if (fileName.length() >= EXT_WAV.length() && fileName.toLowerCase().endsWith(EXT_WAV)) {
            return fileName.substring(0, fileName.length() - EXT_WAV.length());
        }
        return fileName;
    }
}
