package ken5005.kreminder.sound;

import ken5005.kreminder.debug.DEB;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 再生依頼を1本のデーモンスレッドで直列に処理するワーカー。
 * queue.take()でブロッキング待機し、requestStop()はinterrupt()で待機を起こして止める。
 */
public class SoundWorker extends Thread {

    private static final int QUEUE_CAPACITY = 30;
    private static final String FALLBACK_NAME = "Oops";

    private final LinkedBlockingQueue<SoundRequest> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final Map<String, File> soundMap;
    private volatile boolean stopRequested = false;

    public SoundWorker(Map<String, File> soundMap) {
        super("SND-worker");
        this.soundMap = soundMap;
    }

    @Override
    public void run() {
        while (!stopRequested) {
            try {
                SoundRequest request = queue.take();
                playOne(request);
            } catch (InterruptedException e) {
                // requestStop()がtake()を起こすためだけの割り込み。継続可否はwhile条件が見る。
            }
        }
    }

    /** requestStop()が立てた停止フラグでrun()ループを抜けさせ、take()のブロックをinterrupt()で起こす。 */
    public void requestStop() {
        stopRequested = true;
        interrupt();
    }

    boolean enqueue(SoundRequest request) {
        boolean offered = queue.offer(request);
        if (!offered) {
            DEB.pr("SND キュー満杯: drop " + request.name());
        }
        return offered;
    }

    private void playOne(SoundRequest request) {
        File file = soundMap.get(request.name());
        if (file == null) {
            DEB.pr(new RuntimeException("未定義の音声名: " + request.name()));
            file = soundMap.get(FALLBACK_NAME);
            if (file == null) {
                DEB.pr(new RuntimeException("音声ファイルが無い: " + FALLBACK_NAME + ".wav"));
                return;
            }
        }
        try {
            playFile(file, request.volume());
        } catch (Exception e) {
            DEB.pr(e);
        }
    }

    private void playFile(File file, float volume) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            Clip clip = AudioSystem.getClip();
            CountDownLatch latch = new CountDownLatch(1);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    latch.countDown();
                }
            });
            clip.open(ais);
            applyVolume(clip, volume);
            clip.start();
            latch.await();
            clip.close();
        }
    }

    private void applyVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        // volume(0.0-1.0)をdBに変換。0はMASTER_GAINの最小値に丸める（log10(0)は未定義のため）。
        float gain = volume <= 0.0001f
                ? gainControl.getMinimum()
                : (float) (20.0 * Math.log10(volume));
        gain = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gain));
        gainControl.setValue(gain);
    }
}
