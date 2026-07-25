package ken5005.kreminder;

/**
 * モニタ1台分の表示領域（GraphicsConfiguration#getBounds() 相当）。
 * WindowBoundsLogic がAWT非依存でテストできるよう、値だけを渡す入れ物として使う。
 */
public record MonitorBounds(int x, int y, int width, int height) {}
