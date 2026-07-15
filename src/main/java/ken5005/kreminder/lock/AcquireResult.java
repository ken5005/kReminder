package ken5005.kreminder.lock;

/** acquire() の結果。 */
public enum AcquireResult {
    /** ロックを取得し、自分が本プロセスとして起動してよい。 */
    ACQUIRED,
    /** 起動を中止すべき（既存へ譲る／両方止める等）。 */
    ABORTED
}
