package ken5005.kreminder.lock;

/** 既存インスタンスとの競合時に、利用者（ホスト側UI）が下す選択。 */
public enum Choice {
    /** 既存インスタンスへ退去要求を出し、自分が起動する。 */
    STOP_EXISTING,
    /** 何もせず、自分の起動をやめる。 */
    CANCEL,
    /** 既存インスタンスへ退去要求を出すが、自分も起動しない。 */
    STOP_BOTH
}
