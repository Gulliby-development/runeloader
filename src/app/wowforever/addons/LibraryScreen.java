package app.wowforever.addons;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The installed-addons tab. */
final class LibraryScreen extends Screen {
    /** Below this list width the old → new versions move under the name, so the name keeps its room. */
    static final int VERSION_COLUMN_DP = 420;

    LibraryScreen(MainActivity app) { super(app); }

    /** Packages whose modules are shown, by row id. */
    private final Set<String> expanded = new HashSet<>();
    /** The list as shown: top rows, and under an open package its core and modules. */
    private final List<Line> lines = new ArrayList<>();
    private List<Model.Installed> sorted = new ArrayList<>();

    /** One list line: a Library row, or a part of an open package (core is that package's own row). */
    private static final class Line {
        final Model.Installed row;
        final boolean part;
        Line(Model.Installed row, boolean part) { this.row = row; this.part = part; }
    }

    private static String rowId(Model.Installed r) {
        return r.entry != null ? r.entry.key : (r.catalogMatch != null ? r.catalogMatch.key() : r.title);
    }

    private static boolean anyUpdate(Model.Installed r) {
        for (Model.Installed m : r.withModules()) if (m.entry != null && m.entry.updateAvailable) return true;
        return false;
    }

    private void flatten() {
        lines.clear();
        for (Model.Installed r : sorted) {
            lines.add(new Line(r, false));
            if (r.isPackage() && expanded.contains(rowId(r))) for (Model.Installed m : r.withModules()) lines.add(new Line(m, true));
        }
    }

    /** A package line opens or closes its list of parts; where the detail is docked it also shows the package. */
    private void toggle(Model.Installed r) {
        String id = rowId(r);
        if (!expanded.remove(id)) expanded.add(id);
        flatten();
        if (app.layout.docked) app.detail.select(r);
        else app.refreshList();
    }

