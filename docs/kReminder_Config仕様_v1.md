# kReminder Config仕様 v1

`config.properties` と `Config` クラスの正典。初版作成 2026-07-26（チャット57）。
実装は `v0.9.0` 以降の main HEAD（フェーズ4「き」＝PR#33 まで）に対して実ソース照合済み。

---

## §1 位置づけと守備範囲

### 1.1 この冊が正典であるもの

- `config.properties` の**存在する全キー**（キー名・型・既定値・誰がいつ書くか）
- `Config` クラスの**機構**（load/save の契約、防御的パース、`UNSET` 番兵、インスタンスの数）
- **ウィンドウ状態の永続化**（保存対象・最大化ガード・復元アルゴリズム・関連する純関数3クラス）

### 1.2 この冊が正典でないもの（他冊を見る）

| 事項 | 正典 |
|---|---|
| `filter.*` 6トグルの**意味**（バケツ判定・リードタイム・可視判定の順序） | `kReminder_GUI仕様_v2.md` §3 |
| `snd.wav.dir` で指すフォルダの**使われ方**（wav 走査・sound-map・音声名の解決） | `kReminder_SND仕様_v2.md` |
| ベースフォルダ `<base>` の決まり方（`--base` 必須・`AppDir`） | `kReminder_デバッグ起動オプション仕様_v1.md` |
| クラスの所在・依存の向き | `docs/kReminder_コード地図_v1.md` |

**棲み分けの原則**：「そのキーが**存在すること・型・既定値**」は本冊が持ち、「そのキーの値が**何を引き起こすか**」は各機能の正典が持つ。

### 1.3 なぜキーの網羅リストが仕様なのか

`Config.save()` は毎回まっさらな `Properties` を作り直し、**`Config` が知っているキーだけ**を書き出す（→ §4.2）。したがって「`Config` が知っているキーの集合」＝「`config.properties` に存続できるキーの集合」であり、**この一覧そのものが仕様の一部**になる。リストに無いキーは手で書いても次の保存で消える。

---

## §2 ファイルと配置

### 2.1 場所

`<base>/config.properties`。`<base>` は起動時の `--base` で決まるベースフォルダで、`AppDir.base()` 経由で解決する（`Config.configFilePath()` が唯一の入口）。

`Config` は `sound-map.properties` のパス計算も持つ（`getSoundMapPath()`＝同じベースフォルダを指すだけ）。sound-map の中身は SND 仕様の管轄。

### 2.2 形式

`java.util.Properties`（`props.store` / `props.load`）。ここから来る性質：

