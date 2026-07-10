package ken5005.kreminder.sound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundMapParserTest {

    @Test
    void normalMultipleLines_preservesInsertionOrder() {
        List<String> lines = List.of(
            "呼び鈴=ごん.wav",
            "通知=notify.wav"
        );

        LinkedHashMap<String, String> map = SoundMapParser.parse(lines);

        assertEquals(2, map.size());
        assertEquals("ごん.wav", map.get("呼び鈴"));
        assertEquals("notify.wav", map.get("通知"));
        assertEquals(List.of("呼び鈴", "通知"), List.copyOf(map.keySet()));
    }

    @Test
    void commentsAndBlankLinesAreSkipped() {
        List<String> lines = List.of(
            "# コメント行",
            "",
            "   ",
            "呼び鈴=ごん.wav"
        );

        LinkedHashMap<String, String> map = SoundMapParser.parse(lines);

        assertEquals(1, map.size());
        assertEquals("ごん.wav", map.get("呼び鈴"));
    }

    @Test
    void bothSidesAreTrimmed() {
        List<String> lines = List.of("  呼び鈴  =   ごん.wav  ");

        LinkedHashMap<String, String> map = SoundMapParser.parse(lines);

        assertEquals(1, map.size());
        assertEquals("ごん.wav", map.get("呼び鈴"));
    }

    @Test
    void inlineCommentAfterValue_isStrippedAndTrimmed() {
        List<String> lines = List.of("notify=お知らせ.wav  # 通知音");

        LinkedHashMap<String, String> map = SoundMapParser.parse(lines);

        assertEquals(1, map.size());
        assertEquals("お知らせ.wav", map.get("notify"));
    }

    @Test
    void inlineCommentWithoutSpaceBeforeHash_isStrippedAndTrimmed() {
        List<String> lines = List.of("key=val   #コメント");

        LinkedHashMap<String, String> map = SoundMapParser.parse(lines);

        assertEquals(1, map.size());
        assertEquals("val", map.get("key"));
    }

    @Test
    void hashOnlyLine_isSkipped() {
        List<String> lines = List.of("#", "呼び鈴=ごん.wav");

        LinkedHashMap<String, String> map = SoundMapParser.parse(lines);

        assertEquals(1, map.size());
    }

    @Test
    void hashWithLeadingBlank_isSkipped() {
        List<String> lines = List.of("   # foo", "呼び鈴=ごん.wav");

        LinkedHashMap<String, String> map = SoundMapParser.parse(lines);

        assertEquals(1, map.size());
    }

    @Test
    void duplicateKey_throws() {
        List<String> lines = List.of("呼び鈴=ごん.wav", "呼び鈴=notify.wav");

        assertThrows(IllegalArgumentException.class, () -> SoundMapParser.parse(lines));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "呼び鈴ごん.wav",
        "=ごん.wav",
        "呼び鈴=",
        "   =   ",
    })
    void malformedLine_throws(String badLine) {
        List<String> lines = List.of(badLine);

        assertThrows(IllegalArgumentException.class, () -> SoundMapParser.parse(lines));
    }

    @Test
    void renderTemplate_producesStemEqualsFileNameLines() {
        List<File> files = List.of(new File("ごん.wav"), new File("notify.WAV"));

        String template = SoundMapParser.renderTemplate(files);

        assertTrue(template.contains("ごん=ごん.wav"));
        assertTrue(template.contains("notify=notify.WAV"));
    }

    @Test
    void renderTemplate_emptyFileList_hasOnlyHeaderComment() {
        String template = SoundMapParser.renderTemplate(List.of());

        String[] lines = template.split(System.lineSeparator());
        for (String line : lines) {
            if (line.isEmpty()) continue;
            assertTrue(line.startsWith("#"), "データ行が無いはずなのにコメント以外の行がある: " + line);
        }
    }
}
