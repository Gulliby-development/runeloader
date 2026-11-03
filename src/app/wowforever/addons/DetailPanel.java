package app.wowforever.addons;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The addon detail panel: docked on the right in landscape, full screen in portrait. */
final class DetailPanel extends Screen {

    DetailPanel(MainActivity app) { super(app); }

    void select(Object o) {
        app.selected = o;
        if (!app.landscape) {
            if (o != null) { app.portraitDetailOpen = true; app.portraitDetail.setVisibility(View.VISIBLE); }
            else closePortraitDetail();
        }
        app.refreshList();
        renderDetail();
    }

    void closePortraitDetail() {
        app.portraitDetailOpen = false;
        app.selected = null;
        if (app.portraitDetail != null) { app.portraitDetail.removeAllViews(); app.portraitDetail.setVisibility(View.GONE); }
        app.refreshList();
        if (app.landscape) renderDetail();
    }

    void renderDetail() {
        FrameLayout target = app.landscape ? app.detailBox : app.portraitDetail;
        if (target == null) return;
        target.removeAllViews();
        if (!app.landscape && !app.portraitDetailOpen) return;
        if (app.selected == null) {
            if (app.landscape) target.addView(detailPlaceholder());
            return;
        }
        // Keep "Installed" rows fresh after a rescan.
        if (app.selected instanceof Model.Installed) {
            Model.Installed s = (Model.Installed) app.selected;
            Model.Installed fresh = null;
            for (Model.Installed r : app.installed) {
                if (s.entry != null && r.entry != null && r.entry.key.equals(s.entry.key)) fresh = r;
                else if (s.entry == null && r.entry == null && r.main() != null && s.main() != null && r.main().folder.equals(s.main().folder)) fresh = r;
                else if (s.entry == null && r.entry != null && s.catalogMatch != null && r.entry.key.equals(s.catalogMatch.key())) fresh = r;
            }
            if (fresh == null && app.tab == MainActivity.TAB_INSTALLED) { app.selected = null; if (app.landscape) target.addView(detailPlaceholder()); else closePortraitDetail(); return; }
            if (fresh != null) app.selected = fresh;
        }
        ScrollView sv = new ScrollView(app);
        sv.setFillViewport(true);
        LinearLayout col = Ui.vbox(app);
        col.setPadding(Ui.dp(20), Ui.dp(app.landscape ? 18 : 12), Ui.dp(20), Ui.dp(96));
        sv.addView(col);
        if (!app.landscape) {
            LinearLayout back = Ui.button(app, s(R.string.action_back), R.drawable.ic_back, Ui.Kind.GHOST, v -> closePortraitDetail());
            col.addView(back, Ui.margins(Ui.lp(-2, Ui.dp(38)), 0, 0, 0, 14));
        }
        if (app.selected instanceof Model.Item) buildItemDetail(col, (Model.Item) app.selected);
        else buildInstalledDetail(col, (Model.Installed) app.selected);
        target.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private View detailPlaceholder() {
        LinearLayout box = Ui.vbox(app);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(24), 0, Ui.dp(24), 0);
        ImageView iv = Ui.icon(app, app.tab == MainActivity.TAB_INSTALLED ? R.drawable.ic_extension : R.drawable.ic_apps, Ui.SURFACE3, 56);
        box.addView(iv, Ui.lp(Ui.dp(56), Ui.dp(56)));
        String t;
        String s;
        if (app.tab == MainActivity.TAB_SETTINGS) {
            t = "WoW Forever";
            s = s(R.string.detail_hint_installed);
        } else if (app.tab == MainActivity.TAB_INSTALLED) {
            t = s(R.string.detail_pick_addon);
            s = s(R.string.detail_hint_library);
        } else {
            t = s(R.string.detail_pick_addon);
            s = s(R.string.detail_hint_browse);
        }
        TextView tt = Ui.text(app, t, 16, Ui.TEXT2, Ui.BOLD);
        tt.setGravity(Gravity.CENTER);
        box.addView(tt, Ui.margins(Ui.lp(-2, -2), 0, 14, 0, 6));
        TextView st = Ui.text(app, s, 13, Ui.TEXT3, Ui.REGULAR);
        st.setGravity(Gravity.CENTER);
        box.addView(st);
        return box;
    }

