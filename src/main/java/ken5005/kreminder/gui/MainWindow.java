package ken5005.kreminder.gui;

import ken5005.kreminder.Config;
import ken5005.kreminder.EditFormLogic;
import ken5005.kreminder.FilterState;
import ken5005.kreminder.Reminder;
import ken5005.kreminder.ReminderFilter;
import ken5005.kreminder.ReminderStore;
import ken5005.kreminder.debug.DEB;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Clock;
import java.time.LocalDateTime;
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
    private int savedDividerLocation = -1; // 未設定＝初回はDEFAULT_DEBUG_DIVIDER_RATIOを使う
    private boolean initialCollapseApplied = false;

    public MainWindow(Clock clock, List<Reminder> reminders) {
        super("kReminder");
        this.clock = clock;
        this.reminders = reminders;
        setSize(800, 500);
        // 画面中央に配置（null = 自画面基準）
        setLocationRelativeTo(null);
        // ×ボタンで JVM ごと終了（常駐トレイ版とは切り離した学習用 main 前提）
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // buildFilterBar() でチェックボックス初期値に使うため、UIを組む前に読み込む
        config.load();

        getContentPane().add(buildTopBars(),   BorderLayout.NORTH);
        getContentPane().add(buildSplitPane(), BorderLayout.CENTER);
        getContentPane().add(statusBar,       BorderLayout.SOUTH);

        // フィルタUI（buildTopBars）とテーブル/sorter（buildSplitPane）の両方が揃ってから
        // 初期フィルタを適用する。これをしないと起動直後は無フィルタ（全件表示）になってしまう。
        applyFilter();
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

        for (String name : new String[]{"新規", "編集", "複製", "削除", "更新", "デバッグログ"}) {
            var btn = new JButton(name);
            if (name.equals("デバッグログ")) {
                btn.addActionListener(e -> toggleDebugPanel());
            } else if (name.equals("編集")) {
                btn.addActionListener(e -> onEditButton());
            } else {
                // 各ボタンの ActionListener はステータスバー更新＋DEBログ出力のみ（業務ロジックなし）
                btn.addActionListener(e -> {
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
            cb.addItemListener(e -> {
                applyFilter();
                saveFilterState();
            });
            bar.add(cb);
        }

        bar.addSeparator();
        bar.add(new JLabel("検索"));
        searchField = new JTextField(10);
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

    /** デバッグパネルの開閉を切り替える。開く時は直前位置（無ければ既定比率）に復元する。 */
    private void toggleDebugPanel() {
        if (debugPanelOpen) {
            savedDividerLocation = splitPane.getDividerLocation();
            splitPane.setDividerLocation(1.0); // 下段高さ0＝collapsed
        } else {
            int location = savedDividerLocation >= 0
                    ? savedDividerLocation
                    : (int) (splitPane.getHeight() * DEFAULT_DEBUG_DIVIDER_RATIO);
            splitPane.setDividerLocation(location);
        }
        debugPanelOpen = !debugPanelOpen;
    }

    /**
     * 「編集」ボタンの導線（GUI仕様v2 ③-b/③-d）。選択行のReminderをEditDialogに渡して開き、
     * OKで閉じられた場合のみ入力値をoriginalへ書き戻して保存・再描画する。
     * 未選択（viewRow==-1）ならダイアログは開かず、ステータスバーで案内するだけにする。
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
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true); // モーダルなのでダイアログが閉じるまでここで待つ

        if (!dialog.isOkPressed()) return; // キャンセル・Esc・×は何もしない

        // OK活性で保証済みだが、書き戻し前に念のため再パースして確認する（防御的）
        var parsed = EditFormLogic.parseExecTime(dialog.getExecTimeText());
        if (parsed.isEmpty()) return;

        // 案A：入力値をoriginalへ上書き。案(あ)：編集したら発火済みフラグを一律リセットする
        original.fireAt = parsed.get();
        original.repeat = dialog.getRepeatText();
        original.priority = dialog.getSelectedPriority();
        original.message = dialog.getCommentText();
        original.action = dialog.getCmdText();
        original.noticed = false;

        ReminderStore.save(reminders); // 一本化した同一リストを全書き
        tableModel.reminderUpdatedAt(modelRow);
    }

    /** テーブルを組み立てる。reminders はコンストラクタで受け取った同一インスタンス（③-d）。 */
    private JScrollPane buildTable() {
        tableModel = new ReminderTableModel(reminders, clock);
        table = new JTable(tableModel);

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

        // JScrollPane に載せないとヘッダ（列名）が表示されない — これは JTable の仕様
        return new JScrollPane(table);
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
}
