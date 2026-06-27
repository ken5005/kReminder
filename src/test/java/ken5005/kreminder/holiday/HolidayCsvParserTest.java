package ken5005.kreminder.holiday;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HolidayCsvParserTest {

    private static final Charset MS932 = Charset.forName("MS932");

    @Test
    void normalTwoRows() {
        String csv = "国民の祝日・休日月日,国民の祝日・休日名称\r\n"
                   + "2026/1/1,元日\r\n"
                   + "2026/1/12,成人の日\r\n";
        Map<LocalDate, String> map = HolidayCsvParser.parse(csv.getBytes(MS932));
        assertEquals(2, map.size());
        assertEquals("元日", map.get(LocalDate.of(2026, 1, 1)));
        assertEquals("成人の日", map.get(LocalDate.of(2026, 1, 12)));
    }

    @Test
    void emptyInput() {
        Map<LocalDate, String> map = HolidayCsvParser.parse(new byte[0]);
        assertTrue(map.isEmpty());
    }

    @Test
    void headerOnly() {
        String csv = "国民の祝日・休日月日,国民の祝日・休日名称\r\n";
        Map<LocalDate, String> map = HolidayCsvParser.parse(csv.getBytes(MS932));
        assertTrue(map.isEmpty());
    }

    @Test
    void invalidDateRowIsSkipped() {
        String csv = "国民の祝日・休日月日,国民の祝日・休日名称\r\n"
                   + "2026/1/1,元日\r\n"
                   + "baddate,不正行\r\n"
                   + "2026/2/11,建国記念の日\r\n";
        Map<LocalDate, String> map = HolidayCsvParser.parse(csv.getBytes(MS932));
        assertEquals(2, map.size());
        assertTrue(map.containsKey(LocalDate.of(2026, 1, 1)));
        assertTrue(map.containsKey(LocalDate.of(2026, 2, 11)));
    }

    @Test
    void invalidBytes_throws() {
        // 0xFF is not a valid MS932 byte — decoder with REPORT action throws
        byte[] invalid = {(byte) 0xFF};
        assertThrows(IllegalArgumentException.class, () -> HolidayCsvParser.parse(invalid));
    }
}
