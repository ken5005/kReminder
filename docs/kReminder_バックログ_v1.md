# kReminder バックログ — 従来からの積み残し（#1〜25・順不同・いつでも）

作成: 2026-07-27（チャット61）／handoff 本体 C 節「従来からの積み残し」から分離。

**位置づけ**：`docs/` に置くが **「正典9冊」には数えない**。`kReminder_コード地図_v1.md` と同じ別枠扱い。

- **正典（9冊）** ＝ 仕様の真実源（何を作るか）
- **コード地図** ＝ 構造の索引（今どう出来ているか）
- **本書** ＝ いつでも着手できる TODO の置き場（何が残っているか）

---

## このファイルの読み方・書き方

- **番号は振り直さない。** handoff の A3 各節や本書内から「#12」「#24」の形で相互参照しているため。**#13 は既に欠番**（旧「削除後の選択位置」＝chat37 登録が chat58 で N4 に統合された）。完了した項目も番号は空けたまま残す。
- **各項目末尾の「着手時に読む（当たり）／読まなくていい」は消さない**（chat42 で確立した運用＝次チャットの Claude が探索でコンテキストを浪費しないため）。**書くのはファイル名だけで中身の要約は書かない**（中身は古びる＝「handoff の記述より実物のコードが正しい」原則。ファイル名は古びにくい）。**「読まなくていい」の明示が一番効く**（「関係ありそうだから一応読む」を止められる）。
- **本書は優先順位を持たない。** 現役の実装計画（N系・G4／G6）と着手順は **handoff 本体の C 節**が正。本書は「いつでも／順不同」の置き場。
- **全作業に効く作法**（diff を出す step の判断／CC と Claude の使い分け／目視確認の主語 等）は **handoff C 節の「運用ポイント（全件共通）」**に残してある。本書には無い。
- 本文中の `handoff A3-9` のような参照は handoff 本体の節番号を指す。

---

## 本体まわり（やや重い順）

1. **【chat52 登録・本人希望】発火日時補正の「気付かせ」UI**：現状は初回補正が起きても**無言**（→handoff A3-9・A3-3 `firstOnOrAfter`）。補正が発生したとき「実行時刻を修正したよ？」とユーザーに気付かせる。案＝**コメント空の警告と同じ流儀の確認ポップアップ**（`showConfirmDialog`・OK/やっぱ修正 を選ばせる）。※当初本人が挙げた「メッセージ欄1,2行目に警告文を書く」案は本人判断で不採用（本文書き換えは避ける）。
   **着手時に読む（当たり・chat52 に実物を読んで確認済み）**：`gui/EditDialog.onOk`（コメント空警告の `JOptionPane.showConfirmDialog` の手口がそのまま流用できる）／`EditFormLogic.needsEmptyCommentWarning`（同型の純関数判定の例）／`gui/MainWindow` の `correctedFireAt`＋2保存経路（**補正が起きたか＝`base` と `firstOnOrAfter` 結果の一致比較**で分かる）。**読まなくていい**＝`RepeatSpec` の next/parse/firstOnOrAfter 本体（補正ロジックは既存で足りる＝呼ぶだけ）。※chat55 で `MainWindow` は大きく変わったが `correctedFireAt` 周辺は無改変。

2. **【chat52 登録・本来やるべき】発火日時補正＋プレビューの祝日考慮**：現状 `firstOnOrAfter` とプレビューは `HolidayCheck.NONE` で計算＝ex=/in= が祝日を日曜扱いする分を初回だけ無視している（プレビューが元々 NONE なのに整合させた妥協・→handoff A3-9）。本来は実 holiday で計算すべき。着手＝`gui/EditDialog` と `gui/MainWindow` に `Supplier<HolidayCheck>`（供給元 `Main.holidayRef`）を注入し、`buildPreview` と `firstOnOrAfter` 両呼び出しの NONE を実 holiday に差し替え。**`firstOnOrAfter`/`buildPreview` の holiday 引数は既に用意済み＝呼び出しの差し替えのみでロジック本体は不要**。
   **着手時に読む（当たり・chat52 に実物を読んで確認済み）**：`gui/EditDialog.updatePreview`（`buildPreview(..., HolidayCheck.NONE)` を渡している箇所）／`gui/MainWindow.correctedFireAt`（`firstOnOrAfter(base, HolidayCheck.NONE)`）／`Main.holidayRef`（`AtomicReference<HolidayState>`・`.get().check()` で `HolidayCheck` が取れる）。

