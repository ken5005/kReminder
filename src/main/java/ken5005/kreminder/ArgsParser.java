package ken5005.kreminder;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 起動引数（String[] args）のパースのみを行う純関数クラス。I/O・終了処理はMain側の責務。
 */
public final class ArgsParser {

    public static final String USAGE = """
        使い方: java -jar kReminder-x.y.z-all.jar [オプション]

          --fake-now=<日時>   起動時刻を偽装する（書式: YYYY-MM-DDTHH:mm:ss）
                              指定時刻から実時間と同速で進む。祝日・残り時間・発火の目視確認用。
          --data=<絶対パス>   reminders.json の読み書き先を差し替える（存在するファイルのみ）
          --help, -h          この使い方を表示して終了する

        オプションはすべて省略可。詳細は docs/kReminder_デバッグ起動オプション仕様_v1.md""";

    private ArgsParser() {
    }

    public static Args parse(String[] args) {
        // --help/-hはどこにあっても最優先。他の引数が不正でもhelpを打った人を止めない
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                return new Args(null, null, true);
            }
        }

        LocalDateTime fakeNow = null;
        String dataPath = null;
        boolean fakeNowSeen = false;
        boolean dataSeen = false;

        for (String arg : args) {
            if (arg.equals("--fake-now")) {
                throw new IllegalArgumentException("--fake-now には値が必要です（--fake-now=<日時>）");
            } else if (arg.equals("--data")) {
                throw new IllegalArgumentException("--data には値が必要です（--data=<絶対パス>）");
            } else if (arg.startsWith("--fake-now=")) {
                if (fakeNowSeen) {
                    throw new IllegalArgumentException("--fake-now が複数回指定されています");
                }
                fakeNowSeen = true;
                String value = arg.substring("--fake-now=".length());
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("--fake-now に値がありません");
                }
                try {
                    fakeNow = LocalDateTime.parse(value);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(
                        "--fake-now の日時が不正です: \"" + value + "\"（書式: YYYY-MM-DDTHH:mm:ss）");
                }
            } else if (arg.startsWith("--data=")) {
                if (dataSeen) {
                    throw new IllegalArgumentException("--data が複数回指定されています");
                }
                dataSeen = true;
                String value = arg.substring("--data=".length());
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("--data に値がありません");
                }
                dataPath = value;
            } else {
                throw new IllegalArgumentException("不明な引数です: \"" + arg + "\"");
            }
        }

        return new Args(fakeNow, dataPath, false);
    }
}
