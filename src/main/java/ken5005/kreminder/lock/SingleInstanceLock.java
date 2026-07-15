package ken5005.kreminder.lock;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * ベースフォルダ単位で「同時に1プロセスだけ」を保証する単純機構。
 * base 直下に .instance.lock（FileLock本体） / .instance.info（保持者情報） /
 * .stop.request（退去要求フラグ）の3ファイルを置く。
 */
public final class SingleInstanceLock {

    public static final long DEFAULT_STOP_TIMEOUT_MS = 5000L;

    private static final String LOCK_FILE_NAME = ".instance.lock";
    private static final String INFO_FILE_NAME = ".instance.info";
    private static final String STOP_REQUEST_FILE_NAME = ".stop.request";

    private static final long POLL_INTERVAL_MS = 250L;

    private final Path base;
    private final Consumer<String> log;
    private final long stopTimeoutMs;

    private FileChannel heldChannel;
    private FileLock heldLock;

    public SingleInstanceLock(Path base, Consumer<String> log, long stopTimeoutMs) {
        if (stopTimeoutMs < 0) {
            throw new IllegalArgumentException("stopTimeoutMs は負であってはならない: " + stopTimeoutMs);
        }
        this.base = base;
        this.log = log;
        this.stopTimeoutMs = stopTimeoutMs;
    }

    public SingleInstanceLock(Path base, Consumer<String> log) {
        this(base, log, DEFAULT_STOP_TIMEOUT_MS);
    }

    public AcquireResult acquire(ContentionHandler handler) {
        if (tryAcquireLock()) {
            finishAcquire();
            return AcquireResult.ACQUIRED;
        }

        InstanceInfo holder = readHolderInfo();
        Choice choice = handler.onExistingInstance(holder);

        return switch (choice) {
            case CANCEL -> AcquireResult.ABORTED;
            case STOP_BOTH -> {
                writeStopRequest();
                yield AcquireResult.ABORTED;
            }
            case STOP_EXISTING -> {
                writeStopRequest();
                yield waitForExistingToStop(handler, holder);
            }
        };
    }

    private AcquireResult waitForExistingToStop(ContentionHandler handler, InstanceInfo holder) {
        long deadline = System.currentTimeMillis() + stopTimeoutMs;
        do {
            if (tryAcquireLock()) {
                finishAcquire();
                return AcquireResult.ACQUIRED;
            }
            sleepQuietly(POLL_INTERVAL_MS);
        } while (System.currentTimeMillis() < deadline);

        Fallback fallback = handler.onNoResponse(holder);
        if (fallback == Fallback.CANCEL) {
            return AcquireResult.ABORTED;
        }
        return forceKillAndRetry(holder);
    }

    private AcquireResult forceKillAndRetry(InstanceInfo holder) {
        if (holder.pid() <= 0) {
            log.accept("pid 不明のため force-kill できない");
            return AcquireResult.ABORTED;
        }

        ProcessHandle.of(holder.pid()).ifPresentOrElse(
                proc -> {
                    proc.destroy();
                    sleepQuietly(POLL_INTERVAL_MS);
                },
                () -> log.accept("force-kill 対象の pid が見つからない: " + holder.pid()));

        if (tryAcquireLock()) {
            finishAcquire();
            return AcquireResult.ACQUIRED;
        }
        return AcquireResult.ABORTED;
    }

    /** .instance.lock への tryLock を1回試みる。取れれば heldChannel/heldLock に保持して true。 */
    private boolean tryAcquireLock() {
        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockFilePath(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return false;
            }
            heldChannel = channel;
            heldLock = lock;
            return true;
        } catch (OverlappingFileLockException e) {
            closeQuietly(channel);
            return false;
        } catch (IOException e) {
            log.accept("ロックファイルの取得に失敗した: " + e.getMessage());
            closeQuietly(channel);
            return false;
        }
    }

    /** ロック取得成功後の共通後処理: info書き込み + shutdown hook登録。 */
    private void finishAcquire() {
        writeInfo();
        Runtime.getRuntime().addShutdownHook(new Thread(this::release));
    }

    private void writeInfo() {
        InstanceInfo info = new InstanceInfo(
                ProcessHandle.current().pid(),
                LocalDateTime.now().toString(),
                base.toAbsolutePath().toString());
        try {
            Files.writeString(infoFilePath(), info.render());
        } catch (IOException e) {
            log.accept(".instance.info の書き込みに失敗した: " + e.getMessage());
        }
    }

    private void writeStopRequest() {
        try {
            Files.writeString(stopRequestFilePath(), "");
        } catch (IOException e) {
            log.accept(".stop.request の書き込みに失敗した: " + e.getMessage());
        }
    }

    /** 保持者の情報を読む。読めない・壊れていれば pid 不明（-1）として扱う。 */
    private InstanceInfo readHolderInfo() {
        try {
            List<String> lines = Files.readAllLines(infoFilePath());
            return InstanceInfo.parse(lines);
        } catch (IOException | IllegalArgumentException e) {
            log.accept(".instance.info の読み取りに失敗した（pid 不明として扱う）: " + e.getMessage());
            return new InstanceInfo(-1, "", base.toAbsolutePath().toString());
        }
    }

    /** base 直下に .stop.request が在るか。既存プロセス側が定期的に呼ぶ用。 */
    public boolean stopRequested() {
        return Files.exists(stopRequestFilePath());
    }

    /** ロック解放 + FileChannel クローズ + .instance.info / .stop.request 削除。冪等・best-effort。 */
    public void release() {
        if (heldLock != null) {
            try {
                heldLock.release();
            } catch (IOException e) {
                log.accept("FileLock の解放に失敗した: " + e.getMessage());
            }
            heldLock = null;
        }
        closeQuietly(heldChannel);
        heldChannel = null;

        deleteQuietly(infoFilePath());
        deleteQuietly(stopRequestFilePath());
    }

    private Path lockFilePath() {
        return base.resolve(LOCK_FILE_NAME);
    }

    private Path infoFilePath() {
        return base.resolve(INFO_FILE_NAME);
    }

    private Path stopRequestFilePath() {
        return base.resolve(STOP_REQUEST_FILE_NAME);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.accept("ファイル削除に失敗した（無視して続行）: " + path + " / " + e.getMessage());
        }
    }

    private void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            log.accept("FileChannel のクローズに失敗した: " + e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
