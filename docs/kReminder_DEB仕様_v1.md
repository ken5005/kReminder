# kReminder DEB（デバッグログ基盤）仕様

**位置づけ**：本書は kReminder のデバッグ／動作ログ機構「DEB」の正典。実装は `ken5005.kreminder.debug`（`DEB`/`DebFormat`/`DebWorker`/`LogSink`/`ConsoleSink`/`FileSink`）と `ken5005.kreminder.gui`（`PanelSink`/`DebugPanel`）に存在し、チャット19（①-b-1 中核）〜チャット20（①-b-2 パネル表示・①-b-3 Main 実配線・煙テスト）で完成・ship 済み。**本書はチャット22で実ソースと照合して清書したもの**（`MainWindow.java` はチャット23で照合済み）。署名・定数の一次ソースは各 `.java`。以後齟齬が出たら実コードを正とし、本書を追随させる。

---

## 1. 目的と思想

- **どこからでも `DEB.pr(...)` 一発で書ける**。呼び出し側は型を気にせず投げるだけ。
- **デバッグ機能がメイン処理を絶対に止めない**。ログ出力は非同期・ノンブロッキングで、I/O 失敗も本体へ伝播させない（設計原則5「エラーは隔離する」の一適用）。
- **動作ログとデバッグログを敢えて混ぜる**（本人の流儀）。運用時の証跡も同じ経路に流す。
- 旧トレードアプリの静的デバッグログ機構 `DEB` を kReminder へ移植したもの。

**非目標**：構造化ログ（JSON 等）・ログレベル階層（INFO/WARN/ERROR の切り替え）・外部ログライブラリ依存。いずれも現状スコープ外。

---

## 2. アーキテクチャ

**呼び出し側 → ファサード → キュー → 単一ワーカー → 各シンク**、の一方向フロー。純粋なフォーマット部と副作用（I/O・Swing）を持つシンク部を分離する。

```
[任意の呼び出し側]
      │ DEB.pr(...) … enqueue して即 return
      ▼
[DEB]  静的ファサード（init / shutdown / pr 系）
      │ 行を LinkedBlockingQueue に積む
      ▼
[DebWorker]  低優先デーモンスレッド1本・queue を drain
      │ 整形は DebFormat（純粋）に委譲
      ├──────────────┬──────────────┐
      ▼              ▼              ▼
[ConsoleSink]   [FileSink]     [PanelSink]
  System.out    logs/DEB-*.txt  DebugPanel(JTextArea)
                （時ローテ）     （EDT へ marshaling）
```

**層の責務**：

- **純粋部（`DebFormat`）**：入力→文字列の変換のみ。Swing・I/O・Gson を import しない。TDD 対象。
- **配信部（`DEB` / `DebWorker`）**：キューイングと非同期配信。**シンクに触るのは `DebWorker` の1スレッドだけ**という前提を敷く（後述の並行性契約の土台）。
- **シンク部（`LogSink` 実装群）**：実際の出力先。**例外を投げない契約**（§6）。

---

## 3. クラス別仕様

### 3.1 `DEB`（静的ファサード・`ken5005.kreminder.debug`）

アプリ全体から使う入口。すべて static。

- **`init(Clock clock, LogSink... sinks)`**：時刻源となる `Clock` と1個以上のシンクを渡して起動。`DebWorker` を生成しデーモンスレッドを開始する。**`init` 前に呼ばれた `pr` は取りこぼさずコンソールへ直接落とす**（初期化前フォールバック）。
- **`shutdown()`**：ワーカーへ停止を要求。**キューに残った行を吐き切ってから**スレッドを終える（§4）。各シンクの `close` もここで呼ぶ。
- **`pr(...)`（型別オーバーロード9種）**：1行分の値を文字列化してキューへ積み、即 return。**呼び出し側は enqueue するだけでブロックしない**（実測：5000行連打で約 55ms・メイン無ブロック）。受け取る型と変換：
  - `pr(String)` / `pr(int)` / `pr(long)` / `pr(boolean)` / `pr(Object)` → `String.valueOf(...)`
  - `pr(double)` → `String.format("%.4f", v)`（小数4桁・**末尾空白なし**。`prMul` 経由とは違い単値なので区切り空白を付けない）
  - `pr(Throwable)` → `DebFormat.formatStackTrace(t)`
  - `pr(String[])` → `null` なら `"(null)"`、そうでなければ `String.join(", ", arr)`（**カンマ区切り**。`formatArgs` の空白連結とは別物）
