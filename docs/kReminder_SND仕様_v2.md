# kReminder 音声サブシステム仕様書 v2.1

最終更新: 2026-07-13（⑤ポップアップ配線）
v1.0 からの変更＝chat33 実装時の確定事項（音量モデル）＋ chat35 の sound-map 一式を現況化。
v2.0 からの変更＝⑤ポップアップ配線スライスで追加した「通知パターン（Pri ↔ 鳴らし方）」（§11）を反映。

---

## 1. 概要・設計思想

リマインダーの発火通知・操作フィードバック等に効果音を鳴らすサブシステム。

**設計思想**（kReminder 全体の設計原則と同じ軸）：

- **どこからでも鳴らせる**：静的ファサード `SND.play(name)` / `SND.play(name, volume)` を1行書くだけ。呼び出し元は再生の仕組みを知らなくていい（DEB と同構造）。
- **本体を絶対止めない**：再生はデーモンスレッドに委譲。呼び出し元は enqueue して即返る。例外は呑んでログに出す。
- **前の音が終わってから次を鳴らす**：複数リクエストが重なっても順次再生。同時発音なし。
- **エラーは隔離する**：音声再生の失敗がリマインダー通知本体を巻き込まない。
- **層でエラー方針を使い分ける**：設定（sound-map・起動時）は strict＝派手に落とす（原則2「怪しきは罰する」）。実行時（`play` の未知音声名等）は graceful＝フォールバックして続行（原則5「エラーは隔離」）。

---

## 2. クラス構成

パッケージ: `ken5005.kreminder.sound`（＋GUI依存1点は `ken5005.kreminder.gui`）

| クラス | 種別 | 責務 |
|---|---|---|
| `SND` | 静的ファサード | `play` / `init` / `shutdown` の窓口。DEB と同構造。 |
| `SoundWorker` | デーモンスレッド | キューを順次処理し javax.sound.sampled で再生。 |
| `SoundRequest` | record | 音声名 + ボリューム のイミュータブル入れ物。 |
| `WavLoader` | 純関数クラス | wavディレクトリをスキャンして `List<File>`（ファイル名昇順）を返す。 |
| `SoundMapParser` | 純関数クラス | sound-map.properties のパース＋テンプレート文字列生成。 |
| `SoundMapBuilder` | 純関数クラス | wav 一覧×テーブルのマージ（案X）＝最終 `Map<音声名, File>` を構築。 |
| `gui/FatalErrorDialog` | 汎用ヘルパー | 致命エラーを最前面ダイアログで表示して `exit(1)`（→§8.4）。 |

外部依存ゼロ（`javax.sound.sampled` は Java 標準ライブラリ）。

---

## 3. API

### 3.1 SND.init(Map<String, File> soundMap)

```java
public static void init(Map<String, File> soundMap)
```

- **組み立て済みの音声名マップを受け取り** `SoundWorker` を起動する。
  （v1 の `init(Path wavDir)` から変更＝wavDir 走査・sound-map 突き合わせは Main 側の責務。SND は結果マップを受けるだけ。）
- Main 起動時に1回だけ呼ぶ（`DEB.init` の直後）。
- 2回以上呼ばれた場合は2回目以降を無視（warn ログ）。

### 3.2 SND.play(String name, float volume)

```java
public static void play(String name, float volume)
```

- `SoundRequest(name, volume)` をキューに積む。
- `init` 前の呼び出しは silent drop（例外を出さない）。
- volume の有効範囲は 0.0f〜1.0f。範囲外は clamp して使う（warn ログ）。
- キューが上限（30件）を超えていたら drop してログ（→§4）。

### 3.3 SND.play(String name)

```java
public static void play(String name)
```

- 音量省略版。`play(name, 1.0f)` へ委譲（1.0＝元ファイルそのままの標準音量・→§5）。

### 3.4 SND.shutdown()

```java
public static void shutdown()
```

- `SoundWorker` に停止を伝え（`join(2000)`）、現在再生中の音が終わったら終了。
- Main の窓 close・トレイ Exit 両経路で呼ぶ（`DEB.shutdown` と並べる）。

---

## 4. キュー仕様

- **実装**: `LinkedBlockingQueue<SoundRequest>`（capacity=30）
- **上限**: 30件。`offer` が false（キュー満杯）の場合は drop + `DEB.pr("SND キュー満杯: drop " + name)`（軽量文字列ログ・RuntimeException 不要）。
- **順序**: FIFO。先に積まれたリクエストから順に処理。
- **同時発音なし**: SoundWorker は1件の再生が完全に終わってから次を `take()`。再生完了は `LineEvent.Type.STOP` を `CountDownLatch(1)` で待って検知。
- **drop の思想**: 1秒に100件来るような異常リクエストは捨てていい。音が重なるより無音の方がマシ。

