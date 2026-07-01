package ken5005.kreminder.debug;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DEBの静的ファサード。呼び心地は元のトレードアプリ版を踏襲する。
 * 実体はDebWorkerへenqueueするだけ＝呼び出しスレッドは即リターンする。
 *
 * init前にpr()が呼ばれても落ちない：ワーカーが無ければコンソールへ直接落とす。
 */
public final class DEB {

    /** 将来のPanelSink（①-b-2）が使うテキストエリアの文字数上限。今回は未使用。 */
    public static final int PANEL_TEXT_LIMIT = 100_000;

    private static volatile Clock clock = Clock.systemDefaultZone();
    private static volatile DebWorker worker;

    private DEB() {}

    /** 起動時に1回呼ぶ。ワーカーを起動してsinkへの配信を開始する。 */
    public static void init(Clock injectedClock, LogSink... sinks) {
        clock = injectedClock;
        DebWorker w = new DebWorker(List.of(sinks));
        w.start();
        worker = w;
    }

    /** 終了時に1回呼ぶ。キューに残った行をflushしてからsinkを閉じる。 */
    public static void shutdown() {
        DebWorker w = worker;
        if (w != null) w.shutdown();
    }

    public static void pr(String s) {
        enqueue(String.valueOf(s));
    }

    public static void pr(int v) {
        enqueue(String.valueOf(v));
    }

    public static void pr(long v) {
        enqueue(String.valueOf(v));
    }

    public static void pr(double v) {
        enqueue(String.format("%.4f", v));
    }

    public static void pr(boolean v) {
        enqueue(String.valueOf(v));
    }

    public static void pr(Object o) {
        enqueue(String.valueOf(o));
    }

    public static void pr(Throwable t) {
        enqueue(DebFormat.formatStackTrace(t));
    }

    public static void pr(String[] arr) {
        enqueue(arr == null ? "(null)" : String.join(", ", arr));
    }

    public static void prFmt(String format, Object... args) {
        enqueue(String.format(format, args));
    }

    /** prMul：Object...を元DEB流の整形（null→"(null) "、Double→小数4桁）でつなげて出す。 */
    public static void prMul(Object... args) {
        enqueue(DebFormat.formatArgs(args));
    }

    /** ラベル付きで現在時刻（Clock基準）を1行出す。処理の区切りを追うのに使う。 */
    public static void prTime(String label) {
        enqueue(label + " " + DebFormat.formatTime(LocalDateTime.now(clock)));
    }

    private static void enqueue(String body) {
        String line = DebFormat.formatLine(LocalDateTime.now(clock), body);
        DebWorker w = worker;
        if (w == null) {
            // init前はキューが無い＝コンソールへ直接落として取りこぼさない。
            System.out.println(line);
            return;
        }
        w.enqueue(line);
    }
}
