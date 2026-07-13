package ken5005.kreminder.sound;

import java.util.concurrent.atomic.AtomicBoolean;

/** Notifier.start()が返すハンドル。stop()は何度呼んでも安全（冪等）。awaitTerminationはテスト用。 */
public final class NotifyHandle {

    private final Thread thread;
    private final AtomicBoolean stopRequested;

    NotifyHandle(Thread thread, AtomicBoolean stopRequested) {
        this.thread = thread;
        this.stopRequested = stopRequested;
    }

    public void stop() {
        stopRequested.set(true);
    }

    /** スレッドの終了をms待ち、終了していればtrueを返す（テスト用）。 */
    public boolean awaitTermination(long ms) {
        try {
            thread.join(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return !thread.isAlive();
    }
}
