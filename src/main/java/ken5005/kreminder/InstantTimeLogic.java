package ken5005.kreminder;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * instant 入力欄（時刻のみ・相対/絶対）のパース純関数（GUI仕様v2 §4.4）。
 * Swing / java.io / Gson 非依存＝ユニットテストのみで検証できる状態を保つ。clock-free（now は引数注入）。
 */
public final class InstantTimeLogic {

    private InstantTimeLogic() {
    }

    private static final Duration MAX_RELATIVE = Duration.ofHours(72);

    // 相対の最上位（時 or 分）の安全域。これを超えれば *3600 前でも必ず3日超と確定できる
    // （実用上限=4320分/72時間よりも十分大きく、かつ long 乗算がオーバーフローしない範囲）。
    private static final long RELATIVE_TOP_SAFE_LIMIT = 1_000_000L;

    private static final String OVER_LIMIT_ERROR = "3日以上は指定出来ません";
    private static final String GRAMMAR_ERROR =
        "時刻入力エラー  例) 25=25分後 / +1:30=1時間30分後 / 0.25=25秒後 / 15m=15分後 / "
            + "12:34=今日の12:34（過ぎたら翌日） ※最上位以外の桁は2桁必須";

    /** 成功なら fireAt 非null・error null。失敗なら fireAt null・error 非null（表示用メッセージ）。 */
    public record Result(LocalDateTime fireAt, String error) {
        private static Result ok(LocalDateTime fireAt) {
            return new Result(fireAt, null);
        }

        private static Result err(String message) {
            return new Result(null, message);
        }
    }

    public static Result parse(String input, LocalDateTime now) {
        if (input == null) return Result.err(GRAMMAR_ERROR);

        String normalized = normalize(input);
        if (normalized.isEmpty()) return Result.err(GRAMMAR_ERROR);

        // 先頭 + の有無を記録して除去（+ が判定ルールの起点）
        boolean hasPlus = normalized.startsWith("+");
        String body = hasPlus ? normalized.substring(1) : normalized;

        // 単位サフィックス（s/m/h、小文字のみ）＝ 常に相対。既存の [時:]分[.秒] ルートより先に判定し、
        // 該当すれば専用ルートへ分岐する（: や . との併用は numberPart の isDigits チェックで自然に弾かれる）。
        if (!body.isEmpty()) {
            char lastChar = body.charAt(body.length() - 1);
            if (lastChar == 's' || lastChar == 'm' || lastChar == 'h') {
                return parseUnitSuffix(body, lastChar, now);
            }
        }

        // '.' で1回だけ split → 左側が [時:]分、右側が秒（無ければ null）
        String[] dotParts = splitOnce(body, '.');
        if (dotParts == null) return Result.err(GRAMMAR_ERROR);
        String beforeDot = dotParts[0];
        String secStr = dotParts[1];

        // ':' で1回だけ split（. の後に : が来るケースはここで beforeDot に : が残らないので自然に弾かれる）
        String[] colonParts = splitOnce(beforeDot, ':');
        if (colonParts == null) return Result.err(GRAMMAR_ERROR);
        String topStr = colonParts[0];
        String midStr = colonParts[1];
        boolean hasColon = midStr != null;

        // 各フィールドが数字のみ・空でないこと
        if (!isDigits(topStr)) return Result.err(GRAMMAR_ERROR);
        if (hasColon && !isDigits(midStr)) return Result.err(GRAMMAR_ERROR);
        if (secStr != null && !isDigits(secStr)) return Result.err(GRAMMAR_ERROR);

        // 桁ルール：最上位（topStr）のみ自由・それ以外はきっちり2桁
        if (hasColon && midStr.length() != 2) return Result.err(GRAMMAR_ERROR);
        if (secStr != null && secStr.length() != 2) return Result.err(GRAMMAR_ERROR);

        // 判定ルール：+始まり→相対 / +なし+コロンあり→絶対 / +なし+コロンなし→相対
        boolean isAbsolute = !hasPlus && hasColon;

        // topStr は桁数無制限のため long でも収まらない場合がある（打鍵中の暴走入力を想定）。
        // オーバーフローは「意味のある範囲を明らかに超えている」ことの証拠として扱う：
        // 絶対の最上位は時（0〜23）なので文法エラー、相対は分/時間なので3日超エラーに倒す。
        long topVal;
        try {
            topVal = Long.parseLong(topStr);
        } catch (NumberFormatException e) {
            return isAbsolute ? Result.err(GRAMMAR_ERROR) : Result.err(OVER_LIMIT_ERROR);
        }
        int midVal = hasColon ? Integer.parseInt(midStr) : 0;
        int secVal = secStr != null ? Integer.parseInt(secStr) : 0;
        if (secStr != null && secVal > 59) return Result.err(GRAMMAR_ERROR);

        if (isAbsolute) {
            if (topVal > 23) return Result.err(GRAMMAR_ERROR);
            if (midVal > 59) return Result.err(GRAMMAR_ERROR);
            return Result.ok(resolveAbsolute((int) topVal, midVal, secVal, now));
        }

        if (hasColon && midVal > 59) return Result.err(GRAMMAR_ERROR);
        // long の範囲に収まっていても *3600 で桁あふれし得るため、算術前に安全域で足切りする。
        if (topVal > RELATIVE_TOP_SAFE_LIMIT) return Result.err(OVER_LIMIT_ERROR);
        long totalSeconds = hasColon
            ? topVal * 3600 + (long) midVal * 60 + secVal
            : topVal * 60 + secVal;
        Duration duration = Duration.ofSeconds(totalSeconds);
        if (duration.compareTo(MAX_RELATIVE) > 0) return Result.err(OVER_LIMIT_ERROR);
        return Result.ok(now.withNano(0).plus(duration));
    }

