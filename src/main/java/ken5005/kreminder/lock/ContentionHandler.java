package ken5005.kreminder.lock;

/**
 * 既存インスタンスとの競合が起きたときに呼ばれるコールバック。
 * このパッケージは UI を持たないため、ホスト側（例: Swing ダイアログ）が実装する。
 */
public interface ContentionHandler {

    /** 既存インスタンスが動作中と分かった直後に呼ばれる。holder は既存の情報。 */
    Choice onExistingInstance(InstanceInfo holder);

    /** 退去要求を出したが既存インスタンスが timeout 内に応答しなかったときに呼ばれる。 */
    Fallback onNoResponse(InstanceInfo holder);
}
