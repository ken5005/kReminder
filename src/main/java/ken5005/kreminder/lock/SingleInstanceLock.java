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
import java.util.Optional;
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
    // destroy() / destroyForcibly() それぞれの後に何回tryLockを再試行するか（250ms間隔で約1秒分）
    private static final int FORCE_KILL_RETRIES = 4;

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

    /**
     * 段階的な強制終了: destroy() → 約1秒リトライ → 効かなければ destroyForcibly() →
     * 約1秒リトライ。どちらかの段階でロックが取れ次第、即 finishAcquire して ACQUIRED を返す。
     * pid が既に存在しない（相手は既に死んでいる）場合は、破壊操作をスキップして
     * tryLock を1回試すだけにする（もう死んでいるので待つ理由が無い）。
     */
    private AcquireResult forceKillAndRetry(InstanceInfo holder) {
        if (holder.pid() <= 0) {
            log.accept("pid 不明のため force-kill できない");
            return AcquireResult.ABORTED;
        }

        Optional<ProcessHandle> procOpt = ProcessHandle.of(holder.pid());
        if (procOpt.isEmpty()) {
            log.accept("force-kill 対象の pid は既に存在しない。ロック再取得のみ試みる: " + holder.pid());
            return tryAcquireOnce();
        }
        ProcessHandle proc = procOpt.get();

        log.accept("既存プロセス(pid=" + holder.pid() + ")へ destroy() を送る");
        proc.destroy();
        if (tryAcquireWithRetries()) {
            return AcquireResult.ACQUIRED;
        }

        log.accept("destroy() に応答が無いため destroyForcibly() に切り替える(pid=" + holder.pid() + ")");
        proc.destroyForcibly();
        if (tryAcquireWithRetries()) {
            return AcquireResult.ACQUIRED;
        }

        log.accept("強制終了を試みたがロックを取得できなかった(pid=" + holder.pid() + ")");
        return AcquireResult.ABORTED;
    }

    /** POLL_INTERVAL_MS間隔でFORCE_KILL_RETRIES回だけtryLockを試す。取れたらfinishAcquireまで済ませる。 */
    private boolean tryAcquireWithRetries() {
        for (int i = 0; i < FORCE_KILL_RETRIES; i++) {
            sleepQuietly(POLL_INTERVAL_MS);
            if (tryAcquireLock()) {
                finishAcquire();
                return true;
            }
        }
        return false;
    }

    /** 相手が既に死んでいる場合の即時1回試行。 */
    private AcquireResult tryAcquireOnce() {
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

    /**
     * ロック取得成功後の共通後処理: 残存stop.request削除 + info書き込み + shutdown hook登録。
     * 残存するstop.requestは「前ホルダー宛て、または自分がSTOP_EXISTINGで書いたもの」で
     * 用済み＝ここで消しておかないと、force-killで相手が即死した直後に新ホルダーである自分が
     * 自分の1秒Timerでこれを拾って誤って自殺する（stale stop.requestの自己修復も兼ねる）。
     */
    private void finishAcquire() {
        deleteQuietly(stopRequestFilePath());
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

    /**
     * .instance.info / .stop.request 削除 + FileLock解放 + FileChannel クローズ。冪等・best-effort。
     * 削除をロック解放より先に済ませる：逆順（先にロックを手放す）だと、手放した瞬間に
     * 待っていた別プロセスがロックを取ってwriteInfo()した直後に、退場側の遅延した
     * info削除が走って相手の新しい.instance.infoを消してしまう競合がありうるため。
     */
    public void release() {
        deleteQuietly(infoFilePath());
        deleteQuietly(stopRequestFilePath());

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
