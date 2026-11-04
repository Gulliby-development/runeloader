package app.wowforever.addons;

import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Base for screens: shared view helpers that build rows, headers and empty states. */
abstract class Screen {
    final MainActivity app;

    Screen(MainActivity app) { this.app = app; }

    LinearLayout header(String title, String subtitle) {
        LinearLayout h = Ui.hbox(app);
        boolean compact = app.layout.compactHeight;
        h.setPadding(Ui.dp(app.landscape ? 20 : 16), Ui.dp(compact ? 10 : app.landscape ? 14 : 16), Ui.dp(app.landscape ? 16 : 12), Ui.dp(compact ? 6 : 10));
        LinearLayout tbox = Ui.vbox(app);
        TextView tt = Ui.oneLine(Ui.text(app, title, compact ? 19 : app.landscape ? 21 : 22, Ui.TEXT, Ui.BOLD));
        tbox.addView(tt);
        if (subtitle != null) {
            TextView st = Ui.text(app, subtitle, 12.5f, Ui.TEXT3, Ui.REGULAR);
            st.setMaxLines(2);
            st.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tbox.addView(st, Ui.margins(Ui.lp(-2, -2), 0, compact ? 2 : 4, 0, 0));
        }
        h.addView(tbox, Ui.lpw(0, -2, 1));
        app.headerBox.addView(h);
        return h;
    }

    void addHeaderButton(LinearLayout h, String label, int icon, Ui.Kind kind, View.OnClickListener l) {
        // Labels only where the list column is wide enough; otherwise an icon with a tooltip,
        // so the title is never squeezed.
        boolean labeled = app.layout.listDp >= LayoutSpec.LABELED_HEADER_DP;
        LinearLayout b = Ui.button(app, labeled ? label : null, icon, kind, l);
        if (!labeled && label != null) {
            b.setContentDescription(label);
            b.setTooltipText(label);
        }
        h.addView(b, Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
    }

    View emptyState(int icon, String title, String text, View... actions) {
        LinearLayout box = Ui.vbox(app);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(28), Ui.dp(20), Ui.dp(28), Ui.dp(20));
        FrameLayout circle = new FrameLayout(app);
        circle.setBackground(Ui.round(Ui.SURFACE2, 28));
        ImageView iv = Ui.icon(app, icon, Ui.ACCENT, 30);
        circle.addView(iv, Ui.flp(Ui.dp(30), Ui.dp(30), Gravity.CENTER));
        box.addView(circle, Ui.lp(Ui.dp(64), Ui.dp(64)));
        TextView t = Ui.text(app, title, 17, Ui.TEXT, Ui.BOLD);
        t.setGravity(Gravity.CENTER);
        box.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 14, 0, 6));
        if (text != null) {
            TextView s = Ui.text(app, text, 13.5f, Ui.TEXT2, Ui.REGULAR);
            s.setGravity(Gravity.CENTER);
            s.setMaxWidth(Ui.dp(440));
            box.addView(s);
        }
        if (actions.length > 0) {
            LinearLayout row = Ui.hbox(app);
            row.setGravity(Gravity.CENTER);
            // Side by side only where they fit; stacked full width on narrow screens.
            boolean stack = actions.length > 1 && app.layout.listDp < 480;
            if (stack) row.setOrientation(LinearLayout.VERTICAL);
            for (View a : actions) {
                if (stack) row.addView(a, Ui.margins(Ui.lp(-1, Ui.dp(44)), 0, 4, 0, 4));
                else row.addView(a, Ui.margins(Ui.lp(-2, Ui.dp(42)), 4, 0, 4, 0));
            }
            box.addView(row, Ui.margins(Ui.lp(stack ? -1 : -2, -2), 0, 18, 0, 0));
        }
        ScrollView sv = new ScrollView(app);
        sv.setFillViewport(true);
        sv.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        return sv;
    }

    ListView makeList() {
        ListView lv = new ListView(app);
        lv.setDivider(null);
        lv.setDividerHeight(Ui.dp(6));
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0));
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(app.landscape ? 16 : 12), Ui.dp(2), Ui.dp(app.landscape ? 16 : 12), Ui.dp(96));
        lv.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        lv.setItemsCanFocus(false);
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return lv;
    }

    View centerSpinner() {
        FrameLayout f = new FrameLayout(app);
        ProgressBar pb = new ProgressBar(app);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        f.addView(pb, Ui.flp(Ui.dp(40), Ui.dp(40), Gravity.CENTER));
        return f;
    }

    LinearLayout rowShell(boolean active) {
        LinearLayout row = Ui.hbox(app);
        StateListShell(row, active);
        row.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        row.setMinimumHeight(Ui.dp(64));
        row.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        return row;
    }

    void StateListShell(View v, boolean active) {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_selected}, Ui.round(Ui.SURFACE2, 14, Ui.FOCUS, 2f));
        s.addState(new int[]{android.R.attr.state_pressed}, Ui.round(Ui.SURFACE3, 14));
        s.addState(new int[]{}, active ? Ui.round(Ui.SURFACE2, 14, 0x80D9A441, 1.2f) : Ui.round(Ui.SURFACE, 14, Ui.STROKE, 1f));
        v.setBackground(s);
    }

    ImageView avatar(String url, String name, int sizeDp) {
        ImageView iv = new ImageView(app);
        iv.setLayoutParams(Ui.lp(Ui.dp(sizeDp), Ui.dp(sizeDp)));
        ImageLoader.load(iv, url, name);
        return iv;
    }

    String s(int id, Object... args) { return app.getString(id, args); }

    String q(int id, int n) { return app.getResources().getQuantityString(id, n, n); }

    String sourceName(String src) {
        if (Model.SRC_WOWI.equals(src)) return "WoWInterface";
        if (Model.SRC_ZIP.equals(src)) return s(R.string.source_zip);
        return "GitHub";
    }

    /** The orange "Discontinued" tag of a row: the author stopped the addon. */
    TextView discontinuedTag() {
        TextView t = Ui.oneLine(Ui.text(app, s(R.string.tag_discontinued), 10, Ui.ORANGE, Ui.MEDIUM));
        t.setBackground(Ui.round(Ui.ORANGE_SOFT, 6));
        t.setPadding(Ui.dp(6), Ui.dp(1), Ui.dp(6), Ui.dp(1));
        return t;
    }
}
