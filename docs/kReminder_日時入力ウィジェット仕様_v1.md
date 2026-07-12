# kReminder 実行時刻の日時入力ウィジェット仕様書 v1.2（③-e）

最終更新: 2026-07-11（chat38 → Enter/Space の抜け方を改訂で v1.2 に更新）
GUI仕様 v2 §4.8 から分冊（本書が正典・v2 §4.8 はポインタ）。chat31 で仕様確定・chat32 で実装 ship 済み（feat/datetime-field・PR#13）。v1.1 で §3.2/§7 の「新規時デフォルト」を確定（新規/複製/削除スライス）。v1.2 で §3.4/§3.5/§6/§7 の Enter・Space の抜け方を改訂（feat/datetime-field-exit）。

---

## 1. 概要・狙い

編集ダイアログの実行時刻欄を、単一 JTextField から `[yyyy]-[MM]-[dd] [HH]:[mm]:[ss]` の6欄分割ウィジェットに置き換えたもの。

**狙い＝「開いたら即・数字の連打で、近い日時ほど少ない打鍵で入力できる」**。

chat30 まで想定していた桁送り電卓方式（旧 `ken.gui.text` 由来）は正式撤回し、この欄分割方式に全面設計変更した（chat31）。

---

## 2. クラス構成と公開API

| クラス | 種別 | 責務 |
|---|---|---|
| `ken5005.kreminder.DateTimeFieldLogic` | 純関数クラス | 状態遷移の全ロジック。record を受けて record を返す（JUnit5 表駆動 TDD＝一番バグの巣）。 |
| `ken5005.kreminder.DateField` | enum | 6欄（年月日時分秒）の識別子。欄幅（年4・他2）を保持。 |
| `ken5005.kreminder.DateTimeFieldState` | record | 6欄の値＋カーソル位置＋入力バッファ のイミュータブル状態。 |
| `ken5005.kreminder.gui.DateTimeField` | JPanel（Swing器） | 表示・キー/マウスイベントを Logic 呼び出しに変換するだけの薄皮。 |

**純関数コアの主なAPI**：`initial(LocalDateTime)` ／ `typeDigit(s, digit)` ／ `moveLeft(s)` `moveRight(s)` ／ `clickField(s, field)` ／ `pressEnter(s)` ／ `pressSpace(s)` ／ `deactivate(s)` ／ `stepUpDown(s, delta)` ／ `composeText(s)` ／ `fieldDisplayText(s, field)`。

**Swing器の公開API**：

```java
public DateTimeField()
public void setDateTime(LocalDateTime dt)   // 編集時の既存値セット
public String getExecTimeText()             // 常に yyyy-MM-dd HH:mm:ss を返す（→§5）
public boolean isEditing()                  // カーソル活性中か（OK活性の条件に使う）
public void addChangeListener(Runnable l)   // 値変化通知（旧 DocumentListener の代替）
```

---

## 3. 振る舞い仕様（確定・実装済み）

### 3.1 構成

6つの表示欄（年4桁・他2桁）＋区切り JLabel（`-` と `:`）。**欄は表示専用**（手打ちのキャレット編集はさせない）。Swing のフォーカス・キャレットとは別に自前の「カーソル」（アクティブ欄）を状態として持ち、背景色等で可視化する。

### 3.2 初期状態

編集時は既存値。**新規時は現在日時**（→§7・確定＝新規/複製/削除スライス）。ウィジェット自身は無改造で、呼び出し側（`MainWindow`）が `LocalDateTime.now(clock).withSecond(0).withNano(0)` を計算して `setDateTime()` に渡す。ダイアログを開いた時点でカーソルは「日」欄にあり活性。

### 3.3 打鍵入力

- 数字を打つと、その欄の旧値はクリアされ入力バッファが始まる（欄に入っただけでは何も起きない。最初の打鍵でクリア＝再進入リセット）。
- バッファは右詰め・未入力上位桁は空白表示（例: 2打鍵→`[ 2]`）。
- バッファが欄幅（年4・他2）に達したら値確定・カーソルは右隣へ自動送り。
- 秒欄（右端）で満了したらカーソル消滅（実質確定）。このとき Space / Tab と同じく次のコンポーネント（＝繰り返し欄）へフォーカスを送る（v1.2）。

