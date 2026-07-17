# kReminder コード地図 v1

最終更新: 2026-07-16（chat49 新規作成・main HEAD＝`v0.9.0` の実ソースから起こした）。

このファイルは kReminder の**コード構造の正典**＝「どのクラスが何を担い、誰に依存するか」の地図。粒度は**クラス／パッケージ＋依存の向き**まで（メソッド単位は即腐るので書かない）。**設計判断の「なぜ」や急所は handoff A3 が正**（stale index ルール、発火ポップアップの owner=null 厳守など）＝この地図は骨格、handoff は骨格の理由。仕様の逐語は `docs/` の正典8冊が正。

真実源はコード。この地図と実ソースが食い違ったら**実ソースが正**。メンテ規則は §5。

対象＝`src/main/java` の本体63クラス（テスト22本・`docs/`・`testdata/` は対象外）。

---

## 1. パッケージ全体像

6パッケージ。依存は基本「各機能パッケージ → `(root)` のドメイン純関数・データ」に向き、`Main` だけが全パッケージを束ねる配線ハブ。

```
                          ┌──────────────┐
                          │   Main (root) │  配線ハブ＋発火ループ
                          └──────┬───────┘
        ┌───────────┬───────────┼───────────┬───────────┐
        ▼           ▼           ▼           ▼           ▼
      gui         sound       holiday       lock       debug
        │           │           │        (独立)         │
        └─────┬─────┴─────┬─────┘                       │
              ▼           ▼                             ▼
          (root) ドメイン純関数・データ  ◀───────────────┘
     Reminder / RepeatSpec / ReminderFilter / EditFormLogic /
     AppDir / Config / ReminderStore / HolidayCheck ほか
```

パッケージ間依存（実依存・import 実測。コメント言及は除外済み）:

| from → to | 依存の中身 |
|---|---|
| `Main`(root) → 全パッケージ | 起動時に各サブシステムを生成・配線し、発火ループを回す唯一の場所 |
| `gui` → `(root)` / `debug` / `sound` | 器がドメイン純関数を呼ぶ／`DEB`でログ／`EditDialog`が`SND`で警告音 |
| `sound` → `(root)` / `debug` | `NotifyPatterns`が`Reminder`のPriを見る／各所で`DEB` |
| `holiday` → `(root)` | `AppDir`でパス解決／`HolidayCheck`を実装 |
| `debug` → `(root)` | `FileSink`だけが`AppDir`でログ出力先を決める |
| `lock` → （なし） | **完全自己完結＝kReminder非依存**。ファイルごと他アプリへコピー可 |

**load-bearing な構造＝祝日の依存性逆転**：祝日判定の抽象 `HolidayCheck`（`@FunctionalInterface`）は `(root)` に置き、実装（`HolidayTable`／`OverlayHolidayCheck`）は `holiday` パッケージにある。だから `RepeatSpec`・`EditFormLogic`（root）は `holiday` を知らずに `HolidayCheck` インターフェースだけに依存でき、`Main` が起動時に実装を注入する。root は holiday に依存しない（向きは holiday → root）。

---

## 2. 層の分離（設計の背骨）

kReminder は「純関数層／Swing器層／静的ファサード＋ワーカー／配線ハブ」の4層。純関数層は Swing・java.io・Gson を import しない＝時刻非依存の表駆動テストが書ける（handoff A3-8/A3-9）。

- **純関数・データ層**（`(root)` と各パッケージに散在・Swing/io非依存）
  ドメイン：`Reminder`（データ）・`RepeatSpec`（繰り返しエンジン）・`ReminderFilter`＋`FilterState`・`EditFormLogic`・`DateTimeFieldLogic`＋`DateTimeFieldState`＋`DateField`・`InstantTimeLogic`・`RemainFormat`・`CopyName`・`ExtName`・`Args`＋`ArgsParser`
  各パッケージの純関数：`gui/PopupBehaviors`＋`PopupBehavior`／`sound/NotifyPatterns`＋`NotifyPattern`＋`NotifyStep`＋`SoundMapParser`＋`SoundMapBuilder`＋`WavLoader`／`debug/DebFormat`／`holiday/HolidayCsvParser`＋`HolidayService`(判定部)＋`HolidayTable`＋`OverlayHolidayCheck`／`lock/InstanceInfo`
