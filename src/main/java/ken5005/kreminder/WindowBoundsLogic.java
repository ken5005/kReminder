package ken5005.kreminder;

import java.util.List;

/**
 * 保存されていたウィンドウ矩形を、今のモニタ構成に照らして安全な値に直す純関数群（フェーズ4「き」）。
 * Swing/AWT 非依存。GraphicsEnvironment への実問い合わせは呼び出し側（MainWindow）が担う。
 */
public final class WindowBoundsLogic {

    public static final int MIN_WIDTH = 400;
    public static final int MIN_HEIGHT = 300;

    private WindowBoundsLogic() {
    }

    /**
     * resolve() の結果。centered=true のときは x/y は無意味な値（呼び出し側は
     * setLocationRelativeTo(null) 等で中央寄せする＝位置はこの結果を使わない）。
     */
    public record Resolved(boolean centered, int x, int y, int width, int height) {}

    /**
     * 保存されていた矩形(x,y,width,height)が monitors のいずれかと重なるか調べて安全化する。
     * どれとも重ならない（画面外に消えている）場合は中央寄せ指示（centered=true）を返す。
     * 重なる場合は、その画面の表示領域を基準に幅・高さを最小値〜画面サイズの範囲へクランプする。
     */
    public static Resolved resolve(int x, int y, int width, int height, List<MonitorBounds> monitors) {
        MonitorBounds overlapping = findOverlapping(x, y, width, height, monitors);
        if (overlapping == null) {
            // 中央寄せに任せるが、サイズだけは先頭（プライマリ）モニタ基準で正しておく
            MonitorBounds primary = monitors.get(0);
            int w = clamp(width, MIN_WIDTH, primary.width());
            int h = clamp(height, MIN_HEIGHT, primary.height());
            return new Resolved(true, 0, 0, w, h);
        }
        int w = clamp(width, MIN_WIDTH, overlapping.width());
        int h = clamp(height, MIN_HEIGHT, overlapping.height());
        return new Resolved(false, x, y, w, h);
    }

    private static MonitorBounds findOverlapping(int x, int y, int width, int height, List<MonitorBounds> monitors) {
        for (MonitorBounds m : monitors) {
            if (overlaps(x, y, width, height, m)) return m;
        }
        return null;
    }

    // 矩形交差判定。境界が接するだけ（重なり面積0）は「重ならない」扱いにする
    private static boolean overlaps(int x, int y, int width, int height, MonitorBounds m) {
        return x < m.x() + m.width() && x + width > m.x()
            && y < m.y() + m.height() && y + height > m.y();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }
}
