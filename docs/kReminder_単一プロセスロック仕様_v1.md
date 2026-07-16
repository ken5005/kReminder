# kReminder 単一プロセスロック仕様書 v1.0

最終更新: 2026-07-16（chat48 で handoff A3-14 から分冊。lock パッケージ全6ファイルと実ソース照合済み）

---

## 1. 目的・設計思想

- **ベースフォルダ単位で「同時に1プロセスだけ」を保証する**機構。`--base` ベースフォルダ化（→デバッグ起動オプション仕様）と対の一組＝実用は常駐・デバッグは別ベースで同時起動可・**同一ベースの2重起動だけを防ぐ**。
- **再利用可能な純機構**：パッケージ `ken5005.kreminder.lock` は DEB／SND／Config／Swing を一切 import しない。ログは `Consumer<String>` 注入。オーケストレーション（取得→退去要求→待機→強制終了→info 書き→stop 監視）まで機構側が持ち、**競合時に「どの選択肢を取るか」だけをホストが `ContentionHandler` コールバックで注入する**（3択ダイアログ等の UI はホストの責務）。＝**ファイルごと他プロジェクト（kbitflyer 等）へコピー可能**。

## 2. クラス構成（package `ken5005.kreminder.lock`）

| クラス | 種別 | 責務 |
|---|---|---|
| `SingleInstanceLock` | 本体 | ロックの取得・解放・退去要求・強制終了までのオーケストレーション。 |
| `InstanceInfo` | record | 保持プロセスの情報（`pid`/`startedAt`/`base`）。`.instance.info` の内容そのもの。`render()`↔`parse()`。 |
| `Choice` | enum | 競合時の3択＝`STOP_EXISTING`／`CANCEL`／`STOP_BOTH`。 |
| `Fallback` | enum | 無応答時の2択＝`FORCE_KILL`／`CANCEL`。 |
| `AcquireResult` | enum | `acquire()` の結果＝`ACQUIRED`（起動してよい）／`ABORTED`（起動を中止すべき）。 |
| `ContentionHandler` | interface | 競合時コールバック＝`onExistingInstance(holder)→Choice`／`onNoResponse(holder)→Fallback`。ホスト側が実装。 |

## 3. 管理ファイル（base 直下・固定のドット頭汎用名）

| ファイル | 役割 | 生死 |
|---|---|---|
| `.instance.lock` | `FileLock` の台座。中身は空。 | **release でも消さない**（据え置き）。 |
| `.instance.info` | 保持者情報。`pid`/`startedAt`/`base` の `key=value` プレーンテキスト（UTF-8・Gson 非依存）。 | 取得時に書く／release で消す。 |
| `.stop.request` | 退去要求フラグ。中身は空。 | 要求時に置く／新ホルダーの `finishAcquire()` と release で消す。 |

- ドット頭は「機械が管理するファイル」の信号。**Windows では実際には隠れない**（隠し属性は DOS 属性で決まる）＝見えてよい逃げ道系なのであえて隠さない。
- **生死が非対称な理由**：`.instance.info`／`.stop.request` は用済みで消すが、**掴まれている `.instance.lock` を消すと OS 差で競合しうる**ため台座は据え置き（掴み＝FileLock 自体は OS が外す）。
- `.instance.info` の `parse()`：未知の行・空行は無視。必須キー（pid/startedAt/base）欠落、または pid 非数値は `IllegalArgumentException`。**読めない・壊れている場合は pid＝-1 の `InstanceInfo` として扱う**（→§5 force-kill 不可の分岐）。

## 4. API

```java
new SingleInstanceLock(Path base, Consumer<String> log)                    // 既定タイムアウト 5000ms
new SingleInstanceLock(Path base, Consumer<String> log, long stopTimeoutMs)

AcquireResult acquire(ContentionHandler handler)  // 取得を試み、競合ならコールバック経由で解決
boolean stopRequested()                           // .stop.request が在るか（既存側が定期ポーリング）
void release()                                    // info/stop.request 削除＋FileLock 解放（冪等・best-effort）
```

- **`stopTimeoutMs`**：退去要求後にロック解放を待つ上限。ms 指定・負値は `IllegalArgumentException`・**`0` は「待たない」**＝1回試して空かなければ即・無応答扱い（KILL ではない）。既定 `DEFAULT_STOP_TIMEOUT_MS = 5000L`。内部ポーリング間隔は非公開（250ms）。
- **ロックの実体＝`FileChannel.tryLock()`**（`.instance.lock` を CREATE|WRITE で open）。同一 JVM から同ファイルを2回 tryLock すると null ではなく `OverlappingFileLockException` が飛ぶ＝これも「取れなかった」として扱う（→§8 テスト制約）。
- **取得成功時の共通後処理 `finishAcquire()`**＝残存 `.stop.request` 削除 → `.instance.info` 書き込み → **shutdown hook として `release()` を登録**（正常終了経路の保険。Windows の強制終了では hook は走らない＝それでも FileLock は OS が外すので実害なし）。

