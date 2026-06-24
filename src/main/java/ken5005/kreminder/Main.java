package ken5005.kreminder;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            List<Reminder> reminders = ReminderStore.load();
            // TODO (known): past unfired reminders fire immediately on startup.
            //  v0.1 accepts this as a natural result of the 1-sec loop.
            //  A future version must decide: fire-immediately / skip / batch-notify.
            Timer timer = new Timer(1000, e -> checkReminders(reminders));
            timer.start();
            setupTray(timer);
        });
    }

    private static void checkReminders(List<Reminder> reminders) {
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        for (Reminder r : reminders) {
            if (!r.noticed && r.fireAt != null && !r.fireAt.isAfter(now)) {
                r.noticed = true;
                changed = true;
                showPopup(r);
            }
        }
        if (changed) ReminderStore.save(reminders);
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
