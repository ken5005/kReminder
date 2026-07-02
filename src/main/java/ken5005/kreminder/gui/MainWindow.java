package ken5005.kreminder.gui;

import ken5005.kreminder.ReminderStore;
import ken5005.kreminder.debug.ConsoleSink;
import ken5005.kreminder.debug.DEB;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Clock;

/**
 * kReminder のメイン画面。
 * スライス①-b-2 step1: JSplitPaneで下段にデバッグログパネルを追加。
 */
public class MainWindow extends JFrame {

    // 下段の既定復元位置（分割ペイン高さに対する比率）。初回オープン時にこれを使う。
    private static final double DEFAULT_DEBUG_DIVIDER_RATIO = 0.7;

    // ステータスバーは ActionListener から更新するためフィールドに持つ
    private final JLabel statusBar = new JLabel(" ");
    private final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final DebugPanel debugPanel = new DebugPanel();

    private boolean debugPanelOpen = false;
    private int savedDividerLocation = -1; // 未設定＝初回はDEFAULT_DEBUG_DIVIDER_RATIOを使う
    private boolean initialCollapseApplied = false;

    public MainWindow() {
        super("kReminder");
        setSize(800, 500);
        // 画面中央に配置（null = 自画面基準）
        setLocationRelativeTo(null);
        // ×ボタンで JVM ごと終了（常駐トレイ版とは切り離した学習用 main 前提）
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        getContentPane().add(buildToolBar(),  BorderLayout.NORTH);
        getContentPane().add(buildSplitPane(), BorderLayout.CENTER);
        getContentPane().add(statusBar,       BorderLayout.SOUTH);
    }

    /** ツールバーを組み立てる。ボタン機能は seam のみ——将来コントローラへ委譲する。 */
    private JToolBar buildToolBar() {
        var bar = new JToolBar();
        bar.setFloatable(false); // ドラッグで切り離せないようにする（常設ツールバーの慣用）

        for (String name : new String[]{"新規", "編集", "複製", "削除", "更新", "デバッグログ"}) {
            var btn = new JButton(name);
            if (name.equals("デバッグログ")) {
                btn.addActionListener(e -> toggleDebugPanel());
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

    /** テーブルを組み立てる。読込失敗時は空リストで起動（ReminderStore.load() が保証）。 */
    private JScrollPane buildTable() {
        var reminders = ReminderStore.load();
        var model = new ReminderTableModel(reminders);
        var table = new JTable(model);
        // JScrollPane に載せないとヘッダ（列名）が表示されない — これは JTable の仕様
        return new JScrollPane(table);
    }

    /**
     * 学習用の単独起動エントリポイント。
     * Swing のコンポーネントはすべて EDT（Event Dispatch Thread）上で操作する決まりがあるため、
     * invokeLater でウィンドウ生成も EDT に委ねる。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            var window = new MainWindow();
            var panelSink = new PanelSink(window.debugPanel.getTextArea());
            DEB.init(Clock.systemDefaultZone(), new ConsoleSink(), panelSink);

            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    DEB.shutdown();
                }
            });

            window.setVisible(true);
        });
    }
}