    private void detailHeader(LinearLayout col, String icon, String name, String author, View... pills) {
        LinearLayout h = Ui.hbox(app);
        h.setGravity(Gravity.TOP);
        h.addView(avatar(icon, name, 64));
        LinearLayout tb = Ui.vbox(app);
        TextView t = Ui.text(app, name, 19, Ui.TEXT, Ui.BOLD);
        t.setMaxLines(2);
        tb.addView(t);
        if (author != null && !author.isEmpty()) tb.addView(Ui.oneLine(Ui.text(app, author, 13, Ui.TEXT2, Ui.REGULAR)), Ui.margins(Ui.lp(-2, -2), 0, 5, 0, 0));
        LinearLayout pr = Ui.hbox(app);
        for (View p : pills) pr.addView(p, Ui.margins(Ui.lp(-2, -2), 0, 0, 6, 0));
        tb.addView(pr, Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        h.addView(tb, Ui.margins(Ui.lpw(0, -2, 1), 14, 0, 0, 0));
        col.addView(h, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 16));
    }

    private void infoRow(LinearLayout col, String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout r = Ui.hbox(app);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
        r.addView(Ui.text(app, label, 13, Ui.TEXT3, Ui.REGULAR), Ui.lp(Ui.dp(110), -2));
        TextView v = Ui.text(app, value, 13, Ui.TEXT, Ui.MEDIUM);
        v.setTextIsSelectable(false);
        r.addView(v, Ui.lpw(0, -2, 1));
        col.addView(r);
        col.addView(Ui.divider(app));
    }

    private void noteCard(LinearLayout col, String text, int fg, int bg) {
        LinearLayout n = Ui.hbox(app);
        n.setGravity(Gravity.TOP);
        n.setBackground(Ui.round(bg, 12));
        n.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        n.addView(Ui.icon(app, R.drawable.ic_info, fg, 18));
        n.addView(Ui.text(app, text, 12.5f, fg, Ui.REGULAR), Ui.margins(Ui.lpw(0, -2, 1), 10, 0, 0, 0));
        col.addView(n, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
    }

    private LinearLayout actionRow(LinearLayout col) {
        LinearLayout a = Ui.hbox(app);
        col.addView(a, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 16));
        return a;
    }

