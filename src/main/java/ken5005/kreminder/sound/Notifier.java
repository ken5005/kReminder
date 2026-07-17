package ken5005.kreminder.sound;

import ken5005.kreminder.debug.DEB;

import java.util.List;
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
        return start(pattern, () -> {});
    }

    /** onRingは各ステップのSND.play直前（＝実際に音が鳴るたび）に呼ばれる。通知スレッド上での呼び出しなのでEDT操作は呼び出し側でinvokeLaterすること。 */
    public static NotifyHandle start(NotifyPattern pattern, Runnable onRing) {
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        Thread thread = new Thread(() -> run(pattern, stopRequested, onRing), "kReminder-Notifier");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new NotifyHandle(thread, stopRequested);
    }

    private static void run(NotifyPattern pattern, AtomicBoolean stopRequested, Runnable onRing) {
        try {
            long deadline = pattern.maxDuration() != null
                    ? System.currentTimeMillis() + pattern.maxDuration().toMillis()
                    : Long.MAX_VALUE;

            // (1) steps を頭から1回流す
            for (NotifyStep step : pattern.steps()) {
                if (stopRequested.get() || System.currentTimeMillis() >= deadline) return;
                safeRing(onRing);
                SND.play(step.soundName(), step.volume());
                if (!sleepSliced(step.delayAfterMs(), stopRequested, deadline)) return;
            }

            // (2) repeatTail>0 なら steps の末尾 repeatTail 個のサイクルを stop/deadline まで繰り返す
            if (pattern.repeatTail() > 0) {
                List<NotifyStep> tail = pattern.steps()
                        .subList(pattern.steps().size() - pattern.repeatTail(), pattern.steps().size());
                while (!stopRequested.get() && System.currentTimeMillis() < deadline) {
                    for (NotifyStep step : tail) {
                        if (stopRequested.get() || System.currentTimeMillis() >= deadline) return;
                        safeRing(onRing);
                        SND.play(step.soundName(), step.volume());
                        if (!sleepSliced(step.delayAfterMs(), stopRequested, deadline)) return;
                    }
                }
            }
        } catch (Exception e) {
            // デバッグ機能・サブシステムの異常で本体を止めない、という全体方針の延長
            DEB.pr(e);
        }
    }

    /** onRingが投げても通知スレッドを殺さない（既存のtop-level catchとは別の個別ガード）。 */
    private static void safeRing(Runnable onRing) {
        try {
            onRing.run();
        } catch (Exception e) {
            DEB.pr(e);
        }
    }

    /**
     * 待ちをSLEEP_SLICE_MS刻みに割って毎回stopフラグとdeadlineの両方を見る。
     * どちらかに達したら（＝待ちの途中でも）打ち切ってfalseを返す。
     */
    private static boolean sleepSliced(long totalMs, AtomicBoolean stopRequested, long deadline) {
        long remaining = totalMs;
        while (remaining > 0) {
            if (stopRequested.get() || System.currentTimeMillis() >= deadline) return false;
            long slice = Math.min(SLEEP_SLICE_MS, remaining);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remaining -= slice;
        }
        return !stopRequested.get() && System.currentTimeMillis() < deadline;
    }
}
