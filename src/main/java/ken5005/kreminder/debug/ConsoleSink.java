package ken5005.kreminder.debug;

/** 各行をSystem.outへ出力する。 */
public final class ConsoleSink implements LogSink {

    @Override
    public void accept(String line) {
        System.out.println(line);
    }

    @Override
    public void flush() {
        System.out.flush();
    }

    @Override
    public void close() {
        // System.outは自分が開いたものではないので閉じない。
    }
}
