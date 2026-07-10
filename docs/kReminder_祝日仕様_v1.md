# kReminder 祝日サブシステム仕様書 v1.0

最終更新: 2026-07-11（chat36）
仕様ドラフト v0 §5 を吸収し、実装現況（v0.3.1＝chat12・v0.3.2 拡充＝chat15）に合わせて更新した正典。v0 §5 はポインタ化。実装との差異はコード裏取り済み（→§10 に v0 ドラフトからの変更点）。

---

## 1. 概要・目的

「平日のみ」「毎月N日・土日祝なら前後の営業日へ」等を正しく動かすための祝日判定。

**背骨＝「I/O と純関数の分離」**。設計原則は本体と同じ：自動で賢ぶらない／怪しきは罰する／最後の正常データで粘る／証拠を残して人を呼ぶ。

---

## 2. データソース（内閣府 CSV・実物確認済み 2026-06）

| 項目 | 実態 |
|---|---|
| URL（primary） | `https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv` |
| URL（fallback） | `https://www8.cao.go.jp/chosei/shukujitsu/shukujitsu.csv`（過去に y↔h で揺れた実績） |
| 文字コード | **Shift_JIS（MS932 / windows-31j）** |
| 形式 | ヘッダ行＋`YYYY/M/D,名称` の2列・CRLF |
| 日付 | スラッシュ・ゼロ埋めなし（過去にハイフン／ゼロ埋め有の時期あり） |
| 収録範囲 | 1955年〜**当年+1年**（遠い未来は無い） |
| 内容 | 祝日＋振替休日・国民の休日も込み（除外用途には好都合） |
| ライセンス | CC-BY |

取得は `java.net.http.HttpClient`（標準API・外部依存ゼロ）。primary→fallback の順に試行し、HTTP 200 のみ採用（接続タイムアウト 10秒・リクエスト 30秒）。

---

## 3. 公開APIと使われ方

- 判定の窓口は `HolidayCheck`（`isHoliday(LocalDate)`）。
- 繰り返しエンジン `RepeatSpec.next/nextAfter` に渡され、**祝日 → 曜日 idx＝0（日曜扱い）**として効く。
  - つまり「祝日も自動除外」は正確には「祝日＝日曜扱い・**`ex` に 0（日曜）を含む時だけ結果的に除外**」。
- Main は `AtomicReference<HolidayState> holidayRef`（初期 NONE）で現在状態を保持し、発火後再武装で `nextAfter(fireAt, now, holidayRef.get().check())` に渡す。

---

## 4. クラス構成（5+5クラス・パッケージ `ken5005.kreminder.holiday`）

**v0.3.1（基盤5クラス）**：

| クラス | 責務 |
|---|---|
| `HolidayCsvParser` | MS932 バイト列 → `Map<LocalDate,String>`。ゆるいパース（`/`・`-`・ゼロ埋め有無を吸収、不正日付行はスキップ）。 |
| `HolidayTable` | パース結果を包む不変の判定テーブル（`HolidayCheck` 実装）。 |
| `HolidayFetcher` | CSV ダウンロード（試行順 syukujitsu → shukujitsu）。 |
| `HolidayCache` | `%APPDATA%\kReminder\holidays.json`（正規化データ＋最終取得日時 fetchedAt）の load/save。 |
| `HolidayService` | 起動時ロード・バックグラウンド更新・検証・縮退の司令塔。 |

**v0.3.2（拡充5クラス）**：

| クラス | 責務 |
|---|---|
| `HolidayStatus` | enum：OK / DEGRADED / NONE。 |
| `HolidayState` | record：`(HolidayCheck check, HolidayStatus status)`。 |
| `OverlayHolidayCheck` | 不変デコレータ：`isHoliday = !remove && (base || add)`。 |
| `HolidayOverride` | `holiday_override.json` の load＋Overlay ラップ。**例外を投げない契約**。 |
| `HolidayLog` | `holiday.log` への1行追記。**例外を投げない契約**（失敗は stderr）。 |

---

## 5. 起動〜更新フロー

1. **起動時（同期）**：`HolidayService.loadInitial(clock)` ＝キャッシュがあれば OK・無ければ NONE で即返る（ネットワークを待たない）。
2. **override 適用**：`HolidayOverride.load()` 成功時 `applyOverride()` で Overlay ラップして `holidayRef` に格納（→§7）。
3. **バックグラウンド更新（非同期）**：`HolidayService.refreshAsync(...)` ＝単一デーモンスレッド（`holiday-fetcher`）。
   - **鮮度判定**＝純関数 `shouldRefresh(fetchedAt, now)`：キャッシュが無い or **1日以上前**なら再取得（fresh ならネットワークに出ずスキップ）。
   - 取得成功＋検証パス → キャッシュ保存 → `onUpdate` で新 `HolidayState(OK)` を通知（Main 側で override を再適用して格納）。
   - **更新トリガーは起動時の1回**（常駐中の定期再チェックはしない。年1更新のデータなので十分）。

