package ken5005.kreminder;

import ken5005.kreminder.holiday.HolidayOverride;
import ken5005.kreminder.holiday.HolidayService;
import ken5005.kreminder.holiday.HolidayState;
import ken5005.kreminder.holiday.HolidayStatus;
import ken5005.kreminder.holiday.OverlayHolidayCheck;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    private static Clock clock = Clock.systemDefaultZone();
    private static final AtomicReference<HolidayState> holidayRef =
        new AtomicReference<>(new HolidayState(HolidayCheck.NONE, HolidayStatus.NONE));

    // Loaded once at startup; used to wrap every new base check with the same overlay.
    private static OverlayHolidayCheck loadedOverride;

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--fake-now=")) {
                String value = arg.substring("--fake-now=".length());
                try {
                    LocalDateTime fakeNow = LocalDateTime.parse(value);
                    Duration offset = Duration.between(LocalDateTime.now(), fakeNow);
                    clock = Clock.offset(Clock.systemDefaultZone(), offset);
                    System.out.println("[fake-clock] fake-now=" + fakeNow + "  offset=" + offset);
                } catch (DateTimeParseException e) {
                    System.err.println("kReminder: invalid --fake-now value: \"" + value + "\"  (expected YYYY-MM-DDTHH:mm:ss)");
                    System.exit(1);
                }
            }
        }

        // Load override file once — holds add/remove sets for the session
        loadedOverride = HolidayOverride.load(HolidayCheck.NONE);

        // Load cached holidays synchronously before starting the timer
        HolidayState initial = HolidayService.loadInitial(clock);
        holidayRef.set(new HolidayState(applyOverride(initial.check()), initial.status()));

        SwingUtilities.invokeLater(() -> {
            List<Reminder> reminders = ReminderStore.load();
            // TODO (known): past unfired reminders fire immediately on startup.
            //  A future version must decide: fire-immediately / skip / batch-notify.
            Timer timer = new Timer(1000, e -> checkReminders(reminders));
            timer.start();
            setupTray(timer);
        });

        // Background refresh — updates holidayRef when a newer CSV is fetched
        HolidayService.refreshAsync(
            () -> holidayRef.get(),
            newState -> {
                // For OK (new CSV): apply overlay to the raw base check.
                // For DEGRADED: newState.check() is already the current overlay-applied check.
                HolidayCheck activeCheck = newState.status() == HolidayStatus.OK
                    ? applyOverride(newState.check())
                    : newState.check();
                holidayRef.set(new HolidayState(activeCheck, newState.status()));
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
                showPopup(r);
                reschedule(r, now);
            }
        }
        if (changed) ReminderStore.save(reminders);
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

    private static void showPopup(Reminder r) {
        JDialog dialog = new JDialog((Frame) null, "kReminder", true);
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
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
    }

    private static void setupTray(Timer timer) {
        if (!SystemTray.isSupported()) return;

        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        g.setColor(new Color(70, 130, 180));
        g.fillOval(2, 2, 12, 12);
        g.dispose();

        PopupMenu popup = new PopupMenu();
        TrayIcon trayIcon = new TrayIcon(img, "kReminder", popup);

        MenuItem exit = new MenuItem("Exit kReminder");
        exit.addActionListener(e -> {
            timer.stop();
            SystemTray.getSystemTray().remove(trayIcon);
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
