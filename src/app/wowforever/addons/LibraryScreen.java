package app.wowforever.addons;

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

    LibraryScreen(MainActivity app) { super(app); }

    void renderInstalled() {
        int updates = 0;
        int managed = 0;
        for (Model.Installed r : app.installed) {
            if (r.entry != null) managed++;
            if (r.entry != null && r.entry.updateAvailable) updates++;
        }
        String sub = q(R.plurals.addons_count, app.installed.size())
                + " · " + s(R.string.installed_managed, managed) + (app.checking ? " · " + s(R.string.installed_checking) : "");
        LinearLayout h = header(s(R.string.nav_installed), sub);
        addHeaderButton(h, s(R.string.action_from_zip), R.drawable.ic_zip, Ui.Kind.GHOST, v -> app.pickZip());
        addHeaderButton(h, s(app.checking ? R.string.action_checking : R.string.action_check), R.drawable.ic_refresh, Ui.Kind.SECONDARY, v -> app.manager.checkUpdates(true));
        if (updates > 0) {
            addHeaderButton(h, s(R.string.action_update_all, updates), R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> app.manager.updateAll());
        }

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

    private View installedRow(Model.Installed r) {
        LinearLayout row = rowShell(r == app.selected);
        ImageView av = avatar(r.entry != null ? r.entry.iconUrl : (r.catalogMatch != null ? r.catalogMatch.iconUrl : null), r.title, 44);
        row.addView(av);
        LinearLayout mid = Ui.vbox(app);
        LinearLayout tl = Ui.hbox(app);
        TextView title = Ui.oneLine(Ui.text(app, r.title, 15, Ui.TEXT, Ui.MEDIUM));
        tl.addView(title, Ui.lpw(0, -2, 1));
        mid.addView(tl);
        StringBuilder sub = new StringBuilder();
        if (r.version != null && !r.version.isEmpty()) sub.append(r.version);
        String src = r.entry != null ? sourceName(r.entry.source) : s(R.string.installed_manually);
        if (sub.length() > 0) sub.append("  ·  ");
        sub.append(src);
        if (r.folders.size() > 1) sub.append("  ·  ").append(q(R.plurals.folders_count, r.folders.size()));
        TextView st = Ui.oneLine(Ui.text(app, sub.toString(), 12.5f, Ui.TEXT3, Ui.REGULAR));
        mid.addView(st, Ui.margins(Ui.lp(-1, -2), 0, 5, 0, 0));
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        String key = r.entry != null ? r.entry.key : (r.catalogMatch != null ? r.catalogMatch.key() : null);
        if (key != null && app.busy.containsKey(key)) {
            row.addView(Ui.pill(app, s(R.string.pill_installing), Ui.ACCENT, Ui.ACCENT_SOFT));
        } else if (r.missing) {
            row.addView(Ui.pill(app, s(R.string.pill_folders_missing), Ui.RED, Ui.RED_SOFT));
        } else if (r.entry != null && r.entry.updateAvailable) {
            final Model.Installed fr = r;
            LinearLayout up = Ui.button(app, s(R.string.action_update), R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> app.manager.update(fr.entry));
            up.setFocusable(false);
            up.setMinimumHeight(Ui.dp(34));
            up.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            row.addView(up);
        } else if (r.entry != null && r.entry.checkError != null) {
            row.addView(Ui.pill(app, s(R.string.pill_unchecked), Ui.TEXT2, Ui.SURFACE3));
        } else if (r.entry != null && r.entry.latest != null) {
            row.addView(Ui.pill(app, s(R.string.pill_up_to_date), Ui.GREEN, Ui.GREEN_SOFT));
        } else if (r.entry == null && r.catalogMatch != null) {
            row.addView(Ui.pill(app, s(R.string.pill_in_catalog), Ui.BLUE, Ui.BLUE_SOFT));
        } else if (r.entry == null) {
            row.addView(Ui.pill(app, s(R.string.pill_manual), Ui.TEXT2, Ui.SURFACE3));
        }
        if (!r.compatible && !r.missing) {
            ImageView w = Ui.icon(app, R.drawable.ic_warning, Ui.ORANGE, 18);
            row.addView(w, Ui.margins(Ui.lp(Ui.dp(18), Ui.dp(18)), 8, 0, 0, 0));
        }
        return row;
    }
}