---

## 5. 音量モデル（chat33 確定）

- **再生方式＝都度ロード**：`AudioInputStream`＋`Clip` をリクエストのたびに開く（直列処理なので実用上問題なし。`Clip` 事前ロード・チャンネル管理は将来拡張）。
- **音量は `FloatControl.MASTER_GAIN`（dB）を使う**：線形 volume（0.0〜1.0）を `Math.log10(volume) * 20` で dB に変換。
  - `volume=1.0` → 0dB＝元ファイルそのまま
  - `volume=0.5` → 約 -6dB
  - `volume=0.0` → 無音（特別扱い）
- **0dB を実質上限とする減衰モデル**：増幅側（`getMaximum()` までの余地）は使わない＝1.0 が「標準音量」で直感どおり。増幅したくなったら上限側を触る拡張。
- log を使う理由＝人間の聴覚が対数だから（線形で半分にしても半分に聞こえない）。

---

## 6. wavディレクトリ設定

### 6.1 config.properties のキー

```
snd.wav.dir=C:\\tools2\\etc\\wav
```

- キー名: `snd.wav.dir`。値: wav ファイルが入っているディレクトリの絶対パス。
- **起動時に `Config` 経由で読む**。キーが存在しない場合はデフォルト値を config.properties に**書き込んでから**使う（「設定ファイルが説明的であること」方針＝`Config.load()` 時にキー欠けなら即 `save()`）。
- パスの存在確認は Main 側（→§9）。

### 6.2 sound-map のパス

- `Config.getSoundMapPath()` → `%APPDATA%\kReminder\sound-map.properties`（APPDATA 不在時は user.home フォールバック＝config.properties と同じ規則）。

---

## 7. wavロード（WavLoader）

```java
public static List<File> load(Path wavDir)
```

- `wavDir` 直下の `.wav` / `.WAV`（大文字小文字不問・`toLowerCase().endsWith(".wav")` 判定）を列挙し、**ファイル名昇順ソートの `List<File>`** を返す（決定的順序のため）。
- v1 の `Map<stem, File>` 返しから変更＝stem（拡張子抜きファイル名）の導出は `SoundMapBuilder` へ移譲。
- **不在／読めない／0件でも例外を投げず空リスト**（`!Files.isDirectory` 早期 return＋`IOException` catch）。
- 純関数。JUnit5 表駆動テスト（大文字小文字・非wav無視・不在ディレクトリ・空ディレクトリ・ソート順）。

---

## 8. sound-map（音声名↔ファイル名テーブル・chat35）

**目的**＝`SND.play` で呼ぶ「音声名」を、実ファイル名（stem）に縛られず手編集で自分で決められるようにする。

### 8.1 設定ファイル

- パス＝`%APPDATA%\kReminder\sound-map.properties`（→§6.2）。
- **UTF-8**。1行 `音声名=ファイル名`。
- 値は**拡張子アリのファイル名**（例 `ごん.wav`）＝将来 mp3 等に広げる布石。
- `#` は**行内どこでもコメント開始**（＝キー・値に `#` は使えない）。空行・コメントのみ行はスキップ。最初の `=` で分割し両辺トリム。

### 8.2 自前パーサ（SoundMapParser）

- `java.util.Properties` は**使わない**。理由2点＝(1) ISO-8859-1 読みで日本語キーが化ける (2) 重複キーを後勝ちでサイレントに握り潰す（sound-map ではどちらも致命）。
- `parse(List<String>) → LinkedHashMap<String,String>`（挿入順保持）。重複キー・不正行（`=` 無し／キーor値が空）は `IllegalArgumentException`。
- `renderTemplate(List<File>) → String`：現在の全 wav を `stem=ファイル名` で列挙したテンプレート文字列を生成（先頭に書式コメント付き）。

### 8.3 マージ規則＝案X（SoundMapBuilder）

```java
public static Map<String, File> build(List<File> wavFiles, Map<String, String> table)
```

1. テーブルの各 `音声名=ファイル名` を**大小無視**でファイル照合し、その音声名を割当。
2. テーブルの**値として参照されなかったファイルだけ**、stem（拡張子抜きファイル名）を音声名として自動採用。

**＝テーブルに載せたファイルは stem 名を引退し、書いた音声名でのみ呼べる**。非記載ファイルは従来どおり stem。別名2つが同一ファイルを指すのは可（エイリアス）。

