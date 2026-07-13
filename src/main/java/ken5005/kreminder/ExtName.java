package ken5005.kreminder;

/**
 * Extend（＝スヌーズ）でコメント先頭に付与する "(Ext) " マーカーの規則（GUI仕様v2 §5.3/5.4）。
 * Swing / java.io / Gson を import しない純関数クラス。
 */
public final class ExtName {

    // 末尾の半角スペース込みで1つのマーカー。厳格な完全一致判定（CopyName と同じ流儀）
    private static final String EXT_PREFIX = "(Ext) ";

    private ExtName() {
    }

    public static String withExtPrefix(String comment) {
        String s = comment == null ? "" : comment;
        if (hasExtPrefix(s)) return s;
        return EXT_PREFIX + s;
    }

    public static boolean hasExtPrefix(String comment) {
        return comment != null && comment.startsWith(EXT_PREFIX);
    }
}
