package ken5005.kreminder;

import java.time.LocalDateTime;

/**
 * 起動引数のパース結果。help=true のときは他フィールドを見ない（ArgsParser側の規約）。
 */
public record Args(LocalDateTime fakeNow, String basePath, boolean help) {
}