- **Swing器層**（`gui.*`）：`MainWindow`・`EditDialog`・`DateTimeField`・`InstantField`・`DebugPanel`・`ReminderTableModel`・`FatalErrorDialog`・`PanelSink`・`ExecTimeInput`(契約IF)
- **静的ファサード＋専用ワーカー1本**（どこからでも呼べる・本体を止めない・例外を呑む）
  `AppDir`（状態のみ・I/Oなし）／`DEB`＋`DebWorker`＋各Sink／`SND`＋`SoundWorker`／`Notifier`＋`NotifyHandle`／`SingleInstanceLock`
- **配線ハブ**：`Main` 一つ

---

## 3. パッケージ別クラス一覧

各表＝クラス名／責務1行／主な依存先（→は同/他パッケージの実依存。外部ライブラリは省略）。

### 3.1 `(root)` ken5005.kreminder — ドメイン純関数・データ・エントリ

| クラス | 責務 | 主な依存先 |
|---|---|---|
| `Main` | エントリ＋配線ハブ＋1秒発火ループ＋終了集約（§4） | ほぼ全部（AppDir/Args/ArgsParser/Config/ReminderStore/RepeatSpec/HolidayCheck/Reminder/ExtName ＋ gui/debug/sound/holiday/lock） |
| `Reminder` | リマインダー1件のデータ（6フィールド＋`Priority` enum） | （葉） |
| `RepeatSpec` | 繰り返し文法のパーサ＋次回発火計算＋`toJapanese()`（純関数） | → HolidayCheck |
| `ReminderFilter` | 一覧フィルタ判定の純関数群（§3.2短絡・バケツ・リードタイム） | → FilterState, Reminder, RepeatSpec |
| `FilterState` | フィルタ6トグル＋検索文字列（record・不変） | （葉） |
| `EditFormLogic` | 編集ダイアログ向け純関数（プレビュー・空コメント警告判定） | → HolidayCheck, RemainFormat, Reminder, RepeatSpec |
| `DateTimeFieldLogic` | 日時入力ウィジェットの状態遷移（純関数） | → DateField, DateTimeFieldState, EditFormLogic |
| `DateTimeFieldState` | 日時入力の状態（record・不変） | → DateField |
| `DateField` | 日時入力の6欄（enum） | （葉） |
| `InstantTimeLogic` | instant欄（相対/絶対）のパース純関数（絶対throwしない） | （葉） |
| `RemainFormat` | 残り時間を日本語1行に整形（純関数・Duration受け） | （葉） |
| `CopyName` | 複製時の`(copy)`採番規則（純関数） | （葉） |
| `ExtName` | Extend時の`(Ext) `マーカー規則（純関数） | （葉） |
| `Config` | フィルタトグルの永続化（`<base>/config.properties`） | → AppDir |
| `ReminderStore` | `reminders.json`のload/save（Gson＋LocalDateTime TypeAdapter） | → AppDir, Reminder |
| `AppDir` | ベースフォルダ（`--base`）を保持する静的ファサード・パス計算のみ | （葉・I/Oなし） |
| `Args` | 起動引数のパース結果（record） | （葉） |
| `ArgsParser` | 起動引数のパース純関数（Usage文言の置き場） | → Args |
| `HolidayCheck` | 祝日判定の抽象（`@FunctionalInterface`・`isHoliday`＋`NONE`） | （葉・実装はholiday） |
| `Const` | ユーザー調整用の定数集約（フォントサイズ・色） | （葉） |

### 3.2 `gui` — Swing の器

