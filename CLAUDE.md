# kReminder

常駐型リマインダーアプリ。旧 `kconpnl` の `scedule` サブシステムをクリーン再設計。

## プロジェクト概要

- **言語**: Java 18
- **ビルド**: Gradle 9 (Groovy DSL) + shadowJar (fat jar)
- **パッケージ**: `ken5005.kreminder`
- **外部依存**: Gson 2.10.1
- **仕様**: `docs/` 配下の仕様書一式が正典。設計判断で迷ったらコードより仕様書を先に見る

## ビルド

```
gradlew shadowJar
```

`build/libs/kReminder-<version>-all.jar` が生成される（version は `build.gradle` を見る）。
`build/libs` には過去バージョンの jar が消えずに残るので、最新版はタイムスタンプで判別すること。

## 起動

```
javaw -jar build/libs/kReminder-<version>-all.jar
```

コンソール不要の GUI アプリ（`javaw` 推奨）。システムトレイに常駐し、
右クリック → "Exit kReminder" で終了。

## デバッグ用 fake-clock

`--fake-now=YYYY-MM-DDTHH:mm:ss` を渡すと、指定日時を起点とした `Clock.offset(...)` で動作する。
アプリ内の時刻取得はすべてこの `Clock` 経由なので、発火判定も含めて fake 時刻ベースになる。

```
java -jar build\libs\kReminder-<version>-all.jar --fake-now=2026-05-05T08:55:00
```

起動直後に以下のバナーがコンソールへ出力される:

```
[fake-clock] fake-now=2026-05-05T08:55:00  offset=PT-1315H-5M
```

不正値（フォーマット違い等）は stderr にエラーを出力して `exit(1)`。

**本番で誤って `--fake-now` 付きで起動した場合、バナーが出ていたら引数を外して再起動すること。**

### 動作確認手順

`reminders.json` に `fireAt` を「現在時刻（fake-clock 使用時は fake-now）より数秒過去〜数秒後」で1件入れて起動する。
ポップアップが出て OK で消え、`noticed: true` で保存されれば OK。

```
[
  {
    "fireAt": "2026-05-05T08:54:55",
    "message": "テストリマインダー",
    "priority": "Pri3",
    "action": "",
    "noticed": false,
    "repeat": ""
  }
]
```

## 運用ルール

- 作業ディレクトリ（kReminder リポジトリ）外のファイルを参照する必要が生じても、
  自分で `find` / `ls` 等で探しに行かない。まずユーザーに「このファイルを見たい」と
  伝えて指示を仰ぐこと。プロジェクト外へのアクセスは原則しない。
- `docs/覚書.txt` はユーザー個人のメモ。読み書き・コミット対象にしないこと
  （変更を検知しても無視してよい）。

## コード地図

`docs/kReminder_コード地図_v1.md` は、どのクラスが何を担い誰に依存するか（クラス／パッケージ／依存の向き）の全体像＝構造の索引。着手前に、いじるクラスの依存をここで当たると探索が減る。

**このPRでクラスの新設・削除、または依存の向きの変更を行ったら、地図の該当箇所（§1 依存表・§3 パッケージ別一覧、必要なら §4 起動シーケンス）も必ず更新すること。** メソッド単位の変更しかないなら地図は触らない（それは即腐るので載せない約束）。更新の詳細規則は地図の §5 を見る。

## GUI 目標アーキテクチャ（全スライス共通・責務境界を漏らさない）

小さい勉強アプリだが、MVC の責務境界は徹底する。層は上→下の一方向依存：

- **ドメイン**: `Reminder`（不変データ）, `RepeatSpec`（純関数）
- **永続化**: `ReminderStore`（JSON load/save）— UI を一切 import しない
- **表示ロジック（純関数）**: `toJapanese` / 残り時間整形 / `isVisible`・`bucketOf`・`leadWindowOf` / ソート比較器。**Swing も java.io も Gson も import しない**＝全てユニットテスト可能に保つ
- **ビュー**: `MainWindow` 等の Swing ウィジェット。描画と入力受けのみ・業務判断をしない。`ReminderTableModel`（`AbstractTableModel`）がドメインの `List<Reminder>` と `JTable` を橋渡しする
- **コントローラ**: ボタン/メニュー操作を受けて Store とモデルを更新。ビューはリスナ経由で通知するだけ

### 境界ルール

- ビューに業務ロジックを置かない。整形は純関数へ、データ変更はコントローラへ委譲する
- 純関数クラスは Swing / java.io / Gson を import しない（テストで縛れる状態を保つ＝この不変条件が MVC の生命線）
- `ReminderStore` は Swing を import しない
- 新機能は「**純関数＋テスト → 薄いビュー配線**」の順で足す
- **過剰設計はしない（YAGNI）**。将来分は抽象化を先に建てるのではなく、seam（差し込み口）だけ開けておく。層を増やすことが MVC ではなく、責務の境界を漏らさないことが MVC
