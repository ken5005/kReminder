# kReminder デバッグ起動オプション仕様書 v1.0（--fake-now / --base）

最終更新: 2026-07-15（chat46・base-dir 化）
fake-clock（chat13）と、reminders.json 単体の読み先切替だった `--data`（chat29・v0.6.1・PR#11）を
ベースフォルダ切替 `--base`（chat45〜46）へ発展させた版の合本。

**`--base` は本番・デバッグ共通の必須オプション、`--fake-now` はデバッグ専用**（併用可）。

---

## 1. 概要

| オプション | 目的 |
|---|---|
| `--base=<フォルダ>` | アプリが使う全データ（reminders.json・config.properties 等）の置き場所（ベースフォルダ）を指定する。**必須**。 |
| `--fake-now=<日時>` | 起動時刻を任意日時に偽装＝実日付を待たずに 祝日／トレイ色／override／残り時間の各段 を再現テストする。 |

例（本番運用）：

```
javaw -jar kReminder-0.8.0-all.jar --base=C:\tools2\java\IntelliJ\proj\kReminder\data
```

例（デバッグ・fake-clock併用）：

```
java -jar kReminder-0.8.0-all.jar --base=testdata --fake-now=2026-12-31T23:59:50
```

`--base` は相対パスも可（カレントフォルダ基準）。ショートカットから起動する場合は絶対パスを書くこと。

不正値・未指定はどちらも **stderr＋`exit(1)`**（原則2「怪しきは罰する」＝黙って変な状態で走らせない）。

---

## 2. --fake-now（fake-clock）

### 2.1 書式と意味

- `--fake-now=YYYY-MM-DDTHH:mm:ss`（`ISO_LOCAL_DATE_TIME`）。
- Java 標準 `java.time.Clock` を使い、`Clock.offset(systemDefaultZone(), Duration.between(realNow, fakeNow))` を生成。
  - **起動時刻を fake にして、以後は実時間と同速で進む**。`Clock.fixed`（時が止まる）は常駐アプリに不適なので不採用。
- 未指定 → `Clock.systemDefaultZone()`（従来と等価）。
- 不正値 → stderr＋`exit(1)`。
- fake 時は起動直後にバナーを1回出す（`[fake-clock] fake-now=... offset=...`）。

### 2.2 Clock の注入先

`Main` の `clock` を以下へ注入：

- 発火ループ（1秒 Timer の `checkReminders`）
- `HolidayService`（鮮度判定・当年判定も fake 基準）
- `ReminderTableModel`（コンストラクタ注入・残り時間列も fake 基準）
- `DEB.init(clock, ...)`（ログ時刻も fake 基準）

### 2.3 注意

- **起動〜窓表示のラグで数秒進む**：境界ぴったりの目視（例「残り6日」）は、表示時点で5日台に切り捨てられていることがある。境界確認は数秒余裕を持たせた fake 値にする。
- 既存の `shouldRefresh` 等のテストは `now` 引数受けの純関数なので fake-clock の影響を受けない。

---

## 3. --base（ベースフォルダ切替）

### 3.1 書式と意味

- `--base=<フォルダ>`：アプリが使う全データの置き場所を指定する。**必須**（無指定は stderr＋Usage＋`exit(1)`）。
- **相対パスも可**（カレントフォルダ基準）。内部で絶対パスに正規化して保持する（`AppDir.init`）。
- **指定フォルダは存在必須**。存在しない場合は stderr＋Usage＋`exit(1)`（自動作成はしない＝タイポで空の環境が生えて
  「予定が消えた」と誤解する事故を防ぐ）。
- フォルダ配下の個々のファイルは不在でよい（後述の表のとおり、無ければ空リスト起動・デフォルト値・雛形書き出し等、
  従来どおりの挙動になる）。
- **`%APPDATA%\kReminder` の既定パスは廃止済み**。`--base` を省略して「うっかり本番環境がもう1個静かに起動する」
  事故を起こさないための設計。

### 3.2 本番・デバッグの運用

- **本番ベース** = repo直下 `data/`
- **デバッグベース** = repo直下 `testdata/`

本番運用中のプロセス（`--base=data`）を動かしっぱなしにしたまま、デバッグ用プロセス（`--base=testdata`）を
別プロセスで同時起動できる。ベースフォルダが分かれていれば読み書き先が衝突しないため。

### 3.3 読みも書きも同じ先（重要）

`--base` 配下の各ファイルは読み先だけでなく **save 先も同じ**。発火（noticed 更新・再武装）や GUI 編集の save、
config・sound-map・祝日キャッシュ・ログの類もすべて同じベースフォルダに書き戻る。

→ `--base=testdata` で起動して動かすと **git 管理下のダミーデータが汚れる**。動作確認後は：

```
git checkout -- testdata/*.json testdata/*.properties
```

で戻す運用（chat29 由来）。使い捨てデータは `tmp/`（`.gitignore` 済み）等、git 管理外のベースを指す手もある。