### 3.4 カーソル移動

←→キーで隣へ移動。任意の欄クリックで直接指定。**Tab ではカーソルは点かない**。**欄を離れるとき（←→・クリック移動・閉店含む）未完バッファは上位桁ゼロ埋めで欄に確定**（例: 「2」→02）。

**Tab の扱い（v1.2 で明記・実装変更なし）**：ウィジェットは Tab を握らない。Swing 標準のフォーカストラバーサルで次のコンポーネント（＝繰り返し欄）へ移る。その際 `focusLost` で閉店処理（未完バッファのゼロ埋め確定＋カーソル消滅）が走る＝Enter と同じ後始末になる。

### 3.5 確定操作

- **Enter**（v1.2 改訂・chat38）＝現欄の未完バッファをゼロ埋め確定しカーソル消滅。**そのまま OK を発火して登録完了**（v1.1 までの「Enter 2回で登録完了」は廃止＝1回で完結する）。ただし OK が非活性（入力不正・§6）なら登録せず `SND.play("Oops")` で警告音を鳴らす。
  - **Enter の一元化**（v1.2・chat38）＝Enter は EditDialog の rootPane（`WHEN_IN_FOCUSED_WINDOW`）でも握り、**実行時刻欄以外（繰り返し・コメント・Cmd）で押しても同じ挙動**にする（OK 活性なら登録／非活性なら「Oops」）。※優先度コンボは Swing 側が Enter を消費するため対象外（従来どおり）。
- **Space**（v1.2 改訂・chat38）＝カーソル欄のバッファ状態で分岐（chat32 の目視で改訂した仕様、分岐自体は変更なし）：
  - **バッファ活性中**（打ちかけがある）→ 打ちかけをゼロ埋め確定して現欄の値は活かし、**下位の欄だけ**を最小値にして確定・カーソル消滅。
  - **バッファ非活性**（欄に居るがまだ何も打っていない＝再進入直後や自動送り直後）→ **現欄を含めて**最小値にして確定・カーソル消滅。
  - 最小値＝月/日は 1・時分秒は 0。
  - 改訂理由（分岐部分・chat32）：`23:45:55` を `1→2→Space` で `12:00:00` にしたい。時を打ち終えると分へ自動送り＝分は非活性なので、分自身も 0 にする必要がある（旧「カーソルより下位のみ最小値」だとクリア欄が1つ足りなかった）。
  - **確定後、フォーカスを次のコンポーネント（＝繰り返し欄）へ移す**（v1.2 追加）。`KeyboardFocusManager.focusNextComponent()` を使い、ウィジェットは遷移先が誰かを知らない。
- カーソル消滅後は欄クリックまで打鍵無効。
- **他のコンポーネント（コメント欄等）を触った瞬間もカーソル消滅**（＝閉店。未完バッファはゼロ埋め確定）。

### 3.6 ↑↓キー／マウスホイール

対象は常にカーソルのある欄。カーソル無活性時は無反応。

- まず未完バッファをゼロ埋め確定してから対象欄を ±1。
- 秒/分＝0-59、時＝0-23、月＝1-12 でリワインド。日＝1〜その年月の実末日（うるう年考慮）でリワインド。
- 月・年の変更で日が末日を超える場合は**末日にクランプ**（例: 1/31 で月↑→2/28。超えない場合は保持: 2/28 で月↑→3/28）。
- **年は ±1 のみ（リワインド無し）。年↓は 2000 で停止**（それ未満に下げられない）。
- **合成値が不正（`parseExecTime` が empty）の間は↑↓・ホイールとも無効**。

---

## 4. 値の合成と検証

- **ウィジェットは値を修正しない**（時25・月13 等の不正値もそのまま保持・表示する）＝原則1「サイレント誤動作が最悪」＝黙って直さない。
- `getExecTimeText()` は常に `yyyy-MM-dd HH:mm:ss`（バッファ活性中はゼロ埋め解釈で合成）を返す＝**既存 `EditFormLogic` 契約維持**。
- 不正値の検出・エラー表示は既存のライブプレビュー（「時刻入力エラー」）と OK 非活性に委ねる。

