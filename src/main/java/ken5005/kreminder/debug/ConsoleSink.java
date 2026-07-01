package ken5005.kreminder.debug;

/** Writes each line to System.out. */
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
        // System.out is not ours to close.
    }
}