- **`prFmt(String format, Object... args)`**：`String.format(format, args)` の結果を1行出す。
- **`prMul(Object... args)`**：`DebFormat.formatArgs(args)` で連結（null→`"(null) "`、Double→`"%.4f "`、他→`toString()`＋空白。各要素末尾に空白が付く）。
- **`prTime(String label)`**：`label + " " + DebFormat.formatTime(now(clock))` を出す。※全行に共通の行頭タイムスタンプ（下記 `enqueue`）が付くので、`prTime` の行は「行頭時刻＋label＋本文側の時刻」と時刻が2つ入る（区切りを目立たせる用途）。
- **命名**：`_` サフィックスの命名慣習は本人流儀で温存。

**共通の行整形（`enqueue`・private）**：どの `pr` 系も最終的に `DebFormat.formatLine(now(clock), body)` を通す＝**全行の先頭に `yyyy/MM/dd HH:mm:ss.SSS ` の時刻が付く**。`worker == null`（init 前）なら `System.out.println` へ直接落として取りこぼさない。

**定数**：

- **`PANEL_TEXT_LIMIT = 100_000`**（`public static final int`）：`PanelSink` のテキストエリア文字数上限（§3.6）。
- **内部状態**：`clock`・`worker` はいずれも `volatile`（`init` で差し替わり、他スレッドの `pr` から読まれるため）。

### 3.2 `DebFormat`（純粋・`ken5005.kreminder.debug`）

入力→文字列の純粋変換。**Swing 非依存**・状態を持たない（static メソッドのみ・TDD 対象）。I/O は原則使わないが、`formatStackTrace` だけは内部で `ByteArrayOutputStream`＋`PrintStream` を一時バッファに使う（外部 I/O ではない・メモリ内）。

- **`formatArgs(Object...)`**：各引数を文字列化して連結。`args` が `null` か空なら `""`。
  - `null` → `"(null) "`
  - `Double` → `"%.4f "`（小数4桁）
  - その他 → `toString()` ＋ 空白
  - **各要素末尾の空白は意図的**（`pr` 連結時の語間区切り。除去しない）。
- **`formatTime(LocalDateTime)`**：`DateTimeFormatter` で `"yyyy/MM/dd HH:mm:ss.SSS"`。フォーマッタは共有 static インスタンス（`SimpleDateFormat` と違い `DateTimeFormatter` はイミュータブル＝スレッド安全なので共有してよい）。
- **`formatTime(Instant, ZoneId)`**：`Instant`→`LocalDateTime` 変換して上記に委譲するオーバーロード。
- **`formatStackTrace(Throwable)`**：スタックトレースを文字列化。`null` → `"(null)"`。**UTF-8 を明示**してデコード（`PrintStream` に UTF-8 指定）。
- **`formatLine(LocalDateTime, String)`**：`formatTime(time) + " " + body` ＝ 時刻プレフィックス＋本文。

### 3.3 `LogSink`（interface・`ken5005.kreminder.debug`）

- メソッド：`accept(String line)` / `flush()` / `close()`。
- **契約：実装は例外を投げない**。内部で起きた I/O 例外等はすべて握り、必要なら stderr に落とすに留める。ワーカーやメイン処理を巻き込まない。
- **二重防御**：この契約に加え、`DebWorker` 側も各シンク呼び出しを try/catch で包む（§3.7 `safeAccept`/`flushAll`/`closeAll`）。万一シンクが契約を破って投げても、ワーカーは stderr に出して他シンクへの配信・ループを継続する。

### 3.4 `ConsoleSink`（`ken5005.kreminder.debug`）

- `accept` ＝ `System.out.println(line)`。`flush` ＝ `System.out.flush()`。`close` ＝ no-op（`System.out` は自分が開いたものではないので閉じない）。

### 3.5 `FileSink`（`ken5005.kreminder.debug`）

- **出力先**：`%APPDATA%\kReminder\logs\DEB-<key>.txt`。`<key>` は `yyyyMMdd-HH`（`FILE_KEY_FMT`）。
  - ディレクトリは `resolveDir()` で決定：`APPDATA` があれば `%APPDATA%\kReminder\logs`、無ければ `<user.home>\kReminder\logs`。パス組み立て時に決まり、`logs` は書き込み時 `Files.createDirectories` で自動生成。
- **時ローテーション**：`accept` のたびに `ensureCurrentFile()` が `now(clock)` の `yyyyMMdd-HH` キーを計算し、**前回キーと変われば（または writer が無ければ）現 writer を閉じて新ファイルを開く**。ファイル名が時単位で切り替わる。
- **書き込み**：`Files.newBufferedWriter(UTF-8, CREATE, APPEND)`＝UTF-8 追記。1行ごと `write`＋`newLine`。
- **例外方針**：`accept`/`flush`/`close` の I/O 例外はすべて握って **stderr のみ**に出す（§6 の契約）。`closeWriterQuietly` は finally で `writer = null` にするので、閉じ損ねても次回 `accept` で開き直せる。

