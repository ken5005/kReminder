package ken5005.kreminder.sound;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SoundMapBuilderTest {

    @Test
    void tableFileGetsExplicitName_nonTableFileGetsStem() {
        List<File> wavFiles = List.of(new File("ごん.wav"), new File("notify.wav"));
        Map<String, String> table = new LinkedHashMap<>();
        table.put("呼び鈴", "ごん.wav");

        Map<String, File> result = SoundMapBuilder.build(wavFiles, table);

        assertEquals(2, result.size());
        assertEquals("ごん.wav", result.get("呼び鈴").getName());
        assertEquals("notify.wav", result.get("notify").getName());
        // テーブルに載ったファイルはstem名を引退している
        assertFalse(result.containsKey("ごん"));
    }

    @Test
    void danglingTableValue_throws() {
        List<File> wavFiles = List.of(new File("notify.wav"));
        Map<String, String> table = new LinkedHashMap<>();
        table.put("呼び鈴", "ごん.wav");

        assertThrows(IllegalArgumentException.class, () -> SoundMapBuilder.build(wavFiles, table));
    }

    @Test
    void caseInsensitiveFileNameMatch_resolves() {
        List<File> wavFiles = List.of(new File("ごん.wav"));
        Map<String, String> table = new LinkedHashMap<>();
        table.put("呼び鈴", "ごん.WAV");

        Map<String, File> result = SoundMapBuilder.build(wavFiles, table);

        assertEquals("ごん.wav", result.get("呼び鈴").getName());
    }

    @Test
    void explicitNameCollidesWithAutoStemOfAnotherFile_throws() {
        // ごん.wav をテーブルで「呼び鈴」に割り当てる一方、別ファイル 呼び鈴.wav が
        // 自動stem採用されると音声名が衝突する
        List<File> wavFiles = List.of(new File("ごん.wav"), new File("呼び鈴.wav"));
        Map<String, String> table = new LinkedHashMap<>();
        table.put("呼び鈴", "ごん.wav");

        assertThrows(IllegalArgumentException.class, () -> SoundMapBuilder.build(wavFiles, table));
    }

    @Test
    void emptyTable_allFilesUseStem() {
        List<File> wavFiles = List.of(new File("ごん.wav"), new File("notify.wav"));

        Map<String, File> result = SoundMapBuilder.build(wavFiles, Map.of());

        assertEquals(2, result.size());
        assertEquals("ごん.wav", result.get("ごん").getName());
        assertEquals("notify.wav", result.get("notify").getName());
    }

    @Test
    void emptyFilesWithNonEmptyTable_allDangling_throws() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("呼び鈴", "ごん.wav");

        assertThrows(IllegalArgumentException.class, () -> SoundMapBuilder.build(List.of(), table));
    }

    @Test
    void twoDifferentNamesPointingToSameFile_bothOk() {
        List<File> wavFiles = List.of(new File("ごん.wav"));
        Map<String, String> table = new LinkedHashMap<>();
        table.put("呼び鈴", "ごん.wav");
        table.put("チャイム", "ごん.wav");

        Map<String, File> result = SoundMapBuilder.build(wavFiles, table);

        assertEquals(2, result.size());
        assertEquals("ごん.wav", result.get("呼び鈴").getName());
        assertEquals("ごん.wav", result.get("チャイム").getName());
    }
}
