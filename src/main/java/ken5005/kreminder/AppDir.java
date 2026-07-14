package ken5005.kreminder;

import java.nio.file.Path;

/**
 * アプリが使う全データの置き場所（ベースフォルダ）を保持する静的ファサード。DEB/SNDと同じ思想。
 *
 * init()はmain()の一手目で1回だけ呼ぶこと。テストの@BeforeEachでtempディレクトリを
 * 入れ直す用途以外で再呼び出ししてはならない。
 */
public final class AppDir {

    private static volatile Path base;

    private AppDir() {}

    /** 受け取ったPathを絶対パス化・正規化して保持する（CWD依存を起動時に固定する）。 */
    public static void init(Path baseDir) {
        base = baseDir.toAbsolutePath().normalize();
    }

    public static Path base() {
        Path b = base;
        if (b == null) {
            throw new IllegalStateException("AppDir.init()が呼ばれていません");
        }
        return b;
    }

    public static Path resolve(String name) {
        return base().resolve(name);
    }
}
