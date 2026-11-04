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
import java.util.List;

/** The installed-addons tab. */
final class LibraryScreen extends Screen {
    /** Below this list width the old → new versions move under the name, so the name keeps its room. */
    static final int VERSION_COLUMN_DP = 420;

    LibraryScreen(MainActivity app) { super(app); }

    void renderInstalled() {
        int managed = 0;
        List<String> updateNames = new ArrayList<>();
        for (Model.Installed r : app.installed) {
            if (r.entry != null) managed++;
            if (r.entry != null && r.entry.updateAvailable) updateNames.add(r.title);
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
            int ua = a.entry != null && a.entry.updateAvailable ? 0 : 1;
            int ub = b.entry != null && b.entry.updateAvailable ? 0 : 1;
            if (ua != ub) return ua - ub;
            return a.title.compareToIgnoreCase(b.title);
        });
        ListView lv = makeList();
        app.currentAdapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int p) { return rows.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) { return installedRow(rows.get(p)); }
        };
        lv.setAdapter(app.currentAdapter);
        lv.setOnItemClickListener((parent, view, position, id) -> app.detail.select(rows.get(position)));
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
