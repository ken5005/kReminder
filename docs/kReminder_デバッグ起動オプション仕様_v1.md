# kReminder デバッグ起動オプション仕様書 v1.0（--fake-now / --data）

最終更新: 2026-07-11（chat36）
fake-clock（chat13）と `--data` 読み先切替（chat29・v0.6.1・PR#11）の合本。どちらもデバッグ用の起動時オプションで、**併用可**。

---

## 1. 概要

| オプション | 目的 |
|---|---|
| `--fake-now=<日時>` | 起動時刻を任意日時に偽装＝実日付を待たずに 祝日／トレイ色／override／残り時間の各段 を再現テストする。 |
| `--data=<絶対パス>` | reminders.json の**読み書き先**を差し替える＝本番データを汚さずダミーデータで動作確認する。 |

例：

```
java -jar kReminder-0.6.1-all.jar --fake-now=2026-12-31T23:59:50 --data=C:\tools2\java\IntelliJ\proj\kReminder\testdata\reminders_10.json
```

不正値はどちらも **stderr＋`exit(1)`**（原則2「怪しきは罰する」＝黙って変な状態で走らせない）。

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

## 3. --data（読み書き先切替）

### 3.1 書式と意味

- `--data=<絶対パス>`：reminders.json の読み書き先を差し替える。
- **絶対パスのみ**受ける。相対パスは `DataPathResolver` が `IllegalArgumentException` → stderr＋`exit(1)`。
- **指定パスは存在必須**。存在しないパスも stderr＋`exit(1)`（タイポで空リストの新ファイルを黙って作らせない＝本来の reminders.json との混同防止）。
- 未指定 → 既定パス（`%APPDATA%\kReminder\reminders.json`・APPDATA 無いと `user.home` フォールバック）。

### 3.2 読みも書きも同じ先（重要）

`--data` は読み先だけでなく **save 先も同じ**。発火（noticed 更新・再武装）や GUI 編集の save も指定先に書き戻る。

→ `--data=testdata\reminders_10.json` で起動して動かすと **git 管理ファイルが汚れる**。動作確認後は：

```
git checkout -- testdata/*.json
```

で戻す運用（chat29 実地確認済み）。使い捨てデータは `tmp/`（`.gitignore` 済み）に置く手もある（chat30 運用）。

---

## 4. クラス構成

| クラス | 種別 | 責務 |
|---|---|---|
| `ken5005.kreminder.DataPathResolver` | 純関数クラス | パス解決のみ（I/O なし）。JUnit5 表駆動テスト済み。 |
| `ken5005.kreminder.ReminderStore` | インスタンス | 指定 Path への load/save（static 決め打ちから chat29 でインスタンス化）。 |

**DataPathResolver**：

```java
public static Path resolve(String dataOpt)
// null      → defaultPath()
// 絶対パス   → その Path
// 相対パス   → IllegalArgumentException
public static Path defaultPath()
// APPDATA or user.home（パス決定はここに一本化＝二重化排除）
```

存在チェック＋`exit(1)` は Main 側の責務（I/O は純関数の外）。

**ReminderStore**：

```java
public ReminderStore()          // 既定パス
public ReminderStore(Path path) // 明示パス（--data 解決済み Path を注入）
public List<Reminder> load()
public void save(List<Reminder> reminders)
public Path getPath()
```

- Gson＋LocalDateTime 用カスタム TypeAdapter（`ISO_LOCAL_DATE_TIME`）・`disableHtmlEscaping()`（本体仕様＝仕様ドラフト v0 §6）。
- **store インスタンスは Main と MainWindow で共有**（chat29・リスト一本化の一部）＝`--data` 注入時に読み書き先 Path が食い違わない。

---

## 5. testdata（git 収録のダミーデータ・chat29）

repo ルート `testdata/` に収録＝`git pull` でデバッグデータごとマシン間を運べる：

| ファイル | 用途 |
|---|---|
| `testdata/reminders_4.json` | ①-c 目視用の4件版 |
| `testdata/reminders_10.json` | 一般デバッグの10件版 |

起動例：`--data=<repoルートの絶対パス>\testdata\reminders_10.json`（絶対パス必須に注意）。

---

## 6. 設計判断の要点

- **不正は全部 loud に落とす**（相対パス・不在パス・不正日時＝いずれも stderr＋exit(1)）：デバッグオプションこそ黙って変な状態で走ると被害が大きい（本番 reminders.json を巻き込む）。
- **パス解決は純関数・I/O は Main**：`DataPathResolver` を表駆動 TDD 可能に保つ。
- 2オプションは同族（起動時 args パース・不正即 exit・デバッグ専用）として同じ流儀で増やしていく。
