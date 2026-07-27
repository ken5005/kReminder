package ken5005.kreminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeFieldLogicTest {

    private static DateTimeFieldState base() {
        // 2026-07-24 10:30:45、カーソルは仕様どおり DAY 欄から活性
        return DateTimeFieldLogic.initial(LocalDateTime.of(2026, 7, 24, 10, 30, 45));
    }

    // 打鍵連鎖の自動送り＋秒満了で閉店：YEAR欄から6欄すべてを打鍵し尽くして最後にカーソル消滅するまで
    @Test
    void typingChainAdvancesThroughAllFieldsAndClosesAtSecond() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.YEAR);

        s = DateTimeFieldLogic.typeDigit(s, 2);
        s = DateTimeFieldLogic.typeDigit(s, 0);
        s = DateTimeFieldLogic.typeDigit(s, 2);
        s = DateTimeFieldLogic.typeDigit(s, 7);
        assertEquals(2027, s.year());
        assertEquals(DateField.MONTH, s.cursor());
        assertNull(s.buffer());

        s = DateTimeFieldLogic.typeDigit(s, 0);
        s = DateTimeFieldLogic.typeDigit(s, 8);
        assertEquals(8, s.month());
        assertEquals(DateField.DAY, s.cursor());

        s = DateTimeFieldLogic.typeDigit(s, 0);
        s = DateTimeFieldLogic.typeDigit(s, 1);
        assertEquals(1, s.day());
        assertEquals(DateField.HOUR, s.cursor());

        s = DateTimeFieldLogic.typeDigit(s, 0);
        s = DateTimeFieldLogic.typeDigit(s, 9);
        assertEquals(9, s.hour());
        assertEquals(DateField.MINUTE, s.cursor());

        s = DateTimeFieldLogic.typeDigit(s, 3);
        s = DateTimeFieldLogic.typeDigit(s, 0);
        assertEquals(30, s.minute());
        assertEquals(DateField.SECOND, s.cursor());

        s = DateTimeFieldLogic.typeDigit(s, 0);
        s = DateTimeFieldLogic.typeDigit(s, 0);
        assertEquals(0, s.second());
        assertNull(s.cursor()); // 秒欄満了でカーソル消滅

        // カーソル消滅後は打鍵無効
        DateTimeFieldState afterClosed = DateTimeFieldLogic.typeDigit(s, 9);
        assertEquals(s, afterClosed);
    }

    // 再進入リセット：欄に入っただけでは変わらず、最初の打鍵で旧値が捨てられる
    @Test
    void firstDigitClearsOldValueOnReentry() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.DAY);
        assertEquals("24", DateTimeFieldLogic.fieldDisplayText(s, DateField.DAY)); // 入っただけでは旧値のまま

        s = DateTimeFieldLogic.typeDigit(s, 5);
        assertEquals(" 5", DateTimeFieldLogic.fieldDisplayText(s, DateField.DAY)); // 旧値24は捨てられバッファ"5"のみ
        assertEquals(DateField.DAY, s.cursor());
    }

    // Enter: 未完バッファをゼロ埋め確定してカーソル消滅
    @Test
    void enterConfirmsPartialBufferWithZeroPad() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.MINUTE);
        s = DateTimeFieldLogic.typeDigit(s, 3); // バッファ"3"のまま（幅2未達）
        s = DateTimeFieldLogic.pressEnter(s);

        assertEquals(3, s.minute()); // "3" -> ゼロ埋めで 03
        assertNull(s.cursor());
    }

    // Space（バッファ活性中）: 現欄は打ちかけをゼロ埋め確定して値を活かし、下位の欄だけ最小値にする
    @Test
    void spaceWithActiveBufferKeepsCursorValueAndFillsFieldsBelow() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.MONTH);
        s = DateTimeFieldLogic.typeDigit(s, 5); // バッファ"5"（幅2未達＝活性中）
        s = DateTimeFieldLogic.pressSpace(s);

        assertEquals(2026, s.year());   // カーソルより上位は不変
        assertEquals(5, s.month());     // 活性中バッファ"5" -> ゼロ埋めで05のまま活かす
        assertEquals(1, s.day());       // 下位は最小値
        assertEquals(0, s.hour());
        assertEquals(0, s.minute());
        assertEquals(0, s.second());
        assertNull(s.cursor());
    }

    // Space（バッファ活性中）: MINUTE欄で打ちかけを確定して活かし、SECONDだけ最小値にする
    @Test
    void spaceWithActiveBufferOnMinuteKeepsValueAndFillsSecond() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.MINUTE);
        s = DateTimeFieldLogic.typeDigit(s, 3); // バッファ"3"（幅2未達＝活性中）
        s = DateTimeFieldLogic.pressSpace(s);

        assertEquals(3, s.minute());  // 活性中バッファ"3" -> ゼロ埋めで03のまま活かす
        assertEquals(0, s.second());  // 下位は最小値
        assertEquals(10, s.hour());   // カーソルより上位は不変
        assertEquals(24, s.day());
        assertEquals(7, s.month());
        assertEquals(2026, s.year());
        assertNull(s.cursor());
    }

    // Space（バッファ非活性＝欄に居るがまだ何も打っていない）: 現欄を含めて最小値にする
    @Test
    void spaceWithInactiveBufferMinimizesCursorFieldToo() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.DAY); // バッファ非活性

        s = DateTimeFieldLogic.pressSpace(s);

        assertEquals(1, s.day());     // カーソル欄自身も最小値
        assertEquals(0, s.hour());
        assertEquals(0, s.minute());
        assertEquals(0, s.second());
        assertEquals(7, s.month());   // カーソルより上位は不変
        assertEquals(2026, s.year());
        assertNull(s.cursor());
    }

    // Space（自動送り直後の非活性＝実使用再現）: 23:45:55を「1」「2」「Space」で12:00:00にしたいケース
    @Test
    void spaceAfterAutoAdvanceMinimizesCursorFieldToo() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.HOUR);
        s = DateTimeFieldLogic.typeDigit(s, 1);
        s = DateTimeFieldLogic.typeDigit(s, 2); // hour=12確定・自動送りでカーソルはMINUTEへ（バッファ非活性）
        assertEquals(DateField.MINUTE, s.cursor());
        assertNull(s.buffer());

        s = DateTimeFieldLogic.pressSpace(s);

        assertEquals(12, s.hour());   // 打ちかけた時はそのまま
        assertEquals(0, s.minute());  // 自動送り先の分自身もクリアされる
        assertEquals(0, s.second());
        assertNull(s.cursor());
    }

    // ←→移動と端での留まり・欄離脱時のゼロ埋め確定
    @Test
    void moveCommitsPartialBufferAndStopsAtEdges() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.DAY);
        s = DateTimeFieldLogic.typeDigit(s, 2); // バッファ"2"（幅2未達）

        DateTimeFieldState movedRight = DateTimeFieldLogic.moveRight(s);
        assertEquals(2, movedRight.day()); // "2" -> ゼロ埋めで 02
        assertEquals(DateField.HOUR, movedRight.cursor());
        assertNull(movedRight.buffer());

        DateTimeFieldState movedLeft = DateTimeFieldLogic.moveLeft(movedRight);
        assertEquals(DateField.DAY, movedLeft.cursor());
        assertNull(movedLeft.buffer()); // 再進入＝バッファは非活性（確定値02のまま表示）

        // 左端(YEAR)でmoveLeftしても留まる
        DateTimeFieldState atYear = DateTimeFieldLogic.clickField(movedLeft, DateField.YEAR);
        DateTimeFieldState stillYear = DateTimeFieldLogic.moveLeft(atYear);
        assertEquals(DateField.YEAR, stillYear.cursor());

        // 右端(SECOND)でmoveRightしても留まる
        DateTimeFieldState atSecond = DateTimeFieldLogic.clickField(movedLeft, DateField.SECOND);
        DateTimeFieldState stillSecond = DateTimeFieldLogic.moveRight(atSecond);
        assertEquals(DateField.SECOND, stillSecond.cursor());
    }

    // 欄クリックは無活性(カーソル消滅)からも直接指定で再活性化できる
    @Test
    void clickReactivatesFromDeactivatedState() {
        DateTimeFieldState deactivated = DateTimeFieldLogic.deactivate(base());
        assertNull(deactivated.cursor());

        DateTimeFieldState clicked = DateTimeFieldLogic.clickField(deactivated, DateField.HOUR);
        assertEquals(DateField.HOUR, clicked.cursor());
        assertNull(clicked.buffer());
    }

    // 閉店（deactivate）は未完バッファをゼロ埋め確定してからカーソル消滅
    @Test
    void deactivateCommitsPartialBuffer() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.HOUR);
        s = DateTimeFieldLogic.typeDigit(s, 7); // バッファ"7"（幅2未達）
        s = DateTimeFieldLogic.deactivate(s);

        assertEquals(7, s.hour()); // ゼロ埋めで 07
        assertNull(s.cursor());
    }

    // stepUpDown: バッファ活性中でも、まず未完バッファがゼロ埋め確定されてから±1される
    @Test
    void stepUpDownCommitsPartialBufferBeforeApplyingDelta() {
        DateTimeFieldState upState = DateTimeFieldLogic.clickField(base(), DateField.DAY);
        upState = DateTimeFieldLogic.typeDigit(upState, 1); // バッファ"1"（幅2未達で活性のまま）
        assertEquals(DateField.DAY, upState.cursor());
        assertEquals("1", upState.buffer());

        DateTimeFieldState upResult = DateTimeFieldLogic.stepUpDown(upState, 1);
        assertEquals(2, upResult.day()); // "1" -> ゼロ埋め01確定 -> +1で2
        assertEquals(DateField.DAY, upResult.cursor());
        assertNull(upResult.buffer());

        DateTimeFieldState downState = DateTimeFieldLogic.clickField(base(), DateField.DAY);
        downState = DateTimeFieldLogic.typeDigit(downState, 2); // バッファ"2"（幅2未達で活性のまま）

        DateTimeFieldState downResult = DateTimeFieldLogic.stepUpDown(downState, -1);
        assertEquals(1, downResult.day()); // "2" -> ゼロ埋め02確定 -> -1で1
        assertEquals(DateField.DAY, downResult.cursor());
        assertNull(downResult.buffer());
    }

    // ↑↓: 秒/分/時/月のラップ境界
    static Stream<Arguments> wrapCases() {
        return Stream.of(
            Arguments.of(DateField.SECOND, 59, 1, 0),
            Arguments.of(DateField.SECOND, 0, -1, 59),
            Arguments.of(DateField.MINUTE, 59, 1, 0),
            Arguments.of(DateField.MINUTE, 0, -1, 59),
            Arguments.of(DateField.HOUR, 23, 1, 0),
            Arguments.of(DateField.HOUR, 0, -1, 23),
            Arguments.of(DateField.MONTH, 12, 1, 1),
            Arguments.of(DateField.MONTH, 1, -1, 12)
        );
    }

    @ParameterizedTest
    @MethodSource("wrapCases")
    void stepUpDownWrapsAtBoundary(DateField field, int startValue, int delta, int expected) {
        DateTimeFieldState s = withFieldValue(base(), field, startValue);
        s = DateTimeFieldLogic.clickField(s, field);
        DateTimeFieldState result = DateTimeFieldLogic.stepUpDown(s, delta);
        assertEquals(expected, valueOf(result, field));
    }

    // 日のリワインド：非うるう年2月は28日、うるう年2024年2月は29日で一周
    @Test
    void dayRewindsWithinMonthLength() {
        // 2026-02-01 (非うるう年) の1日前は28日
        DateTimeFieldState s = DateTimeFieldLogic.initial(LocalDateTime.of(2026, 2, 1, 0, 0, 0));
        s = DateTimeFieldLogic.clickField(s, DateField.DAY);
        assertEquals(28, DateTimeFieldLogic.stepUpDown(s, -1).day());

        // 2026-02-28 の1日後は1日
        DateTimeFieldState s2 = DateTimeFieldLogic.initial(LocalDateTime.of(2026, 2, 28, 0, 0, 0));
        s2 = DateTimeFieldLogic.clickField(s2, DateField.DAY);
        assertEquals(1, DateTimeFieldLogic.stepUpDown(s2, 1).day());

        // うるう年2024-02-28の1日後は29日、2024-02-29の1日後は1日に一周
        DateTimeFieldState leap = DateTimeFieldLogic.initial(LocalDateTime.of(2024, 2, 28, 0, 0, 0));
        leap = DateTimeFieldLogic.clickField(leap, DateField.DAY);
        DateTimeFieldState leap29 = DateTimeFieldLogic.stepUpDown(leap, 1);
        assertEquals(29, leap29.day());
        assertEquals(1, DateTimeFieldLogic.stepUpDown(leap29, 1).day());
    }

    // 月変更による日の末日クランプ：1/31で月↑→2/28、2/28で月↑→3/28（超えなければ保持）
    @Test
    void monthChangeClampsDayToLastDayOfMonth() {
        DateTimeFieldState jan31 = DateTimeFieldLogic.initial(LocalDateTime.of(2026, 1, 31, 0, 0, 0));
        jan31 = DateTimeFieldLogic.clickField(jan31, DateField.MONTH);
        DateTimeFieldState feb = DateTimeFieldLogic.stepUpDown(jan31, 1);
        assertEquals(2, feb.month());
        assertEquals(28, feb.day()); // 31 -> 末日28にクランプ

        DateTimeFieldState feb28 = DateTimeFieldLogic.initial(LocalDateTime.of(2026, 2, 28, 0, 0, 0));
        feb28 = DateTimeFieldLogic.clickField(feb28, DateField.MONTH);
        DateTimeFieldState mar = DateTimeFieldLogic.stepUpDown(feb28, 1);
        assertEquals(3, mar.month());
        assertEquals(28, mar.day()); // 超えないので28のまま保持
    }

    // 年変更でも同様に日の末日クランプが働く（うるう年2/29→非うるう年で2/28へ）
    @Test
    void yearChangeClampsLeapDayToLastDayOfMonth() {
        DateTimeFieldState s = DateTimeFieldLogic.initial(LocalDateTime.of(2024, 2, 29, 0, 0, 0));
        s = DateTimeFieldLogic.clickField(s, DateField.YEAR);
        DateTimeFieldState result = DateTimeFieldLogic.stepUpDown(s, -1);
        assertEquals(2023, result.year());
        assertEquals(28, result.day());
    }

    // 年は2000で下限停止（それ未満に下げられない）
    @Test
    void yearStepDownStopsAtMinimum() {
        DateTimeFieldState atMin = DateTimeFieldLogic.initial(LocalDateTime.of(2000, 6, 15, 0, 0, 0));
        atMin = DateTimeFieldLogic.clickField(atMin, DateField.YEAR);
        assertEquals(2000, DateTimeFieldLogic.stepUpDown(atMin, -1).year());

        DateTimeFieldState above = DateTimeFieldLogic.initial(LocalDateTime.of(2001, 6, 15, 0, 0, 0));
        above = DateTimeFieldLogic.clickField(above, DateField.YEAR);
        assertEquals(2000, DateTimeFieldLogic.stepUpDown(above, -1).year());
    }

    // 合成値が不正（例: 時25）の間は↑↓が無効
    @Test
    void stepUpDownNoOpWhenComposedValueInvalid() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.HOUR);
        s = DateTimeFieldLogic.typeDigit(s, 2);
        s = DateTimeFieldLogic.typeDigit(s, 5); // hour=25（不正値だが打鍵はそのまま保持される）
        assertEquals(DateField.MINUTE, s.cursor());

        DateTimeFieldState backAtHour = DateTimeFieldLogic.moveLeft(s);
        assertEquals(DateField.HOUR, backAtHour.cursor());
        assertEquals(25, backAtHour.hour());
        assertFalse(backAtHour.touched());
        assertTrue(EditFormLogic.parseExecTime(DateTimeFieldLogic.composeText(backAtHour)).isEmpty());

        DateTimeFieldState result = DateTimeFieldLogic.stepUpDown(backAtHour, 1);
        assertEquals(backAtHour, result); // no-op（値もtouchedも変化しない）
        assertFalse(result.touched());
    }

    // touched: stepUpDownで実際に増減できた場合のみtouched=trueになり、
    // その直後のSpaceは現欄の値を活かして下位のみ最小化する（N11の本題）
    @Test
    void stepUpDownSetsTouchedAndSpaceKeepsCursorValue() {
        DateTimeFieldState s = withFieldValue(base(), DateField.HOUR, 12);
        s = DateTimeFieldLogic.clickField(s, DateField.HOUR);
        assertFalse(s.touched());

        DateTimeFieldState stepped = DateTimeFieldLogic.stepUpDown(s, -1);
        assertEquals(11, stepped.hour());
        assertTrue(stepped.touched());

        DateTimeFieldState result = DateTimeFieldLogic.pressSpace(stepped);
        assertEquals(11, result.hour());  // touchedにより時は活かされる（現状のバグでは0になっていた）
        assertEquals(0, result.minute());
        assertEquals(0, result.second());
        assertNull(result.cursor());
    }

    // 回帰: 打鍵による自動送り（touchedは立たない経路）は従来どおりSpaceで現欄含め最小化される
    @Test
    void typedFieldAutoAdvanceStaysUntouchedAndSpaceMinimizesCursorFieldToo() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.HOUR);
        s = DateTimeFieldLogic.typeDigit(s, 1);
        s = DateTimeFieldLogic.typeDigit(s, 1); // hour=11確定・自動送りでMINUTEへ
        assertEquals(DateField.MINUTE, s.cursor());
        assertFalse(s.touched());

        DateTimeFieldState result = DateTimeFieldLogic.pressSpace(s);
        assertEquals(11, result.hour());  // 打鍵した時はそのまま
        assertEquals(0, result.minute()); // 自動送り先の分自身もクリアされる（touchedではないため）
        assertEquals(0, result.second());
        assertNull(result.cursor());
    }

    // touchedは欄離脱／消滅で解除される：stepUpDown後にmove/clickFieldで移った先の欄は
    // 「未編集」扱いになり、Spaceでその欄自身も最小化される
    static Stream<Arguments> touchedResetOnLeaveCases() {
        return Stream.of(
            Arguments.of((UnaryOperator<DateTimeFieldState>) DateTimeFieldLogic::moveLeft, DateField.DAY, 1),
            Arguments.of((UnaryOperator<DateTimeFieldState>) DateTimeFieldLogic::moveRight, DateField.MINUTE, 0),
            Arguments.of((UnaryOperator<DateTimeFieldState>) (s -> DateTimeFieldLogic.clickField(s, DateField.SECOND)), DateField.SECOND, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("touchedResetOnLeaveCases")
    void touchedResetsWhenCursorLeavesField(UnaryOperator<DateTimeFieldState> leaveTransition,
                                             DateField expectedCursorField, int expectedMinValue) {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.HOUR);
        s = DateTimeFieldLogic.stepUpDown(s, 1); // hour touched=true
        assertTrue(s.touched());

        DateTimeFieldState moved = leaveTransition.apply(s);
        assertEquals(expectedCursorField, moved.cursor());
        assertFalse(moved.touched()); // 欄離脱でtouchedは解除される

        DateTimeFieldState result = DateTimeFieldLogic.pressSpace(moved);
        assertEquals(expectedMinValue, valueOf(result, expectedCursorField)); // 移動先欄自身も最小化＝未編集扱い
        assertNull(result.cursor());
    }

    // stepUpDown: バッファ活性中（打ちかけあり）でも確定＋増減が行われtouched=trueになる
    @Test
    void stepUpDownWithActiveBufferCommitsAppliesDeltaAndSetsTouched() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.DAY);
        s = DateTimeFieldLogic.typeDigit(s, 1); // バッファ"1"（幅2未達で活性中）
        assertFalse(s.touched());

        DateTimeFieldState result = DateTimeFieldLogic.stepUpDown(s, 1);
        assertEquals(2, result.day()); // "1" -> ゼロ埋め01確定 -> +1で2
        assertTrue(result.touched());
        assertNull(result.buffer());
    }

    // composeText: バッファ活性中はゼロ埋め解釈で合成される
    @Test
    void composeTextUsesZeroPaddedInterpretationForActiveBuffer() {
        DateTimeFieldState s = DateTimeFieldLogic.clickField(base(), DateField.DAY);
        s = DateTimeFieldLogic.typeDigit(s, 2); // バッファ"2"（幅2未達）
        assertEquals("2026-07-02 10:30:45", DateTimeFieldLogic.composeText(s));
    }

    private static DateTimeFieldState withFieldValue(DateTimeFieldState s, DateField field, int value) {
        return switch (field) {
            case YEAR -> new DateTimeFieldState(value, s.month(), s.day(), s.hour(), s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case MONTH -> new DateTimeFieldState(s.year(), value, s.day(), s.hour(), s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case DAY -> new DateTimeFieldState(s.year(), s.month(), value, s.hour(), s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case HOUR -> new DateTimeFieldState(s.year(), s.month(), s.day(), value, s.minute(), s.second(), s.cursor(), s.buffer(), s.touched());
            case MINUTE -> new DateTimeFieldState(s.year(), s.month(), s.day(), s.hour(), value, s.second(), s.cursor(), s.buffer(), s.touched());
            case SECOND -> new DateTimeFieldState(s.year(), s.month(), s.day(), s.hour(), s.minute(), value, s.cursor(), s.buffer(), s.touched());
        };
    }

    private static int valueOf(DateTimeFieldState s, DateField field) {
        return switch (field) {
            case YEAR -> s.year();
            case MONTH -> s.month();
            case DAY -> s.day();
            case HOUR -> s.hour();
            case MINUTE -> s.minute();
            case SECOND -> s.second();
        };
    }
}
