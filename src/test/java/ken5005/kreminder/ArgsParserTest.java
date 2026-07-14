package ken5005.kreminder;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgsParserTest {

    @Test
    void emptyArgsThrowsBecauseBaseIsRequired() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{}));
        assertTrue(e.getMessage().contains("--base は必須です"));
    }

    @Test
    void helpLongForm() {
        Args args = ArgsParser.parse(new String[]{"--help"});
        assertTrue(args.help());
    }

    @Test
    void helpShortForm() {
        Args args = ArgsParser.parse(new String[]{"-h"});
        assertTrue(args.help());
    }

    @Test
    void helpWinsEvenWithUnknownArgPresent() {
        Args args = ArgsParser.parse(new String[]{"--nonsense", "-h"});
        assertTrue(args.help());
    }

    @Test
    void helpWinsEvenWithoutBase() {
        // --baseが無くても--helpが最優先されるべき（Usageを見たいだけの人を止めない）
        Args args = ArgsParser.parse(new String[]{"--help"});
        assertTrue(args.help());
        assertNull(args.fakeNow());
        assertNull(args.basePath());
    }

    @Test
    void fakeNowValid() {
        Args args = ArgsParser.parse(new String[]{
            "--fake-now=2026-05-05T08:55:00", "--base=testdata"});
        assertEquals(LocalDateTime.of(2026, 5, 5, 8, 55, 0), args.fakeNow());
        assertFalse(args.help());
    }

    @Test
    void fakeNowInvalidDateTimeThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--fake-now=not-a-date", "--base=testdata"}));
        assertTrue(e.getMessage().contains("--fake-now の日時が不正です"));
    }

    @Test
    void fakeNowEmptyValueThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--fake-now=", "--base=testdata"}));
        assertTrue(e.getMessage().contains("--fake-now に値がありません"));
    }

    @Test
    void fakeNowWithoutEqualsThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--fake-now", "--base=testdata"}));
        assertTrue(e.getMessage().contains("--fake-now には値が必要です"));
    }

    @Test
    void fakeNowDuplicateThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{
                "--fake-now=2026-05-05T08:55:00", "--fake-now=2026-05-06T08:55:00", "--base=testdata"}));
        assertTrue(e.getMessage().contains("--fake-now が複数回指定されています"));
    }

    @Test
    void baseValid() {
        Args args = ArgsParser.parse(new String[]{"--base=C:\\testdata"});
        assertEquals("C:\\testdata", args.basePath());
        assertFalse(args.help());
    }

    @Test
    void baseRelativePathValid() {
        Args args = ArgsParser.parse(new String[]{"--base=testdata"});
        assertEquals("testdata", args.basePath());
    }

    @Test
    void baseEmptyValueThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--base="}));
        assertTrue(e.getMessage().contains("--base に値がありません"));
    }

    @Test
    void baseWithoutEqualsThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--base"}));
        assertTrue(e.getMessage().contains("--base には値が必要です"));
    }

    @Test
    void baseMissingThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--fake-now=2026-05-05T08:55:00"}));
        assertTrue(e.getMessage().contains("--base は必須です"));
    }

    @Test
    void unknownArgThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--bogus", "--base=testdata"}));
        assertTrue(e.getMessage().contains("不明な引数です"));
    }

    @Test
    void duplicateOptionThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--base=C:\\a", "--base=C:\\b"}));
        assertTrue(e.getMessage().contains("--base が複数回指定されています"));
    }

    @Test
    void fakeNowAndBaseCombinedBothApplied() {
        Args args = ArgsParser.parse(new String[]{
            "--fake-now=2026-05-05T08:55:00", "--base=C:\\testdata"});
        assertEquals(LocalDateTime.of(2026, 5, 5, 8, 55, 0), args.fakeNow());
        assertEquals("C:\\testdata", args.basePath());
        assertFalse(args.help());
    }
}
