package ken5005.kreminder;

/**
 * テーブル列幅を Config の1キー（カンマ区切り文字列）で保持するための変換純関数（フェーズ4「き」）。
 * 列幅は位置（何番目か）が列そのものの意味を持つため、途中の1トークンだけを捨てて残りを
 * ずらして返すと「誤った列に誤った幅を当てる」事故になる。よってトークンが1つでも
 * 壊れていれば（数値でない・負数）全体を諦めて空配列を返す（部分的に信用できない列幅を
 * 適用するくらいなら、未設定のまま扱う方が安全という方針）。
 */
public final class ColumnWidthsCodec {

    private ColumnWidthsCodec() {
    }

    public static String format(int[] widths) {
        var sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(widths[i]);
        }
        return sb.toString();
    }

    public static int[] parse(String value) {
        if (value == null || value.isBlank()) return new int[0];

        String[] tokens = value.split(",", -1);
        int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            int width;
            try {
                width = Integer.parseInt(tokens[i].trim());
            } catch (NumberFormatException e) {
                return new int[0]; // 1つでも数値でなければ全体を諦める（位置ずれ事故を防ぐ）
            }
            if (width < 0) return new int[0]; // 負数も同様に全体を諦める
            result[i] = width;
        }
        return result;
    }
}
