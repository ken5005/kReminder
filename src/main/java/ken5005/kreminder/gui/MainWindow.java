package ken5005.kreminder.gui;

import ken5005.kreminder.ColumnWidthsCodec;
import ken5005.kreminder.Config;
import ken5005.kreminder.Const;
import ken5005.kreminder.CopyName;
import ken5005.kreminder.EditFormLogic;
import ken5005.kreminder.ExtName;
import ken5005.kreminder.FilterState;
import ken5005.kreminder.HolidayCheck;
import ken5005.kreminder.MonitorBounds;
import ken5005.kreminder.Reminder;
import ken5005.kreminder.ReminderFilter;
import ken5005.kreminder.ReminderStore;
import ken5005.kreminder.RepeatSpec;
import ken5005.kreminder.WindowBoundsLogic;
import ken5005.kreminder.debug.DEB;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * kReminder のメイン画面。
 * スライス①-b-3: エントリポイントは Main.java に一本化。ここは窓の組み立てのみ担う。
 */
public class MainWindow extends JFrame {

    // 下段の既定復元位置（分割ペイン高さに対する比率）。初回オープン時にこれを使う。
    private static final double DEFAULT_DEBUG_DIVIDER_RATIO = 0.7;

    // ステータスバーは ActionListener から更新するためフィールドに持つ
    private final JLabel statusBar = new JLabel(" ");
    private final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final DebugPanel debugPanel = new DebugPanel();
    private final Clock clock;
    // フィルタ6トグルの永続化（GUI仕様v2 §3.7）。load()はUIを組む前に呼ぶ必要がある
    private final Config config = new Config();
    // Main が起動時にload()した同一インスタンス（③-d・リスト一本化）。
    // MainWindowが自前でload()すると別インスタンスになり、編集や発火状態の書き戻し先が食い違う
    private final List<Reminder> reminders;
    // Main と同一のstoreインスタンス。編集保存もMain側のcheckRemindersと同じPathへ書く
    private final ReminderStore store;

    // 残り時間列は now 依存なので、Main の1秒 Timer から tick() で再描画させるために保持する
    private ReminderTableModel tableModel;
    // RowFilter の設定・再適用（applyFilter）に使うためフィールド化
    private JTable table;
    private TableRowSorter<ReminderTableModel> sorter;

    // フィルタUI（②-step5a・GUI仕様v2 §3.3）。currentFilterState() から読む
    private JCheckBox showEndedCheck;
    private JCheckBox showImminentCheck;
    private JCheckBox showSoonCheck;
    private JCheckBox showFarCheck;
    private JCheckBox showLowPriorityCheck;
    private JCheckBox showAllRepeatCheck;
    private JTextField searchField;

    private boolean debugPanelOpen = false;
    // 展開時の分割位置を絶対pxではなく比率(0.0〜1.0)で保持する（窓高さが変わっても破綻しないため）。
    // 範囲外・未設定の値はConfig読込時にこの既定値へフォールバックする
    private double savedDividerRatio = DEFAULT_DEBUG_DIVIDER_RATIO;
    private boolean initialCollapseApplied = false;

    public MainWindow(Clock clock, List<Reminder> reminders, ReminderStore store) {
        super("kReminder");
        this.clock = clock;
        this.reminders = reminders;
        this.store = store;

        // ウィンドウ位置・サイズやフィルタUIの初期値（buildFilterBar()で使う）に必要なため、
        // UIを組み立てるより前に読み込む（従来はsetSizeの後ろにあり、この用途には遅すぎた）
        config.load();

        applyWindowBounds();

        // 展開したときに前回比率へ戻すための値。範囲外(0.0〜1.0外・UNSETの-1.0含む)ならフィールド既定の
        // DEFAULT_DEBUG_DIVIDER_RATIOのまま（config.propertiesに残る旧形式の絶対px値もこれで自動的に無効化される）。
        // 起動直後にDEBパネルが畳まれている見た目は変えない
        double loadedRatio = config.getMainDividerRatio();
        if (loadedRatio >= 0.0 && loadedRatio <= 1.0) {
            savedDividerRatio = loadedRatio;
        }

        // ×ボタンで JVM ごと終了（常駐トレイ版とは切り離した学習用 main 前提）
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // タスクバー/タイトルバーのアイコンを既定のコーヒーカップから差し替える。
        // JDialogはタスクバーボタンを作らないため、JFrameであるここ1箇所で足りる
        var iconUrl = getClass().getResource("/icons/Calendar.png");
        if (iconUrl != null) {
            setIconImage(new ImageIcon(iconUrl).getImage());
        } else {
            // アイコンが無いだけでアプリを落とすほどではないので、ログのみで起動は続行する
            DEB.pr("タスクバーアイコンの読み込みに失敗しました: /icons/Calendar.png が見つかりません");
        }

        getContentPane().add(buildTopBars(),   BorderLayout.NORTH);
        getContentPane().add(buildSplitPane(), BorderLayout.CENTER);
        getContentPane().add(statusBar,       BorderLayout.SOUTH);

        // buildSplitPane()内のbuildTable()でtableが組まれた直後＝列がすべて揃ってから列幅を反映する
        applyColumnWidths();

        // フィルタUI（buildTopBars）とテーブル/sorter（buildSplitPane）の両方が揃ってから
        // 初期フィルタを適用する。これをしないと起動直後は無フィルタ（全件表示）になってしまう。
        applyFilter();

        // Ctrl+N/Ctrl+D は窓スコープ（GUI仕様v2 §2.5.6）。EditDialog等の別窓にフォーカスがある間は発火しない
        setupWindowKeyBindings();
    }