    private void buildItemDetail(LinearLayout col, final Model.Item it) {
        View src = Ui.pill(app, sourceName(it.source), Ui.TEXT2, Ui.SURFACE3);
        View fv = it.foreverVerified ? Ui.pill(app, s(R.string.pill_forever), Ui.GREEN, Ui.GREEN_SOFT) : Ui.pill(app, s(R.string.pill_forever_unverified), Ui.ORANGE, Ui.ORANGE_SOFT);
        detailHeader(col, it.iconUrl, it.name, it.author, src, fv);

        final Model.Entry e = app.store.entry(it.key());
        LinearLayout actions = actionRow(col);
        if (app.busy.containsKey(it.key())) {
            LinearLayout b = Ui.button(app, app.busy.get(it.key()), 0, Ui.Kind.SECONDARY, null);
            b.setEnabled(false);
            actions.addView(b, Ui.lpw(0, Ui.dp(44), 1));
        } else if (it.distributionOff) {
            actions.addView(Ui.button(app, s(R.string.action_download_site), R.drawable.ic_open, Ui.Kind.PRIMARY, v -> app.openUrl(it.pageUrl)), Ui.lpw(0, Ui.dp(44), 1));
        } else {
            String label = s(e == null ? R.string.action_install : R.string.action_reinstall);
            LinearLayout b = Ui.button(app, label, R.drawable.ic_download, e == null ? Ui.Kind.PRIMARY : Ui.Kind.SECONDARY, v -> app.manager.install(it, null));
            actions.addView(b, Ui.lpw(0, Ui.dp(44), 1));
            if (app.landscape) b.post(b::requestFocus);
        }
        if (it.pageUrl != null) {
            actions.addView(Ui.button(app, null, R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl(it.pageUrl)), Ui.margins(Ui.lp(-2, Ui.dp(44)), 8, 0, 0, 0));
        }
        if (e != null && !app.busy.containsKey(it.key())) {
            actions.addView(Ui.button(app, null, R.drawable.ic_delete, Ui.Kind.DANGER, v -> app.manager.confirmRemoveEntry(e)), Ui.margins(Ui.lp(-2, Ui.dp(44)), 8, 0, 0, 0));
        }

        if (it.note != null) noteCard(col, it.note, Ui.ORANGE, Ui.ORANGE_SOFT);
        if (it.distributionOff) noteCard(col, s(R.string.note_distribution_off), Ui.ORANGE, Ui.ORANGE_SOFT);
        if (Model.SRC_WOWI.equals(it.source)) noteCard(col, s(R.string.note_wowi_version), Ui.BLUE, Ui.BLUE_SOFT);

        if (!it.summary.isEmpty()) {
            TextView s = Ui.text(app, it.summary, 14, Ui.TEXT2, Ui.REGULAR);
            col.addView(s, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
        }
        final TextView desc = Ui.text(app, "", 13, Ui.TEXT2, Ui.REGULAR);
        desc.setVisibility(View.GONE);
        col.addView(desc, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
        if (Model.SRC_WOWI.equals(it.source)) {
            MainActivity.net.execute(() -> {
                final String d = Sources.Wowi.description(it.id);
                app.main.post(() -> { if (d != null && !d.isEmpty() && app.selected == it) { desc.setText(d); desc.setVisibility(View.VISIBLE); } });
            });
        }

        if (e != null) infoRow(col, s(R.string.info_installed), e.version);
        if (it.version != null && !Model.SRC_CURSE.equals(it.source)) infoRow(col, s(R.string.info_version), it.version);
        if (!it.category.isEmpty()) infoRow(col, s(R.string.info_category), it.category);
        if (it.downloads > 0) infoRow(col, s(R.string.info_downloads), Ui.formatCount(it.downloads));
        if (it.updated > 0) infoRow(col, s(R.string.info_updated), Ui.formatDate(it.updated));
        if (!it.folders.isEmpty()) infoRow(col, s(R.string.info_folders), joinShort(it.folders, 6));
        if (!it.deps.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String d : it.deps) { Model.Item c = Catalog.byKey(d); names.add(c != null ? c.name : d); }
            infoRow(col, s(R.string.info_dependencies), s(R.string.info_dependencies_auto, String.join(", ", names)));
        }
        infoRow(col, s(R.string.info_source), Model.SRC_GITHUB.equals(it.source) ? "github.com/" + it.id : sourceName(it.source) + " #" + it.id);
    }

    private void buildInstalledDetail(LinearLayout col, final Model.Installed r) {
        final Model.Entry e = r.entry;
        String icon = e != null ? e.iconUrl : (r.catalogMatch != null ? r.catalogMatch.iconUrl : null);
        List<View> pills = new ArrayList<>();
        pills.add(Ui.pill(app, e != null ? sourceName(e.source) : s(R.string.pill_manual), Ui.TEXT2, Ui.SURFACE3));
        if (r.missing) pills.add(Ui.pill(app, s(R.string.pill_folders_missing), Ui.RED, Ui.RED_SOFT));
        else if (r.compatible) pills.add(Ui.pill(app, s(R.string.pill_forever), Ui.GREEN, Ui.GREEN_SOFT));
        else pills.add(Ui.pill(app, s(R.string.pill_not_forever), Ui.ORANGE, Ui.ORANGE_SOFT));
        detailHeader(col, icon, r.title, r.author, pills.toArray(new View[0]));

        String busyKey = e != null ? e.key : (r.catalogMatch != null ? r.catalogMatch.key() : null);
        LinearLayout actions = actionRow(col);
        LinearLayout primary = null;
        if (busyKey != null && app.busy.containsKey(busyKey)) {
            LinearLayout b = Ui.button(app, app.busy.get(busyKey), 0, Ui.Kind.SECONDARY, null);
            actions.addView(b, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e != null && !Model.SRC_ZIP.equals(e.source)) {
            if (e.updateAvailable) {
                primary = Ui.button(app, s(R.string.action_update_to, shortVer(e.latest)), R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> app.manager.update(e));
            } else if (r.missing) {
                primary = Ui.button(app, s(R.string.action_install_again), R.drawable.ic_download, Ui.Kind.PRIMARY, v -> app.manager.update(e));
            } else {
                primary = Ui.button(app, s(R.string.action_check_update), R.drawable.ic_refresh, Ui.Kind.SECONDARY, v -> app.manager.checkOne(e));
            }
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e == null && r.catalogMatch != null) {
            primary = Ui.button(app, s(R.string.action_track_catalog), R.drawable.ic_link, Ui.Kind.PRIMARY, v -> app.manager.install(r.catalogMatch, null));
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e == null && r.main() != null && r.main().curseId != null && Sources.hasCurseKey()) {
            final String cid = r.main().curseId;
            primary = Ui.button(app, s(R.string.action_track_curse), R.drawable.ic_link, Ui.Kind.PRIMARY, v -> app.manager.adoptCurse(cid, r));
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e == null && r.main() != null && r.main().wowiId != null) {
            final String wid = r.main().wowiId;
            primary = Ui.button(app, s(R.string.action_track_wowi), R.drawable.ic_link, Ui.Kind.PRIMARY, v -> app.manager.adoptWowi(wid, r));
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        }
        String page = e != null ? e.pageUrl : (r.catalogMatch != null ? r.catalogMatch.pageUrl : (r.main() != null ? r.main().website : null));
        if (page != null) {
            final String p = page;
            actions.addView(Ui.button(app, primary == null ? s(R.string.action_page) : null, R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl(p)), Ui.margins(Ui.lp(primary == null ? 0 : -2, Ui.dp(44)), primary == null ? 0 : 8, 0, 0, 0));
            if (primary == null) ((LinearLayout.LayoutParams) actions.getChildAt(actions.getChildCount() - 1).getLayoutParams()).weight = 1;
        }
        LinearLayout del = Ui.button(app, primary == null && page == null ? s(R.string.action_remove) : null, R.drawable.ic_delete, Ui.Kind.DANGER, v -> app.manager.confirmRemove(r));
        actions.addView(del, Ui.margins(Ui.lp(primary == null && page == null ? 0 : -2, Ui.dp(44)), actions.getChildCount() == 0 ? 0 : 8, 0, 0, 0));
        if (primary == null && page == null) ((LinearLayout.LayoutParams) del.getLayoutParams()).weight = 1;
        final View focusTarget = primary != null ? primary : del;
        if (app.landscape) focusTarget.post(focusTarget::requestFocus);

        if (!r.compatible && !r.missing) {
            noteCard(col, s(R.string.note_not_forever), Ui.ORANGE, Ui.ORANGE_SOFT);
        }
        if (e != null && e.checkError != null) noteCard(col, s(R.string.note_check_error, e.checkError), Ui.TEXT2, Ui.SURFACE2);
        Model.Item cat = e != null ? Catalog.byKey(e.key) : r.catalogMatch;
        if (cat != null && cat.note != null) noteCard(col, cat.note, Ui.ORANGE, Ui.ORANGE_SOFT);
        if (e == null && r.catalogMatch != null) noteCard(col, s(R.string.note_manual_in_catalog), Ui.BLUE, Ui.BLUE_SOFT);

        if (r.notes != null && !r.notes.isEmpty()) {
            col.addView(Ui.text(app, r.notes, 14, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
        }
        infoRow(col, s(R.string.info_version), r.version);
        if (e != null && e.latest != null) infoRow(col, s(R.string.info_latest), e.latest);
        if (e != null && e.installedAt > 0) infoRow(col, s(R.string.info_installed_at), Ui.formatDate(e.installedAt));
        if (r.main() != null) infoRow(col, s(R.string.info_interface), r.main().interfaceVersion);
        List<String> fs = new ArrayList<>();
        for (Model.LocalAddon a : r.folders) fs.add(a.folder);
        if (fs.isEmpty() && e != null) fs.addAll(e.folders);
        infoRow(col, s(R.string.info_folders), joinShort(fs, 8));
        if (e != null && !Model.SRC_ZIP.equals(e.source)) infoRow(col, s(R.string.info_source), Model.SRC_GITHUB.equals(e.source) ? "github.com/" + e.id : sourceName(e.source) + " #" + e.id);
    }

    private static String shortVer(String v) {
        if (v == null) return "";
        return v.length() > 22 ? v.substring(0, 22) + "…" : v;
    }

    static String joinShort(List<String> l, int max) {
        if (l.size() <= max) return String.join(", ", l);
        return Lang.s(R.string.join_more, String.join(", ", l.subList(0, max)), l.size() - max);
    }
}