エラー（`IllegalArgumentException`）＝dangling（値のファイルが wav 一覧に無い）／衝突（明示音声名と別ファイルの自動 stem 名が一致）。

### 8.4 起動時エラー＝全部 loud に落とす（原則2）

以下の5条件はいずれも `FatalErrorDialog.showAndExit` で**最前面ダイアログ→`exit(1)`**：

(a) dangling ／ (b) 重複キー ／ (c) 衝突 ／ (d) 不正行 ／ (e) sound-map の読込 I/O 失敗

※これは**設定ミス＝起動時**の話。実行中の未知音声名（`SND.play("typo")`）は "Oops" フォールバック＝graceful（→§9）。

**`gui/FatalErrorDialog`**：`static showAndExit(String)`＝EDT 安全（非EDTは `invokeAndWait` で包む）→ JOptionPane(ERROR) → `exit(1)`。親 `null` だと共有隠しフレームが親になり他アプリの背後に隠れる（「無言で起動失敗」と同じ体験に堕ちる）ため、**一時的な `alwaysOnTop`＋`undecorated` フレームを可視化して親にする**。汎用ヘルパーだが現在の配線先は sound-map のみ（常駐窓がある実行中エラーに使う時はその窓を親にすれば owner 生成不要＝Javadoc 記載）。

### 8.5 テンプレート自動生成

- `sound-map.properties` が**無いときだけ**、`renderTemplate` の内容を UTF-8 で書き出す。
- **生成はこの一度きり・以後アプリは一切書き換えない**（後から増えた wav はテーブルに無ければ stem 自動で鳴る。音声名を付けたければ手動追加）。
- 生成した回はテーブル空扱いで先へ進む（＝全 stem 自動＝生成内容と同義・生成直後の読み直しはしない）。書込失敗は DEB 警告してテーブル空で続行（graceful）。
- 実ファイル一覧はテンプレ生成で得られるため、v1 §6.2 の一覧表は本仕様から廃止。フォールバック音は "Oops"（`SoundWorker.FALLBACK_NAME` で固定・音声名の解決は大小区別）。

---

## 9. エラー処理一覧

**実行時（graceful）**：

| 状況 | 処理 |
|---|---|
| 未知の音声名 | `DEB.pr(new RuntimeException("未定義の音声名: " + name))` + "Oops" でフォールバック再生 |
| "Oops" 自体がマップに無い | `DEB.pr(new RuntimeException("音声ファイルが無い: Oops.wav"))` + スキップ |
| 再生中に例外 | 例外を呑んで `DEB.pr(e)` + 次のリクエストへ進む |
| キュー満杯（30件超） | drop + `DEB.pr` の文字列ログ |
| volume 範囲外 | clamp(0.0f, 1.0f) + `DEB.pr` で warn |
| wavDir 不在／非ディレクトリ | Main 側で DEB 警告し **SND を init せずスキップ**（以降 play は silent drop） |
| テンプレ書込失敗 | DEB 警告してテーブル空で続行 |

**起動時（strict）**：sound-map の parse/build エラー5条件（→§8.4）＝最前面ダイアログ→`exit(1)`。

**StackTrace 付きの意図**：`SND.play` はどこからでも呼ばれるため、`RuntimeException` の StackTrace で呼び出し元を特定できるようにする（DEB の `pr(Throwable)` オーバーロード活用）。

---

## 10. Main 配線（sound-map パイプライン・chat35 本配線）

`invokeLater` 内・`DEB.init(...)` 直後：

```
(a) WavLoader.load(wavDir) → List<File>
(b) wavDir が非ディレクトリ → DEB.pr 警告して SND を init せずスキップ（空ディレクトリは先へ進む）
(c) sound-map.properties 不在 → SoundMapParser.renderTemplate を UTF-8 で書き出し（この一度きり）
(d) 存在 → UTF-8 で Files.readAllLines → SoundMapParser.parse（読込 I/O 失敗は致命）
(e) SoundMapBuilder.build(wavFiles, table)
(f) parse/build の IllegalArgumentException は try/catch → FatalErrorDialog.showAndExit
(g) 成功 → SND.init(soundMap)
```

窓 close／トレイ Exit の両経路で `DEB.shutdown()` に並べて `SND.shutdown()`。

---

## 11. 通知パターン（Pri ↔ 鳴らし方）

⑤ポップアップ配線スライスで追加。priority に紐づくのは音声ファイル名ではなく**「鳴らし方」**＝ `NotifyPattern`。**実際に音を出すのは既存の `SoundWorker`（直列1本）のまま**——`Notifier` は `SND.play` を呼ぶ（キューに積む）だけで、独自の再生経路は持たない。

