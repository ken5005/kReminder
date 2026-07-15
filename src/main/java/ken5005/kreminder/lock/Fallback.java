package ken5005.kreminder.lock;

/** 退去要求を出したのに既存インスタンスが無応答（タイムアウト）だった場合の対応。 */
public enum Fallback {
    /** 既存プロセスを強制終了して自分が起動する。 */
    FORCE_KILL,
    /** 自分の起動をやめる。 */
    CANCEL
}
