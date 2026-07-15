package ken5005.kreminder.lock;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceInfoTest {

    @Test
    void renderThenParseRoundTrips() {
        InstanceInfo original = new InstanceInfo(12345L, "2026-07-16T09:00:00", "C:\\base");

        String rendered = original.render();
        InstanceInfo parsed = InstanceInfo.parse(Arrays.asList(rendered.split("\n")));

        assertEquals(original, parsed);
    }

    @Test
    void parseIgnoresUnknownAndBlankLines() {
        List<String> lines = List.of(
                "pid=100",
                "",
                "unknownKey=whatever",
                "startedAt=2026-01-01T00:00:00",
                "base=/some/base");

        InstanceInfo parsed = InstanceInfo.parse(lines);

        assertEquals(new InstanceInfo(100L, "2026-01-01T00:00:00", "/some/base"), parsed);
    }

    @Test
    void parseThrowsWhenRequiredKeyMissing() {
        List<String> lines = List.of("pid=100", "startedAt=2026-01-01T00:00:00");

        assertThrows(IllegalArgumentException.class, () -> InstanceInfo.parse(lines));
    }

    @Test
    void parseThrowsWhenPidNotNumeric() {
        List<String> lines = List.of(
                "pid=abc",
                "startedAt=2026-01-01T00:00:00",
                "base=/some/base");

        assertThrows(IllegalArgumentException.class, () -> InstanceInfo.parse(lines));
    }
}
