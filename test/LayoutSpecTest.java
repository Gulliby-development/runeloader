package app.wowforever.addons;

/** Checks the layout picked for common window sizes. Run on a plain JVM. */
public class LayoutSpecTest {
    public static void main(String[] args) {
        // 20:9 phone in landscape: docked detail, wide list.
        check(new LayoutSpec(914, 411), true, true, false, true);
        // 16:9 phone in landscape: still docked.
        check(new LayoutSpec(732, 411), true, true, false, true);
        // 4:3 in landscape (548dp wide): side sheet over a full-width list.
        check(new LayoutSpec(548, 411), true, false, true, true);
        // 4:3 tablet: docked, normal header.
        check(new LayoutSpec(1024, 768), true, true, false, false);
        // Portrait phone: bottom navigation, full-screen detail.
        check(new LayoutSpec(411, 823), false, false, false, false);

        LayoutSpec l = new LayoutSpec(732, 411);
        if (l.listDp < LayoutSpec.LIST_MIN_DP) throw new AssertionError("list too narrow: " + l.listDp);
        LayoutSpec s = new LayoutSpec(548, 411);
        if (s.detailDp > s.widthDp - LayoutSpec.RAIL_DP) throw new AssertionError("sheet wider than content: " + s.detailDp);
        System.out.println("LayoutSpecTest: OK");
    }

    private static void check(LayoutSpec l, boolean rail, boolean docked, boolean sheet, boolean compact) {
        String id = l.widthDp + "x" + l.heightDp;
        if (l.rail != rail) throw new AssertionError(id + " rail=" + l.rail);
        if (l.docked != docked) throw new AssertionError(id + " docked=" + l.docked);
        if (l.sheet != sheet) throw new AssertionError(id + " sheet=" + l.sheet);
        if (l.compactHeight != compact) throw new AssertionError(id + " compact=" + l.compactHeight);
    }
}