    /**
     * 保存されていた位置・サイズを、今のモニタ構成に照らして安全化してから適用する（フェーズ4「き」）。
     * 位置が一度も保存されていない(UNSET)場合はサイズだけConfigの既定値を使い、位置は
     * 従来どおり画面中央に出す。保存済みの場合はWindowBoundsLogicへ値を渡して安全化する
     * （AWTのGraphicsEnvironmentへ問い合わせるのはここ＝MainWindowの責務。純関数側は値だけを見る）。
     */
    private void applyWindowBounds() {
        int savedWidth = config.getMainWidth();
        int savedHeight = config.getMainHeight();

        if (config.getMainX() == Config.UNSET || config.getMainY() == Config.UNSET) {
            setSize(savedWidth, savedHeight);
            setLocationRelativeTo(null); // 画面中央に配置（null = 自画面基準）
            return;
        }

        WindowBoundsLogic.Resolved resolved = WindowBoundsLogic.resolve(
            config.getMainX(), config.getMainY(), savedWidth, savedHeight, currentMonitorBounds());

        setSize(resolved.width(), resolved.height());
        if (resolved.centered()) {
            setLocationRelativeTo(null); // モニタ構成が変わり画面外に消えていた等＝中央へ置き直す
        } else {
            setLocation(resolved.x(), resolved.y());
        }
    }

