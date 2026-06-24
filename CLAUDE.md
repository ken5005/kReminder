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

`build/libs/kReminder-0.1-all.jar` が生成される。

## 起動

```
javaw -jar build/libs/kReminder-0.1-all.jar
```

コンソール不要の GUI アプリ（`javaw` 推奨）。システムトレイに常駐し、
右クリック → "Exit kReminder" で終了。

## v0.1 のスコープ（最小縦スライス）

実装済み:
- データモデル `Reminder`（6フィールド: fireAt / message / priority / action / noticed / repeat）
- 永続化: `%APPDATA%\kReminder\reminders.json`（Gson, UTF-8）。APPDATA 未設定時は `user.home` フォールバック
- 常駐ループ: Swing Timer 1秒ごとに全件チェック → `!noticed && fireAt <= now` で最小ポップアップ → `noticed=true` → 保存

## v0.1 にまだ無いもの

- **繰り返し（repeat）**: フィールドは保持・JSON化するが解釈しない。すべて一回限り扱い
- **発火アクション（action）**: フィールドは保持・JSON化するが実行しない
- **祝日サポート**: 実装なし（仕様は `docs/kReminder_仕様ドラフト_v0.md` 第5節参照）
- **リスト管理 GUI**: 追加・編集・削除の UI なし。`reminders.json` を手編集で運用
- **移行ツール（旧 .sce → JSON）**: 実装なし

## 既知の TODO（コード内コメント参照）

起動時に過ぎた未通知 `fireAt` は即発火する（1秒ループの自然な結果）。
v0.1 ではこれを許容するが、**完成版では「過去分の扱い（即発火 / スキップ / まとめ通知）」を必ず設計し直す**。

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
