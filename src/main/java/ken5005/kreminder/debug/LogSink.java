package ken5005.kreminder.debug;

/** Destination for formatted DEB log lines. Implementations must never throw. */
public interface LogSink {

    void accept(String line);

    void flush();

    void close();
}