3. **【chat55登録・GUI小】編集ダイアログを縦長にすると余白だけ増えてコメント欄が広がらない**：ウィンドウを縦に伸ばすと、増えた高さがすべてプレビュー欄の下の余白に吸われ、コメント欄は3行のまま（本人がスクリーンショットで報告）。本来は**余った高さをコメント欄が吸う**べき＝GridBag の `weighty` がプレビュー側か余白側に付いていると思われる。**chat55 の「き」でダイアログのサイズが記憶されるようになったため、この見た目のまま固定されて目に付きやすくなった**（→handoff A3-15）。
   **着手時に読む（当たり）**：`gui/EditDialog.java` のレイアウト構築部（`GridBagConstraints` の `weighty`/`fill` の配分）。**※chat55 では EditDialog は `pack()` の位置・`enum Mode`・`setLocationRelativeTo` の有無しか見ておらず、レイアウト構築部は未読＝当たりは「このファイルのどこか」までで、行や変数名は未確認。chat57 でも同じ範囲を grep しただけで `weighty` の配分は読んでいない。**捏造ではなく未確認と理解すること。**読まなくていい**＝`WindowBoundsLogic`/`ColumnWidthsCodec`（サイズの記憶であって内部レイアウトとは無関係）。

---

## 軽い残（いつでも）

4. **【chat43登録・本人希望】ポップアップキューの緊急排出（脱出口）**：何らかのミスで大量のポップアップがキューに溜まった場合の逃げ道。**あるリマインダーを右クリック →「全てのポップアップを消去」→ そのリマインダー由来のポップアップを、待ち行列からも表示中からも全削除**。**この手のアプリで思いがけず強制終了せざるを得なくなるパターンを避けるための脱出口**（本人談）。実装は素直＝`popupQueue`（`Deque<Reminder>`）を `removeIf` で間引き、`openPopups` を回して該当の `dialog.dispose()`。**トレイメニューに「全ポップアップ消去」も足すと保険になる**（右クリックする元の行が既に消えているケース）。
   **着手時に読む（当たり・実物が正）**：`Main.java` の `popupQueue`／`openPopups`／`showPopup`／`pumpPopups`（状態は全部ここ）＋`MainWindow` の右クリックメニュー配線。

5. **【chat43登録・番外編】Claude Code の「import 文だけの Edit」を自動許可したい**：同一ファイルでも CC は Edit を細切れに投げてくるので、**体感で承認プロンプトの3回に1回が import 文の追加/削除だけ**。PreToolUse フック（`~/.claude/hooks/scope-guard.ps1` と同じ土俵）で `Edit` の `old_string`/`new_string` を覗き、**差分行が全部 `import ` で始まる（か空行）なら `allow` を返す**、という判定は書ける（域外パスの hard deny は先に効かせたまま、その後段に置く）。**本人希望＝完全に番外編として専用回でやる**（本線に混ぜない）。

6. **②仕上げ**：既定ソート以外がかかっている間、ソート列ヘッダ背景を緑に（`RowSorterListener`＋ヘッダレンダラ・→handoff A3-12）。

7. **コア小改修**：有効曜日ゼロを `parse()` でエラー化（現状 `toJapanese()` が防御的に例外・原則2・→handoff A3-11）＋テスト1本。