---

## 4. ベースフォルダ配下に置かれるファイル一覧

| ファイル / フォルダ | 用途 | 不在時の挙動 |
|---|---|---|
| `reminders.json` | リマインダー本体データ | 空リストで起動（save 時に新規作成） |
| `config.properties` | フィルタトグル・`snd.wav.dir` 等の設定 | デフォルト値で起動 |
| `sound-map.properties` | 通知音ファイル名の割当マップ | 雛形を書き出す |
| `holidays.json` | 祝日データのキャッシュ | キャッシュ無し扱い（`HolidayStatus.NONE`）で起動、後で取得を試みる |
| `holiday_override.json` | 祝日の手動 add/remove オーバーレイ | 空オーバーレイ（無効化なし）で起動 |
| `holiday.log` | 祝日サブシステムの動作ログ | 必要時に新規作成 |
| `holiday_last_failure.csv` | 祝日データ取得失敗時の生CSV保存（診断用） | 失敗が起きるまで作られない |
| `logs/` | DEB（デバッグパネル）ログの日次ファイル置き場 | 必要時に `createDirectories` される |

パス計算はすべて `ken5005.kreminder.AppDir`（`AppDir.init(Path)` で起動時に1回だけ確定、
`AppDir.resolve(name)` で各ファイルを解決する静的ファサード）に一本化されている。

---

## 5. クラス構成

| クラス | 種別 | 責務 |
|---|---|---|
| `ken5005.kreminder.AppDir` | 静的ファサード | ベースフォルダの保持・解決のみ（I/O なし）。`AppDirTest` で表駆動テスト済み。 |
| `ken5005.kreminder.ArgsParser` | 純関数クラス | 起動引数のパースのみ（I/O なし）。`ArgsParserTest` で表駆動テスト済み。 |
| `ken5005.kreminder.ReminderStore` | インスタンス | 指定 Path への load/save。 |

**AppDir**：

```java
public static void init(Path baseDir)
// main()の一手目（--help判定の直後）で1回だけ呼ぶ。toAbsolutePath().normalize()して保持する
public static Path base()
// 未initならIllegalStateException（fail-fast）
public static Path resolve(String name)
// base().resolve(name)
```

存在チェック（フォルダかどうか）＋`exit(1)` は Main 側の責務（I/O は静的ファサードの外）。

**ReminderStore**：

```java
public ReminderStore(Path path) // 明示パス（AppDir.resolve("reminders.json")を注入）
public List<Reminder> load()
public void save(List<Reminder> reminders)
public Path getPath()
```

- Gson＋LocalDateTime 用カスタム TypeAdapter（`ISO_LOCAL_DATE_TIME`）・`disableHtmlEscaping()`（本体仕様＝仕様ドラフト v0 §6）。
- **store インスタンスは Main と MainWindow で共有**（chat29・リスト一本化の一部）＝読み書き先 Path が食い違わない。

---

## 6. 引数パースの現況

- 未知の引数（`--base`/`--fake-now`/`--help`/`-h` 以外）は stderr＋Usage＋`exit(1)`。
- `--help` / `-h` はどこにあっても最優先。他の引数が不正・`--base` 未指定でも Usage を表示して `exit(0)`
  （正常終了。エラー扱いではないので FatalErrorDialog は出さない）。
- `--base` / `--fake-now` の重複指定はどちらも stderr＋`exit(1)`。
- コンソールが無い起動（`javaw` 等、`System.console() == null`）では、stderr の内容を `FatalErrorDialog` でも表示する
  （`Main.abort`）。

---

## 7. testdata（git 収録のダミーデータ・chat29）

repo ルート `testdata/` に収録＝`git pull` でデバッグデータごとマシン間を運べる：

| ファイル | 用途 |
|---|---|
| `testdata/reminders_4.json` | ①-c 目視用の4件版 |
| `testdata/reminders_10.json` | 一般デバッグの10件版 |

`testdata/` は §3.2 のとおり `--base=testdata` で指すデバッグベースフォルダそのもの。
起動確認では `reminders_4.json` / `reminders_10.json` を `testdata/reminders.json` にコピーしてから使う。

---

## 8. 設計判断の要点

- **不正は全部 loud に落とす**（`--base` 未指定・存在しないフォルダ・不正日時＝いずれも stderr＋Usage＋exit(1)）：
  デバッグオプションこそ黙って変な状態で走ると被害が大きい（本番データを巻き込む）。
- **パス解決は静的ファサード・I/O は Main**：`AppDir` を表駆動 TDD 可能に保つ。
- **`%APPDATA%\kReminder` 既定は廃止**：`--base` を省略した「うっかり本番相当プロセスの多重起動」を起動時点で弾く。
- 2オプションは同族（起動時 args パース・不正即 exit）だが守備範囲は異なる：`--base` は本番・デバッグ共通の必須項目、
  `--fake-now` はデバッグ専用のオプション項目。
