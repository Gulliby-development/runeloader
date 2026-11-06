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

    /** The red "Discontinued" tag of a row: the author stopped the addon. */
    TextView discontinuedTag() {
        TextView t = Ui.oneLine(Ui.text(app, s(R.string.tag_discontinued), 10, Ui.RED, Ui.MEDIUM));
        t.setBackground(Ui.round(Ui.RED_SOFT, 6));
        t.setPadding(Ui.dp(6), Ui.dp(1), Ui.dp(6), Ui.dp(1));
        return t;
    }
    // ── the queue on rows: a status line, a thin bar and a pill, updated in place by tick ──

    /** The operation that works on a row now: a removal of any of its folders first, else an install or update of its keys. */
    Queue.Op opFor(Model.Installed r) {
        java.util.List<String> folders = new java.util.ArrayList<>();
        for (Model.Installed m : r.withModules()) {
            for (Model.LocalAddon a : m.folders) folders.add(a.folder);
            if (m.entry != null) folders.addAll(m.entry.folders);
        }
        Queue.Op rm = app.queue.removing(folders);
        if (rm != null) return rm;
        for (Model.Installed m : r.withModules()) {
            Queue.Op o = app.queue.activeFor(m.entry != null ? m.entry.key : (m.catalogMatch != null ? m.catalogMatch.key() : null));
            if (o != null) return o;
        }
        return null;
    }

    String kindName(Queue.Kind k) {
        switch (k) {
            case UPDATE: return s(R.string.queue_kind_update);
            case REMOVE: return s(R.string.queue_kind_remove);
            case ZIP: return s(R.string.queue_kind_zip);
            default: return s(R.string.queue_kind_install);
        }
    }

    /** What the row says under its name: the kind while it waits, then the step that runs. */
    String statusLine(Queue.Op op) {
        if (op.state == Queue.State.WAITING || op.status == null || op.status.isEmpty())
            return kindName(op.kind) + (op.modules > 0 ? "  ·  " + q(R.plurals.modules_count, op.modules) : "");
        return op.status;
    }

    String pillText(Queue.Op op) {
        boolean rm = op.kind == Queue.Kind.REMOVE;
        if (op.state == Queue.State.WAITING) return s(rm ? R.string.pill_remove_queued : R.string.pill_queued);
        if (op.state == Queue.State.DONE) return s(R.string.queue_done);
        if (op.state == Queue.State.FAILED) return s(R.string.queue_failed);
        if (op.state == Queue.State.CANCELLED) return s(R.string.queue_cancelled);
        if (rm) return s(R.string.pill_removing);
        return op.fraction >= 0 ? Math.round(op.fraction * 100) + "%" : s(R.string.pill_installing);
    }

    /** Red for a removal, grey while waiting, gold while it runs. */
    TextView queuePill(Queue.Op op) {
        boolean rm = op.kind == Queue.Kind.REMOVE, waiting = op.state == Queue.State.WAITING;
        TextView p = Ui.pill(app, pillText(op), rm ? Ui.RED : waiting ? Ui.TEXT2 : Ui.ACCENT, rm ? Ui.RED_SOFT : waiting ? Ui.SURFACE3 : Ui.ACCENT_SOFT);
        p.setTag("qpill:" + op.id);
        return p;
    }

    /** The status line and a thin bar, for the middle column of a row. */
    void queueLines(LinearLayout mid, Queue.Op op) {
        boolean rm = op.kind == Queue.Kind.REMOVE, waiting = op.state == Queue.State.WAITING;
        TextView st = Ui.oneLine(Ui.text(app, statusLine(op), 11.5f, waiting ? Ui.TEXT3 : rm ? Ui.RED : Ui.ACCENT, Ui.REGULAR));
        st.setTag("qstatus:" + op.id);
        mid.addView(st, Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
        mid.addView(queueBar(op, rm ? Ui.RED : Ui.ACCENT), Ui.margins(Ui.lp(-1, Ui.dp(3)), 0, 6, 0, 0));
    }

    ProgressBar queueBar(Queue.Op op, int color) {
        ProgressBar b = new ProgressBar(app, null, android.R.attr.progressBarStyleHorizontal);
        b.setProgressTintList(ColorStateList.valueOf(color));
        b.setIndeterminateTintList(ColorStateList.valueOf(color));
        b.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.SURFACE3));
        b.setMax(1000);
        b.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getHeight() / 2f); }
        });
        b.setClipToOutline(true);
        b.setTag("qbar:" + op.id);
        setBar(b, op);
        return b;
    }

    static void setBar(ProgressBar b, Queue.Op op) {
        boolean indeterminate = op.state == Queue.State.RUNNING && op.fraction < 0;
        if (b.isIndeterminate() != indeterminate) b.setIndeterminate(indeterminate);
        if (!indeterminate) b.setProgress(op.state == Queue.State.WAITING ? 0 : (int) (Math.max(0, op.fraction) * 1000));
    }

    /** A progress step: updates every status line, bar and pill of this operation under the view. */
    void tick(View v, Queue.Op op) {
        if (v == null) return;
        Object tag = v.getTag();
        if (tag instanceof String) {
            String s = (String) tag;
            if (s.equals("qbar:" + op.id) && v instanceof ProgressBar) setBar((ProgressBar) v, op);
            else if (s.equals("qpill:" + op.id) && v instanceof TextView) ((TextView) v).setText(pillText(op));
            else if (s.equals("qstatus:" + op.id) && v instanceof TextView) ((TextView) v).setText(statusLine(op));
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tick(g.getChildAt(i), op);
        }
    }

    /** A slow breathing of the alpha for a row that waits its turn; it stops when the row leaves the screen. */
    static void pulse(View v, float from, float to) {
        final android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, from, to);
        a.setDuration(900);
        a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        v.setAlpha(from);
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) { a.start(); }
            @Override public void onViewDetachedFromWindow(View x) { a.cancel(); }
        });
    }
}