### 3.6 `PanelSink`（`ken5005.kreminder.gui`）

DEB ログを GUI の `DebugPanel` 内 `JTextArea` へ流すシンク。**Swing を触るため gui パッケージに置く**（debug パッケージの純度を守る）。

- **スレッド越え（marshaling）**：`accept` は `DebWorker` の**非 EDT スレッドから**呼ばれる。実際のテキスト追記は `SwingUtilities.invokeLater` で **EDT に載せ替えて**行う。
- **coalescing（invokeLater の氾濫防止）**：`pr` 連打で `invokeLater` を大量発行しないよう、
  1. 受け取った行は `ConcurrentLinkedQueue` に積む。
  2. `AtomicBoolean.compareAndSet(false, true)`（CAS）でドレイン予約を立て、**同時に走るドレイン予約を1個だけ**に制限する。
  3. drain（EDT 上）は「**予約フラグを先に false へ下ろしてから**キューを吸い出す」順で実行する。これにより lost-wakeup 型の取りこぼし（吸い出し中に来た行が予約されず埋もれる）を防ぐ。
- **文字数 cap**：追記後にテキスト長が `DEB.PANEL_TEXT_LIMIT`（100,000）を超えたら、**超過分を先頭から行境界（直近の改行）で削る**。
  - `Document.remove` ＋ 局所的な `getText` を使い、**テキストエリア全体の `getText` は呼ばない**（重いので）。
  - `BadLocationException` は握る（best-effort）。
- `flush` / `close` は no-op。

### 3.7 `DebWorker`（非同期の心臓・`ken5005.kreminder.debug`・package-private）

- **キュー**：**無限長 `LinkedBlockingQueue<String>`**。`enqueue` は `queue.add(line)` だけ＝呼び出し元は無条件で即返る。
- **スレッド**：名前 `"DEB-worker"`・`setDaemon(true)`・`setPriority(Thread.MIN_PRIORITY)`（本体と CPU を取り合わないよう最低優先度）の1本。これが queue を drain して各シンクへ配信する。
- **定数**：`FLUSH_THRESHOLD = 50`（バッチ行数）／`POLL_TIMEOUT_MS = 2000`。
- **runLoop**：`while (running || !queue.isEmpty())` で回す。`queue.poll(2000ms)` し、行が取れたら全シンクへ `safeAccept`（try/catch 包み）して `sinceFlush++`。**`line == null`（timeout＝一段落）または `sinceFlush >= 50` で `flushAll` してカウンタリセット**。ループを抜けたら最後に `flushAll`→`closeAll`。
- **停止時の吐き切り**：ループ条件により **shutdown 後もキューが空になるまで吐き続ける**。
- **単一ワーカー前提**：**シンクに触れるのはこのワーカー1本だけ**。この前提により `FileSink` は `synchronized` 無しでもデータ競合しない（前提を崩すなら各シンクのスレッド安全性を作り直す・§4）。
- **`shutdown()`**：`running = false` → `thread.interrupt()`（`poll` を即起こすため）→ `thread.join(5000)`（最大5秒待つ）。割り込みで止まるかはループ条件が判断する（interrupt 自体は poll を起こすだけ）。

---

## 4. スレッドモデルと並行性の契約

- **書き手は多数・シンクを触るのは1スレッド**：任意のスレッド（EDT・祝日デーモン・メインループ等）が `DEB.pr` を呼べるが、`pr` は queue に積むだけ。実際に各シンクへ書くのは `DebWorker` の1スレッドに限定される。
- **この単一化が並行安全の土台**：`FileSink` が無ロックで安全なのはこの前提ゆえ。将来ワーカーを増やす等でこの前提を崩すなら、各シンクのスレッド安全性を作り直す必要がある。
- **PanelSink は例外的に EDT へ再度渡す**：Swing は EDT 専用のため、ワーカースレッド → `invokeLater` → EDT の二段構え。coalescing/CAS はこの再ディスパッチを氾濫させないための機構（§3.6）。
- **非 EDT 発ログの実証**：①-b-3 の煙テストで、祝日更新デーモン（非 EDT スレッド）発の `DEB.pr` が PanelSink 経由でパネルに例外なく描画されることを live 確認済み。marshaling 設計が本番で証明された。

---

## 5. 出力フォーマットとパス

