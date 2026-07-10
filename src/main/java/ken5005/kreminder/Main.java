package ken5005.kreminder;

import ken5005.kreminder.debug.ConsoleSink;
import ken5005.kreminder.debug.DEB;
import ken5005.kreminder.debug.FileSink;
import ken5005.kreminder.gui.MainWindow;
import ken5005.kreminder.gui.PanelSink;
import ken5005.kreminder.holiday.HolidayLog;
import ken5005.kreminder.holiday.HolidayOverride;
import ken5005.kreminder.holiday.HolidayService;
import ken5005.kreminder.holiday.HolidayState;
import ken5005.kreminder.holiday.HolidayStatus;
import ken5005.kreminder.holiday.OverlayHolidayCheck;
import ken5005.kreminder.sound.SND;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
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

    // reminders.json の読み書き先。step3で--dataによる注入に対応する（現時点はデフォルト固定）
    private static ReminderStore store;

    // ポップアップ同時表示の上限。全処理が EDT 一本なのでカウンタの同期は不要
    private static final int MAX_POPUPS = 10;
    // 位置ずらしの1回あたりオフセット(px)。動作確認時に折り返しを見るため値を変えられるよう定数化
    private static final int POPUP_OFFSET = 105;

    private static final Deque<Reminder> popupQueue = new ArrayDeque<>();
    private static int openPopupCount = 0;
    private static Point nextPopupLocation; // null = 次の1枚は画面中央に配置

    public static void main(String[] args) {
        boolean fakeClockUsed = false;
        String dataOpt = null;
        for (String arg : args) {
            if (arg.startsWith("--fake-now=")) {
                String value = arg.substring("--fake-now=".length());
                try {
                    LocalDateTime fakeNow = LocalDateTime.parse(value);
                    Duration offset = Duration.between(LocalDateTime.now(), fakeNow);
                    clock = Clock.offset(Clock.systemDefaultZone(), offset);
                    System.out.println("[fake-clock] fake-now=" + fakeNow + "  offset=" + offset);
                    fakeClockUsed = true;
                } catch (DateTimeParseException e) {
                    System.err.println("kReminder: invalid --fake-now value: \"" + value + "\"  (expected YYYY-MM-DDTHH:mm:ss)");
                    System.exit(1);
                }
            } else if (arg.startsWith("--data=")) {
                dataOpt = arg.substring("--data=".length());
            }
        }
        final boolean fakeClockUsedFinal = fakeClockUsed;

        // --data: 絶対パスのみ許可し、かつ指定時はファイル存在必須（新規作成で本来のreminders.jsonと
        // 混同するのを防ぐ）。相対パス・未存在パスはfake-nowの不正値と同じくstderr+exit(1)
        try {
            Path dataPath = DataPathResolver.resolve(dataOpt);
            if (dataOpt != null && !Files.exists(dataPath)) {
                System.err.println("kReminder: --data path does not exist: \"" + dataPath + "\"");
                System.exit(1);
                return;
            }
            store = new ReminderStore(dataPath);
        } catch (IllegalArgumentException e) {
            System.err.println("kReminder: " + e.getMessage());
            System.exit(1);
            return;
        }

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
            // 読み書き先（Path）を一致させる（--data注入時の食い違い防止）。
            // store自体はmain()冒頭で--data解決済みでここではload()するだけ
            List<Reminder> reminders = store.load();
            MainWindow window = new MainWindow(clock, reminders, store);
            PanelSink panelSink = new PanelSink(window.getDebugTextArea());
            DEB.init(clock, new ConsoleSink(), new FileSink(clock), panelSink);

            // snd.wav.dir はMainWindow内のConfigとは別インスタンス。DEB配線直後に読んで
            // SNDワーカーを起動するだけの一度きりの用途なので、フィルタ状態と共有する必要はない
            Config config = new Config();
            config.load();
            // TODO(sound-map step4で本配線): WavLoader.load + sound-map読込/parse/buildへ差し替える。
            // このstepではSND.initのシグネチャ変更(Map<String,File>化)にコンパイルを合わせるだけの仮対応。
            SND.init(Map.of());

            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    DEB.shutdown();
                    SND.shutdown();
                }
            });
            window.setVisible(true);

            DEB.pr(fakeClockUsedFinal ? "起動: kReminder（fake-clock使用）" : "起動: kReminder");
            DEB.pr("reminders読込先: " + store.getPath());
            DEB.pr("reminders読込: " + reminders.size() + "件");
            DEB.pr("祝日loadInitial: " + initial.status());

            // TODO (known): past unfired reminders fire immediately on startup.
            //  A future version must decide: fire-immediately / skip / batch-notify.
            Timer timer = new Timer(1000, e -> {
                checkReminders(reminders);
                updateTrayStatus();
                window.tick();
            });
            timer.start();
            setupTray(timer);
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

    /** Wraps base with the session-level override overlay (same add/remove for every base). */
    private static HolidayCheck applyOverride(HolidayCheck base) {
        if (loadedOverride.isEmpty()) return base;
        return loadedOverride.withBase(base);
    }

    private static void checkReminders(List<Reminder> reminders) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean changed = false;
        for (Reminder r : reminders) {
            if (!r.noticed && r.fireAt != null && !r.fireAt.isAfter(now)) {
                r.noticed = true;
                changed = true;
                popupQueue.add(r);
                reschedule(r, now);
            }
        }
        if (changed) {
            pumpPopups();
            store.save(reminders);
        }
    }

    /** 待ち行列から枚数上限まで補充してポップアップを開く。ポップアップが閉じた側からも呼ばれる。 */
    private static void pumpPopups() {
        while (openPopupCount < MAX_POPUPS && !popupQueue.isEmpty()) {
            Reminder r = popupQueue.poll();
            openPopupCount++;
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

    private static void showPopup(Reminder r) {
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
        dialog.add(south, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setMinimumSize(new Dimension(240, 100));
        placePopup(dialog);
        dialog.setAlwaysOnTop(true);

        // OK（dispose()）・×（DISPOSE_ON_CLOSE）のどちらで閉じても windowClosed が発火するので、
        // 枚数カウンタの後処理をここに一本化する
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                openPopupCount--;
                if (openPopupCount == 0) nextPopupLocation = null; // 全部閉じたら次の1枚目はまた中央から
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

    private static void setupTray(Timer timer) {
        if (!SystemTray.isSupported()) return;

        HolidayStatus initialStatus = holidayRef.get().status();
        lastTrayStatus = initialStatus;

        PopupMenu popup = new PopupMenu();
        trayIcon = new TrayIcon(createIcon(statusColor(initialStatus)), buildTooltip(initialStatus), popup);

        MenuItem exit = new MenuItem("Exit kReminder");
        exit.addActionListener(e -> {
            timer.stop();
            SystemTray.getSystemTray().remove(trayIcon);
            DEB.shutdown();
            SND.shutdown();
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