8. **kReminder小改善**：JSON手編集タイポで無言起動失敗＝`load()` catch を `Exception` に広げ空リスト＋エラー表示（handoff A3-7）。**chat35 の `FatalErrorDialog`（最前面ダイアログ→exit）が使える**（→handoff A3-13）。

9. **DEB `ConsoleSink` の SJIS 対応【chat33で表面化・優先度低・javawなら実害なし】**：本人環境はコンソールSJIS・アプリUTF-8で、日本語をコンソールに出すと化ける（DEBパネルは正常）。SNDのStackTraceログで初表面化。恒久対応するなら `ConsoleSink` の `System.out` を Shift_JIS 指定で包む等（SNDではなくDEB側の独立タスク・→handoff A3-13）。

10. **DEB.pr_()（改行しない版）復活**（優先度低・複数箇所・現行の改行付与と衝突しうる・本人談で後回し）。

---

## ドキュメント小

11. **【chat37登録】GUI仕様v2 §2.5.3 に「`(copy)` 採番は既存アイテムを走査しない＝重複しうる（許容）」の1行を明記**（今は採番規則だけで、衝突を見ない旨が書かれていない＝将来「バグでは？」と迷わないための注記）。

12. **【chat42登録・chat43で表現更新】SND仕様 §13 将来拡張の「ループ再生」に補記**：`Clip` の1音ループの意味であり、通知パターンの `NotifyPattern.repeatTail`（ステップ列の末尾サイクルの繰り返し）とは**別物**。将来読み返したとき紛らわしいので「（`Clip` の1音ループ。`NotifyPattern.repeatTail` とは別）」と1行足す。**§13 の「10枚同時発火で直列 `SoundWorker` が詰まる」の記載も、chat43 の交通整理で解消済みなので現況化する**（→handoff A3-13）。＋**§11 にホットループガード（`repeatTail>0` で末尾サイクルの delay 合計≤0 は IAE）が未記載＝1行追記する**（→handoff A3-13）。

13. **（欠番）** — 旧「削除後の選択位置」（chat37登録）は **chat58 で N4 に統合**（N4 の方が本人の希望を具体的に書いている）。他節から番号を参照している箇所があるため**番号は振り直さず欠番のまま残す**。

25. **【chat59登録】GUI仕様v2 §6 マッピング表のステータス欄が陳腐化している**：「未実装」表記が実装済みの項目（`RepeatSpec.toJapanese()`・`isVisible`/`bucketOf`/`leadWindowOf`・ソート比較器・相対時間パース 等）に残ったまま。chat59 の docs 現況化で CC が指摘し、本PRの範囲外として表記（「後」の有無）だけ直した。**直すなら §6 の表を一度ぜんぶ実装現況に洗い直す**（表そのものを畳む選択もある）。

---

## 環境・その他（本体コードの外）

14. **環境系の残**：
   - (d) 母艦グローバル `~/.claude` を更新したら実家へ手コピー（**chat45 時点で両機同期済み＝未反映ゼロ**）。
   - (e) **実家に WinMerge 未導入＝difftool 保留**（設定手順そのものは `開発環境セットアップ手引書_Windows_v2.md` に収録済み＝入れたらそれを見る）。
   - **【chat51】verbose 恒久化の反映＝両機の `~/.claude/settings.json` に `"verbose": true`**（または各機で `/config verbose=true`）を入れておく。settings.json は母艦・実家で別管理なので**両方でやる**。
   - ※旧 (c)「開発環境セットアップ手引書 追記候補」は **chat61 で手引書 v2 に全部反映済み＝消化**。

15. **【chat38登録・未実施】idea64.exe と Chrome が同時にクラッシュした件の原因調査**：GPU ドライバの TDR（両アプリとも GPU 描画を使う）かメモリ枯渇が第一候補。イベントビューアで切り分けられる＝`Get-WinEvent` で Application ログの Id 1000/1001/1002（障害モジュール名が出る）／System ログの Id 4101（GPU 応答停止と回復）／Id 2004（Resource-Exhaustion-Detector＝メモリ枯渇）。**本人は「後でやる」で保留**。再発しなければ深追い不要。

