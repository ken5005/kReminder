package ken5005.kreminder;

/**
 * 一覧のフィルタトグル＋検索文字列（GUI仕様v2 §3.1）。イミュータブル。
 * searchText は null 可（呼び出し側都合）。isVisible 側で null/空を「非空でない」扱いにする。
 */
public record FilterState(
    boolean showEnded, boolean showImminent, boolean showSoon, boolean showFar,
    boolean showAll, boolean showLowPriority, boolean showAllRepeat, String searchText
) {}