| クラス | 責務 | 主な依存先 |
|---|---|---|
| `MainWindow` | メイン画面（JTable・フィルタバー・行操作・Extend導線・デバッグパネル） | → EditDialog, ReminderTableModel, DebugPanel／root多数(Config/CopyName/ExtName/EditFormLogic/FilterState/Reminder/ReminderFilter/ReminderStore)／debug.DEB |
| `EditDialog` | リマインダー編集ダイアログ（器・書き戻しはMainWindow側） | → DateTimeField, InstantField, ExecTimeInput／root(EditFormLogic/HolidayCheck/Reminder)／sound.SND |
| `ExecTimeInput` | 「実行時刻」欄の契約IF（DateTimeField/InstantFieldが実装） | （契約） |
| `DateTimeField` | 欄分割の日時入力ウィジェット（器） | implements ExecTimeInput／root(DateField/DateTimeFieldLogic/DateTimeFieldState) |
| `InstantField` | instant入力欄（器・解決済み絶対日時文字列を返す） | implements ExecTimeInput／root.InstantTimeLogic |
| `ReminderTableModel` | JTableとList&lt;Reminder&gt;の橋渡し（Clock注入・1秒再描画） | → root(Reminder/RemainFormat/RepeatSpec) |
| `PopupBehaviors` | priority→発火ポップアップ挙動の対応表（純関数） | → PopupBehavior／root.Reminder |
| `PopupBehavior` | ポップアップ挙動（自動消滅時間＋Extend表示可否・record） | （葉） |
| `DebugPanel` | デバッグログ表示パネル（薄いビュー） | （葉） |
| `PanelSink` | DEBログをデバッグパネルへ流すsink | → debug.DEB, debug.LogSink |
| `FatalErrorDialog` | 致命エラーをloudに知らせ即終了する汎用ヘルパー | （葉） |

### 3.3 `sound` — 音声再生＋通知パターン

| クラス | 責務 | 主な依存先 |
|---|---|---|
| `SND` | 音声再生の静的ファサード（`SND.play`・直列再生） | → SoundRequest, SoundWorker／debug.DEB |
| `SoundWorker` | 再生依頼を1本のデーモンで直列処理するワーカー | → SoundRequest／debug.DEB |
| `SoundRequest` | 再生依頼（音声名＋音量・record） | （葉） |
| `WavLoader` | wavDir直下の.wav列挙（純関数） | （葉） |
| `SoundMapParser` | sound-map.properties⇔テーブルの変換（純関数） | （葉） |
| `SoundMapBuilder` | wav一覧＋mapから「音声名→File」を組む（純関数） | → SoundMapParser |
| `Notifier` | NotifyPatternを使い捨てデーモンで鳴らす静的ファサード | → NotifyHandle, NotifyPattern, NotifyStep, SND／debug.DEB |
| `NotifyHandle` | Notifier.start()が返す停止ハンドル（stop冪等） | （葉） |
| `NotifyPatterns` | priority→鳴らし方の対応表（純関数） | → NotifyPattern, NotifyStep／root.Reminder |
| `NotifyPattern` | 「鳴らし方」の手順書（steps＋repeatTail＋maxDuration・record） | → NotifyStep |
| `NotifyStep` | 1ステップ（音声名＋音量＋後続待ち・record） | （葉） |

### 3.4 `holiday` — 祝日サブシステム

| クラス | 責務 | 主な依存先 |
|---|---|---|
| `HolidayService` | 祝日パイプラインの束ね（loadInitial/refreshAsync・判定部は純関数） | → HolidayCache, HolidayCsvParser, HolidayFetcher, HolidayLog, HolidayState, HolidayStatus, HolidayTable／root(AppDir, HolidayCheck) |
| `HolidayTable` | CSVから作る祝日表（`HolidayCheck`実装＋getName） | implements root.HolidayCheck |
| `OverlayHolidayCheck` | base表に個別日を足し引きする不変オーバレイ | implements root.HolidayCheck |
| `HolidayOverride` | holiday_override.jsonを読みOverlayを組む | → OverlayHolidayCheck／root(AppDir, HolidayCheck) |
| `HolidayState` | 祝日の現在状態（check＋status・record） | → HolidayStatus／root.HolidayCheck |
| `HolidayStatus` | パイプライン健康度（OK/DEGRADED/NONE・enum） | （葉） |
| `HolidayCache` | 祝日CSVのキャッシュ読み書き | → root.AppDir |
| `HolidayCsvParser` | 内閣府CSV(MS932)のパース（純関数） | （葉） |
| `HolidayFetcher` | 内閣府CSVの取得（生バイト） | （葉） |
| `HolidayLog` | 祝日専用ログ（`<base>/holiday.log`） | → root.AppDir |

### 3.5 `lock` — 単一プロセスロック（自己完結・kReminder非依存）

| クラス | 責務 | 主な依存先 |
|---|---|---|
| `SingleInstanceLock` | ベースフォルダ単位で「同時1プロセス」を保証する機構 | → AcquireResult, Choice, ContentionHandler, Fallback, InstanceInfo |
| `ContentionHandler` | 競合時にホスト側UIへ問い合わせるコールバックIF | → Choice, Fallback, InstanceInfo |
| `AcquireResult` | acquire()の結果（enum） | （葉） |
| `Choice` | 競合時に利用者が下す選択（enum） | （葉） |
| `Fallback` | 退去要求が無応答だったときの対応（enum） | （葉） |
| `InstanceInfo` | ロック保持プロセスの情報（.instance.infoの内容・record） | （葉） |

