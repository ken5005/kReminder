package ken5005.kreminder;

import ken5005.kreminder.debug.ConsoleSink;
import ken5005.kreminder.debug.DEB;
import ken5005.kreminder.debug.FileSink;
import ken5005.kreminder.gui.FatalErrorDialog;
import ken5005.kreminder.gui.MainWindow;
import ken5005.kreminder.gui.PanelSink;
import ken5005.kreminder.gui.PopupBehavior;
import ken5005.kreminder.gui.PopupBehaviors;
import ken5005.kreminder.holiday.HolidayLog;
import ken5005.kreminder.holiday.HolidayOverride;
import ken5005.kreminder.holiday.HolidayService;
import ken5005.kreminder.holiday.HolidayState;
import ken5005.kreminder.holiday.HolidayStatus;
import ken5005.kreminder.holiday.OverlayHolidayCheck;
import ken5005.kreminder.lock.AcquireResult;
import ken5005.kreminder.lock.Choice;
import ken5005.kreminder.lock.ContentionHandler;
import ken5005.kreminder.lock.Fallback;
import ken5005.kreminder.lock.InstanceInfo;
import ken5005.kreminder.lock.SingleInstanceLock;
import ken5005.kreminder.sound.NotifyHandle;
import ken5005.kreminder.sound.NotifyPatterns;
import ken5005.kreminder.sound.NotifyStep;
import ken5005.kreminder.sound.Notifier;
import ken5005.kreminder.sound.SND;
import ken5005.kreminder.sound.SoundMapBuilder;
import ken5005.kreminder.sound.SoundMapParser;
import ken5005.kreminder.sound.WavLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    private static Clock clock = Clock.systemDefaultZone();
    private static final AtomicReference<HolidayState> holidayRef =
        new AtomicReference<>(new HolidayState(HolidayCheck.NONE, HolidayStatus.NONE));

    // Loaded once at startup; used to wrap every new base check with the same overlay.
    private static OverlayHolidayCheck loadedOverride;

    // Tray icon — set by setupTray; updated from the 1-second EDT timer.
    private static TrayIcon trayIcon;
    private static HolidayStatus lastTrayStatus;

    // 1秒ごとの発火判定・トレイ更新・stop.request監視を担うTimer。invokeLater内で生成するが、
    // 自身のコールバックからtimer.stop()を呼べる必要があるためローカル変数ではなくstatic昇格させている
    private static Timer timer;

    // shutdownApp()の多重実行防止フラグ。トレイExit／窓close／stop.request検知の
    // どの経路から呼ばれてもEDT上（同期不要）なので単純なbooleanガードでよい
    private static boolean shutdownDone = false;

    // reminders.json の読み書き先。AppDir.resolve("reminders.json")で決まる
    private static ReminderStore store;

    // ベースフォルダ単位の単一プロセスロック。main()冒頭で確保し、終了経路でrelease()する
    private static SingleInstanceLock instanceLock;

    // ⑤: showPopup（Extend導線）・checkReminders（(Ext)自動削除）からアクセスするため static 保持。
    // 従来はinvokeLater内のローカル変数だったが、両メソッドともstatic文脈から呼ばれるため昇格させた
    private static MainWindow window;

    // ポップアップ同時表示の上限。全処理が EDT 一本なのでカウンタの同期は不要
    private static final int MAX_POPUPS = 20;
    // 位置ずらしの1回あたりオフセット(px)。動作確認時に折り返しを見るため値を変えられるよう定数化
    private static final int POPUP_OFFSET = 105;

    private static final Deque<Reminder> popupQueue = new ArrayDeque<>();
    private static Point nextPopupLocation; // null = 次の1枚は画面中央に配置

    /** 開いている発火ポップアップ1件分＝priorityを見るためのReminderと、実体のJDialog。 */
    private record PopupEntry(Reminder reminder, JDialog dialog) {
    }

    // 現在開いているポップアップ一覧（開いた順）。同時発火時の交通整理（GUI仕様v2 §5.5）は
    // このリストから引く。openPopups.size() が同時表示枚数そのものなので、旧 openPopupCount は廃止。
    // notifyingEntry/notifyingHandle は「継続音を鳴らしている担当」の1件を指す（無ければ両方null）。
    // 全処理がEDT上（Swing Timer・ボタンリスナ・windowClosedはすべてEDTから呼ばれる）で走るため、
    // これらのフィールドへのアクセスに同期は不要。
    private static final List<PopupEntry> openPopups = new ArrayList<>();
    private static PopupEntry notifyingEntry;
    private static NotifyHandle notifyingHandle;

    public static void main(String[] args) {
        // 引数パース自体はArgsParser（純関数）に委譲。ここでは結果の受け取りとI/O判断のみ行う
        Args parsedArgs;
        try {
            parsedArgs = ArgsParser.parse(args);
        } catch (IllegalArgumentException e) {
            abort(e.getMessage());
            return;
        }

        if (parsedArgs.help()) {
            // --help/-hはエラーではないのでFatalErrorDialogは出さず、stderrへUsageを出して正常終了
            System.err.println(ArgsParser.USAGE);
            System.exit(0);
            return;
        }

        // AppDir.init はmain()の一手目（--help判定の直後）で呼ぶ。この後すぐ動くHolidayOverride.load /
        // HolidayService.loadInitial がinvokeLaterより前でベースフォルダを参照するため、他の初期化より先に確定させる。
        AppDir.init(Path.of(parsedArgs.basePath()));
        // 存在チェックはここで一度だけ行う。打ち間違いで空フォルダが自動生成される事故を防ぐため
        // 自動作成はせず、無ければUsage表示付きでabort（javaw起動でも見えるようFatalErrorDialogを使う）
        if (!Files.isDirectory(AppDir.base())) {
            abort("--base で指定したフォルダが存在しません: \"" + AppDir.base() + "\"");
            return;
        }

        // 単一プロセスロック。この時点ではDEBが未初期化のためSystem.out::printlnをロガーとして渡す
        // （lockパッケージ自体もDEBに依存させない＝他ツールへそのままコピーできる汎用実装にするため）。
        instanceLock = new SingleInstanceLock(AppDir.base(), System.out::println);
        ContentionHandler contentionHandler = new ContentionHandler() {
            @Override
            public Choice onExistingInstance(InstanceInfo holder) {
                return runOnEdtAndWait(() -> showContentionDialogOnEdt(holder), Choice.CANCEL);
            }

            @Override
            public Fallback onNoResponse(InstanceInfo holder) {
                return runOnEdtAndWait(() -> showNoResponseDialogOnEdt(holder), Fallback.CANCEL);
            }
        };
        if (instanceLock.acquire(contentionHandler) == AcquireResult.ABORTED) {
            System.exit(0);
            return;
        }

        boolean fakeClockUsed = false;
        if (parsedArgs.fakeNow() != null) {
            LocalDateTime fakeNow = parsedArgs.fakeNow();
            Duration offset = Duration.between(LocalDateTime.now(), fakeNow);
            clock = Clock.offset(Clock.systemDefaultZone(), offset);
            System.out.println("[fake-clock] fake-now=" + fakeNow + "  offset=" + offset);
            fakeClockUsed = true;
        }
        final boolean fakeClockUsedFinal = fakeClockUsed;

        // reminders.jsonの存在チェックは撤廃：不在なら空リストで起動する（ReminderStoreの従来挙動のまま）
        store = new ReminderStore(AppDir.resolve("reminders.json"));

        // Load override file once — holds add/remove sets for the session
        loadedOverride = HolidayOverride.load(HolidayCheck.NONE);
        HolidayLog.log(clock, "[Main] override loaded: +" + loadedOverride.addCount()
            + "/-" + loadedOverride.removeCount());

        // Load cached holidays synchronously before starting the timer
        HolidayState initial = HolidayService.loadInitial(clock);
        holidayRef.set(new HolidayState(applyOverride(initial.check()), initial.status()));
        HolidayLog.log(clock, "[Main] loadInitial: " + initial.status());

        SwingUtilities.invokeLater(() -> {
            // ③-d: リスト一本化。ここでload()した同一インスタンスをMainWindow/checkReminders双方に渡す
            // （以前はMainWindowが自前でload()しており、編集や発火状態の書き戻し先が食い違っていた）
            // storeも同様に単一インスタンスをMainWindow/checkReminders双方に渡し、
            // 読み書き先（Path）を一致させる（AppDir切り替え時の食い違い防止）。
            // store自体はmain()冒頭でAppDir.resolve済みでここではload()するだけ
            List<Reminder> reminders = store.load();
            window = new MainWindow(clock, reminders, store);
            PanelSink panelSink = new PanelSink(window.getDebugTextArea());
            DEB.init(clock, new ConsoleSink(), new FileSink(clock), panelSink);

            // snd.wav.dir はMainWindow内のConfigとは別インスタンス。DEB配線直後に読んで
            // SNDワーカーを起動するだけの一度きりの用途なので、フィルタ状態と共有する必要はない
            Config config = new Config();
            config.load();
            initSound(config);

            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // EXIT_ON_CLOSEなのでこの後Swingが自分でJVMを落とす＝System.exitはここでは呼ばない
                    shutdownApp();
                }
            });
            window.setVisible(true);

            DEB.pr(fakeClockUsedFinal ? "起動: kReminder（fake-clock使用）" : "起動: kReminder");
            DEB.pr("reminders読込先: " + store.getPath());
            DEB.pr("reminders読込: " + reminders.size() + "件");
            DEB.pr("祝日loadInitial: " + initial.status());

            // TODO (known): past unfired reminders fire immediately on startup.
            //  A future version must decide: fire-immediately / skip / batch-notify.
            timer = new Timer(1000, e -> {
                // 別プロセスから「既存を停止して起動」/「両方停止」を選ばれた合図。
                // 通常の発火判定より先に見る＝stop.requestが立っていたら以降は一切進めず退去する
                if (instanceLock.stopRequested()) {
                    DEB.pr("別プロセスから停止要求を受信。終了します。");
                    timer.stop();
                    shutdownApp();
                    System.exit(0);
                    return;
                }
                checkReminders(reminders);
                updateTrayStatus();
                window.tick();
            });
            timer.start();
            setupTray();
        });

        // Background refresh — updates holidayRef when a newer CSV is fetched
        HolidayService.refreshAsync(
            holidayRef::get,
            newState -> {
                HolidayStatus prevStatus = holidayRef.get().status();
                // For OK (new CSV): apply overlay to the raw base check.
                // For DEGRADED: newState.check() is already the current overlay-applied check.
                HolidayCheck activeCheck = newState.status() == HolidayStatus.OK
                    ? applyOverride(newState.check())
                    : newState.check();
                holidayRef.set(new HolidayState(activeCheck, newState.status()));
                HolidayLog.log(clock, "[Main] status: " + prevStatus + " -> " + newState.status());
                DEB.pr("祝日status: " + prevStatus + " -> " + newState.status());
            },
            clock
        );
    }

    /**
     * 引数不正時の共通終了処理（戻らない）。stderrへは常に出す一方、javaw起動時はstderrが誰にも
     * 見えないため（System.console() == null で判定）、FatalErrorDialogでも同じ内容を知らせる。
     * showAndExit内部でexit(1)するが、コンソール起動時の経路（if文をスキップする側）を閉じるため
     * 末尾に改めてSystem.exit(1)を置く。
     */
    private static void abort(String message) {
        System.err.println("kReminder: " + message);
        System.err.println();
        System.err.println(ArgsParser.USAGE);
        if (System.console() == null) {
            FatalErrorDialog.showAndExit(message + "\n\n" + ArgsParser.USAGE);
        }
        System.exit(1);
    }

    /**
     * ロック競合まわりのダイアログはmain()本体（非EDT）から呼ばれる前提でinvokeAndWaitに包む。
     * FatalErrorDialogと同様、万一EDT上から呼ばれた場合の保険としてisEventDispatchThread()で
     * 分岐しておく。ダイアログ表示自体に失敗した場合は呼び出し側が渡すfallback（安全側の値）に倒す。
     */
    private static <T> T runOnEdtAndWait(java.util.function.Supplier<T> showDialog, T fallback) {
        if (SwingUtilities.isEventDispatchThread()) {
            return showDialog.get();
        }
        AtomicReference<T> result = new AtomicReference<>(fallback);
        try {
            SwingUtilities.invokeAndWait(() -> result.set(showDialog.get()));
        } catch (Exception e) {
            System.err.println("ダイアログの表示に失敗した。安全側へ倒します: " + e.getMessage());
        }
        return result.get();
    }

    /**
     * JOptionPaneをalwaysOnTopな一時的な無装飾JFrame（FatalErrorDialogと同じ手口）を親にして出す。
     * 親にnullを渡すと他窓の裏に隠れることがあるため、これで必ず最前面に出す。
     */
    private static int showOwnedOptionDialog(String message, String title, String[] options, int defaultIndex) {
        JFrame owner = new JFrame();
        owner.setAlwaysOnTop(true);
        owner.setUndecorated(true);
        owner.setLocationRelativeTo(null);
        owner.setVisible(true); // alwaysOnTopを効かせるにはownerが表示されている必要がある

        int selected = JOptionPane.showOptionDialog(
            owner,
            message,
            title,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[defaultIndex]);

        owner.dispose();
        return selected;
    }

    /**
     * 既存インスタンスとの競合時に出す3択。ボタン並びは
     * 「既存を停止して起動」「起動を中止」「両方停止」で固定、既定選択（初期フォーカス）は
     * 一番安全な「起動を中止」。×やEscapeでの close（CLOSED_OPTION）も含め、
     * 0（既存を停止して起動）と2（両方停止）以外はすべてCANCELへ倒す。
     */
    private static Choice showContentionDialogOnEdt(InstanceInfo holder) {
        String[] options = {"既存を停止して起動", "起動を中止", "両方停止"};
        int selected = showOwnedOptionDialog(
            buildContentionMessage(holder), "kReminder — 起動の競合", options, 1);

        return switch (selected) {
            case 0 -> Choice.STOP_EXISTING;
            case 2 -> Choice.STOP_BOTH;
            default -> Choice.CANCEL; // 1（起動を中止）／CLOSED_OPTION(-1)含め全部CANCEL
        };
    }

    /** 競合ダイアログの本文。pidが-1（info読み取り失敗）のときはpid/startedAtを出さず一言添える。 */
    private static String buildContentionMessage(InstanceInfo holder) {
        StringBuilder sb = new StringBuilder();
        sb.append("同じベースフォルダを使う kReminder が既に起動しています。\n\n");
        if (holder.pid() > 0) {
            sb.append("pid: ").append(holder.pid()).append("\n");
            sb.append("起動時刻: ").append(holder.startedAt()).append("\n");
        } else {
            sb.append("既存プロセスの情報ファイルが読めませんでした。\n");
        }
        sb.append("base: ").append(holder.base());
        return sb.toString();
    }

    /**
     * 退去要求を出したのに既存が timeout 内に応答しなかった場合の2択。
     * 「強制終了して起動」→FORCE_KILL、「起動を中止」→CANCEL。既定選択は安全側のCANCEL。
     */
    private static Fallback showNoResponseDialogOnEdt(InstanceInfo holder) {
        String[] options = {"強制終了して起動", "起動を中止"};
        int selected = showOwnedOptionDialog(
            buildNoResponseMessage(holder), "kReminder — 応答なし", options, 1);
        return selected == 0 ? Fallback.FORCE_KILL : Fallback.CANCEL;
    }

    /** 無応答ダイアログの本文。pidが-1のときはpid行を出さない（競合ダイアログと同じ扱い）。 */
    private static String buildNoResponseMessage(InstanceInfo holder) {
        StringBuilder sb = new StringBuilder();
        sb.append("既存プロセスが応答しません。強制終了して起動しますか？\n\n");
        if (holder.pid() > 0) {
            sb.append("pid: ").append(holder.pid()).append("\n");
        }
        sb.append("base: ").append(holder.base());
        return sb.toString();
    }

    /**
     * SNDの初期化一式：wavDir走査→sound-map未生成なら雛形書き出し→有ればUTF-8読込→parse→build→SND.init。
     * wavDirが無ければSND自体をinitせずスキップ（従来のgraceful挙動を維持）。
     * sound-mapの読み込み失敗・parse/buildの不正（dangling/重複キー/衝突/不正行）は
     * FatalErrorDialogでloudに落とす（手編集した設定が壊れて読めない状態を握り潰さない方針）。
     */
    private static void initSound(Config config) {
        Path wavDir = config.getWavDir();
        if (!Files.isDirectory(wavDir)) {
            DEB.pr("SND: wavDir が存在しない: " + wavDir + "（音声再生はスキップ）");
            return;
        }
        List<File> wavFiles = WavLoader.load(wavDir);

        Path soundMapPath = config.getSoundMapPath();
        Map<String, String> table;
        if (!Files.exists(soundMapPath)) {
            // 初回だけ雛形を書き出す。生成した回はテーブル空＝全ファイルstem自動採用のまま進めてよい
            String template = SoundMapParser.renderTemplate(wavFiles);
            try {
                Files.writeString(soundMapPath, template, StandardCharsets.UTF_8);
            } catch (IOException e) {
                DEB.pr("sound-map.properties の書き出しに失敗: " + e.getMessage());
            }
            table = Map.of();
        } else {
            List<String> lines;
            try {
                lines = Files.readAllLines(soundMapPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                FatalErrorDialog.showAndExit("sound-map.properties が読み込めません: " + e.getMessage());
                return;
            }
            try {
                table = SoundMapParser.parse(lines);
            } catch (IllegalArgumentException e) {
                FatalErrorDialog.showAndExit(e.getMessage());
                return;
            }
        }

        Map<String, File> soundMap;
        try {
            soundMap = SoundMapBuilder.build(wavFiles, table);
        } catch (IllegalArgumentException e) {
            FatalErrorDialog.showAndExit(e.getMessage());
            return;
        }

        SND.init(soundMap);
    }

    /** Wraps base with the session-level override overlay (same add/remove for every base). */
    private static HolidayCheck applyOverride(HolidayCheck base) {
        if (loadedOverride.isEmpty()) return base;
        return loadedOverride.withBase(base);
    }

    private static void checkReminders(List<Reminder> reminders) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean changed = false;
        // 拡張forの中でreminders.remove()するとConcurrentModificationExceptionになるため、
        // (Ext)自動削除対象はここに溜めておき、ループを抜けてから削除する（GUI仕様v2 §5.4）
        List<Reminder> extToRemove = new ArrayList<>();
        for (Reminder r : reminders) {
            if (!r.noticed && r.fireAt != null && !r.fireAt.isAfter(now)) {
                r.noticed = true;
                changed = true;
                popupQueue.add(r);
                reschedule(r, now);
                if (ExtName.hasExtPrefix(r.message) && (r.repeat == null || r.repeat.isEmpty())) {
                    extToRemove.add(r);
                }
            }
        }
        for (Reminder r : extToRemove) {
            window.removeReminder(r);
            DEB.pr("(Ext) 発火後自動削除: " + r.message);
        }
        if (changed) {
            pumpPopups();
            store.save(reminders);
        }
    }

    /** 待ち行列から枚数上限まで補充してポップアップを開く。ポップアップが閉じた側からも呼ばれる。 */
    private static void pumpPopups() {
        while (openPopups.size() < MAX_POPUPS && !popupQueue.isEmpty()) {
            Reminder r = popupQueue.poll();
            showPopup(r);
        }
    }

    private static void reschedule(Reminder r, LocalDateTime now) {
        if (r.repeat == null || r.repeat.isEmpty()) return;
        try {
            RepeatSpec spec = RepeatSpec.parse(r.repeat);
            r.fireAt  = spec.nextAfter(r.fireAt, now, holidayRef.get().check());
            r.noticed = false;
        } catch (Exception e) {
            // 壊れた repeat 文字列は本体を巻き込まない — noticed=true のまま単発扱い
            System.err.println("bad repeat, treated as one-shot: " + r.repeat + " / " + e);
        }
    }

    /** Called from the 1-second EDT timer — refreshes tray icon and tooltip when status changes. */
    private static void updateTrayStatus() {
        if (trayIcon == null) return;
        HolidayStatus status = holidayRef.get().status();
        if (status == lastTrayStatus) return;
        lastTrayStatus = status;
        trayIcon.setImage(createIcon(statusColor(status)));
        trayIcon.setToolTip(buildTooltip(status));
    }

    private static Color statusColor(HolidayStatus status) {
        return switch (status) {
            case OK       -> new Color(50, 180, 50);
            case DEGRADED -> new Color(220, 180, 0);
            case NONE     -> new Color(200, 60, 60);
        };
    }

    private static Image createIcon(Color color) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        g.setColor(color);
        g.fillOval(2, 2, 12, 12);
        g.dispose();
        return img;
    }

    private static String buildTooltip(HolidayStatus status) {
        String label = switch (status) {
            case OK       -> "正常";
            case DEGRADED -> "縮退";
            case NONE     -> "無視";
        };
        return "kReminder — 祝日:" + label
            + "（override +" + loadedOverride.addCount() + "/-" + loadedOverride.removeCount() + "）";
    }

    /** priorityがnull（旧JSON防御）ならPri3相当として扱う。ordinalが大きいほど優先度が高い（Pri5が最高）。 */
    private static int priorityRank(Reminder r) {
        Reminder.Priority p = r.priority != null ? r.priority : Reminder.Priority.Pri3;
        return p.ordinal();
    }

    /**
     * 「開いているポップアップのうち priority が最も高いエントリが、継続音の担当である」という
     * 不変条件をここに集約する（GUI仕様v2 §5.5）。showPopup・windowClosedの両方から呼ばれる。
     * 同率なら先着優先＝リストは開いた順に並んでいるので、先頭から見て「厳密に上回った時だけ」
     * 更新すれば自然に先着優先になる。
     */
    private static void retuneNotification() {
        if (openPopups.isEmpty()) {
            if (notifyingHandle != null) notifyingHandle.stop();
            notifyingEntry = null;
            notifyingHandle = null;
            return;
        }

        PopupEntry best = openPopups.get(0);
        for (PopupEntry entry : openPopups) {
            if (priorityRank(entry.reminder()) > priorityRank(best.reminder())) {
                best = entry;
            }
        }
        if (best == notifyingEntry) return; // 既に担当なら鳴らし直さない

        if (notifyingHandle != null) notifyingHandle.stop();
        notifyingEntry = best;
        notifyingHandle = Notifier.start(NotifyPatterns.forPriority(best.reminder().priority));
    }

    private static void showPopup(Reminder r) {
        PopupBehavior behavior = PopupBehaviors.forPriority(r.priority);

        // 非モーダル化: モーダルのままだと setVisible(true) が EDT をブロックし、
        // ポップアップ表示中に1秒 Timer（残り時間表示・編集）が全部止まってしまう
        JDialog dialog = new JDialog((Frame) null, "kReminder", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        String text = r.message != null ? r.message : "(no message)";
        JLabel label = new JLabel("<html>" + text.replace("\n", "<br>") + "</html>");
        label.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        dialog.add(label, BorderLayout.CENTER);

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> dialog.dispose());

        JPanel south = new JPanel();
        south.add(ok);
        // showExtend=false（Pri1）ならExtendボタンはそもそも生成・配置しない＝OKのみ（GUI仕様v2 §5.1）
        if (behavior.showExtend()) {
            // Extend（＝スヌーズ）: instant編集ダイアログを開き、OKで実際に登録できたときだけ
            // このポップアップを閉じる（GUI仕様v2 §5.3）。キャンセル/Esc/×なら通知は消さずポップアップを残す。
            // instant側もalwaysOnTopなので、開いている間だけ自分（張本人のポップアップ）は最前面を降りる
            // ＝そうしないと2つのalwaysOnTop窓が被り、instant側が背後に隠れて読めなくなる
            JButton extend = new JButton("Extend");
            extend.addActionListener(e -> {
                dialog.setAlwaysOnTop(false);
                boolean added = window.openExtendEditor(r);
                if (added) {
                    dialog.dispose();
                } else {
                    dialog.setAlwaysOnTop(true); // キャンセルなら最前面に復帰
                }
            });
            south.add(extend);
        }
        dialog.add(south, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setMinimumSize(new Dimension(240, 100));
        placePopup(dialog);
        dialog.setAlwaysOnTop(true);

        // 自動消滅（Pri1のみ・autoCloseAfterが非null）: 単発Timerでdispose()する。
        // ダイアログがOK等で先に閉じられた場合に備え、windowClosedでTimerをstopする（生き残り防止）
        Timer autoCloseTimer;
        if (behavior.autoCloseAfter() != null) {
            autoCloseTimer = new Timer((int) behavior.autoCloseAfter().toMillis(), e -> dialog.dispose());
            autoCloseTimer.setRepeats(false);
            autoCloseTimer.start();
        } else {
            autoCloseTimer = null;
        }

        // 一覧に加えてから交通整理（GUI仕様v2 §5.5）。自分が担当にならなかった場合に限り、
        // 新着に気づけるよう自分のパターンの第1ステップだけを1回鳴らす（継続音は鳴らさない）
        PopupEntry entry = new PopupEntry(r, dialog);
        openPopups.add(entry);
        retuneNotification();
        if (notifyingEntry != entry) {
            NotifyStep firstStep = NotifyPatterns.forPriority(r.priority).steps().get(0);
            SND.play(firstStep.soundName(), firstStep.volume());
        }

        // OK（dispose()）・Extend（OK登録時のdispose()）・×（DISPOSE_ON_CLOSE）のどれで閉じても
        // windowClosed が発火するので、通知停止と枚数カウンタの後処理をここに一本化する
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (autoCloseTimer != null) autoCloseTimer.stop();
                openPopups.remove(entry);
                if (notifyingEntry == entry) {
                    notifyingHandle.stop();
                    notifyingEntry = null;
                    notifyingHandle = null;
                }
                retuneNotification(); // 残っている最優先のポップアップが頭から鳴り始める
                if (openPopups.isEmpty()) nextPopupLocation = null; // 全部閉じたら次の1枚目はまた中央から
                pumpPopups();
            }
        });

        dialog.setVisible(true);
    }

    /**
     * ポップアップの表示位置を決めて dialog に設定する。
     * 1枚目（nextPopupLocation未設定）は画面中央。以降は前回位置から右下に POPUP_OFFSET ずつずらし、
     * 使用可能領域（タスクバー等を除く）をはみ出す場合は左上に折り返す。
     */
    private static void placePopup(JDialog dialog) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Point location;
        if (nextPopupLocation == null) {
            location = new Point(
                screen.x + (screen.width - dialog.getWidth()) / 2,
                screen.y + (screen.height - dialog.getHeight()) / 2
            );
        } else {
            location = nextPopupLocation;
            boolean overflows = location.x + dialog.getWidth() > screen.x + screen.width
                || location.y + dialog.getHeight() > screen.y + screen.height;
            if (overflows) {
                location = new Point(screen.x, screen.y);
            }
        }
        dialog.setLocation(location);
        nextPopupLocation = new Point(location.x + POPUP_OFFSET, location.y + POPUP_OFFSET);
    }

    /**
     * アプリの終了処理を1箇所に集約。トレイExit／窓close／stop.request検知のどの経路から
     * 呼ばれてもここを通る。全経路EDT上（ActionListener・windowClosing・Timerコールバックは
     * すべてEDTから呼ばれる）なので同期は不要。多重に呼ばれても副作用が起きないよう
     * 冒頭でガードする（＝冪等）。System.exitは呼び出し側の事情が異なるためここには含めない
     * （トレイExitはSystem.exit(0)を続けて呼ぶ／windowClosingはEXIT_ON_CLOSEがJVMを落とす）。
     */
    private static void shutdownApp() {
        if (shutdownDone) return;
        shutdownDone = true;

        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        DEB.shutdown();
        SND.shutdown();
        instanceLock.release();
    }

    private static void setupTray() {
        if (!SystemTray.isSupported()) return;

        HolidayStatus initialStatus = holidayRef.get().status();
        lastTrayStatus = initialStatus;

        PopupMenu popup = new PopupMenu();
        trayIcon = new TrayIcon(createIcon(statusColor(initialStatus)), buildTooltip(initialStatus), popup);

        MenuItem exit = new MenuItem("Exit kReminder");
        exit.addActionListener(e -> {
            timer.stop();
            shutdownApp();
            System.exit(0);
        });
        popup.add(exit);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("tray icon failed: " + e.getMessage());
        }
    }
}