    void renderInstalled() {
        int managed = 0;
        List<String> updateNames = new ArrayList<>();
        for (Model.Installed r : app.installed) {
            if (r.entry != null) managed++;
            if (anyUpdate(r)) updateNames.add(r.title);
        }
        String sub = q(R.plurals.addons_count, app.installed.size())
                + " · " + s(R.string.installed_managed, managed) + (app.checking ? " · " + s(R.string.installed_checking) : "");
        if (!app.layout.rail && app.layout.listDp >= 120 + 32) {
            // Portrait has no rail to carry the brand: the wordmark sits above the title.
            int wm = Math.min(22, Math.round((app.layout.listDp - 32) / Ui.WORDMARK_RATIO));
            app.headerBox.addView(Ui.wordmark(app, wm), Ui.margins(Ui.lp(Math.round(Ui.dp(wm) * Ui.WORDMARK_RATIO), Ui.dp(wm)), 16, 14, 0, 0));
        }
        LinearLayout h = header(s(R.string.library_title), sub);
        addHeaderButton(h, s(R.string.action_from_zip), R.drawable.ic_zip, Ui.Kind.GHOST, v -> app.pickZip());
        addHeaderButton(h, s(app.checking ? R.string.action_checking : R.string.action_check), R.drawable.ic_refresh, Ui.Kind.SECONDARY, v -> app.manager.checkUpdates(true));

        if (!app.scanned) {
            app.bodyBox.addView(centerSpinner());
            return;
        }
        if (app.installed.isEmpty()) {
            app.bodyBox.addView(emptyState(R.drawable.ic_extension, s(R.string.installed_empty_title),
                    s(R.string.installed_empty_text),
                    Ui.button(app, s(R.string.action_open_catalog), R.drawable.ic_apps, Ui.Kind.PRIMARY, v -> app.switchTab(MainActivity.TAB_BROWSE)),
                    Ui.button(app, s(R.string.action_install_zip), R.drawable.ic_zip, Ui.Kind.SECONDARY, v -> app.pickZip())));
            return;
        }
        if (!updateNames.isEmpty()) app.headerBox.addView(updateBar(updateNames));
        final List<Model.Installed> rows = new ArrayList<>(app.installed);
        Collections.sort(rows, (a, b) -> {
            int ua = anyUpdate(a) ? 0 : 1;
            int ub = anyUpdate(b) ? 0 : 1;
            if (ua != ub) return ua - ub;
            return a.title.compareToIgnoreCase(b.title);
        });
        sorted = rows;
        flatten();
        ListView lv = makeList();
        app.currentAdapter = new BaseAdapter() {
            @Override public int getCount() { return lines.size(); }
            @Override public Object getItem(int p) { return lines.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) {
                Line l = lines.get(p);
                return l.part ? partRow(l.row) : l.row.isPackage() ? packageRow(l.row) : installedRow(l.row);
            }
        };
        lv.setAdapter(app.currentAdapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Line l = lines.get(position);
            if (!l.part && l.row.isPackage()) toggle(l.row);
            else app.detail.select(l.row);
        });
        app.bodyBox.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        app.currentList = lv;
    }

    /** Gold strip above the list: "3 updates available: A, B, C" and an "Update all" button. */
    private View updateBar(List<String> names) {
        LinearLayout bar = Ui.hbox(app);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Ui.ACCENT_SOFT, alpha(Ui.ACCENT, 0x0D)});
        bg.setCornerRadius(Ui.dp(14));
        bg.setStroke(Math.max(1, Ui.dp(1)), alpha(Ui.ACCENT, 0x47));
        bar.setBackground(bg);
        bar.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        bar.addView(Ui.icon(app, R.drawable.ic_upgrade, Ui.ACCENT, 18));

        String head = q(R.plurals.updates_available, names.size());
        String full = s(R.string.updates_bar_names, head, TextUtils.join(", ", names));
        SpannableString span = new SpannableString(full);
        int at = full.indexOf(head);
        if (at >= 0) {
            span.setSpan(new ForegroundColorSpan(Ui.ACCENT), at, at + head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), at, at + head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView t = Ui.oneLine(Ui.text(app, "", 13, Ui.TEXT, Ui.REGULAR));
        t.setText(span);
        bar.addView(t, Ui.margins(Ui.lpw(0, -2, 1), 10, 0, 10, 0));

        LinearLayout all = Ui.button(app, s(R.string.action_update_all_plain), 0, Ui.Kind.PRIMARY, v -> app.manager.updateAll());
        all.setMinimumHeight(Ui.dp(36));
        all.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        bar.addView(all, Ui.lp(-2, Ui.dp(36)));

        int side = app.landscape ? 16 : 12;
        LinearLayout.LayoutParams lp = Ui.margins(Ui.lp(-1, -2), side, 0, side, 8);
        bar.setLayoutParams(lp);
        return bar;
    }

    private View installedRow(Model.Installed r) {
        LinearLayout row = rowShell(r == app.selected);
        row.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        row.setMinimumHeight(Ui.dp(60));
        ImageView av = avatar(r.entry != null ? r.entry.iconUrl : (r.catalogMatch != null ? r.catalogMatch.iconUrl : null), r.title, 40);
        row.addView(av);

        final Model.Entry e = r.entry;
        String key = e != null ? e.key : (r.catalogMatch != null ? r.catalogMatch.key() : null);
        boolean busy = key != null && app.busy.containsKey(key);
        boolean update = !busy && !r.missing && e != null && e.updateAvailable;
        boolean upToDate = !busy && !r.missing && !update && e != null && e.checkError == null && e.latest != null;
        boolean wide = app.layout.listDp >= VERSION_COLUMN_DP;
        String installedVer = e != null && e.version != null && !e.version.isEmpty() ? e.version : r.version;

        LinearLayout mid = Ui.vbox(app);
        mid.addView(Ui.oneLine(Ui.text(app, r.title, 14, Ui.TEXT, Ui.MEDIUM)), Ui.lp(-1, -2));
        TextView st;
        if (update && !wide) {
            st = versionText(installedVer, e.latest);
        } else {
            StringBuilder sub = new StringBuilder();
            // The version shows on the right for updates and in the "up to date" pill.
            if (!update && !upToDate && installedVer != null && !installedVer.isEmpty()) sub.append(installedVer).append("  ·  ");
            sub.append(e != null ? sourceName(e.source) : s(R.string.installed_manually));
            if (r.folders.size() > 1) sub.append("  ·  ").append(q(R.plurals.folders_count, r.folders.size()));
            st = Ui.oneLine(Ui.text(app, sub.toString(), 11.5f, Ui.TEXT3, Ui.REGULAR));
        }
        Model.Item ix = e != null && Model.SRC_GITHUB.equals(e.source) ? Index.item(e.id) : null;
        if (ix != null && ix.isDiscontinued()) {
            LinearLayout line = Ui.hbox(app);
            line.addView(discontinuedTag(), Ui.lp(-2, -2));
            line.addView(st, Ui.margins(Ui.lpw(0, -2, 1), 6, 0, 0, 0));
            mid.addView(line, Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
        } else {
            mid.addView(st, Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
        }
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        if (busy) {
            row.addView(Ui.pill(app, s(R.string.pill_installing), Ui.ACCENT, Ui.ACCENT_SOFT));
        } else if (r.missing) {
            row.addView(Ui.pill(app, s(R.string.pill_folders_missing), Ui.RED, Ui.RED_SOFT));
        } else if (update) {
            if (wide) row.addView(versionText(installedVer, e.latest), Ui.margins(Ui.lp(-2, -2), 0, 0, 10, 0));
            LinearLayout up = Ui.button(app, null, R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> app.manager.update(e));
            up.setFocusable(false);
            up.setMinimumHeight(Ui.dp(36));
            up.setPadding(Ui.dp(9), Ui.dp(9), Ui.dp(9), Ui.dp(9));
            up.setContentDescription(s(R.string.action_update));
            up.setTooltipText(s(R.string.action_update));
            row.addView(up, Ui.lp(Ui.dp(36), Ui.dp(36)));
        } else if (e != null && e.checkError != null) {
            row.addView(Ui.pill(app, s(R.string.pill_unchecked), Ui.TEXT2, Ui.SURFACE3));
        } else if (upToDate) {
            boolean hasVer = installedVer != null && !installedVer.isEmpty();
            TextView p = iconPill(app, hasVer ? clip(installedVer, 14) : s(R.string.pill_up_to_date), R.drawable.ic_check, Ui.GREEN, Ui.GREEN_SOFT);
            p.setContentDescription(s(R.string.pill_up_to_date));
            row.addView(p);
        } else if (e == null && r.catalogMatch != null) {
            row.addView(Ui.pill(app, s(R.string.pill_in_catalog), Ui.BLUE, Ui.BLUE_SOFT));
        } else if (e == null) {
            row.addView(Ui.pill(app, s(R.string.pill_manual), Ui.TEXT2, Ui.SURFACE3));
        }
        if (!r.compatible && !r.missing) {
            ImageView w = Ui.icon(app, R.drawable.ic_warning, Ui.ORANGE, 18);
            row.addView(w, Ui.margins(Ui.lp(Ui.dp(18), Ui.dp(18)), 8, 0, 0, 0));
        }
        return row;
    }

    /** A package: one line for the core and its modules, with a chevron that opens the list of parts. */
    private View packageRow(final Model.Installed r) {
        LinearLayout row = rowShell(r == app.selected);
        row.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        row.setMinimumHeight(Ui.dp(60));
        row.addView(avatar(r.entry != null ? r.entry.iconUrl : (r.catalogMatch != null ? r.catalogMatch.iconUrl : null), r.title, 40));

        int updates = 0;
        boolean busy = false, unchecked = false, missing = false;
        for (Model.Installed m : r.withModules()) {
            String key = m.entry != null ? m.entry.key : (m.catalogMatch != null ? m.catalogMatch.key() : null);
            if (key != null && app.busy.containsKey(key)) busy = true;
            if (m.entry != null && m.entry.updateAvailable) updates++;
            if (m.entry != null && m.entry.checkError != null) unchecked = true;
            if (m.missing) missing = true;
        }
        LinearLayout mid = Ui.vbox(app);
        mid.addView(Ui.oneLine(Ui.text(app, r.title, 14, Ui.TEXT, Ui.MEDIUM)), Ui.lp(-1, -2));
        String parts = q(R.plurals.modules_count, r.modules.size());
        TextView st = Ui.oneLine(Ui.text(app, updates > 0 && !busy ? q(R.plurals.updates_available, updates) + "  ·  " + parts
                : parts + "  ·  " + (r.entry != null ? sourceName(r.entry.source) : s(R.string.installed_manually)), 11.5f, updates > 0 && !busy ? Ui.ACCENT : Ui.TEXT3, Ui.REGULAR));
        mid.addView(st, Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        String coreVer = r.entry != null && r.entry.version != null && !r.entry.version.isEmpty() ? r.entry.version : r.version;
        if (busy) {
            row.addView(Ui.pill(app, s(R.string.pill_installing), Ui.ACCENT, Ui.ACCENT_SOFT));
        } else if (missing) {
            row.addView(Ui.pill(app, s(R.string.pill_folders_missing), Ui.RED, Ui.RED_SOFT));
        } else if (updates > 0) {
            LinearLayout up = Ui.button(app, null, R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> app.manager.updatePackage(r));
            up.setFocusable(false);
            up.setMinimumHeight(Ui.dp(36));
            up.setPadding(Ui.dp(9), Ui.dp(9), Ui.dp(9), Ui.dp(9));
            up.setContentDescription(s(R.string.action_update));
            up.setTooltipText(s(R.string.action_update));
            row.addView(up, Ui.lp(Ui.dp(36), Ui.dp(36)));
        } else if (unchecked) {
            row.addView(Ui.pill(app, s(R.string.pill_unchecked), Ui.TEXT2, Ui.SURFACE3));
        } else if (r.entry != null && r.entry.latest != null) {
            boolean hasVer = coreVer != null && !coreVer.isEmpty();
            TextView p = iconPill(app, hasVer ? clip(coreVer, 14) : s(R.string.pill_up_to_date), R.drawable.ic_check, Ui.GREEN, Ui.GREEN_SOFT);
            p.setContentDescription(s(R.string.pill_up_to_date));
            row.addView(p);
        }
        boolean open = expanded.contains(rowId(r));
        ImageView chev = Ui.icon(app, R.drawable.ic_chevron, Ui.TEXT2, 22);
        chev.setRotation(open ? 270 : 90);
        row.setContentDescription(r.title + ", " + parts + ", " + s(open ? R.string.action_hide_modules : R.string.action_show_modules));
        row.addView(chev, Ui.margins(Ui.lp(Ui.dp(22), Ui.dp(22)), 6, 0, 0, 0));
        return row;
    }

    /** A part of an open package, indented under it: the core first, then each module by its short name. */
    private View partRow(Model.Installed m) {
        LinearLayout row = (LinearLayout) installedRow(m);
        boolean core = m.parent == null;
        Model.Installed pkg = core ? m : m.parent;
        // The title is the second child's first line.
        LinearLayout mid = (LinearLayout) row.getChildAt(1);
        TextView name = (TextView) mid.getChildAt(0);
        name.setText(core ? s(R.string.module_core) : Packages.moduleName(m.title, pkg.title));
        row.removeViewAt(0);
        row.setMinimumHeight(Ui.dp(48));
        row.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(8), Ui.dp(6));
        LinearLayout box = new LinearLayout(app);
        box.setOrientation(LinearLayout.HORIZONTAL);
        // A thin line on the left ties the parts to their package.
        View rule = new View(app);
        rule.setBackground(Ui.round(Ui.STROKE, 2));
        box.addView(rule, Ui.margins(Ui.lp(Ui.dp(3), -1), 22, 2, 10, 2));
        box.addView(row, Ui.lpw(0, -2, 1));
        box.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, -2));
        return box;
    }

    /** "1.14.3 → 1.15.0" in monospace, the new version in gold. */
    private TextView versionText(String from, String to) {
        String a = clip(from == null || from.isEmpty() ? "?" : from, 12);
        String b = clip(to, 12);
        String full = a + " → " + b;
        SpannableString span = new SpannableString(full);
        span.setSpan(new ForegroundColorSpan(Ui.ACCENT), full.length() - b.length(), full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = Ui.oneLine(Ui.text(app, "", 11.5f, Ui.TEXT3, Typeface.MONOSPACE));
        t.setText(span);
        return t;
    }

    static String clip(String v, int max) {
        if (v == null) return "";
        return v.length() > max ? v.substring(0, max - 1) + "…" : v;
    }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    /** A status pill with a small leading icon, such as the check of "up to date". */
    static TextView iconPill(MainActivity app, String text, int iconRes, int fg, int bg) {
        TextView p = Ui.pill(app, text, fg, bg);
        Drawable d = app.getDrawable(iconRes);
        if (d != null) {
            d = d.mutate();
            d.setTint(fg);
            d.setBounds(0, 0, Ui.dp(13), Ui.dp(13));
            p.setCompoundDrawablesRelative(d, null, null, null);
            p.setCompoundDrawablePadding(Ui.dp(5));
        }
        p.setGravity(Gravity.CENTER_VERTICAL);
        return p;
    }
}
