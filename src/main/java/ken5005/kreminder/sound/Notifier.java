package ken5005.kreminder.sound;

import ken5005.kreminder.debug.DEB;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NotifyPatternを使い捨てのデーモンスレッドで再生し続ける静的ファサード。
 * 実際に音を出すのは既存のSoundWorker（直列1本）のまま＝ここはSND.playを呼ぶ（enqueueする）だけ。
 * start()は呼び出し元（EDT）を止めずに即returnする。
 */
public final class Notifier {

    // stopフラグの確認間隔。OKを押した瞬間からこの刻み以内に鳴りやむ。
    private static final long SLEEP_SLICE_MS = 100;

    private Notifier() {
    }

    public static NotifyHandle start(NotifyPattern pattern) {
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        Thread thread = new Thread(() -> run(pattern, stopRequested), "kReminder-Notifier");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new NotifyHandle(thread, stopRequested);
    }

    private static void run(NotifyPattern pattern, AtomicBoolean stopRequested) {
        try {
            long deadline = pattern.loop() && pattern.maxDuration() != null
                    ? System.currentTimeMillis() + pattern.maxDuration().toMillis()
                    : Long.MAX_VALUE;
            do {
                for (NotifyStep step : pattern.steps()) {
                    if (stopRequested.get()) return;
                    SND.play(step.soundName(), step.volume());
                    if (!sleepSliced(step.delayAfterMs(), stopRequested)) return;
                }
            } while (pattern.loop() && !stopRequested.get() && System.currentTimeMillis() < deadline);
        } catch (Exception e) {
            // デバッグ機能・サブシステムの異常で本体を止めない、という全体方針の延長
            DEB.pr(e);
        }
    }

    /** 待ちをSLEEP_SLICE_MS刻みに割って毎回stopフラグを見る。stopで抜けたらfalseを返す。 */
    private static boolean sleepSliced(long totalMs, AtomicBoolean stopRequested) {
        long remaining = totalMs;
        while (remaining > 0) {
            if (stopRequested.get()) return false;
            long slice = Math.min(SLEEP_SLICE_MS, remaining);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remaining -= slice;
        }
        return !stopRequested.get();
    }
}