    /** 現在のモニタ構成をAWTから取得し、WindowBoundsLogicが読める値だけの形に詰め替える。 */
    private static List<MonitorBounds> currentMonitorBounds() {
        List<MonitorBounds> monitors = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle b = device.getDefaultConfiguration().getBounds();
            monitors.add(new MonitorBounds(b.x, b.y, b.width, b.height));
        }
        return monitors;
    }

    /**
     * 保存されていた列幅をテーブルの各列へ反映する。保存値の個数が今の列数と食い違う場合
     * （保存後に列構成が変わった等）は、一致する分だけ反映して残りは無視する（起動は止めない）。
     */
    private void applyColumnWidths() {
        int[] widths = ColumnWidthsCodec.parse(config.getTableColumnWidths());
        var columnModel = table.getColumnModel();
        int count = Math.min(widths.length, columnModel.getColumnCount());
        for (int i = 0; i < count; i++) {
            columnModel.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /**
     * 新規(Ctrl+N)・複製(Ctrl+D)・instant(Ctrl+I)をウィンドウ全体のキーバインドとして登録する（GUI仕様v2 §2.5.6）。
     * rootPaneのWHEN_IN_FOCUSED_WINDOWに置くことで、検索欄やテーブルなどフォーカス位置に関わらず効く。
     * 別ウィンドウ（EditDialog・発火ポップアップ）にフォーカスが移っている間はこの窓の外なので発火しない。
     */
    private void setupWindowKeyBindings() {
        var inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var actionMap = getRootPane().getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "kreminder.new");
        actionMap.put("kreminder.new", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onNewButton(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "kreminder.duplicate");
        actionMap.put("kreminder.duplicate", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onDuplicateButton(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK), "kreminder.instant");
        actionMap.put("kreminder.instant", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onInstantButton(); }
        });
    }

    /** 上段=既存ツールバー・下段=フィルタバーの2段組みを1枚のパネルにまとめる。 */
    private JPanel buildTopBars() {
        var panel = new JPanel(new GridLayout(2, 1));
        panel.add(buildToolBar());
        panel.add(buildFilterBar());
        return panel;
    }

    /** ツールバーを組み立てる。ボタン機能は seam のみ——将来コントローラへ委譲する。 */
    private JToolBar buildToolBar() {
        var bar = new JToolBar();
        bar.setFloatable(false); // ドラッグで切り離せないようにする（常設ツールバーの慣用）

        for (String name : new String[]{"新規", "instant", "編集", "複製", "削除", "更新", "デバッグログ"}) {
            var btn = new JButton(name);
            setFontSize(btn, Const.FONT_SIZE_BUTTON);
            switch (name) {
                case "デバッグログ" -> btn.addActionListener(e -> toggleDebugPanel());
                case "編集" -> btn.addActionListener(e -> onEditButton());
                case "新規" -> btn.addActionListener(e -> onNewButton());
                case "instant" -> btn.addActionListener(e -> onInstantButton());
                case "複製" -> btn.addActionListener(e -> onDuplicateButton());
                case "削除" -> btn.addActionListener(e -> onDeleteButton());
                // 「更新」は今回もダミー配線のまま（スコープ外・GUI仕様v2 §2.5関連スライドで対応予定）
                default -> btn.addActionListener(e -> {
                    statusBar.setText(name + " が押されました");
                    DEB.pr(name + " が押されました");
                });
            }
            bar.add(btn);
        }
        return bar;
    }

    /**
     * フィルタバーを組み立てる（②-step5a・GUI仕様v2 §3.3）。チェックボックス6個＋検索欄。
     * 「全表示(showAll)」トグルは今回UIに出さない。検索欄が上書き層として同じ役割を果たせるため、
     * currentFilterState() 側で常に false 固定にして組む（将来必要になったら足す）。
     */
    private JToolBar buildFilterBar() {
        var bar = new JToolBar();
        bar.setFloatable(false);

        showEndedCheck = new JCheckBox("終了済", config.isShowEnded());
        showImminentCheck = new JCheckBox("直近", config.isShowImminent());
        showSoonCheck = new JCheckBox("近日", config.isShowSoon());
        showFarCheck = new JCheckBox("先", config.isShowFar());
        showLowPriorityCheck = new JCheckBox("重要度低", config.isShowLowPriority());
        showAllRepeatCheck = new JCheckBox("繰り返し全表示", config.isShowAllRepeat());

        // チェック変化のたびにフィルタを再適用し、次回起動用に現在状態を保存する
        for (JCheckBox cb : List.of(showEndedCheck, showImminentCheck, showSoonCheck,
                showFarCheck, showLowPriorityCheck, showAllRepeatCheck)) {
            setFontSize(cb, Const.FONT_SIZE_FILTER);
            cb.addItemListener(e -> {
                applyFilter();
                saveFilterState();
            });
            bar.add(cb);
        }

        bar.addSeparator();
        JLabel searchLabel = new JLabel("検索");
        setFontSize(searchLabel, Const.FONT_SIZE_FILTER);
        bar.add(searchLabel);
        searchField = new JTextField(10);
        setFontSize(searchField, Const.FONT_SIZE_SEARCH);
        // 1文字打つたびに再適用（検索は上書き層＝トグル無視でコメント一致のみ表示に切り替わる）
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        bar.add(searchField);

        return bar;
    }

    /** 上=テーブル・下=デバッグパネルのJSplitPaneを組み立てる。起動時は下段collapsed。 */
    private JSplitPane buildSplitPane() {
        splitPane.setTopComponent(buildTable());
        splitPane.setBottomComponent(debugPanel);
        // リサイズ時は上段（テーブル）だけが伸び、下段は据え置き
        splitPane.setResizeWeight(1.0);
        splitPane.setOneTouchExpandable(false);
        // 実サイズが確定するまでdividerLocationは設定できないため、表示された瞬間に1回だけcollapseする
        splitPane.addHierarchyListener(this::onSplitPaneHierarchyChanged);
        return splitPane;
    }

    private void onSplitPaneHierarchyChanged(HierarchyEvent e) {
        if (initialCollapseApplied) return;
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && splitPane.isShowing()) {
            initialCollapseApplied = true;
            SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(1.0));
        }
    }

    /** デバッグパネルの開閉を切り替える。開く時は直前の比率（無ければ既定比率）に復元する。 */
    private void toggleDebugPanel() {
        if (debugPanelOpen) {
            int height = splitPane.getHeight();
            // 高さ0での0除算を避ける（万一起きても既定比率へ倒すだけで済む安全側の値）
            savedDividerRatio = height > 0
                    ? (double) splitPane.getDividerLocation() / height
                    : DEFAULT_DEBUG_DIVIDER_RATIO;
            splitPane.setDividerLocation(1.0); // 下段高さ0＝collapsed
        } else {
            splitPane.setDividerLocation((int) (splitPane.getHeight() * savedDividerRatio));
        }
        debugPanelOpen = !debugPanelOpen;
    }

    /**
     * 実行時刻を、繰り返し条件（曜日限定・第N週限定・月固定日等）に合わせて黙って補正する。
     * repeatが空、またはparse失敗（不正repeat）なら無補正でbaseをそのまま返す。
     * 編集経路・新規/instant経路の両方（onEditButton／openEditorForNew）で共有する。
     */
    private LocalDateTime correctedFireAt(String execText, String repeatText) {
        LocalDateTime base = EditFormLogic.parseExecTime(execText).get(); // 呼び出し側でOK活性＝present前提
        String rep = repeatText == null ? "" : repeatText.trim();
        if (rep.isEmpty()) return base;

        RepeatSpec spec;
        try {
            spec = RepeatSpec.parse(rep);
        } catch (RuntimeException e) {
            return base; // 不正repeatは無補正（OK活性判定と食い違うが、書き戻し前の防御的パースなので安全側へ）
        }
        return spec.firstOnOrAfter(base, HolidayCheck.NONE);
    }

    /**
     * 「編集」ボタンの導線（GUI仕様v2 ③-b/③-d）。選択行のReminderをEditDialogに渡して開き、
     * OKで閉じられた場合のみ入力値をoriginalへ書き戻して保存・再描画する。
     * 未選択（viewRow==-1）ならダイアログは開かず、ステータスバーで案内するだけにする。
     *
     * 【stale index対策】dialog.setVisible(true)はモーダルなので呼び出し元をブロックするが、
     * 内部でネストしたイベントループを回す＝その間も1秒TimerのMain.checkRemindersはEDT上で走る。
     * ⑤で「(Ext)発火後自動削除」が入ったことで、編集中に対象がリストから消えうるようになったため、
     * ここで最初に掴んだmodelRowはダイアログが閉じた後にはstaleな可能性がある＝以降使わない。
     * 閉じた後はreminders.indexOf(original)で参照一致により引き直す。
     */
    private void onEditButton() {
        int viewRow = table.getSelectedRow();
        if (viewRow == -1) {
            statusBar.setText("編集する行を選択してください");
            return;
        }
        // ソート/フィルタ後のビュー行 → モデル行へ変換してからReminderを引く
        int modelRow = sorter.convertRowIndexToModel(viewRow);
        Reminder original = tableModel.getReminderAt(modelRow);

        var dialog = new EditDialog(this, original, clock);
        showEditDialog(dialog, EditDialog.Mode.NORMAL, false); // モーダルなのでこの中で閉じるまで待つ（この間にoriginalが消えうる）

        if (!dialog.isOkPressed()) return; // キャンセル・Esc・×は何もしない（消えていてもそのまま＝仕様どおり）

        // OK活性で保証済みだが、書き戻し前に念のため再パースして確認する（防御的）
        var parsed = EditFormLogic.parseExecTime(dialog.getExecTimeText());
        if (parsed.isEmpty()) return;

        // 案A：入力値をoriginalへ上書き。案(あ)：編集したら発火済みフラグを一律リセットする
        original.fireAt = correctedFireAt(dialog.getExecTimeText(), dialog.getRepeatText());
        original.repeat = dialog.getRepeatText();
        original.priority = dialog.getSelectedPriority();
        original.message = dialog.getCommentText();
        original.action = dialog.getCmdText();
        original.noticed = false;

        // ダイアログを閉じた後に参照一致で引き直す（開いている間に(Ext)自動削除で消えている可能性）
        int currentRow = reminders.indexOf(original);
        if (currentRow == -1) {
            // 編集中に発火して(Ext)自動削除された（§5.4）。§4.7「編集＝削除＋追加」に従い復活させる
            int newRow = tableModel.addReminder(original);
            store.save(reminders);
            revealAddedRow(newRow);
            String message = "編集中に発火して自動削除されたため、新しい予定として追加しました";
            statusBar.setText(message);
            DEB.pr(message + ": " + original.message);
        } else {
            store.save(reminders); // 一本化した同一リスト・同一storeで全書き
            tableModel.reminderUpdatedAt(currentRow);
        }
    }

    /**
     * 「新規」ボタン／Ctrl+N の導線（GUI仕様v2 §2.5.1）。
     * 現在日時（秒0丸め）をfireAtに入れたReminderを用意し、共通の編集導線へ渡す。
     */
    private void onNewButton() {
        Reminder r = new Reminder();
        r.fireAt = LocalDateTime.now(clock).withSecond(0).withNano(0);
        r.message = "";
        openEditorForNew(r, EditDialog.Mode.NORMAL, false);
    }

    /**
     * 「instant」ボタン／Ctrl+I／テーブルSpaceの導線（GUI仕様v2 §4.1・クイック追加）。
     * 選択行の有無に関係なく開く。fireAtの初期値はonNewButtonと同じ現在日時(秒0丸め)を入れるが、
     * InstantField.setDateTimeはno-op（instantは空欄スタート仕様）なので実質使われない。
     */
    private void onInstantButton() {
        Reminder r = new Reminder();
        r.fireAt = LocalDateTime.now(clock).withSecond(0).withNano(0);
        r.message = "";
        openEditorForNew(r, EditDialog.Mode.INSTANT, false);
    }

    /**
     * 「複製」ボタン／Ctrl+D の導線（GUI仕様v2 §2.5.2）。
     * 選択行の全フィールドをコピーし、noticedはfalseにリセット・messageだけCopyNameで採番してから
     * 共通の編集導線へ渡す。選択行が無ければ何もしない。
     */
    private void onDuplicateButton() {
        int viewRow = table.getSelectedRow();
        if (viewRow == -1) {
            statusBar.setText("複製する行を選択してください");
            return;
        }
        int modelRow = sorter.convertRowIndexToModel(viewRow);
        Reminder original = tableModel.getReminderAt(modelRow);

        Reminder copy = new Reminder();
        copy.fireAt = original.fireAt;
        copy.message = CopyName.nextCopyComment(original.message);
        copy.priority = original.priority;
        copy.action = original.action;
        copy.repeat = original.repeat;
        copy.noticed = false;

        openEditorForNew(copy, EditDialog.Mode.NORMAL, false);
    }

    /**
     * 新規・複製・instantで共通の編集導線（GUI仕様v2 §2.5.1/2.5.2/④）。
     * 渡されたReminder（まだreminders未追加）をEditDialogで開き、OKなら入力値を書き戻したうえで
     * リストへ追加・保存・表示（選択+スクロール、フィルタで隠れる場合はメッセージ）を行う。
     * キャンセル/Esc/×は何もしない＝reminders/JSONに一切影響を与えない。
     * 戻り値はOKで実際にremindersへ追加できたかどうか（⑤・openExtendEditorがポップアップの
     * 開閉判定に使う。新規/instant/複製の既存3呼び出しは戻り値を無視するだけで挙動は変わらない）。
     * alwaysOnTop：trueならダイアログを最前面固定にする（⑤・openExtendEditorがtrueを渡す。
     * 発火ポップアップも最前面固定のため、被って読めなくなるのを避けるため＝GUI仕様v2 §5.3）。
     */
    private boolean openEditorForNew(Reminder r, EditDialog.Mode mode, boolean alwaysOnTop) {
        var dialog = new EditDialog(this, r, clock, mode);
        showEditDialog(dialog, mode, alwaysOnTop);

        if (!dialog.isOkPressed()) return false; // キャンセル・Esc・×は追加しない

        var parsed = EditFormLogic.parseExecTime(dialog.getExecTimeText());
        if (parsed.isEmpty()) return false; // OK活性で保証済みだが、書き戻し前に念のため再パース（防御的）

        r.fireAt = correctedFireAt(dialog.getExecTimeText(), dialog.getRepeatText());
        r.repeat = dialog.getRepeatText();
        r.priority = dialog.getSelectedPriority();
        r.message = dialog.getCommentText();
        r.action = dialog.getCmdText();
        r.noticed = false;

        int modelRow = tableModel.addReminder(r);
        store.save(reminders);
        revealAddedRow(modelRow);
        return true;
    }

    /**
     * EditDialogを開いて閉じるまでの共通処理（フェーズ4「き」step3）。onEditButton／
     * openEditorForNewの2箇所が同じことをしていたためここへ集約した。
     * dialogは呼び出し側で生成済み（コンストラクタ内でpack()済み＝この時点のサイズがpacked）。
     * サイズの復元→位置設定→表示→サイズの保存、という順で行う。閉じた後の isOkPressed() 判定等は
     * 呼び出し元がdialogを使って続ける。
     */
    private void showEditDialog(EditDialog dialog, EditDialog.Mode mode, boolean alwaysOnTop) {
        int packedWidth = dialog.getWidth();
        int packedHeight = dialog.getHeight();

        boolean normal = mode == EditDialog.Mode.NORMAL;
        int savedWidth = normal ? config.getEditWidth() : config.getInstantWidth();
        int savedHeight = normal ? config.getEditHeight() : config.getInstantHeight();

        WindowBoundsLogic.DialogSize size = WindowBoundsLogic.resolveDialogSize(
            savedWidth, savedHeight, packedWidth, packedHeight, currentMonitorBounds());
        dialog.setSize(size.width(), size.height());

        // setLocationRelativeTo(null)は必ずsetSizeの後に呼ぶ：先に呼ぶと変更前のサイズを基準に
        // 中央位置が計算され実際にはずれてしまう。nullを渡すのは親(this)ではなく画面中央に出すため
        dialog.setLocationRelativeTo(null);

        if (alwaysOnTop) dialog.setAlwaysOnTop(true);

        dialog.setVisible(true); // モーダルなのでダイアログが閉じるまでここで待つ

        // ここから先はダイアログが閉じた後。最終サイズをその場でConfigへ保存する。
        // saveWindowState()（終了時）に相乗りしないのは、そちらがメインウィンドウ最大化中は
        // 早期returnするため＝メインウィンドウを最大化したまま終了するとダイアログサイズだけ
        // 永久に保存されなくなってしまう。ダイアログのサイズはメインウィンドウの最大化状態とは
        // 無関係なので、そのガードの外側でここで保存する。
        // 副作用：ここでのconfig.save()はwindow.main.*を「起動時に読んだ値」のまま書き出すが
        // （ウィンドウを動かしてもその時点ではConfigに未反映のため）、終了時のsaveWindowState()が
        // 正しい値で上書きするので最終結果には影響しない。
        if (normal) {
            config.setEditWidth(dialog.getWidth());
            config.setEditHeight(dialog.getHeight());
        } else {
            config.setInstantWidth(dialog.getWidth());
            config.setInstantHeight(dialog.getHeight());
        }
        config.save();
    }

    /**
     * 発火ポップアップの Extend（＝スヌーズ）ボタンの導線（GUI仕様v2 §5.3）。
     * コメント頭に "(Ext) " を前置し、繰り返しは引き継がず（単発）、優先度・Cmdは引き継いだ
     * Reminderを組み立て、instantモードのEditDialogをopenEditorForNewへ渡す。
     * 実行時刻はinstant仕様上ユーザーが打つため、ここではnon-null埋め（InstantField.setDateTimeは
     * no-opだが値そのものはnullを許さないため）のダミー値を入れるだけにとどめる。
     * 戻り値はopenEditorForNewの結果そのまま＝呼び出し元（発火ポップアップ）がこれを見て
     * 「OKで登録できたときだけポップアップを閉じる」判定に使う（§5.3）。
     */
    public boolean openExtendEditor(Reminder fired) {
        Reminder ext = new Reminder();
        ext.fireAt = LocalDateTime.now(clock).withSecond(0).withNano(0);
        ext.message = ExtName.withExtPrefix(fired.message);
        ext.priority = fired.priority;
        ext.action = fired.action;
        ext.repeat = "";
        ext.noticed = false;
        return openEditorForNew(ext, EditDialog.Mode.INSTANT, true);
    }

    /**
     * (Ext) 付き単発予定の発火後自動削除（GUI仕様v2 §5.4）で使う、行の除去だけを行う口。
     * remindersの参照一致でモデル行を引き、見つかった場合のみtableModel.removeReminderAtへ委譲する
     * （行の増減はReminderTableModelに閉じる、というCLAUDE.mdの原則を守るため）。
     * store.saveはここでは呼ばない＝呼び出し元（Main.checkReminders）が発火時に必ずsaveするため、
     * 二重保存を避ける。見つからない場合は何もしない（防御的）。
     */
    public void removeReminder(Reminder r) {
        int modelRow = reminders.indexOf(r);
        if (modelRow == -1) return;
        tableModel.removeReminderAt(modelRow);
    }

    /**
     * 追加した行をビュー上で選択・スクロールして見せる（GUI仕様v2 §2.5.5）。
     * 現在のフィルタで非表示（convertRowIndexToViewが-1）の場合は、statusBarとDEBログの
     * 両方にその旨を出す（追加自体は成功しているので、混乱を避けるため気づかせる）。
     */
    private void revealAddedRow(int modelRow) {
        int viewRow = sorter.convertRowIndexToView(modelRow);
        if (viewRow == -1) {
            String message = "追加しましたが現在のフィルタでは非表示です";
            statusBar.setText(message);
            DEB.pr(message);
            return;
        }
        table.setRowSelectionInterval(viewRow, viewRow);
        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
    }

    /**
     * 「削除」ボタン／Delete キーの導線（GUI仕様v2 §2.5.4）。
     * 選択行が無ければ何もしない。確認ダイアログで「はい」を選んだ場合のみ、
     * リストから除去・保存・選択解除まで行う。
     *
     * 【stale index対策】JOptionPane.showConfirmDialogもonEditButtonと同じくネストしたイベント
     * ループを回すため、確認待ちの間に対象が(Ext)自動削除で消えている可能性がある。
     * ここで最初に掴んだmodelRowは確認ダイアログが閉じた後は使わず、reminders.indexOf(target)で
     * 参照一致により引き直す。
     */
    private void onDeleteButton() {
        int viewRow = table.getSelectedRow();
        if (viewRow == -1) {
            statusBar.setText("削除する行を選択してください");
            return;
        }
        int modelRow = sorter.convertRowIndexToModel(viewRow);
        Reminder target = tableModel.getReminderAt(modelRow);
        // messageはnull/空でありうる（新規未編集のまま複製・保存された等）。素の値をそのまま
        // ダイアログに出すと「「null」を削除しますか？」になってしまうため、表示専用に補う
        String label = (target.message == null || target.message.isEmpty())
            ? "（コメントなし）"
            : target.message;

        int result = JOptionPane.showConfirmDialog(
            this,
            "「" + label + "」を削除しますか？",
            "削除の確認",
            JOptionPane.YES_NO_OPTION);
        if (result != JOptionPane.YES_OPTION) return;

        // 確認待ちの間に対象が(Ext)自動削除で消えている可能性があるため引き直す
        int currentRow = reminders.indexOf(target);
        if (currentRow == -1) {
            String message = "対象は確認中に発火して自動削除されました（(Ext) 規約・§5.4）";
            statusBar.setText(message);
            DEB.pr(message + ": " + target.message);
            return;
        }

        tableModel.removeReminderAt(currentRow);
        store.save(reminders);
        table.clearSelection();
        statusBar.setText("削除しました");
    }

    /** テーブルを組み立てる。reminders はコンストラクタで受け取った同一インスタンス（③-d）。 */
    private JScrollPane buildTable() {
        tableModel = new ReminderTableModel(reminders, clock);
        table = new JTable(tableModel);
        // 複数選択は今回対象外（GUI仕様v2 §2.5）。新規/複製/削除は常に単一の対象行に対して働く
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFontSize(table, Const.FONT_SIZE_TABLE);
        setFontSize(table.getTableHeader(), Const.FONT_SIZE_TABLE);
        // フォント拡大で文字が行内に収まりきらなくなるのを防ぐため、行高をフォント基準で広げる
        table.setRowHeight(table.getFontMetrics(table.getFont()).getHeight() + 4);

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        // 既定ソート: 次回実行(col2)昇順 → コメント(col4)昇順（GUI仕様v2 §2.3）
        sorter.setSortKeys(List.of(
            new RowSorter.SortKey(2, SortOrder.ASCENDING),
            new RowSorter.SortKey(4, SortOrder.ASCENDING)
        ));
        // Type列は "1".."5" の文字列なので、桁揺れを避けるため数値比較の専用コンパレータを使う
        // setComparator は Comparator<?> を受けるため、ワイルドカード解決のためメソッド参照側の型を明示する
        sorter.setComparator(0, (Comparator<String>) ReminderFilter::compareType);
        // 残り時間列は now 依存で毎秒変わるため、ヘッダクリックでのソートを無効化する
        sorter.setSortable(3, false);

        // ダブルクリックで「編集」ボタンと同じ導線を開く。ダブルクリックした行はJTableの既定挙動で
        // 選択済みになっているため、onEditButton() 側の getSelectedRow() でそのまま拾える
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEditButton();
                }
            }
        });

        setupTableKeyBindings();

        // JScrollPane に載せないとヘッダ（列名）が表示されない — これは JTable の仕様
        return new JScrollPane(table);
    }

    /**
     * テーブルにフォーカスがある時だけ効くキーバインド（GUI仕様v2 §2.5.6）。
     * WHEN_FOCUSED に限定するのは、検索欄でのスペース入力・文字削除を殺さないため
     * （WHEN_IN_FOCUSED_WINDOWにすると窓全体で奪ってしまう）。
     * EnterはJTable既定の「次行へ移動」を上書きする形になる。
     * Spaceはinstant起動（④）に割り当て、選択行の有無に関係なく開く（onInstantButton自体が無選択を許容する）。
     */
    private void setupTableKeyBindings() {
        var inputMap = table.getInputMap(JComponent.WHEN_FOCUSED);
        var actionMap = table.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "kreminder.edit");
        actionMap.put("kreminder.edit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onEditButton(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "kreminder.instant");
        actionMap.put("kreminder.instant", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onInstantButton(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "kreminder.delete");
        actionMap.put("kreminder.delete", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onDeleteButton(); }
        });
    }

    /**
     * チェックボックス・検索欄の現在値から FilterState を組み立てる。
     * showAll は今回UIに出していないので常に false 固定（buildFilterBar のコメント参照）。
     */
    private FilterState currentFilterState() {
        return new FilterState(
            showEndedCheck.isSelected(),
            showImminentCheck.isSelected(),
            showSoonCheck.isSelected(),
            showFarCheck.isSelected(),
            false, // showAll: UIなし・固定false
            showLowPriorityCheck.isSelected(),
            showAllRepeatCheck.isSelected(),
            searchField.getText()
        );
    }

    /**
     * チェックボックス6個の現在状態を Config へ反映して保存する（GUI仕様v2 §3.7）。
     * 検索欄は永続化対象外（案A・仕様通り）なので触らない。
     */
    private void saveFilterState() {
        config.setShowEnded(showEndedCheck.isSelected());
        config.setShowImminent(showImminentCheck.isSelected());
        config.setShowSoon(showSoonCheck.isSelected());
        config.setShowFar(showFarCheck.isSelected());
        config.setShowLowPriority(showLowPriorityCheck.isSelected());
        config.setShowAllRepeat(showAllRepeatCheck.isSelected());
        config.save();
    }

    /**
     * 現在のウィンドウ状態（位置・サイズ・列幅・DEBパネル分割比率）をConfigへ書き込んで保存する
     * （フェーズ4「き」。Main.shutdownAppから終了時に1回呼ばれる）。
     * 最大化中は位置・サイズだけでなく列幅・分割比率も一切保存しない：どちらも最大化時の
     * 窓サイズから導かれるピクセル値であり、ユーザーが選んだ寸法ではないため。保存してしまうと
     * 次回「最大化ではないが画面いっぱいの窓」という中途半端な状態や、分割位置が範囲外の値で
     * クランプされて動かなくなる不具合につながる。最大化中はConfigに一切触れず、前回保存された
     * 値をそのまま残す（このガードだけで全項目を守る＝条件分岐を1箇所に集約する）。
     */
    public void saveWindowState() {
        boolean maximized = (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        if (maximized) return;

        config.setMainX(getX());
        config.setMainY(getY());
        config.setMainWidth(getWidth());
        config.setMainHeight(getHeight());

        var columnModel = table.getColumnModel();
        int[] widths = new int[columnModel.getColumnCount()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = columnModel.getColumn(i).getWidth();
        }
        config.setTableColumnWidths(ColumnWidthsCodec.format(widths));

        // 畳まれている間はsplitPane.getDividerLocation()が「畳んだ位置(下段高さ0)」を指すため、
        // その値から比率を求めてしまわないよう、展開時の比率を保持しているsavedDividerRatioを使う
        // （toggleDebugPanel参照。debugPanelOpen=falseなら現在は畳まれている）
        int height = splitPane.getHeight();
        double ratio = (debugPanelOpen && height > 0)
                ? (double) splitPane.getDividerLocation() / height
                : savedDividerRatio;
        config.setMainDividerRatio(ratio);

        config.save();
    }

    /** 現在のフィルタ状態を RowFilter として sorter に適用し、表示行を再評価させる。 */
    private void applyFilter() {
        FilterState filter = currentFilterState();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends ReminderTableModel, ? extends Integer> entry) {
                Reminder r = tableModel.getReminderAt(entry.getIdentifier());
                return ReminderFilter.isVisible(r, filter, LocalDateTime.now(clock));
            }
        });
    }

    /**
     * 1秒ごとに Main の Timer から呼ばれ、残り時間列を再描画させる。
     * 残り時間が時間バケツの閾値をまたいで表示/非表示が切り替わりうるため、
     * フィルタも同時に再適用する（setRowFilter で全行を再評価させるだけなので毎秒でも軽い）。
     */
    public void tick() {
        tableModel.tick();
        applyFilter();
    }

    /** PanelSink 生成用にデバッグパネルの JTextArea を取り出す。パネル内部構造を過度に公開しない範囲。 */
    public JTextArea getDebugTextArea() {
        return debugPanel.getTextArea();
    }

    /** フォントサイズだけを差し替える（ファミリ・スタイルはderiveFontで維持）。 */
    private static void setFontSize(JComponent c, int size) {
        c.setFont(c.getFont().deriveFont((float) size));
    }
}
