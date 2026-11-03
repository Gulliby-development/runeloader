package app.wowforever.addons;

/**
 * Picks the shell layout from the window size in dp, so 4:3, 16:9, 20:9 and portrait
 * follow one set of rules instead of a landscape/portrait switch.
 */
final class LayoutSpec {
    static final int RAIL_DP = 84;
    /** The list keeps at least this much width next to a docked detail panel. */
    static final int LIST_MIN_DP = 340;
    static final int DETAIL_MIN_DP = 300;
    static final int DETAIL_MAX_DP = 440;
    static final int SHEET_MAX_DP = 420;
    /** At or below this height the headers use the compact variant. */
    static final int COMPACT_HEIGHT_DP = 480;
    /** Header buttons keep their labels only when the list column is at least this wide. */
    static final int LABELED_HEADER_DP = 560;

    final int widthDp, heightDp;
    /** Navigation rail on the left (wide window) or a bottom bar (tall window). */
    final boolean rail;
    /** Detail panel docked on the right; otherwise it opens over the list. */
    final boolean docked;
    /** Detail opens as a side sheet over the list (rail layouts without room to dock). */
    final boolean sheet;
    final boolean compactHeight;
    /** Width of the docked panel or the side sheet in dp; 0 when the detail is full screen. */
    final int detailDp;
    /** Width of the list column in dp. */
    final int listDp;

    LayoutSpec(int widthDp, int heightDp) {
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        rail = widthDp > heightDp;
        compactHeight = heightDp <= COMPACT_HEIGHT_DP;
        int content = widthDp - (rail ? RAIL_DP : 0);
        docked = rail && content >= LIST_MIN_DP + DETAIL_MIN_DP;
        sheet = rail && !docked;
        if (docked) detailDp = clamp(Math.round(content * 0.38f), DETAIL_MIN_DP, DETAIL_MAX_DP);
        else if (sheet) detailDp = Math.min(SHEET_MAX_DP, content - 24);
        else detailDp = 0;
        listDp = docked ? content - detailDp - 1 : content;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
