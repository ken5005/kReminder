package ken5005.kreminder.holiday;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class HolidayCsvParser {

    private static final Charset MS932 = Charset.forName("MS932");

    private HolidayCsvParser() {}

    /**
     * @param bytes MS932-encoded CSV bytes from the Cabinet Office
     * @return date → holiday name map
     * @throws IllegalArgumentException if bytes cannot be decoded as MS932
     */
    public static Map<LocalDate, String> parse(byte[] bytes) {
        if (bytes.length == 0) return Map.of();

        CharsetDecoder decoder = MS932.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        String text;
        try {
            CharBuffer buf = decoder.decode(ByteBuffer.wrap(bytes));
            text = buf.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("MS932 decode failed: " + e.getMessage(), e);
        }

        Map<LocalDate, String> result = new HashMap<>();
        boolean firstLine = true;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                line = line.trim();
                if (line.isEmpty()) continue;
                int comma = line.indexOf(',');
                if (comma < 0) {
                    System.err.println("HolidayCsvParser: skipping line with no comma: " + line);
                    continue;
                }
                String datePart = line.substring(0, comma).trim();
                String name = line.substring(comma + 1).trim();
                try {
                    result.put(parseDate(datePart), name);
                } catch (Exception e) {
                    System.err.println("HolidayCsvParser: skipping invalid date: " + datePart);
                }
            }
        } catch (Exception e) {
            System.err.println("HolidayCsvParser: read error: " + e.getMessage());
        }
        return result;
    }

    private static LocalDate parseDate(String s) {
        // YYYY/M/D or YYYY-M-D, with or without zero padding
        String[] parts = s.contains("/") ? s.split("/") : s.split("-");
        if (parts.length != 3) throw new IllegalArgumentException("not 3 parts: " + s);
        return LocalDate.of(
            Integer.parseInt(parts[0].trim()),
            Integer.parseInt(parts[1].trim()),
            Integer.parseInt(parts[2].trim())
        );
    }
}
