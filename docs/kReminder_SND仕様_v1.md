# kReminder 音声サブシステム仕様書 v1.0

最終更新: 2026-07-10

---

## 1. 概要・設計思想

リマインダーの発火通知・操作フィードバック等に効果音を鳴らすサブシステム。

**設計思想**（kReminder 全体の設計原則と同じ軸）：

- **どこからでも鳴らせる**：静的ファサード `SND.play(name, volume)` を1行書くだけ。呼び出し元は再生の仕組みを知らなくていい（DEBと同構造）。
- **本体を絶対止めない**：再生はデーモンスレッドに委譲。呼び出し元は enqueue して即返る。例外は呑んでログに出す。
- **前の音が終わってから次を鳴らす**：複数リクエストが重なっても順次再生。同時発音なし。
- **エラーは隔離する**：音声再生の失敗がリマインダー通知本体を巻き込まない。

---

## 2. クラス構成

パッケージ: `ken5005.kreminder.sound`

| クラス | 種別 | 責務 |
|---|---|---|
| `SND` | 静的ファサード | `play` / `init` / `shutdown` の窓口。DEB と同構造。 |
| `SoundWorker` | デーモンスレッド | キューを順次処理し javax.sound.sampled で再生。 |
| `SoundRequest` | record | 音声名 + ボリューム のイミュータブル入れ物。 |
| `WavLoader` | 純関数クラス | wavディレクトリをスキャンして `Map<String, File>` を返す。 |

外部依存ゼロ（`javax.sound.sampled` は Java 標準ライブラリ）。

---

## 3. API

### 3.1 SND.init(Path wavDir)

```java
public static void init(Path wavDir)
```

- `WavLoader.load(wavDir)` でマップを構築し `SoundWorker` を起動する。
- Main 起動時に1回だけ呼ぶ（`DEB.init` の直後が自然）。
- 2回以上呼ばれた場合は2回目以降を無視（warn ログ）。
- wavDir が存在しない / 読めない場合は DEB.pr でエラーログを出し、以降の `play` 呼び出しは全て silent drop。

### 3.2 SND.play(String name, float volume)

```java
public static void play(String name, float volume)
```

- `SoundRequest(name, volume)` をキューに積む。
- `init` 前の呼び出しは silent drop（例外を出さない）。
- volume の有効範囲は 0.0f〜1.0f。範囲外は clamp して使う（warn ログ）。
- キューが上限（30件）を超えていたら drop して DEB.pr でログ（→§4）。

### 3.3 SND.shutdown()

```java
public static void shutdown()
```

- `SoundWorker` に停止を伝え、現在再生中の音が終わったら終了。
- Main の窓 close・トレイ Exit 両経路で呼ぶ（DEB.shutdown と同タイミング）。

---

## 4. キュー仕様

- **実装**: `LinkedBlockingQueue<SoundRequest>`（capacity=30）
- **上限**: 30件。`offer` が false（キュー満杯）の場合は drop + DEB.pr でログ。
- **順序**: FIFO。先に積まれたリクエストから順に処理。
- **同時発音なし**: SoundWorker は1件の再生が完全に終わってから次を `take()`。
- **drop の思想**: 1秒に100件来るような異常リクエストは捨てていい。ユーザーが気づかないような過多は音が重なるより無音の方がマシ。

---

## 5. wavディレクトリ設定

### 5.1 config.properties のキー

```
snd.wav.dir=C:\\tools2\\etc\\wav
```

- キー名: `snd.wav.dir`
- 値: wavファイルが入っているディレクトリの絶対パス。
- **起動時に `Config` 経由で読む**。キーが存在しない場合はデフォルト値 `C:\tools2\etc\wav` を config.properties に**書き込んでから**使う（「設定ファイルが説明的であること」方針）。
- 値が存在する場合はそのまま使う（パスの存在確認は `SND.init` 側で行う）。

### 5.2 デフォルト値の書き込みタイミング

Main 起動時 → Config.load() → `snd.wav.dir` が無ければ即 `Config.save()` → `SND.init(resolvedPath)` の順。

---

## 6. wavロード（WavLoader）

### 6.1 スキャン仕様

```java
public static Map<String, File> load(Path wavDir)
```

