package ken5005.kreminder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowBoundsLogicTest {

    private static final List<MonitorBounds> SINGLE_MONITOR =
        List.of(new MonitorBounds(0, 0, 1920, 1080));

    // プライマリ(0,0起点) + その右に並んだセカンダリ
    private static final List<MonitorBounds> DUAL_MONITOR = List.of(
        new MonitorBounds(0, 0, 1920, 1080),
        new MonitorBounds(1920, 0, 1280, 1024)
    );

    // プライマリの真上に配置されたモニタ（yが負になるケース）
    private static final List<MonitorBounds> MONITOR_ABOVE_PRIMARY =
        List.of(new MonitorBounds(0, -1080, 1920, 1080));

    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("画面内に収まっている場合はそのまま",
                100, 100, 800, 500, SINGLE_MONITOR,
                new WindowBoundsLogic.Resolved(false, 100, 100, 800, 500)),

            Arguments.of("完全に画面外なら中央指示（サイズは画面内なのでそのまま）",
                5000, 5000, 800, 500, SINGLE_MONITOR,
                new WindowBoundsLogic.Resolved(true, 0, 0, 800, 500)),

            Arguments.of("小さすぎる場合は最小値(400x300)まで広げる",
                100, 100, 200, 100, SINGLE_MONITOR,
                new WindowBoundsLogic.Resolved(false, 100, 100, 400, 300)),

            Arguments.of("大きすぎる場合は表示領域まで縮める（yも上端0まで持ち上がる）",
                -100, -100, 3000, 2000, SINGLE_MONITOR,
                new WindowBoundsLogic.Resolved(false, -100, 0, 1920, 1080)),

            Arguments.of("複数モニタ：重なった方のモニタを基準に判定する",
                2000, 100, 800, 500, DUAL_MONITOR,
                new WindowBoundsLogic.Resolved(false, 2000, 100, 800, 500)),

            Arguments.of("複数モニタ：どれとも重ならなければ中央指示（サイズは先頭モニタ基準）",
                9000, 9000, 800, 500, DUAL_MONITOR,
                new WindowBoundsLogic.Resolved(true, 0, 0, 800, 500)),

            Arguments.of("モニタが空リスト：中央指示で、サイズは最小値と保存値の大きい方",
                999, 999, 200, 2000, List.<MonitorBounds>of(),
                new WindowBoundsLogic.Resolved(true, 0, 0, 400, 2000)),

            Arguments.of("シングルモニタでyが負（タイトルバーが上端よりはみ出し）なら上端0まで持ち上がる",
                100, -50, 800, 500, SINGLE_MONITOR,
                new WindowBoundsLogic.Resolved(false, 100, 0, 800, 500)),

            Arguments.of("上に配置されたモニタと重なる場合、その負のyはそのまま保たれる（0にワープさせない）",
                100, -1000, 800, 500, MONITOR_ABOVE_PRIMARY,
                new WindowBoundsLogic.Resolved(false, 100, -1000, 800, 500)),

            Arguments.of("境界が接するだけ（重なり面積0）は重ならない扱い→中央指示",
                1920, 100, 800, 500, SINGLE_MONITOR,
                new WindowBoundsLogic.Resolved(true, 0, 0, 800, 500))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void resolveMatchesTable(String label, int x, int y, int width, int height,
                              List<MonitorBounds> monitors, WindowBoundsLogic.Resolved expected) {
        assertEquals(expected, WindowBoundsLogic.resolve(x, y, width, height, monitors));
    }
}