## 5. 競合時のフロー

```
acquire(handler)
 ├─ tryLock 成功 → finishAcquire → ACQUIRED
 └─ 失敗 → .instance.info を読み holder 特定 → handler.onExistingInstance(holder)
     ├─ CANCEL        → ABORTED
     ├─ STOP_BOTH     → .stop.request を置くだけで自分も起動しない → ABORTED（「今日はもう使わない」）
     └─ STOP_EXISTING → .stop.request を置く → stopTimeoutMs まで 250ms 間隔で tryLock
         ├─ 取れた → finishAcquire → ACQUIRED
         └─ 無応答 → handler.onNoResponse(holder)
             ├─ CANCEL     → ABORTED
             └─ FORCE_KILL → 段階式強制終了（下記）
```

- **段階式強制終了**：`destroy()` → 250ms×4回 tryLock リトライ → 効かなければ `destroyForcibly()` → 250ms×4回リトライ（`FORCE_KILL_RETRIES=4`）。どの段階でも取れ次第 `finishAcquire` して ACQUIRED。**Windows では `destroy()` も `destroyForcibly()` も内部は TerminateProcess ＝ destroy 段階で即死・2段目は他 OS 向けの保険**。
- **pid が既に存在しない**（相手はもう死んでいる）→ 破壊操作をスキップし tryLock を1回だけ試す。**pid≦0**（info が読めなかった／壊れていた）→ force-kill 不可としてログを出し ABORTED。

## 6. 設計根拠（load-bearing）

- **`FileLock` を選んだ理由＝OS がプロセス死亡時に必ず解放する**＝クラッシュしても幽霊ロックが原理的に残らない（当初構想の「Lock 手動解除機能」は不要になった）。
- **`finishAcquire()` 冒頭で `.stop.request` を掃除する理由**（chat47 の目視で発見した自殺バグの恒久修正）：force-kill で相手が即死すると `release()` が走らず `.stop.request` が残る。掃除しないと**新ホルダーが自分の定期ポーリングでそれを拾って誤って自殺する**。ホルダーになった時点で残存 stop.request は「前ホルダー宛て or 自分が書いたもの」＝用済み。stale ファイルの自己修復も兼ねる。
- **`release()` の順序＝info 削除 → stop.request 削除 → FileLock 解放 → channel クローズ**：先にロックを手放すと、待っていた別プロセスが取得して `writeInfo()` した直後に、退場側の遅延した削除が**相手の新しい `.instance.info` を消してしまう**競合がありうるため。

## 7. ホスト統合の要件（kReminder での実例）

1. **取得はアプリ初期化の最初期に**（kReminder＝`Main` の `--base` 存在チェック直後・fake-clock 設定や holiday ロード・invokeLater より前）。ログ基盤が未初期化の時点なら `System.out::println` を注入。
2. **既存側の受け口**＝定期的に `stopRequested()` を見て**整然終了**する（kReminder＝1秒 Timer の先頭で拾い `shutdownApp()`→`System.exit(0)`）。
3. **すべての終了経路で `release()` を呼ぶ**（kReminder＝トレイ Exit／窓 close／stop.request 検知の3経路を `shutdownApp()` 1メソッドに集約・冪等ガード）。
4. **`ContentionHandler` の UI はホストの流儀で**（kReminder＝`invokeAndWait` で EDT に出す3択ダイアログ・alwaysOnTop な無装飾 JFrame を親にして最前面・**既定選択は安全側 CANCEL・×/Esc も CANCEL**）。

## 8. テスト

- **純関数部・ファイル操作部のみ JUnit**：`InstanceInfo` render↔parse／必須キー欠落・pid 非数値で IAE／未知行無視／空 dir で acquire→ACQUIRED＋管理ファイル生成／release の掃除／負タイムアウト IAE・0 は可。
- **競合・stop.request・強制終了の経路はユニット化不可＝目視で担保**：FileLock は JVM 単位＝同一 JVM 内で2プロセス分の競合を再現できない（`OverlappingFileLockException` になる）。無応答の再現は IntelliJ の Debug 起動＋Pause で相手プロセスを凍結する。