### 11.1 クラス構成

パッケージ: `ken5005.kreminder.sound`

| クラス | 種別 | 責務 |
|---|---|---|
| `NotifyStep` | record | 1ステップ分＝音声名・音量・鳴らした後の待ち時間（ms）。 |
| `NotifyPattern` | record | ステップ列＋ループするか＋ループ時の最大時間。 |
| `NotifyPatterns` | 純関数の表 | `Reminder.Priority → NotifyPattern` の対応表（`forPriority`）。 |
| `Notifier` | 静的ファサード | `NotifyPattern` を渡すとデーモンスレッドを1本起動し `NotifyHandle` を返す（`start`）。 |
| `NotifyHandle` | ハンドル | 起動したスレッドの停止（`stop`・冪等）と終了待ち（`awaitTermination`、テスト用）。 |

### 11.2 API

```java
public record NotifyStep(String soundName, float volume, long delayAfterMs) {}
public record NotifyPattern(List<NotifyStep> steps, boolean loop, Duration maxDuration) {}

public static NotifyPattern NotifyPatterns.forPriority(Reminder.Priority p)

public static NotifyHandle Notifier.start(NotifyPattern pattern)
public void NotifyHandle.stop()                        // 冪等
public boolean NotifyHandle.awaitTermination(long ms)   // テスト用
```

- `NotifyPatterns.forPriority` は Pri-1〜Pri-5 を switch で明示的に分岐する（将来ここだけ書き換えられるように、まとめて default に潰さない）。`p` が null のときも既定パターンを返す（旧 JSON 防御）。
- ⑤時点は全 priority 共通で `new NotifyPattern(List.of(new NotifyStep("Standard", 1.0f, 0)), false, null)`。将来 Pri-5 を「0.5で鳴らす→500ms休む→1.0で鳴らす→…をループ、最大90分」に変えるときは、この表だけ書き換えれば `Notifier` も呼び出し元（Main）も触らずに済む。

### 11.3 停止経路＝windowClosed 一本化

- 発火ポップアップを開く直前に `Notifier.start(...)` で通知を開始する。
- ポップアップの `windowClosed`（OK・Extend・× すべてここを通る）の先頭で `NotifyHandle.stop()` を呼ぶ。停止経路はここ1箇所に集約する。
- `Notifier` 内部のスレッドは待ちを**100ms 刻み**（`SLEEP_SLICE_MS`）に割って毎回停止フラグを見る＝OK を押した瞬間に鳴りやむ。各ステップを鳴らす前にも停止フラグを見る。
- `loop=true` のパターンは `maxDuration` に達するか外部からの `stop()` が来るまでステップを繰り返す。
- 停止フラグは `AtomicBoolean`。`stop()` は何度呼んでも安全（冪等）。
- スレッド名 `kReminder-Notifier`、`setDaemon(true)`、優先度 `MIN_PRIORITY`（DEB／SND と同じ流儀）。
- 例外は握りつぶして `DEB.pr` にログするだけで外へは投げない＝デバッグ機能やサブシステムの異常で本体を止めないという全体方針の延長。

---

## 12. 実装履歴（参考）

- **chat33（feature/sound）**：`WavLoader` 純関数 TDD → `SoundRequest`/`SoundWorker`/`SND` → `Config` に `snd.wav.dir` ＋ Main 配線 → 目視（正常直列再生・フォールバック・clamp・デフォルト書込）。
- **chat35（feat/snd-soundmap・PR#15）**：`SoundMapParser` 純関数 TDD → `SoundMapBuilder` 純関数 TDD（案X／dangling／衝突／大小＝一番バグの巣）→ `WavLoader` List 化・`SND.init(Map)` 化・`play(name)` 追加・`Config.getSoundMapPath()` → `FatalErrorDialog`＋Main 本配線 → 目視（テンプレ生成／リネーム反映＝`呼び鈴=notify.wav`／音量省略／大小無視／致命3種で最前面ダイアログ→exit）。

---

## 13. 将来拡張（現バージョンのスコープ外）

- mp3 等 wav 以外の形式（sound-map の値が拡張子アリなのはこの布石）
- 同時発音・チャンネル管理（現状は完全直列）／`Clip` 事前ロード
- ループ再生
- 増幅（0dB 超・`getMaximum()` 側）
- **同時多発時の交通整理**：10枚同時発火 × エスカレート音だと直列の `SoundWorker` が詰まる。実装する段で「鳴らすのは最優先の1件だけ」等の整理が要る。
