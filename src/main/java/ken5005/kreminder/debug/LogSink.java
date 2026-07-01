package ken5005.kreminder.debug;

/** 整形済みDEBログ1行の出力先。実装は例外を投げてはいけない。 */
public interface LogSink {

    void accept(String line);

    void flush();

    void close();
}
