package ken5005.kreminder;

/** ユーザーが後から手で調整したい値（フォントサイズ・色）だけを置く定数集約クラス。 */
public final class Const {

    private Const() {
    }

    public static final int FONT_SIZE_BUTTON = 14;         // ツールバーのボタン文字
    public static final int FONT_SIZE_FILTER = 14;         // フィルタのチェックボックス＋「検索」ラベル
    public static final int FONT_SIZE_SEARCH = 14;         // 検索入力欄
    public static final int FONT_SIZE_TABLE = 15;          // Reminderテーブルの本文＋ヘッダ
    public static final int FONT_SIZE_EDIT_LABEL = 14;     // 編集/instant画面の項目ラベル
    public static final int FONT_SIZE_EDIT_FIELD = 15;     // 編集/instant画面の入力欄（繰り返し/コメント/Cmd/優先度/instant欄）
    public static final int FONT_SIZE_DATETIME_FIELD = 18; // 日時入力ウィジェットの数字欄
    public static final int FONT_SIZE_DATETIME_SEP = 18;   // 日時入力ウィジェットの区切り記号

    public static final int POPUP_BG_RGB = 0xDDDDDD;       // 発火ポップアップの背景色
}
