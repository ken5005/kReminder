package ken5005.kreminder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReminderStore {

    private static final String FILE_NAME = "reminders.json";

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
            @Override
            public void write(JsonWriter out, LocalDateTime v) throws IOException {
                if (v == null) out.nullValue();
                else out.value(v.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            @Override
            public LocalDateTime read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
                return LocalDateTime.parse(in.nextString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        })
        .create();

    static Path getDataPath() {
        String appData = System.getenv("APPDATA");
        Path dir = appData != null
            ? Path.of(appData, "kReminder")
            : Path.of(System.getProperty("user.home"), "kReminder");
        return dir.resolve(FILE_NAME);
    }

    public static List<Reminder> load() {
        Path path = getDataPath();
        if (!Files.exists(path)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Reminder>>() {}.getType();
            List<Reminder> list = GSON.fromJson(reader, listType);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("reminders load failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void save(List<Reminder> reminders) {
        Path path = getDataPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(reminders, writer);
            }
        } catch (IOException e) {
            System.err.println("reminders save failed: " + e.getMessage());
        }
    }
}