### 3.6 `debug` — DEB デバッグログ基盤

| クラス | 責務 | 主な依存先 |
|---|---|---|
| `DEB` | 静的ファサード（`DEB.pr`でenqueue・即リターン） | → DebFormat, DebWorker, LogSink |
| `DebWorker` | キューから取り出し全シンクへ配る背景スレッド1本 | → LogSink |
| `DebFormat` | DEB用の純粋な文字列整形（Swing/io非依存） | （葉） |
| `LogSink` | シンクの契約IF（例外を投げない契約） | （契約） |
| `ConsoleSink` | 標準出力へ出すsink | implements LogSink |
| `FileSink` | `<base>/logs/DEB-*.txt`へ追記するsink | implements LogSink／root.AppDir |

（`gui/PanelSink` も LogSink 実装だが Swing を触るので gui パッケージ側。§3.2）

---

## 4. 起動シーケンス（`Main.main()`）

上から順に実行。**invokeLater の前（main本体）で動くもの**と**invokeLater 内（EDT）で動くもの**の境目が load-bearing（DEB初期化の位置＝A3-10）。

1. `ArgsParser.parse(args)` → `Args`。失敗は `abort()`（stderr＋Usage、コンソール無ければ`FatalErrorDialog`）
2. `--help`/`-h` なら Usage 出して `exit(0)`
3. **`AppDir.init(Path.of(basePath))`** ＝一手目。以後のパス解決の基点
4. `--base` フォルダの存在チェック → 無ければ `abort()`
5. **`instanceLock = new SingleInstanceLock(AppDir.base(), System.out::println)`** → 取得。競合時は3択ダイアログ、中止なら `exit(0)`（この時点DEB未初期化＝ロガーは`System.out`）
6. fake-clock 設定（`--fake-now` あれば `Clock.offset`）
7. `store = new ReminderStore(AppDir.resolve("reminders.json"))` → load
8. `HolidayOverride.load(...)` → override（`HolidayLog`へ）
9. `HolidayService.loadInitial(clock)` → 初期祝日状態 ※**ここまで main 本体＝DEB未init**
10. **`SwingUtilities.invokeLater`（EDT）**：
    - `MainWindow` 生成 → `panelSink` を取得
    - **`DEB.init(clock, ConsoleSink, FileSink, panelSink)`** ＝ここで初めてDEB稼働
    - `Config` load
    - window close / トレイExit → `shutdownApp()` を配線
    - 起動ログ `DEB.pr(...)`
    - **`timer = new Timer(1000, …)`**：毎秒 発火チェック＋再武装／`stopRequested()` 監視で他プロセスからの停止に応答 → `timer.start()`
11. `HolidayService.refreshAsync(...)` ＝バックグラウンドで内閣府CSV取得、完了で祝日状態を差し替え

**終了は `Main.shutdownApp()` に集約**（トレイExit／窓close／stop.request検知の3経路・冪等）。中身＝トレイ除去→`DEB.shutdown()`→`SND.shutdown()`→`instanceLock.release()`（→A3-14）。

---

## 5. この地図のメンテ規則

- **直すのは「クラスの新設・削除・依存の向きが変わる」PRのときだけ**。メソッドの増減・ロジック変更では触らない（それは即腐る＝書かない約束）。
- 責務1行や依存先が実ソースとズレたら**実ソースが正**。気づいたら直す。
- handoff とは棲み分ける：**構造＝この地図／設計判断の理由・急所＝handoff A3／仕様の逐語＝docs**。同じ事実を2箇所に書かない。

**CC指示文テンプレ**（クラス構成が変わるPRの締めに貼る）:

```
このPRでクラスの新設・削除・依存の向きの変更があれば、
docs/kReminder_コード地図_v1.md の該当表（§3のパッケージ別一覧）と
§1の依存表・必要なら§4起動シーケンスも直して。
メソッド単位の変更しかないなら地図は触らないこと。
文字コードUTF-8(BOM無し)・改行CRLF。
```
