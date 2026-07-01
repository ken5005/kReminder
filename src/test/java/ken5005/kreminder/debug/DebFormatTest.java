package ken5005.kreminder.debug;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DebFormatTest {

    @Test
    void formatArgs_emptyReturnsEmptyString() {
        assertEquals("", DebFormat.formatArgs());
    }

    @Test
    void formatArgs_nullBecomesNullMarker() {
        assertEquals("(null) ", DebFormat.formatArgs((Object) null));
    }

    @Test
    void formatArgs_doubleUsesFourDecimals() {
        assertEquals("3.1416 ", DebFormat.formatArgs(3.14159));
    }

    @Test
    void formatArgs_otherTypesUseToString() {
        assertEquals("abc 42 true ", DebFormat.formatArgs("abc", 42, true));
    }

    @Test
    void formatArgs_mixedTypesInOrder() {
        assertEquals("(null) 3.1416 abc 42 ", DebFormat.formatArgs(null, 3.14159, "abc", 42));
    }

    @Test
    void formatTime_localDateTime() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 2, 9, 5, 3, 123_000_000);
        assertEquals("2026/07/02 09:05:03.123", DebFormat.formatTime(time));
    }

    @Test
    void formatTime_instantWithZoneMatchesLocalDateTime() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        LocalDateTime time = LocalDateTime.of(2026, 7, 2, 9, 5, 3, 123_000_000);
        Instant instant = time.atZone(tokyo).toInstant();
        assertEquals("2026/07/02 09:05:03.123", DebFormat.formatTime(instant, tokyo));
    }

    @Test
    void formatStackTrace_nullReturnsNullMarker() {
        assertEquals("(null)", DebFormat.formatStackTrace(null));
    }

    @Test
    void formatStackTrace_containsMessageAndFrame() {
        Exception e = new RuntimeException("boom");
        String trace = DebFormat.formatStackTrace(e);
        assertTrue(trace.contains("boom"));
        assertTrue(trace.contains("ken5005.kreminder.debug.DebFormatTest"));
    }

    @Test
    void formatLine_prefixesTimeBeforeBody() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 2, 9, 5, 3, 123_000_000);
        assertEquals("2026/07/02 09:05:03.123 hello world", DebFormat.formatLine(time, "hello world"));
    }
}
