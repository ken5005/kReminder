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
    void emptyArgsReturnsAllNullNonHelp() {
        Args args = ArgsParser.parse(new String[]{});
        assertFalse(args.help());
        assertNull(args.fakeNow());
        assertNull(args.dataPath());
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
    void fakeNowValid() {
        Args args = ArgsParser.parse(new String[]{"--fake-now=2026-05-05T08:55:00"});
        assertEquals(LocalDateTime.of(2026, 5, 5, 8, 55, 0), args.fakeNow());
        assertFalse(args.help());
    }

    @Test
    void fakeNowInvalidDateTimeThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--fake-now=not-a-date"}));
        assertTrue(e.getMessage().contains("--fake-now の日時が不正です"));
    }

    @Test
    void fakeNowEmptyValueThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--fake-now="}));
        assertTrue(e.getMessage().contains("--fake-now に値がありません"));
    }

    @Test
    void fakeNowWithoutEqualsThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--fake-now"}));
        assertTrue(e.getMessage().contains("--fake-now には値が必要です"));
    }

    @Test
    void dataValid() {
        Args args = ArgsParser.parse(new String[]{"--data=C:\\testdata\\reminders_4.json"});
        assertEquals("C:\\testdata\\reminders_4.json", args.dataPath());
        assertFalse(args.help());
    }

    @Test
    void dataEmptyValueThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--data="}));
        assertTrue(e.getMessage().contains("--data に値がありません"));
    }

    @Test
    void dataWithoutEqualsThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--data"}));
        assertTrue(e.getMessage().contains("--data には値が必要です"));
    }

    @Test
    void unknownArgThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--bogus"}));
        assertTrue(e.getMessage().contains("不明な引数です"));
    }

    @Test
    void duplicateOptionThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ArgsParser.parse(new String[]{"--data=C:\\a.json", "--data=C:\\b.json"}));
        assertTrue(e.getMessage().contains("--data が複数回指定されています"));
    }

    @Test
    void fakeNowAndDataCombinedBothApplied() {
        Args args = ArgsParser.parse(new String[]{
            "--fake-now=2026-05-05T08:55:00", "--data=C:\\testdata\\reminders_4.json"});
        assertEquals(LocalDateTime.of(2026, 5, 5, 8, 55, 0), args.fakeNow());
        assertEquals("C:\\testdata\\reminders_4.json", args.dataPath());
        assertFalse(args.help());
    }
}
