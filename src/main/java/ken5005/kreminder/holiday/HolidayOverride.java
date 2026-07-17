package ken5005.kreminder.holiday;

import ken5005.kreminder.AppDir;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads holiday_override.json and builds an OverlayHolidayCheck.
 * File absent → empty overlay. Broken JSON → stderr + empty overlay.
 */
public final class HolidayOverride {

    private static final String FILE_NAME = "holiday_override.json";
    private static final Gson GSON = new Gson();

    private HolidayOverride() {}

    private static class AddEntry {
        String date;
        String name;
    }

    private static class OverrideFile {
        @SerializedName("add")
        List<AddEntry> add;
        @SerializedName("remove")
        List<String> remove;
    }

    static Path getOverridePath() {
        return AppDir.resolve(FILE_NAME);
    }

    /** Loads the override file and wraps base with an OverlayHolidayCheck. Never throws. */
    public static OverlayHolidayCheck load(ken5005.kreminder.HolidayCheck base) {
        Path path = getOverridePath();
        if (!Files.exists(path)) {
            System.out.println("HolidayOverride: holiday_override.json が見つかりません（手動オーバレイ無しで起動）");
            return new OverlayHolidayCheck(base, Map.of(), Set.of());
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OverrideFile of = GSON.fromJson(reader, OverrideFile.class);
            if (of == null) {
                return new OverlayHolidayCheck(base, Map.of(), Set.of());
            }
            Map<LocalDate, String> addMap = new HashMap<>();
            if (of.add != null) {
                for (AddEntry e : of.add) {
                    if (e.date == null) continue;
                    try {
                        addMap.put(LocalDate.parse(e.date), e.name != null ? e.name : "");
                    } catch (Exception ex) {
                        System.err.println("HolidayOverride: skipping invalid add date: " + e.date);
                    }
                }
            }
            Set<LocalDate> removeSet = new HashSet<>();
            if (of.remove != null) {
                for (String s : of.remove) {
                    if (s == null) continue;
                    try {
                        removeSet.add(LocalDate.parse(s));
                    } catch (Exception ex) {
                        System.err.println("HolidayOverride: skipping invalid remove date: " + s);
                    }
                }
            }
            System.out.println("HolidayOverride: loaded +" + addMap.size() + "/-" + removeSet.size());
            return new OverlayHolidayCheck(base, addMap, removeSet);
        } catch (Exception e) {
            System.err.println("HolidayOverride: load failed (using empty overlay): " + e.getMessage());
            return new OverlayHolidayCheck(base, Map.of(), Set.of());
        }
    }
}
