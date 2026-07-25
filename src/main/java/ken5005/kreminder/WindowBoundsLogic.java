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
     * 重なる場合は、その画面の表示領域を基準に幅・高さを最小値〜画面サイズの範囲へクランプし、
     * さらに y がその画面の上端より上にはみ出していれば上端まで持ち上げる（タイトルバーが
     * 画面上端より上に出てマウスで掴めなくなる事故を防ぐ。x方向はクランプ/救済の対象外）。
     */
    public static Resolved resolve(int x, int y, int width, int height, List<MonitorBounds> monitors) {
        MonitorBounds overlapping = findOverlapping(x, y, width, height, monitors);
        if (overlapping == null) {
            if (monitors.isEmpty()) {
                // 上限の根拠になるモニタが無いため、最小値と保存値の大きい方をそのまま使う
                int w = Math.max(MIN_WIDTH, width);
                int h = Math.max(MIN_HEIGHT, height);
                return new Resolved(true, 0, 0, w, h);
            }
            // 中央寄せに任せるが、サイズだけは先頭（プライマリ）モニタ基準で正しておく
            MonitorBounds primary = monitors.get(0);
            int w = clamp(width, MIN_WIDTH, primary.width());
            int h = clamp(height, MIN_HEIGHT, primary.height());
            return new Resolved(true, 0, 0, w, h);
        }
        int w = clamp(width, MIN_WIDTH, overlapping.width());
        int h = clamp(height, MIN_HEIGHT, overlapping.height());
        // 比較対象は0ではなくそのモニタの上端(m.y())。上に並んだモニタはy自体が負になりうるため
        int adjustedY = y < overlapping.y() ? overlapping.y() : y;
        return new Resolved(false, x, adjustedY, w, h);
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

    /** resolveDialogSize() の結果。ダイアログは画面中央に出す設計のため位置は扱わない。 */
    public record DialogSize(int width, int height) {}

    /**
     * 編集ダイアログのサイズを決める（フェーズ4「き」step3）。メインウィンドウのresolve()とは
     * 下限の考え方が異なるため別メソッドにしている：メインウィンドウの MIN_WIDTH/MIN_HEIGHT は
     * 固定の最小値だが、ダイアログの下限は「そのダイアログ自身がpack()で必要とした寸法
     * (packedWidth/packedHeight)」であるべき＝それより縮めると中の欄が潰れる。
     * savedWidth/savedHeightがConfig.UNSET(-1、一度も保存されていない)ならpackedをそのまま返す。
     * 上限はmonitorsの先頭（プライマリ。ダイアログは中央寄せなのでこれで足りる）の表示領域。
     * monitorsが空なら上限を課さない。上限が下限(packed)を下回る矛盾が起きた場合は下限を優先する
     * （潰さない方を優先＝ユーザー入力欄が見えなくなる事故を避ける）。
     */
    public static DialogSize resolveDialogSize(int savedWidth, int savedHeight,
                                                int packedWidth, int packedHeight,
                                                List<MonitorBounds> monitors) {
        if (savedWidth == Config.UNSET || savedHeight == Config.UNSET) {
            return new DialogSize(packedWidth, packedHeight);
        }

        // 下限はpacked。保存値がそれより小さければpackedまで広げる
        int width = Math.max(packedWidth, savedWidth);
        int height = Math.max(packedHeight, savedHeight);

        if (!monitors.isEmpty()) {
            MonitorBounds primary = monitors.get(0);
            // 上限はプライマリモニタの表示領域。Math.max(packed, ...)により、上限が下限を
            // 下回る矛盾時はpacked（下限）の方が採用される
            width = Math.min(width, Math.max(packedWidth, primary.width()));
            height = Math.min(height, Math.max(packedHeight, primary.height()));
        }

        return new DialogSize(width, height);
    }
}
