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
    public static final int POPUP_FLASH_RGB = 0xFF8080;    // 発火フラッシュの色（薄い赤）。手で調整可
    public static final int POPUP_FLASH_FADE_MS = 1000;    // 赤→通常背景へ戻すフェード時間(ms)

    // 消音中ダイアログを画面中央からどれだけ右上にずらすか（発火ポップアップ1枚目＝中央と重ならないため）
    public static final int SILENT_DIALOG_OFFSET_X = 160;  // 右方向・px
    public static final int SILENT_DIALOG_OFFSET_Y = 120;  // 上方向・px（値は正で持ち、使う側で引く）

    public static final int SILENT_DIALOG_BG_RGB = 0x00CC00; // 消音中ダイアログの背景色（緑）。手で調整可
}