- `#` で始まる行はコメント。値の中の `:` `=` `\` はエスケープされる（例：`snd.wav.dir=C\:\\tools2\\etc\\wav`）。これは load と対称なので壊れない
- **`store()` は並び順を保存しない**（実質ハッシュ順）。**コメント行も保存しない**。よって保存のたびに人が付けたコメントと並びは失われる
- ファイル先頭に日時のコメント行が1行入る（`props.store(out, null)` の仕様）

### 2.3 誰が書くか

**アプリが書く**。よって git では追跡しない（`data/` は丸ごと ignore、`testdata/` は種だけ追跡）。種は `testdata/config.properties.example`。

この「誰が書くか」の判断基準は handoff A3-2 のデータ配置原則（git が持つべきは仕様の一部、持つべきでないのは実行時に育つ状態）に従う。

### 2.4 手編集について

手編集は**できる**が、次に §4.5 のいずれかの契機で `save()` が走った時点で以下が起きる：

- `Config` が知らないキー → **消える**
- コメント行・並び順 → **消える**
- `Config` が知っているキー → アプリがメモリ上に持っている値で上書き

したがって手編集が意味を持つのは「アプリが書き換えないキー」だけ。現状それに該当するのは `debug.enabled`（setter が無い＝読むだけ）と `snd.wav.dir`（キーが在れば書き戻さない）。

---

## §3 キー一覧

**全キー表**（この表に無いキーは `config.properties` に存続できない）：

| キー | 型 | 既定値 | 書き込む契機 | 意味の正典 |
|---|---|---|---|---|
| `filter.showEnded` | boolean | `false` | フィルタ操作 | GUI仕様v2 §3 |
| `filter.showImminent` | boolean | `true` | フィルタ操作 | GUI仕様v2 §3 |
| `filter.showSoon` | boolean | `true` | フィルタ操作 | GUI仕様v2 §3 |
| `filter.showFar` | boolean | `false` | フィルタ操作 | GUI仕様v2 §3 |
| `filter.showLowPriority` | boolean | `true` | フィルタ操作 | GUI仕様v2 §3 |
| `filter.showAllRepeat` | boolean | `false` | フィルタ操作 | GUI仕様v2 §3 |
| `snd.wav.dir` | String（パス） | `C:\tools2\etc\wav` | キー欠け時の書き戻しのみ | SND仕様 |
| `window.main.x` | int | `-1`（UNSET） | 終了時 | 本冊 §5 |
| `window.main.y` | int | `-1`（UNSET） | 終了時 | 本冊 §5 |
| `window.main.width` | int | `800` | 終了時 | 本冊 §5 |
| `window.main.height` | int | `500` | 終了時 | 本冊 §5 |
| `window.main.dividerRatio` | double | `-1.0`（UNSET由来） | 終了時 | 本冊 §5.5 |
| `table.columnWidths` | String（カンマ区切り） | `""`（空文字） | 終了時 | 本冊 §5.6 |
| `window.edit.width` | int | `-1`（UNSET） | 編集ダイアログを閉じた時 | 本冊 §5.7 |
| `window.edit.height` | int | `-1`（UNSET） | 編集ダイアログを閉じた時 | 本冊 §5.7 |
| `window.instant.width` | int | `-1`（UNSET） | instant ダイアログを閉じた時 | 本冊 §5.7 |
| `window.instant.height` | int | `-1`（UNSET） | instant ダイアログを閉じた時 | 本冊 §5.7 |
| `debug.enabled` | boolean | `false` | **書き込まない**（read-only 運用） | 本冊 §3.4 |

### 3.1 `filter.*`（6キー）

`FilterState`（record）は7つの boolean を持つが、**永続化するのは6つ**。`showAll` は UI 未実装（将来拡張の器）で常に false のため対象外。**検索文字列も永続化しない**（一時的な絞り込みであり、次回起動時に前回の検索語が残っていると混乱するため）。

書き込みは `MainWindow.saveFilterState()` ＝チェックボックスを1つ操作するたびに `config.save()` まで走る。

### 3.2 `snd.wav.dir`

既定値 `C:\tools2\etc\wav` はコード内の定数（`DEFAULT_WAV_DIR`）。**本人のマシン固有のパスがハードコードされている**が、他マシンでは「フォルダが存在しない → SND を init せずスキップ」という graceful な縮退になるので破綻はしない。

**このキーだけ「キーが無ければデフォルト値を書き戻して保存する」**という特別扱いがある（→ §4.5(1)）。理由は、設定ファイルを見た人に「ここを変えれば wav の場所を変えられる」と分かる形にしておくため（設定ファイルが自己説明的であることを優先）。キーが在る場合は値をそのまま採用し、書き戻さない。

### 3.3 `window.*` / `table.columnWidths`

§5 で詳述。

### 3.4 `debug.enabled`

**getter だけがあり setter が無い**＝アプリは読むだけで書き換えない。手編集で切り替える運用を想定した唯一のキー。`save()` では他のキーと同様に書き出されるので、`Config` が知っているキーとして消えずに残る（§4.2 の性質に対する対策として、あえて `Config` に知らせてある）。

値は `Boolean.parseBoolean` で解釈されるため、`true` 以外の文字列（`abc` など）はすべて `false` になる＝壊れた値でも例外にならない。

現状この値を読む側の配線は無い（将来の器）。

---

## §4 `Config` クラスの機構

### 4.1 `load()` の契約

1. **ファイルが存在しなければ `save()` を呼んでデフォルト値で実体化し、return する**（初回起動時に `config.properties` を生えさせるため）。この動作は chat52・C#1 で入った。旧版は何もせず return していたため、`sound-map.properties`（不在時に雛形を吐く）と非対称だった
2. 読み込み時の `IOException` は `System.err` に1行出して return（＝全キーがデフォルト値のまま）。**本体は落とさない**（`ReminderStore` と同じ方針）
3. **キーが欠けていれば、そのキーだけデフォルト値を維持**する
4. 値が壊れていて型として読めなければ、そのキーだけデフォルト値を維持する（→ §4.3）

### 4.2 `save()` の契約 ★

**毎回まっさらな `Properties` を作り直し、`Config` が知っているキーだけを `setProperty` して書き出す。** 元ファイルを読み直してマージすることは**しない**。

帰結（§2.4・§1.3 で述べたもの）：

- `Config` が知らないキーは次の保存で消える
- コメントと並び順も消える
- 書き出し先の親フォルダが無ければ `createDirectories` で作る
- 書き込み時の `IOException` は `System.err` に1行出すだけ（本体は落とさない）

### 4.3 防御的パース

| ヘルパ | 対象 | 挙動 |
|---|---|---|
| `parseBool` | boolean 4種 | キー欠け（`getProperty()==null`）なら既定値。**null チェックを先に行う**のが必須＝`Boolean.parseBoolean(null)` は `false` を返すので、これを通すと既定値 `true` のキーを潰す |
| `parseInt` | int 系 | キー欠け・`NumberFormatException` の両方で既定値。小数（`1.5`）も int として読めないので既定値に落ちる |
| `parseDouble` | `window.main.dividerRatio` | 同上。**範囲（0.0〜1.0 等）の妥当性は見ない**＝数値として読めるかどうかだけ（→ §4.7） |

**`table.columnWidths` だけはヘルパを通さず `props.getProperty(key, default)` で素の文字列として読む。** 型が String なので `Config` の段階では検証しようがなく、妥当性の判断は利用時に `ColumnWidthsCodec.parse` が行う（→ §5.6）。責務分担としては §4.7 と同じ形。

### 4.4 `UNSET = -1`

「一度も保存されていない」を表す番兵。`public static final int` として公開されており、`MainWindow` と `WindowBoundsLogic` が同じ値を参照する（`public static final int` はコンパイル時定数なので実行時の結合は生じない）。

`UNSET` を既定値に持つのは `window.main.x` / `window.main.y` / `window.edit.*` / `window.instant.*` と、`window.main.dividerRatio`（`-1` を double に広げた `-1.0`）。

**`window.main.width` / `height` は `UNSET` ではなく実寸の既定値 `800` / `500`** を持つ。ウィンドウのサイズには「未設定なら pack に任せる」に相当する自然な代替が無く、常に具体的な数値が要るため。この2つが `MainWindow` にリテラルで書かれていた `setSize(800, 500)` を吸収しており、**サイズの既定値の置き場は `Config` 1箇所**である（二重管理を解消済み）。

`dividerRatio` の `-1.0` は「有効範囲 0.0〜1.0 の外側」なので、範囲チェックする側（`MainWindow`）が**未設定と壊れた値を区別せずに同じ扱いで既定へ倒せる**。これは意図した設計。

### 4.5 `save()` が走る契機（全5つ）

0. `load()` 時にファイルが存在しなかった（実体化）
1. `load()` 時に `snd.wav.dir` のキーが欠けていた（デフォルト値の書き戻し）
2. フィルタのチェックボックス操作 → `MainWindow.saveFilterState()`
3. 編集／instant ダイアログを閉じた → `MainWindow.showEditDialog()` の末尾（→ §5.7）
4. アプリ終了 → `Main.shutdownApp()` → `MainWindow.saveWindowState()`（**最大化中は早期 return するので走らない**・→ §5.2）

### 4.6 `Config` インスタンスは2つある ★

- **`MainWindow` が持つインスタンス**（`private final Config config = new Config();`）＝フィルタ状態とウィンドウ状態を担当。書き込みはすべてこちら
- **`Main` が SND 初期化用に作るインスタンス**（`new Config()` → `load()` → `initSound(config)`）＝`snd.wav.dir` と `getSoundMapPath()` を読むだけの一度きりの用途

**依存している不変条件**：

- Main 側のインスタンスは load 後に setter を一切呼ばず、`initSound` を最後に以後使われない。よって MainWindow 側の書き込みと競合しない
- ただし **Main 側の `load()` からも `save()` が走りうる**（§4.5 の (0) と (1)）。そのとき書き出されるのは「たった今 load で読んだ値」なので、ファイルの内容は実質変わらず破壊しない
- Main 側の `load()` は `MainWindow` 生成より**後**に走る（`window = new MainWindow(...)` → `DEB.init(...)` → `new Config(); load(); initSound(...)` の順）

**将来この前提が崩れる条件**：Main 側で設定を書き換える必要が出たとき。そのときはインスタンスを共有するか、書き込み口を `MainWindow` 側へ一本化すること。**「Config は状態を持つインスタンスであり、複数作ると最後に save した方が勝つ」**という点だけ忘れなければよい。

### 4.7 責務分担 — 型は `Config`、範囲は利用側

**`Config` は「その型として読めるか」だけを見る。値が業務的に妥当かは見ない。**

代表例が `window.main.dividerRatio`：有効範囲 0.0〜1.0 の判定は `MainWindow` 側で行う。理由は、`DEFAULT_DEBUG_DIVIDER_RATIO` という **GUI 固有の既定値を知っているのが `MainWindow` だから**。両方で見ると二重チェックになり、既定値の定義も二箇所に散る。

この契約はテストで固定してある（→ §7）。`Config` に 5.0 を読ませても弾かれずそのまま返ることを確認するテストがあり、「`Config` が弾かないのは仕様である」ことを将来の読み手に示している。

---

## §5 ウィンドウ状態の永続化

フェーズ4「き」（chat55・PR#33）で実装。

### 5.1 保存する／しない

**保存する**：

- `MainWindow` の位置とサイズ
- テーブルの列幅（絶対値）
- DEB パネルの分割位置（**絶対 px ではなく比率**）
- 編集ダイアログのサイズ（`Mode` 別に2組＝通常用と instant 用）

**保存しない（理由つき）**：

| 項目 | 理由 |
|---|---|
| 編集ダイアログの位置 | 毎回スクリーン中央に出す。当初は「親の中央」を推奨したが、本人判断で中央固定 |
| 発火ポップアップの位置・サイズ | 位置は Main 側の階段ロジックが決める（GUI仕様v2 §5）。永続化する意味が無い |
| 列の並び順 | 幅だけ。並び替えは現状 UI から行えない |
| 検索文字列 | §3.1 と同じ理由 |

### 5.2 ★最大化中は一切保存しない

`saveWindowState()` の**先頭 1 箇所**にガードを置き、最大化中は `Config` に一切触れず前回値を残す。

```java
boolean maximized = (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
if (maximized) return;
```

**位置・サイズだけでなく列幅と分割比率も含めて全項目**を守る。理由は、列幅も分割位置も**最大化時の窓サイズから導かれるピクセル値**であり、ユーザーが選んだ寸法ではないため。条件分岐を1箇所に集約する形にしてあるので、保存項目が増えても自動的に守られる。

**このガードが無かったときに実際に起きた不具合**（chat55 で目視により発覚）：

- **分割位置**：展開時は `setDividerLocation(絶対px)` を呼んでいたため、最大化時に保存された大きな値（例 900）を通常サイズの splitPane（高さ約 400）へ渡すと `JSplitPane` が内部で最大値までクランプし、**下端に貼り付いて動かなくなる**。ボタンを押すたび同じ値を渡すので何度押しても直らない
- **列幅**：最大化時の幅の合計が通常サイズのビューポートに収まらず再配分される筋だが、**再配分の詳細な挙動までは追い切っていない**（分割位置ほど確定的には説明できていない）

修正は2階建てで、(1) ガードを全項目へ拡張（必須）、(2) 分割位置を比率化（→ §5.5）。(2) は最大化と無関係な場面（`WindowBoundsLogic` が窓を縮めた場合など）での再発も塞ぐ。

### 5.3 保存タイミング

- **終了時に1回**：`Main.shutdownApp()` の冒頭で `if (window != null) window.saveWindowState();`。`shutdownApp()` はトレイ Exit・窓 close・stop.request 検知の3経路を集約しているので、ここ1箇所で足りる（→ 単一プロセスロック仕様）。**null チェックは必須**＝`MainWindow` 生成前に終了する経路（起動直後のロック競合で中止など）がありうる
- **ダイアログのサイズだけは閉じた時点で即 `config.save()`**。終了時の `saveWindowState()` に相乗りしないのは、**そちらが最大化中に早期 return するため**。相乗りすると「メインウィンドウを最大化したまま終了した場合、ダイアログのサイズだけ永久に保存されない」ことになる。ダイアログのサイズはメインウィンドウの最大化状態とは無関係な値なので、**ガードの外**に置く

リサイズのたびの保存（debounce つき）は検討したが、割に合わないので採らなかった。

**既知の副作用**：ダイアログを閉じた時点の `config.save()` は `window.main.*` を「起動時に読んだ値」のまま書き出す（ウィンドウを動かしてもその時点では `Config` に未反映のため）。ただし終了時の `saveWindowState()` が正しい値で上書きするので最終結果には影響しない。

### 5.4 復元 — `WindowBoundsLogic.resolve()` の契約

まず `MainWindow.applyWindowBounds()` が入口の分岐を持つ：

- **`window.main.x` か `y` のどちらかが `UNSET`**（＝一度も保存されていない）→ サイズだけ `Config` の値を使い、位置は従来どおり `setLocationRelativeTo(null)` で画面中央
- そうでなければ `WindowBoundsLogic.resolve()` に通す

`resolve(x, y, width, height, monitors)` → `Resolved(boolean centered, int x, int y, int width, int height)`：

- **`centered=true` のとき x/y は無意味な値**（0,0 が入る）。呼び出し側が `setLocationRelativeTo(null)` で中央寄せする契約。サイズは `centered` に関わらず使う
- 最小サイズ `MIN_WIDTH=400` / `MIN_HEIGHT=300`
- **矩形交差判定 `overlaps` は境界が接するだけ（重なり面積 0）を「重ならない」扱いにする**（厳密不等号）。窓の左端がモニタの右端にぴったり接している状態は「画面外」と判定される
- **どのモニタとも重ならない場合**（モニタ構成が変わって画面外に消えている）：`centered=true`。サイズは先頭（プライマリ）モニタを基準にクランプ
- **`monitors` が空リストの場合**：`centered=true`、サイズは**最小値と保存値の大きい方**（上限の根拠になるモニタが無いため上限を課さない）。ヘッドレス環境などで `get(0)` が `IndexOutOfBoundsException` になる穴を塞いだもの
- **重なるモニタがある場合**：そのモニタの表示領域でサイズをクランプし、位置は原則そのまま。ただし上端救済（下記）が入る

**★上端はみ出し救済 — 「y が負なら 0」は間違い**

タイトルバーだけが画面上端より上にはみ出した状態（y が負で本体は画面内）が復元されると、マウスで窓を掴めなくなる。そこで持ち上げるのだが、**比較対象は 0 ではなく重なったモニタの上端 `m.y()`**：

```java
// 比較対象は0ではなくそのモニタの上端(m.y())。上に並んだモニタはy自体が負になりうるため
int adjustedY = y < overlapping.y() ? overlapping.y() : y;
```

プライマリの**真上**にモニタを置いている構成ではそのモニタの y が負（例 `MonitorBounds(0, -1080, 1920, 1080)`）になり、`y = -500` は完全に正しい画面内の位置である。0 に持ち上げると別のモニタへワープする。シングルモニタなら `m.y() == 0` なので結果は同じ。

**x（左右方向）は救済の対象外。** 左右は上端ほど致命的ではない（タイトルバーの一部が残る）ため。

`GraphicsEnvironment` への実問い合わせは `MainWindow.currentMonitorBounds()` の責務で、純関数側（`WindowBoundsLogic`）は値だけを見る。この分離のために `MonitorBounds`（record）がある。

### 5.5 DEB パネルの分割は比率で持つ

キーは `window.main.dividerRatio`（値 0.0〜1.0）。**絶対 px ではなく比率**である理由：

- §5.2 の貼り付き不具合が、最大化以外の経路（窓が縮められた場合など）でも起きうる
- **コード的にむしろ素直になった**。既存コードには元々 `DEFAULT_DEBUG_DIVIDER_RATIO` があり「保存値が無ければ高さ×比率」という計算をしていた＝絶対 px と比率の2概念が同居していた。比率へ一本化してフィールドが1つ減った

キー名を `window.main.divider` から `dividerRatio` に変えたのは、値が比率であることを名前で示すため。旧キーは `Config` が知らないキーになるので次回保存で自然に消え、残っていた大きな整数値も範囲外として無効判定される。

**★NaN は肯定形でしか弾けない**

`Double.parseDouble` は `"NaN"` や `"Infinity"` を**例外なく受け付ける**ため、手編集で実際に到達しうる。NaN との比較は `==` を含めてすべて false になるので：

- `ratio >= 0.0 && ratio <= 1.0` で**有効判定**（肯定形）→ NaN は両方 false → 既定値に落ちる ✅
- `ratio < 0.0 || ratio > 1.0` で**無効判定**（否定形）→ NaN は両方 false → **有効と誤判定** ❌

すり抜けると `setDividerLocation((int)(height * NaN))` が 0 になる。**範囲チェックは必ず肯定形（有効な範囲を書く形）で書くこと。**

実装は肯定形で書かれており、条件を通らなければフィールド初期化子の `DEFAULT_DEBUG_DIVIDER_RATIO` が残る（フィールド初期化子はコンストラクタ本体より先に走るので順序も問題ない）。

**★保存時の分岐**：畳まれている間は `splitPane.getDividerLocation()` が「畳んだ位置（下段高さ 0）」を指すため、その値から比率を求めてはいけない。`saveWindowState()` は `debugPanelOpen && height > 0` のときだけ実測から比率を求め、それ以外は**展開時の比率を保持している `savedDividerRatio` をそのまま書く**。

**既知の穴（放置で合意済み）**：有効範囲が 0.0〜1.0 なので**両端も有効値として通る**。divider をマウスで一番下までドラッグした状態で終了すると比率 ≒ 1.0 が保存され、次回 debug ボタンを押しても下端に貼り付く（§5.2 で直したのと同じ症状が別ルートで再現する）。上端（≒0.0）も同様。有効範囲を 0.05〜0.95 に狭める案は「ユーザーが意図してそこに置いたのに勝手に戻される」とも言えるため採らなかった。

### 5.6 列幅 — `ColumnWidthsCodec`

`table.columnWidths` は1キーにカンマ区切りで持つ（`40,120,80` の形）。`format(int[])` ↔ `parse(String)` の純関数。

**★1トークンでも壊れていれば全体を諦めて空配列を返す。**

理由が load-bearing：**列幅は位置（何番目か）が列そのものの意味を持つ**ため、途中の1トークンだけを捨てて残りをずらして返すと「誤った列に誤った幅を当てる」事故になる。部分的に信用できない列幅を適用するくらいなら、未設定のまま扱う方が安全。

- `null` / 空文字 / 空白のみ → 空配列
- 数値でないトークン → 全体を諦める
- **負数** → 全体を諦める
- **空トークン（カンマの連続 `40,,80`）** → 全体を諦める
- **`0` は有効な幅**として扱う
- トークン前後の空白は `trim` で無視

**列数が食い違った場合**：保存値の個数と現在の列数のうち**少ない方**だけ反映する（例外にしない）。列構成を変更した後も起動できる。

適用は `applyColumnWidths()` で、`buildSplitPane()` を add した**後**に呼ぶ（列が揃ってから）。設定するのは `setPreferredWidth`。

### 5.7 編集ダイアログのサイズ

**`EditDialog.java` は無改造。** 位置決めもサイズ決めも呼び出し側（`MainWindow`）でやれる。

`Mode`（`NORMAL` / `INSTANT`）別に2組のキーを持つ＝通常の編集と instant で別々のサイズを覚える。

**★下限に `MIN_WIDTH`（400）を使ってはいけない。** あれはメインウィンドウ用の固定値であり、`pack()` で 400 未満に収まる instant ダイアログが不自然に横へ広げられる。**ダイアログの下限はその窓自身の `pack()` 結果**であるべき（chat53 で踏んだ「JScrollPane の欄が 7px に潰れる」の同類を防ぐ意味でも）。この違いのために `resolve()` とは別メソッド `resolveDialogSize()` を立ててある。

`resolveDialogSize(savedW, savedH, packedW, packedH, monitors)` → `DialogSize(width, height)`：

- `savedWidth` **または** `savedHeight` が `Config.UNSET` なら packed をそのまま返す（一度も保存されていない＝pack の結果を尊重）
- 下限は packed（`Math.max`）
- 上限は monitors の先頭（プライマリ。ダイアログは中央寄せなのでこれで足りる）。**空リストなら上限を課さない**
- 上限が下限を下回る矛盾時は**下限を優先**（潰さない方を優先＝入力欄が見えなくなる事故を避ける）

**★処理順が load-bearing**（`MainWindow.showEditDialog()`）：

1. ダイアログ生成（コンストラクタ内で `pack()` 済み＝この時点のサイズが packed）
2. `getWidth()` / `getHeight()` で packed を取得
3. `Config` から `Mode` 別に保存サイズを読む
4. `resolveDialogSize` → `setSize`
5. **その後で** `setLocationRelativeTo(null)`。**必ず `setSize` の後**（先に呼ぶと変更前のサイズを基準に中央位置が計算されてずれる）。引数は `this` ではなく `null`（親の中央ではなくスクリーン中央）
6. `setVisible(true)`（モーダルなので閉じるまで待つ）
7. 戻ったら `getSize()` を `Mode` 別キーで `Config` にセットし、**その場で `config.save()`**（理由は §5.3）

`alwaysOnTop` はこのヘルパ内で扱う（⑤ Extend 経由の呼び出しが true を渡す）。

---

## §6 クラス構成

所在と依存の向きは `docs/kReminder_コード地図_v1.md` §3.1（ルートパッケージのクラス表）／§3.2 が正典。本冊に関わるクラスだけ挙げると：

| クラス | 位置 | 性格 |
|---|---|---|
| `Config` | root | 状態を持つ。ファイル I/O あり。`AppDir` に依存 |
| `MonitorBounds` | root | record。モニタ1台分の表示領域を運ぶだけの値 |
| `WindowBoundsLogic` | root | 純関数のみ（final・private コンストラクタ）。Swing/AWT 非依存。`Config.UNSET` を参照 |
| `ColumnWidthsCodec` | root | 純関数のみ。依存なし |
| `MainWindow` | gui | AWT への問い合わせと Swing 配線の責務。`Config` を1つ所有 |

`WindowBoundsLogic` → `Config` の依存が1本あるのは `Config.UNSET` を参照するため。`public static final int` はコンパイル時定数なので実行時の結合は生じない。

---

## §7 テストで固定してある契約

テストの実体が正。ここは「何を固定してあるか」の索引。

**`ConfigTest`**：

- 非デフォルト値の保存→読み込みの往復（フィルタ系・ウィンドウ状態系で別メソッド）
- **ファイル不在なら実体化しつつデフォルトを保つ**（§4.1）
- **キー欠けはそのキーだけデフォルト維持**（§4.1）
- **壊れた値（`abc` / 空文字 / 小数）はデフォルトへフォールバック**。`debug.enabled=abc` だけは例外にならず単に false になる旨もテスト内コメントで明示
- **`window.main.dividerRatio=5.0` が `Config` に弾かれずそのまま返る**（§4.7 の責務分担を固定するためのテスト。「弾かないのが仕様」を将来の読み手に示す）

**`WindowBoundsLogicTest`**（表駆動・シングル／デュアル／プライマリ真上の3構成を用意）：

- 画面内はそのまま／完全に画面外なら中央指示／最小値まで広げる／表示領域まで縮める
- 複数モニタで重なった方を基準にする／どれとも重ならなければ中央指示
- **モニタが空リストでも落ちない**
- **シングルモニタで y が負なら上端 0 まで持ち上がる**
- **上に配置されたモニタ（y が負）と重なる場合、その負の y は保たれる**（0 にワープさせない）
- **境界が接するだけなら重ならない扱い**
- `resolveDialogSize` 側：UNSET なら packed そのまま／packed より小さければ広げる／画面より大きければ縮む／monitors 空でも落ちない／上限が下限を下回る矛盾時は下限優先

**`ColumnWidthsCodecTest`**（表駆動）：§5.6 に挙げた全ケース＋ format の往復。

---

## §8 決定ログ

chat55 のフェーズ4「き」で決めたこと。理由が要るものだけ。

| # | 決定 | 理由 |
|---|---|---|
| 1 | 保存対象にメイン窓の**位置**と DEB パネルの**分割位置**を追加 | 当初案（列幅・メイン窓サイズ・ダイアログサイズ・デバッグフラグ）だけでは「毎回同じ場所に出る」体験にならない |
| 2 | 編集ダイアログの**位置は保存せず毎回スクリーン中央** | 推奨は「親の中央」だったが本人判断で中央固定。「いったんそれで使ってみる」 |
| 3 | 保存は**終了時にまとめて1回** | リサイズのたびの保存は debounce が要って割に合わない。`shutdownApp()` が3経路の集約点なので1箇所で足りる |
| 4 | 復元時に**モニタ構成の変化を吸収する**安全弁を入れる | 解像度変更・モニタ取り外しで窓が画面外に出ると操作不能になる |
| 5 | ダイアログサイズは `Mode` 別に**2組**持つ | 通常編集と instant は必要な寸法が違う |
| 6 | 列幅は**幅だけ**、並び順は対象外 | 並び替えの UI が無い。列数不一致は「キー欠け＝デフォルト維持」と同じ方針で吸収 |
| 7 | `debug.enabled` は **getter だけ** | 手編集で切り替える運用。書き込む契機が無いので setter は不要 |
| 8 | 最大化ガードを**全項目**に掛ける | §5.2。「最大化中の窓の寸法はユーザーが選んだ寸法ではないので、そこから導かれる値は一切保存しない」で統一 |
| 9 | 分割位置を**比率**にする | §5.5。既に比率の概念が同居していた／最大化以外の経路の再発も塞ぐ |
| 10 | 分割比率の**両端（0.0 / 1.0）は許容**する | §5.5 末尾。ユーザーが意図して置いた位置を勝手に戻さない方を採った |
| 11 | ダイアログの下限は `MIN_WIDTH` ではなく **`pack()` 結果** | §5.7。固定値を使うと instant ダイアログが不自然に広がる |

---

## 付録A. 既知の不整合（本冊作成時点・chat57）

本冊を書くにあたって実ソースと照合した際に見つかった、**コード側の古い記述**。実害は無いが、次に触るときに直すとよい。

1. **`Config` クラスの javadoc が古い**：「フィルタトグルの永続化設定（GUI仕様v2 §3.7・案A＝**今回はフィルタ6トグルのみ**）」と書かれているが、実際にはウィンドウ状態・`snd.wav.dir`・`debug.enabled` も持つ。参照先も本冊に差し替えるべき
2. **`testdata/config.properties.example` が古い**：フィルタ6キーと `snd.wav.dir` しか無く、`window.*` / `table.columnWidths` / `debug.enabled` が入っていない。キー欠けはデフォルト維持なので実害は無いが、種としては不完全