---

## 6. 検証と縮退（堅牢性の肝）

### 6.1 検証5項目（1個でも失敗→採用しない）

| # | チェック | 実装定数 |
|---|---|---|
| 1 | 取得サイズが妥当（エラーHTML・巨大応答の排除） | `MIN_BYTES=1_024` ≦ size ＜ `MAX_BYTES=1_048_576` |
| 2 | パース成功 | `HolidayCsvParser.parse` が例外を出さない |
| 3 | 総件数 | `MIN_COUNT=10` 以上 |
| 4 | 当年元日サニティ | 当年 1/1 が含まれる |
| 5 | 当年件数 | 当年の祝日が `MIN_CURRENT_YEAR_COUNT=12` 以上 |

不採用時は**生 CSV を `%APPDATA%\kReminder\holiday_last_failure.csv` に保存**（＝「内閣府がまた形式を変えた」を即診断するための、将来の自分への手土産）＋ `holiday.log` に却下理由を記録。

### 6.2 縮退（採用順位）

1. 今回 DL した正常データ（検証パス時のみ）→ **OK**
2. ダメなら → **前回の正常キャッシュで粘る** → **DEGRADED**（現在の check を保持したまま status だけ落とす。現在が NONE のときは DEGRADED を発報しない）
3. キャッシュも無い → **祝日連動オフに縮退** → **NONE**（土日判定だけは確実に動く）

**思想＝最悪でも「祝日を見落とすかもしれないが鳴る」側に倒す。「データが怪しいからアラーム停止」はやらない。**

---

## 7. 手動オーバーライド

- ファイル＝`%APPDATA%\kReminder\holiday_override.json`（UTF-8・ユーザー手編集）。
- スキーマ：

```json
{
  "add":    [ { "date": "2026-08-14", "name": "会社の夏休み" } ],
  "remove": [ "2026-10-12" ]
}
```

- 最終判定＝**`(CSV ∪ add) - remove`**（`OverlayHolidayCheck`：`isHoliday = !remove && (base || add)`）。
- **base が NONE でも効く**（CSV が全滅していても add/remove は生きる）。
- **起動時1回だけ読む＝ファイル編集後は再起動が必要**。
- 壊れた JSON・不正日付は**例外にせず**空オーバーレイ／該当行スキップ（stderr に記録）＝override が本体を巻き込まない。

---

## 8. 通報（握りつぶされないルート）

- **トレイアイコン常時ステータス**：OK→緑／DEGRADED→黄／NONE→赤。**色は CSV パイプラインの健康度のみを表す**（override の有無では変えない）。tooltip に override の `+N/-M` を併記。
- **`%APPDATA%\kReminder\holiday.log`**：取得開始・却下理由・成功件数・縮退遷移を1行ずつ追記（`HolidayLog` は例外を投げない契約）。
- v0 ドラフトにあった「黄/赤時に起動時一度だけ目立つ通知」は**未実装**（現状はトレイ色＋tooltip のみ）。

---

## 9. fake-clock との関係

`HolidayService` は `Clock` を受ける＝`--fake-now` 起動時は鮮度判定・当年判定も fake 基準で動く（デバッグ起動オプション仕様を参照）。

---

## 10. v0 ドラフト（§5）からの変更点

実装時に確定・変更された点。**本書が正**：

| 項目 | v0 ドラフト | 実装 |
|---|---|---|
| override ファイル名 | `holidays-override.json` | `holiday_override.json` |
| ログの場所 | `log\holiday.log` | `%APPDATA%\kReminder\holiday.log`（直下） |
| 失敗時生データ | `holiday-raw-failed.csv` | `holiday_last_failure.csv` |
| 鮮度判定 | 「例: 30日超」 | **1日超**（`shouldRefresh` 純関数） |
| 検証項目 | 7項目の案 | **5項目に確定**（→§6.1・HTTP 200 判定は Fetcher 側） |
| 黄/赤の起動時通知 | 出す | **未実装**（トレイ色＋tooltip のみ） |
| 黄（DEGRADED）の意味 | キャッシュで稼働中 | **更新失敗だが直前の正常 check で継続中**（キャッシュ稼働自体は OK 扱い） |

---

## 11. 実機実績

1067件（1955〜来年分）取得・キャッシュ・鮮度判定・トレイ緑/赤の目視確認済み（chat12/15・fake-clock 併用）。
