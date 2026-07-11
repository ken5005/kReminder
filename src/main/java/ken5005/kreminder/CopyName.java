package ken5005.kreminder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 複製操作でコメント先頭に付与する "(copy)" マーカーの採番規則（GUI仕様v2 §2.5.3）。
 * Swing / java.io / Gson を import しない純関数クラス。
 */
public final class CopyName {

    // 先頭の "(copy)" または "(copyN)" のみにマッチ。大文字・空白入りは非マッチ扱い
    private static final Pattern COPY_PREFIX = Pattern.compile("^\\(copy(\\d+)?\\)");

    private CopyName() {
    }

    public static String nextCopyComment(String comment) {
        String s = comment == null ? "" : comment;
        if (s.isEmpty()) return "(copy)";

        Matcher m = COPY_PREFIX.matcher(s);
        if (!m.find()) {
            return "(copy)" + s;
        }
        int n = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
        String rest = s.substring(m.end());
        return "(copy" + (n + 1) + ")" + rest;
    }
}