---

## 5. parseExecTime の年下限（コア側の追加）

`EditFormLogic.parseExecTime` に「**年 2000 未満は empty（エラー扱い）**」を追加した。

理由：年欄の途中 Enter（「26」→ 0026年）は日時として合法なのでプレビュー網に引っかからず、**サイレント登録されうる唯一の穴**だった。回帰テスト（`0026-07-24` → empty）を step1 で追加済み。

---

## 6. OKボタンとの関係

- **OK 活性 = `isTotallyValid` かつ カーソル無活性（`!dateTimeField.isEditing()`）**。カーソル活性中は OK は押せない（Enter または他項目タッチで閉店してから）。
- **開いた瞬間は OK 非活性で始まる**のは仕様の帰結だが、v1.2 で Enter が1回で登録完了するようになったため、「日時をいじらない時も Enter を一手入れる必要がある」という v1.1 までの割り切りは解消された（Enter 1回で閉店＋OK発火が同時に走る）。
- Esc はカーソル状態に関わらずダイアログキャンセル。Cancel/Esc/× は従来どおり一切書き戻さない（`MainWindow.onEditButton` が `!isOkPressed()` で早期 return・Cancel/Esc/× は `dispose()` のみ＝chat31 で実物裏取り済み）。

**EditDialog 統合（chat32 step3）**：execTime 欄の DocumentListener を `addChangeListener` に差し替え（**repeat 欄の DocumentListener は残す**）／OK 活性条件に `&& !dateTimeField.isEditing()` 追加／`getRootPane().setDefaultButton(okButton)` 新設。

**Enter 1回化（v1.2・chat38）**：`setDefaultButton(okButton)` は OK ボタンの見た目（太枠）のために残すが、Enter の実処理は rootPane の自前バインド（`onEnterPressed()`）が担う。デフォルトボタン機構への素通し（v1.1 の「2回目の Enter がデフォルトボタンに流れる」）はもう使わない。

---

## 7. 設計判断（実装経緯の要点）

- **`DateTimeFieldLogic` は clock-free**：now / Clock をコアに入れない（決定的でないと TDD が崩れる）。生 `now()` を埋め込まない。
- **`stepUpDown` は増減前に `isValid` を門番に置く**：`adjustDay/Month/Year` は妥当状態しか受けない＝「2月31日で日を弄る」類の事故が構造的に起きない。
- **「新規＝現在日時」デフォルトは、新規/複製/削除スライスで確定・実装した（v1.1）**。当初想定していた「`DateTimeField` にコンストラクタで `Clock` を注入する」案は採らなかった＝ウィジェット・`DateTimeFieldLogic` とも clock-free のまま無改造。代わりに呼び出し側（`MainWindow`）が `LocalDateTime.now(clock).withSecond(0).withNano(0)` を計算して `new Reminder()` の `fireAt` にセットし、`EditDialog` 経由で `execTimeField.setDateTime(...)` に渡す形にした＝コアの clock-free 原則（本節冒頭）はそのまま維持される。
- セパレータは `-` / `:`（`/` ではない）。chat32 目視での「/ のはず」は思い込みの見間違いで、変更なし。
- **Enter 1回化（v1.2・chat38）は「OK が押せない時に無反応だとユーザーが気づけない」問題を、SND の警告音（`"Oops"`）で解決した**。`DateTimeField` 自身は `SND` を import せず、Enter 押下を `addEnterListener(Runnable)` で `EditDialog` に通知するだけ＝ウィジェットは音を知らない（疎結合維持）。音を鳴らすか・OK を発火するかの判断は `EditDialog.onEnterPressed()` の責務。

---

## 8. 関連する既知タスク（本ウィジェット外・handoff C 管理）

- リスト画面キーバインド（Space/Enter=編集 等）は**別レイヤー**：DateTimeField が Enter/Space を握るのはダイアログ内の話（C#1 に統合）。
- Edit×発火ポップアップのモーダリティねじれ（C#4・独立PR）。
