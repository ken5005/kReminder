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

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
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

    private final Path path;

    /** 従来どおりのデフォルト先（%APPDATA%\kReminder\reminders.json 等）を使う。 */
    public ReminderStore() {
        this(DataPathResolver.defaultPath());
    }

    /** --data 等で明示指定された Path を読み書き先にする。 */
    public ReminderStore(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public List<Reminder> load() {
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

    public void save(List<Reminder> reminders) {
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
