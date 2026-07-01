package ken5005.kreminder.debug;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * %APPDATA%\kReminder\logs\DEB-yyyyMMdd-HH.txt へ追記する。Clockの時が
 * 変わったら新しいファイルへロールオーバーする。IO失敗は全て握って
 * stderrへ出すだけ — ログの失敗が本体を巻き込んではいけない。
 */
public final class FileSink implements LogSink {

    private static final DateTimeFormatter FILE_KEY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HH");

    private final Clock clock;
    private final Path dir;
    private BufferedWriter writer;
    private String currentFileKey;

    public FileSink(Clock clock) {
        this.clock = clock;
        this.dir = resolveDir();
    }

    private static Path resolveDir() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null
            ? Path.of(appData, "kReminder")
            : Path.of(System.getProperty("user.home"), "kReminder");
        return base.resolve("logs");
    }

    @Override
    public void accept(String line) {
        try {
            ensureCurrentFile();
            writer.write(line);
            writer.newLine();
        } catch (Exception e) {
            System.err.println("FileSink: write failed: " + e.getMessage());
        }
    }

    private void ensureCurrentFile() throws IOException {
        String key = LocalDateTime.now(clock).format(FILE_KEY_FMT);
        if (key.equals(currentFileKey) && writer != null) return;
        closeWriterQuietly();
        Files.createDirectories(dir);
        Path file = dir.resolve("DEB-" + key + ".txt");
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        currentFileKey = key;
    }

    @Override
    public void flush() {
        try {
            if (writer != null) writer.flush();
        } catch (Exception e) {
            System.err.println("FileSink: flush failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            closeWriterQuietly();
        } catch (Exception e) {
            System.err.println("FileSink: close failed: " + e.getMessage());
        }
    }

    private void closeWriterQuietly() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            System.err.println("FileSink: close failed: " + e.getMessage());
        } finally {
            writer = null;
        }
    }
}
