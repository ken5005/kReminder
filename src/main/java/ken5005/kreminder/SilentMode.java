package ken5005.kreminder;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import ken5005.kreminder.debug.DEB;

/**
 * 消音モードのON/OFF状態を保持する静的ファサード。AppDir/DEBと同じ思想。
 *
 * マーカーファイル（{@code <base>/.silentmode}）は外部ツールが消音状態を見るためのもの。
 * 中身は空で、turnOn/turnOffに連動して作成・削除されるほか、プロセスの起動時と終了時にも
 * removeMarker()で残骸が消される。
 *
 * 全メソッドはEDTから呼ばれる前提で、同期は入れない（Mainのstatic状態と同じ流儀）。
 */
public final class SilentMode {

    private static final String MARKER_NAME = ".silentmode";

    private static boolean on;
    private static Runnable onChange;

    private SilentMode() {
    }

    public static boolean isOn() {
        return on;
    }

    /** 消音ONにしてマーカーを作成する。既にONなら何もしない。 */
    public static void turnOn() {
        if (on) {
            return;
        }
        on = true;
        try {
            Files.createFile(markerPath());
        } catch (FileAlreadyExistsException e) {
            // 前回の残骸等で既にある場合は何もしない
        } catch (IOException e) {
            DEB.pr(e);
        }
        fireOnChange();
    }

    /** 消音OFFにしてマーカーを削除する。既にOFFなら何もしない。 */
    public static void turnOff() {
        if (!on) {
            return;
        }
        on = false;
        try {
            Files.deleteIfExists(markerPath());
        } catch (IOException e) {
            DEB.pr(e);
        }
        fireOnChange();
    }

    /** 状態には関係なくマーカーファイルだけを消す。起動時の残骸掃除・終了時に使う。 */
    public static void removeMarker() {
        try {
            Files.deleteIfExists(markerPath());
        } catch (IOException e) {
            DEB.pr(e);
        }
    }

    /** turnOn/turnOffの最後に呼ぶコールバックを1本だけ登録する。 */
    public static void setOnChange(Runnable listener) {
        onChange = listener;
    }

    private static void fireOnChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    private static Path markerPath() {
        return AppDir.resolve(MARKER_NAME);
    }
}
