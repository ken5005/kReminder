package ken5005.kreminder.gui;

import ken5005.kreminder.debug.DEB;
import ken5005.kreminder.debug.LogSink;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DEBログをGUIのデバッグパネル（JTextArea）へ流し込むsink。
 * accept()はDebWorkerの非EDTスレッドから呼ばれる前提——Swingコンポーネントは
 * EDT上でしか触れないため、実際の追記はinvokeLaterでEDTへ回す。
 *
 * pr()の連打でinvokeLaterを大量発行しないよう、ドレイン予約は同時に1個だけ
 * （flushScheduledのCASでcoalescingする）。
 */
public final class PanelSink implements LogSink {

    private final JTextArea textArea;
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

    public PanelSink(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void accept(String line) {
        pending.add(line);
        // まだ予約が無い（false→true に成功した）ときだけドレインを1個予約する。
        if (flushScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::drain);
        }
    }

    /** EDT上で実行。予約フラグを先に下ろしてから吸い出す＝取りこぼしを防ぐ。 */
    private void drain() {
        flushScheduled.set(false);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = pending.poll()) != null) {
            sb.append(line).append('\n');
        }
        if (sb.length() > 0) {
            textArea.append(sb.toString());
            trimIfOverLimit();
        }
    }

    /** 上限超過分を先頭から削る。行の途中で切れないよう、直近の改行までまとめて消す。 */
    private void trimIfOverLimit() {
        Document doc = textArea.getDocument();
        int overflow = doc.getLength() - DEB.PANEL_TEXT_LIMIT;
        if (overflow <= 0) return;
        try {
            String head = doc.getText(0, overflow);
            int cutEnd = head.lastIndexOf('\n') + 1;
            if (cutEnd > 0) {
                doc.remove(0, cutEnd);
            }
        } catch (BadLocationException e) {
            // 起こり得ないが、万一失敗してもログ表示自体は止めない
        }
    }

    @Override
    public void flush() {
        // 追記はinvokeLater経由で反映されるためno-op。
    }

    @Override
    public void close() {
        // JTextAreaは閉じる対象ではないためno-op。
    }
}
