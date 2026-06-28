# kReminder

常駐型リマインダーアプリ。旧 `kconpnl` の `scedule` サブシステムをクリーン再設計。

## プロジェクト概要

- **言語**: Java 18
- **ビルド**: Gradle 9 (Groovy DSL) + shadowJar (fat jar)
- **パッケージ**: `ken5005.kreminder`
- **外部依存**: Gson 2.10.1
- **全体仕様**: `docs/kReminder_仕様ドラフト_v0.md`

## ビルド

```
gradlew shadowJar
```

`build/libs/kReminder-0.3.1-all.jar` が生成される。

## 起動

```
javaw -jar build/libs/kReminder-0.3.1-all.jar
```

コンソール不要の GUI アプリ（`javaw` 推奨）。システムトレイに常駐し、
右クリック → "Exit kReminder" で終了。

## v0.1 のスコープ（最小縦スライス）

実装済み:
- データモデル `Reminder`（6フィールド: fireAt / message / priority / action / noticed / repeat）
- 永続化: `%APPDATA%\kReminder\reminders.json`（Gson, UTF-8）。APPDATA 未設定時は `user.home` フォールバック
- 常駐ループ: Swing Timer 1秒ごとに全件チェック → `!noticed && fireAt <= now` で最小ポップアップ → `noticed=true` → 保存

## v0.3.1: 祝日サブシステム

パッケージ `ken5005.kreminder.holiday`。内閣府CSVから祝日を取得し、繰り返しエンジンの除外日判定に組み込む。

### クラス構成

| クラス | 役割 |
|---|---|
| `HolidayCheck` | `isHoliday(LocalDate)` の関数型 interface。`NONE` 実装（常 false）で縮退時の代替として使う |
| `HolidayTable` | `Map<LocalDate, String>` を保持するイミュータブルな実装。`isHoliday` と `getName` を提供 |
| `HolidayCsvParser` | `byte[]`（MS932）→ `Map<LocalDate, String>`。純関数・I/O なし |
| `HolidayCache` | `%APPDATA%\kReminder\holidays.json` の読み書き |
| `HolidayFetcher` | 内閣府 CSV を HTTP 取得して `byte[]` を返す。I/O のみ |
| `HolidayService` | オーケストレータ。`loadInitial()`（同期）と `refreshAsync()`（バックグラウンド）を提供 |

### キャッシュ

- 場所: `%APPDATA%\kReminder\holidays.json`
- 形式: `{ "fetchedAt": "2026-06-28T00:28:55", "holidays": { "2026-01-01": "元日", ... } }`
- 日付キーは ISO-8601（`YYYY-MM-DD`）、昇順ソート

### 鮮度判定と再取得

- `fetchedAt` から **1日以上**経過していれば起動時バックグラウンドで再取得
- 再取得は `HolidayService.refreshAsync()` がデーモンスレッドで実行。成功時のみ `AtomicReference<HolidayCheck>` を差し替える

### 縮退挙動（「通知を止めない」優先）

1. 今回取得した CSV が検証パス → 採用・キャッシュ更新
2. 取得失敗 or 検証失敗 → **前回キャッシュのまま継続**（土日＋祝日除外で動く）
3. キャッシュ自体も無い → **`HolidayCheck.NONE` で縮退**（土日のみ除外、祝日を見落とす可能性があるが鳴る）

検証チェック: サイズ 1KB〜1MB・件数 10件以上・当年1/1の存在。

### 失敗時の診断用ファイル

HTTP 取得成功後に検証/パース失敗した場合、生 CSV を `%APPDATA%\kReminder\holiday_last_failure.csv` に保存。
このファイルと stderr ログを見れば「内閣府がまた仕様変更した」等の原因を即診断できる。

### HolidayService シグネチャ（v0.3.2 以降の参照用）

`loadInitial(Clock)` / `refreshAsync(Consumer<HolidayCheck>, Clock)` — Clock を外から注入する設計。
`shouldRefresh(LocalDateTime, LocalDateTime)` は pure function のままで変更なし。

### v0.3.2 予定

- **トレイ色ステータス**: 緑（最新正常）/ 黄（キャッシュで稼働中）/ 赤（NONE に縮退）
- **手動オーバーライド**: `%APPDATA%\kReminder\holidays-override.json` で祝日の追加・除外

## v0.1 にまだ無いもの

- **発火アクション（action）**: フィールドは保持・JSON化するが実行しない
- **リスト管理 GUI**: 追加・編集・削除の UI なし。`reminders.json` を手編集で運用
- **移行ツール（旧 .sce → JSON）**: 実装なし

## 既知の TODO（コード内コメント参照）

起動時に過ぎた未通知 `fireAt` は即発火する（1秒ループの自然な結果）。
**完成版では「過去分の扱い（即発火 / スキップ / まとめ通知）」を必ず設計し直す**。

## デバッグ用 fake-clock

`--fake-now=YYYY-MM-DDTHH:mm:ss` を渡すと、指定日時を起点とした `Clock.offset(...)` で動作する。
リマインダー発火判定（`checkReminders`）と祝日サービス（鮮度判定・当年チェック・キャッシュ保存時刻）がすべて fake 時刻ベースになる。

```
java -jar build\libs\kReminder-0.3.1-all.jar --fake-now=2026-05-05T08:55:00
```

起動直後に以下のバナーがコンソールへ出力される:

```
[fake-clock] fake-now=2026-05-05T08:55:00  offset=PT...
```

不正値（フォーマット違い等）は stderr にエラーを出力して `exit(1)`。

### fake-clock での動作確認手順

`reminders.json` に `fireAt` を fake-now より過去に設定しておくと起動直後に発火する。

```json
[
  {
    "fireAt": "2026-05-05T08:54:55",
    "message": "fake-clock テスト",
    "priority": "Pri3",
    "action": "",
    "noticed": false,
    "repeat": ""
  }
]
```

```
gradlew shadowJar
java -jar build\libs\kReminder-0.3.1-all.jar --fake-now=2026-05-05T08:55:00
```

## テスト方法

`%APPDATA%\kReminder\reminders.json` を手編集して数秒後の `fireAt` を1件入れ、起動する。
ポップアップが出て OK で消え、`noticed: true` で保存されれば OK。

```json
[
  {
    "fireAt": "2026-06-24T15:30:00",
    "message": "テストリマインダー",
    "priority": "Pri3",
    "action": "",
    "noticed": false,
    "repeat": ""
  }
]
```
