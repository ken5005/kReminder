package ken5005.kreminder.sound;

import ken5005.kreminder.debug.DEB;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 音声再生の静的ファサード。DEBと同じ思想：どこからでも呼べる・本体を止めない・例外を呑む。
 * init前やwavDir不在時はplay()がsilent dropになる（音が鳴らないだけで本体は動き続ける）。
 */
public final class SND {

    private static volatile SoundWorker worker;

    private SND() {}

    public static void init(Path wavDir) {
        if (worker != null) {
            DEB.pr("SND: 既にinit済み（2回目以降の呼び出しは無視）");
            return;
        }
        if (!Files.isDirectory(wavDir)) {
            DEB.pr(new RuntimeException("SND: wavDir が存在しない: " + wavDir));
            return;
        }
        Map<String, File> soundMap = WavLoader.load(wavDir);
        SoundWorker w = new SoundWorker(soundMap);
        w.setDaemon(true);
        w.start();
        worker = w;
    }

    public static void play(String name, float volume) {
        SoundWorker w = worker;
        if (w == null) {
            return;
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, volume));
        if (clamped != volume) {
            DEB.pr("SND: volume clamp " + volume + " -> " + clamped);
        }
        w.enqueue(new SoundRequest(name, clamped));
    }

    public static void shutdown() {
        SoundWorker w = worker;
        if (w == null) {
            return;
        }
        w.requestStop();
        try {
            w.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