    /**
     * 単位サフィックス（15m / 30s / 2h）専用ルート。常に相対。
     * body は末尾に unit（s/m/h）を含んだままの文字列＝先頭側が数値部。
     */
    private static Result parseUnitSuffix(String body, char unit, LocalDateTime now) {
        String numberPart = body.substring(0, body.length() - 1);
        if (!isDigits(numberPart)) return Result.err(GRAMMAR_ERROR);

        long numberVal;
        try {
            numberVal = Long.parseLong(numberPart);
        } catch (NumberFormatException e) {
            return Result.err(OVER_LIMIT_ERROR);
        }
        // 乗算前の安全域足切り（[時:]分ルートの topVal と同じ考え方）
        if (numberVal > RELATIVE_TOP_SAFE_LIMIT) return Result.err(OVER_LIMIT_ERROR);

        long secondsPerUnit = switch (unit) {
            case 'h' -> 3600L;
            case 'm' -> 60L;
            default -> 1L; // 's'
        };
        Duration duration = Duration.ofSeconds(numberVal * secondsPerUnit);
        if (duration.compareTo(MAX_RELATIVE) > 0) return Result.err(OVER_LIMIT_ERROR);
        return Result.ok(now.withNano(0).plus(duration));
    }

    /** 絶対時刻の候補が now 以前（同時刻含む）なら翌日へ送る。 */
    private static LocalDateTime resolveAbsolute(int hour, int minute, int second, LocalDateTime now) {
        LocalDateTime nowTrunc = now.withNano(0);
        LocalDateTime candidate = nowTrunc.toLocalDate().atTime(hour, minute, second);
        return candidate.isAfter(nowTrunc) ? candidate : candidate.plusDays(1);
    }

    /** 全角→半角変換（数字・+・:・.のみ）＋前後trim。それ以外の文字は素通しし、後段のisDigitsで弾く。 */
    private static String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.trim().toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else if (c == '＋') {
                sb.append('+');
            } else if (c == '：') {
                sb.append(':');
            } else if (c == '．' || c == '。') {
                sb.append('.');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isDigits(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    /**
     * sep で最大1回だけ split する。2箇所以上あれば null（呼び出し側でエラー扱い）。
     * sep が無ければ [全体, null] を返す（第2要素 null＝区切り無し）。
     */
    private static String[] splitOnce(String s, char sep) {
        int idx = s.indexOf(sep);
        if (idx < 0) return new String[] { s, null };
        if (s.indexOf(sep, idx + 1) >= 0) return null;
        return new String[] { s.substring(0, idx), s.substring(idx + 1) };
    }
}