- `wavDir` 直下の `*.wav` / `*.WAV`（大文字小文字不問）を列挙。
- ファイル名から拡張子を除いた文字列を**音声名**とする。
  - 例: `ごん.wav` → `"ごん"`, `カッ.WAV` → `"カッ"`
  - 拡張子の除去は `.wav` / `.WAV` 両方を処理（`String#toLowerCase` で判定）。
- マップは `Collections.unmodifiableMap` でラップして返す。
- ディレクトリが空・wavが0件の場合は空マップを返す（例外を投げない）。
- 純関数（副作用なし・I/O は引数 wavDir 経由のみ）。

### 6.2 仮音声名マップ（初期値・本人が後で名前を手直し）

| 音声名（現在） | ファイル名 |
|---|---|
| daisyuuryou | daisyuuryou.wav |
| notify | notify.wav |
| ＵＰ！ | ＵＰ！.wav |
| カシャッ | カシャッ.wav |
| カッ | カッ.WAV |
| ごん | ごん.wav |
| ショワッ | ショワッ.wav |
| ダメージ | ダメージ.wav |
| ちょい下げ | ちょい下げ.wav |
| ちょい上げ | ちょい上げ.wav |
| ぴぃ | ぴぃ.wav |
| フォルダ開く | フォルダ開く.wav |
| ぽん | ぽん.wav |
| ポッ | ポッ.wav |
| 大お知らせ | 大お知らせ.wav |
| 大ころろん | 大ころろん.wav |
| 大終了 | 大終了.wav |
| 大注意 | 大注意.wav |
| 抑えたカシャ | 抑えたカシャ.wav |

音声名はファイルシステムを変えずにコード側（設定や定数）で管理する。
ファイル名を変えなくていいよう、音声名とファイル名のマッピングは将来 `sound-map.properties` 等に外出しする余地を残す（現バージョンはファイル名ベース自動生成）。

---

## 7. エラー処理

| 状況 | 処理 |
|---|---|
| 未知の音声名 | `DEB.pr(new RuntimeException("未定義の音声名: " + name))` + "ごん" でフォールバック再生 |
| "ごん" 自体がマップに無い | `DEB.pr(new RuntimeException("音声ファイルが無い: ごん.wav"))` + silent drop |
| 再生中に例外 | 例外を呑んで `DEB.pr(e)` + 次のリクエストへ進む |
| キュー満杯（30件超） | drop + `DEB.pr` でログ（RuntimeException 不要・情報レベルで可） |
| wavDir 不在 / 読めない | `DEB.pr(new RuntimeException(...))` + init 失敗扱い（以降 play は silent drop） |
| volume 範囲外 | clamp(0.0f, 1.0f) + `DEB.pr` で warn |

**StackTrace 付きの意図**：`SND.play` はどこからでも呼ばれるため、`RuntimeException` の StackTrace で呼び出し元を特定できるようにする。DEB の `pr(Throwable)` オーバーロードがそのまま使える。

---

## 8. Main 配線

```
Main.main()
  └─ invokeLater {
       Config.load()                         // snd.wav.dir を読む（無ければ書き込み）
       DEB.init(clock, ...)                  // 先に DEB を起動
       SND.init(resolvedWavDir)              // 音声サブシステム起動
       new MainWindow(...)
     }

窓 close / トレイ Exit
  └─ DEB.shutdown()
     SND.shutdown()                          // DEB より後に呼んでも可
```

---

## 9. 実装スライス（Claude Code 指示文用・参考）

| Step | 内容 | TDD |
|---|---|---|
| step1 | `WavLoader` 純関数 + JUnit5 表駆動テスト | ○ |
| step2 | `SoundRequest`(record) + `SoundWorker` + `SND` ファサード | △（再生部分は目視） |
| step3 | `Config` に `snd.wav.dir` 対応追加 + Main 配線 | 目視 |
| step4 | 目視確認（各音声を SND.play で順次再生・StackTrace確認） | 目視 |

---

## 10. 将来拡張（現バージョンのスコープ外）

- Pri-1〜5 と音声名のマッピング（⑤ポップアップ配線スライスで追加予定）
- 音声名↔ファイル名の外部マッピングファイル（`sound-map.properties`）
- 同時発音・チャンネル管理（現状は完全直列）
- ループ再生