- **1行の形**：`formatLine` ＝ 時刻 ＋ 本文。時刻書式は `yyyy/MM/dd HH:mm:ss.SSS`（§3.2）。
- **時刻源は `Clock`**：`DEB.init` に渡された `Clock` を使う。Main は fake-clock 込みの `clock` を渡すため、**`--fake-now` 起動時はログ時刻も fake 基準**で揃う（デバッグ時に本体の時刻と食い違わない）。
- **ファイル**：`%APPDATA%\kReminder\logs\DEB-yyyyMMdd-HH.txt`（`APPDATA` 無→`user.home`）。時単位ローテ。UTF-8 追記。

---

## 6. 恒久ルール

- **シンクは例外を投げない**（§3.3）。I/O 失敗は握って stderr に留め、本体・ワーカーを巻き込まない。
- **純関数からは `DEB.pr` を呼ばない**。`toJapanese()`・残り時間整形（`RemainFormat`）・`isVisible` 等の純関数にログ副作用を生やさない。理由：
  - 副作用が生えるとテストで挙動を縛れなくなる。
  - 層の一方向依存（純粋部←配信部←シンク部）が崩れる。
  - **ログは呼んだ側（ビュー層・サービス層）で出す**。純粋部の異常は `throw` して上層で受ける（＝戻り値の一種として扱い、純粋性を保つ）。

---

## 7. Main への配線（①-b-3）

- `Main.main` の `invokeLater` 内で `MainWindow` を生成し、そこから **`MainWindow.getDebugTextArea()`**（`JTextArea` を返す public メソッド。内部で private フィールド `debugPanel` の `debugPanel.getTextArea()` に委譲）を取り出して `new PanelSink(textArea)` を作る。`PanelSink` のコンストラクタは `JTextArea` を受ける。
  - `MainWindow` は `debugPanel` そのものを返す getter は公開していない（Main が要るのは `JTextArea` だけなので不要）。Main から `debugPanel` へ直接触ることはない。
- `DEB.init(clock, new ConsoleSink(), new FileSink(clock), panelSink)` で3シンク構成で起動。`clock` は Main の fake-clock 込みインスタンス。
- **shutdown 経路は2つ**：ウィンドウ close（`WindowListener`）と トレイ Exit の両方で `DEB.shutdown()` を呼び、残りキューを吐き切る。
- 既存の `HolidayLog`（`holiday.log`）は削除せず、DEB を併存で追加した（当面二重・§9）。

---

## 8. 起動時イベントの記録（煙テスト実績）

①-b-3 で以下4イベントを `DEB.pr` で出力し、動作を確認した：

1. 起動
2. `reminders.json` 読込件数
3. 祝日 `loadInitial` 結果
4. **祝日更新デーモンの `onUpdate`（★非 EDT スレッド発）**

確認できたこと：非 EDT 発ログが PanelSink 経由で例外なくパネルへ出る／`--fake-now` で DEB 時刻が fake 基準になる／`logs\DEB-*.txt`（FileSink 経路）にも同じ行が出る／ウィンドウ close で JVM が正常終了（ハング・例外なし）。

> 注：`onUpdate` は「新しい CSV を取れた時だけ」呼ばれる（キャッシュが新鮮ならスキップ）。起動直後にすぐ出なくても異常ではない。

---

## 9. 既知の未実装・TODO

- **キュー満杯時のドロップ**：現状キューは無限長。将来メモリ保護のため、上限＋`offer` が false のとき捨てる方式を検討（優先度低）。
- **`holiday.log` と DEB の一本化**：現在は併存。将来 DEB に寄せて一本化する余地あり（当面は併存でよい）。

---

## 10. 使用例

```java
// 起動（Main.main の invokeLater 内・シンク3枚構成）
DEB.init(clock, new ConsoleSink(), new FileSink(clock), panelSink);

// 単一の値（即 return）。pr は1引数のみ。
DEB.pr("起動しました");
DEB.pr(count);                 // int → そのまま

// 複数の値を1行にまとめたいときは prMul（可変長）
DEB.prMul("reminders.json 読込:", count, "件");
DEB.prMul("祝日 loadInitial:", state.status());

// 書式つき
DEB.prFmt("残り %d 件・状態 %s", count, state.status());

// 区切り時刻を打つ
DEB.prTime("=== 発火チェック開始 ===");

// 例外はそのまま渡せる（内部で formatStackTrace される）
DEB.pr(e);                     // e は Throwable

// 終了時（window close / トレイ Exit の両経路で）
DEB.shutdown();   // 残りキューを吐き切ってから停止
```

※`DEB.pr("ラベル:", value)` のような**複数引数呼び出しはコンパイルできない**（`pr` に該当オーバーロードが無い）。複数値は `prMul` を使う。