16. **時計アプリ仕上げ**：kClock2 の README → 配色・サイズ微調整。

17. **git「3バッチ集約」**（`gstart`/`gsave`/`gship` を `$PROFILE` に・退屈になったら・→handoff A4-1。関数本体は archive#chat13）。

18. **トレードアプリ `kbitflyer-ver4` を GitHub へ**（別タスク・秘密情報を絶対コミットしない・private・専用回）。**本人のメイン長期目標**。

19. 最新 JDK(25) 検証、IntelliJ 2022.1 削除：任意。

20. **【chat28登録・かなり後回し】本運用時の `reminders.json` 跨マシン同期設計**：今はダミーだから上書きコピーで済むが、実運用に入ると「共通リマインダー」と「マシン固有リマインダー（例: ゴミ捨て日）」を分けてマシンをまたいで同期する仕組みが要る。**本体完成後の設計テーマ**（10年前には無かった新しめの難問・優先度＝かなり後回し・→`マシン乗り換え作業チェックリスト.md` 将来ネタ）。

21. **【chat47登録・番外編】CC のキー暴発の検証と恒久対策**：空プロンプトで `←`＝Agent View に遷移／`Shift+Tab`＝権限モード循環（manual→accept edits→plan→auto→manual…・**無印は無く `manual` が通常のデフォルト**）。chat47 で別アプリのショートカットと衝突して誤爆し長く迷子になった（実害なし・git clean のまま復帰）。恒久対策するなら `/keybindings`（`~/.claude/keybindings.json`）でカスタム。**専用回で。**
   **【chat60 で再発・対処法が判明したので恒久対策の優先度は下がった】**：`←` で Agent View に飛んだときの表示は `Your conversation moved to the background — enter opens it · esc returns to it · ctrl+c twice quits`。**戻り方は `esc`**（元の会話にそのまま復帰。`enter` は「一覧で選んだセッションを開く」で、1セッションしか無ければ結果は同じ）。**復帰後に右に出る青バックの文字はセッション名ラベル**（CC が自動で付けたタイトル）＝表示だけで状態は変わらない。**左下の `manual mode on` は通常のデフォルト**（無印モードは存在せず、モードを回すのは `Shift+Tab` で `←` では変わらない）。Agent View には `Needs input` として承認待ちが表示され続ける＝**会話も承認待ちも失われない**。

22. **【chat51登録・優先度低】instant 単位サフィックスの全角対応**：現状 `１５ｍ`（全角 `ｍ`）は `InstantTimeLogic.normalize` が変換対象にしていないので弾かれる（`１５m`＝数字全角・単位半角なら通る）。normalize に `ｓ/ｍ/ｈ`→半角 s/m/h の3文字を足すだけ。
   **着手時に読む（当たり・実物が正）**：`InstantTimeLogic.java` の `normalize`（**ここだけ**）＋テスト。

23. **【chat53登録・微調整・実用上放置可】発火フラッシュのムラ解消**：鳴る間隔＜フェード1秒の区間はフェード途中を次の発火が叩き直して色変化が鈍る（→handoff A3-13）。対策＝`Const.POPUP_FLASH_FADE_MS` を 300〜400 へ下げるだけ（`Const` 1行）。実用上は放置で合意済み＝気になったら。

24. **【chat53登録・構想・急がない】`Main.java` 肥大化の分割**：発火/通知/フラッシュ（`showPopup`/`retuneNotification`/`flashPopup`/`openPopups`/`popupQueue` 等の static 状態）が Main に集まり「何でも屋」化。切り出すなら `gui/PopupManager`（か `sound` 寄せ）へ。本人も自覚あり。**本体完成後の整理テーマ**（急がない・専用回向き）。**N12「1リマインダー＝1Popup」と隣接**（→handoff C 節 N12）。
