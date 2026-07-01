package ken5005.kreminder.debug;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 整形済みの行をキューから取り出し、全シンクへ配る専用の背景スレッド1本。
 * 呼び出し側は enqueue するだけ — キューは無限長なので、pr()がどれだけ
 * 激しく呼ばれても呼び出し元はブロックしない。
 *
 * flushのタイミングは「{@link #FLUSH_THRESHOLD}行たまった」か
 * 「poll()がタイムアウトして新規が無かった（＝キューが一段落した）」の
 * どちらか。queue.poll(timeout) を使うと「たまった or タイムアウト」が
 * 自然に書ける。
 */
final class DebWorker {

    private static final int FLUSH_THRESHOLD = 50;
    private static final long POLL_TIMEOUT_MS = 2000;

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final List<LogSink> sinks;
    private final Thread thread;
    private volatile boolean running = true;

    DebWorker(List<LogSink> sinks) {
        this.sinks = List.copyOf(sinks);
        this.thread = new Thread(this::runLoop, "DEB-worker");
        this.thread.setDaemon(true);
        // ログはアプリ本体とCPUを取り合ってはいけないので優先度は最低に。
        this.thread.setPriority(Thread.MIN_PRIORITY);
    }

    void start() {
        thread.start();
    }

    void enqueue(String line) {
        queue.add(line);
    }

    private void runLoop() {
        int sinceFlush = 0;
        while (running || !queue.isEmpty()) {
            String line = null;
            try {
                line = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // shutdown()がpoll()を即座に起こすためだけに使う割り込み。
                // 止まるかどうかは下のwhile条件が判断する。
            }
            if (line != null) {
                for (LogSink sink : sinks) safeAccept(sink, line);
                sinceFlush++;
            }
            if (line == null || sinceFlush >= FLUSH_THRESHOLD) {
                flushAll();
                sinceFlush = 0;
            }
        }
        flushAll();
        closeAll();
    }

    private void safeAccept(LogSink sink, String line) {
        try {
            sink.accept(line);
        } catch (Exception e) {
            System.err.println("DebWorker: sink accept failed: " + e.getMessage());
        }
    }

    private void flushAll() {
        for (LogSink sink : sinks) {
            try {
                sink.flush();
            } catch (Exception e) {
                System.err.println("DebWorker: sink flush failed: " + e.getMessage());
            }
        }
    }

    private void closeAll() {
        for (LogSink sink : sinks) {
            try {
                sink.close();
            } catch (Exception e) {
                System.err.println("DebWorker: sink close failed: " + e.getMessage());
            }
        }
    }

    /** ループに「残りを吐き出したら止まれ」と伝える。完了まで（上限付きで）待つ。 */
    void shutdown() {
        running = false;
        thread.interrupt();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
