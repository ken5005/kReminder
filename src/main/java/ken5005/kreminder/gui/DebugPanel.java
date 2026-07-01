package ken5005.kreminder.gui;

import javax.swing.*;
import java.awt.*;

/**
 * デバッグログ表示パネル。JTextArea を JScrollPane に載せただけの薄いビュー。
 * ログの追記は {@link PanelSink} が担当し、このクラスは入れ物に徹する。
 */
public final class DebugPanel extends JPanel {

    private final JTextArea textArea = new JTextArea();

    public DebugPanel() {
        super(new BorderLayout());
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    public JTextArea getTextArea() {
        return textArea;
    }
}
