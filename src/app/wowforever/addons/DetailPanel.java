package app.wowforever.addons;

import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The addon detail panel: docked on the right when it fits, otherwise a side sheet or full screen. */
final class DetailPanel extends Screen {

    DetailPanel(MainActivity app) { super(app); }

    void select(Object o) {
        app.selected = o;
        if (!app.layout.docked) {
            if (o != null) app.showDetailSheet(true);
            else closeDetailSheet();
        }
        app.refreshList();
        renderDetail();
    }

    void closeDetailSheet() {
        app.detailSheetOpen = false;
        app.selected = null;
        if (app.detailSheet != null) app.detailSheet.removeAllViews();
        app.showDetailSheet(false);
        app.refreshList();
        if (app.layout.docked) renderDetail();
    }

    void renderDetail() {
        FrameLayout target = app.layout.docked ? app.detailBox : app.detailSheet;
        if (target == null) return;
        target.removeAllViews();
        if (!app.layout.docked && !app.detailSheetOpen) return;
        if (app.selected == null) {
            if (app.layout.docked) target.addView(detailPlaceholder());
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
            if (fresh == null && app.tab == MainActivity.TAB_INSTALLED) { app.selected = null; if (app.layout.docked) target.addView(detailPlaceholder()); else closeDetailSheet(); return; }
            if (fresh != null) app.selected = fresh;
        }
        ScrollView sv = new ScrollView(app);
        sv.setFillViewport(true);
        LinearLayout col = Ui.vbox(app);
        col.setPadding(Ui.dp(18), Ui.dp(app.landscape ? 18 : 12), Ui.dp(18), Ui.dp(96));
        // A faint mint light from the top, like the glow of the rune.
        GradientDrawable tint = new GradientDrawable();
        tint.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        tint.setColors(new int[]{Ui.GREEN_SOFT, Color.TRANSPARENT});
        tint.setGradientCenter(0.3f, 0f);
        tint.setGradientRadius(Ui.dp(240));
        col.setBackground(tint);
        sv.addView(col);
        if (!app.layout.docked) {
            LinearLayout back = Ui.button(app, s(R.string.action_back), R.drawable.ic_back, Ui.Kind.GHOST, v -> closeDetailSheet());
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

    /** Hero: logo with a mint glow, name, author and source, then the status pills. */
    private void detailHeader(LinearLayout col, String icon, String name, String byline, View... pills) {
        LinearLayout h = Ui.hbox(app);
        // Let the glow spill past the hero row.
        h.setClipChildren(false);
        col.setClipChildren(false);
        ImageView av = avatar(icon, name, 60);
        final float radius = Ui.dp(60) * 0.26f;
        av.setBackground(Ui.round(Ui.SURFACE, 16, Ui.STROKE, 1f));
        av.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        av.setElevation(Ui.dp(10));
        av.setOutlineAmbientShadowColor(Ui.GREEN);
        av.setOutlineSpotShadowColor(Ui.GREEN);
        h.addView(av, Ui.margins(Ui.lp(Ui.dp(60), Ui.dp(60)), 2, 2, 0, 2));
        LinearLayout tb = Ui.vbox(app);
        tb.addView(Ui.text(app, name, 18.5f, Ui.TEXT, Ui.BOLD));
        if (byline != null && !byline.isEmpty()) tb.addView(Ui.text(app, byline, 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        h.addView(tb, Ui.margins(Ui.lpw(0, -2, 1), 14, 0, 0, 0));
        col.addView(h, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
        if (pills.length > 0) {
            Ui.FlowRow badges = new Ui.FlowRow(app, 6, 6);
            for (View p : pills) badges.addView(p);
            col.addView(badges, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 16));
        }
    }

    private static String byline(String author, String source) {
        if (author == null || author.isEmpty()) return source;
        return author + "  ·  " + source;
    }

    /** One key/value line: label on the left, value on the right; versions in monospace. */
    private void infoRow(LinearLayout col, String label, String value) { infoRow(col, label, value, false); }

    private void infoRow(LinearLayout col, String label, String value, boolean mono) {
        if (value == null || value.isEmpty()) return;
        LinearLayout r = Ui.hbox(app);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(5), 0, Ui.dp(5));
        r.addView(Ui.text(app, label, 12.5f, Ui.TEXT3, Ui.REGULAR), Ui.lp(-2, -2));
        TextView v = Ui.text(app, value, mono ? 12 : 12.5f, Ui.TEXT, mono ? Typeface.MONOSPACE : Ui.MEDIUM);
        v.setGravity(Gravity.END);
        r.addView(v, Ui.margins(Ui.lpw(0, -2, 1), 14, 0, 0, 0));
        col.addView(r);
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

    /** The main action, full width. */
    private void primaryAction(LinearLayout col, View b) {
        col.addView(b, Ui.margins(Ui.lp(-1, Ui.dp(44)), 0, 0, 0, 8));
    }

    /** Secondary and danger actions below the main one; they wrap instead of shrinking. */
    private Ui.FlowRow secondaryActions(LinearLayout col) {
        Ui.FlowRow f = new Ui.FlowRow(app, 8, 8);
        col.addView(f, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 16));
        return f;
    }

    private void addSecondary(Ui.FlowRow f, LinearLayout b) {
        b.setMinimumHeight(Ui.dp(40));
        f.addView(b, new ViewGroup.LayoutParams(-2, Ui.dp(40)));
    }

    private void buildItemDetail(LinearLayout col, final Model.Item it) {
        final Model.Entry e = app.store.entry(it.key());
        List<View> pills = new ArrayList<>();
        if (e != null) pills.add(LibraryScreen.iconPill(app, s(R.string.pill_installed), R.drawable.ic_check, Ui.GREEN, Ui.GREEN_SOFT));
        pills.add(it.foreverVerified ? Ui.pill(app, s(R.string.pill_forever), Ui.GREEN, Ui.GREEN_SOFT) : Ui.pill(app, s(R.string.pill_forever_unverified), Ui.ORANGE, Ui.ORANGE_SOFT));
        detailHeader(col, it.iconUrl, it.name, byline(it.author, sourceName(it.source)), pills.toArray(new View[0]));

        if (app.busy.containsKey(it.key())) {
            LinearLayout b = Ui.button(app, app.busy.get(it.key()), 0, Ui.Kind.SECONDARY, null);
            b.setEnabled(false);
            primaryAction(col, b);
        } else {
            String label = s(e == null ? R.string.action_install : R.string.action_reinstall);
            LinearLayout b = Ui.button(app, label, R.drawable.ic_download, e == null ? Ui.Kind.PRIMARY : Ui.Kind.SECONDARY, v -> app.manager.install(it, null));
            primaryAction(col, b);
            if (app.landscape) b.post(b::requestFocus);
        }
        boolean canRemove = e != null && !app.busy.containsKey(it.key());
        if (it.pageUrl != null || canRemove) {
            Ui.FlowRow more = secondaryActions(col);
            if (it.pageUrl != null) addSecondary(more, Ui.button(app, s(R.string.action_page), R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl(it.pageUrl)));
            if (canRemove) addSecondary(more, Ui.button(app, s(R.string.action_remove), R.drawable.ic_delete, Ui.Kind.DANGER, v -> app.manager.confirmRemoveEntry(e)));
        } else {
            col.addView(Ui.space(app, 1, 8));
        }

        if (it.note != null) noteCard(col, it.note, Ui.ORANGE, Ui.ORANGE_SOFT);
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

        if (e != null) infoRow(col, s(R.string.info_installed), e.version, true);
        if (it.version != null) infoRow(col, s(R.string.info_version), it.version, true);
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
        String busyKey = e != null ? e.key : (r.catalogMatch != null ? r.catalogMatch.key() : null);
        boolean busy = busyKey != null && app.busy.containsKey(busyKey);
        if (busy) pills.add(Ui.pill(app, s(R.string.pill_installing), Ui.ACCENT, Ui.ACCENT_SOFT));
        else if (r.missing) pills.add(Ui.pill(app, s(R.string.pill_folders_missing), Ui.RED, Ui.RED_SOFT));
        else if (e != null && e.updateAvailable) pills.add(LibraryScreen.iconPill(app, s(R.string.action_update_to, shortVer(e.latest)), R.drawable.ic_upgrade, Ui.ACCENT, Ui.ACCENT_SOFT));
        else if (e != null && e.checkError != null) pills.add(Ui.pill(app, s(R.string.pill_unchecked), Ui.TEXT2, Ui.SURFACE3));
        else if (e != null && e.latest != null) pills.add(LibraryScreen.iconPill(app, s(R.string.pill_up_to_date), R.drawable.ic_check, Ui.GREEN, Ui.GREEN_SOFT));
        else if (e == null && r.catalogMatch != null) pills.add(Ui.pill(app, s(R.string.pill_in_catalog), Ui.BLUE, Ui.BLUE_SOFT));
        else if (e == null) pills.add(Ui.pill(app, s(R.string.pill_manual), Ui.TEXT2, Ui.SURFACE3));
        if (!r.missing) {
            if (r.compatible) pills.add(Ui.pill(app, s(R.string.pill_forever), Ui.GREEN, Ui.GREEN_SOFT));
            else pills.add(Ui.pill(app, s(R.string.pill_not_forever), Ui.ORANGE, Ui.ORANGE_SOFT));
        }
        detailHeader(col, icon, r.title, byline(r.author, e != null ? sourceName(e.source) : s(R.string.pill_manual)), pills.toArray(new View[0]));

        LinearLayout primary = null;
        if (busy) {
            LinearLayout b = Ui.button(app, app.busy.get(busyKey), 0, Ui.Kind.SECONDARY, null);
            primaryAction(col, b);
        } else if (e != null && !Model.SRC_ZIP.equals(e.source)) {
            if (e.updateAvailable) {
                primary = Ui.button(app, s(R.string.action_update_to, shortVer(e.latest)), R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> app.manager.update(e));
            } else if (r.missing) {
                primary = Ui.button(app, s(R.string.action_install_again), R.drawable.ic_download, Ui.Kind.PRIMARY, v -> app.manager.update(e));
            } else {
                primary = Ui.button(app, s(R.string.action_check_update), R.drawable.ic_refresh, Ui.Kind.SECONDARY, v -> app.manager.checkOne(e));
            }
        } else if (e == null && r.catalogMatch != null) {
            primary = Ui.button(app, s(R.string.action_track_catalog), R.drawable.ic_link, Ui.Kind.PRIMARY, v -> app.manager.install(r.catalogMatch, null));
        } else if (e == null && r.main() != null && r.main().wowiId != null) {
            final String wid = r.main().wowiId;
            primary = Ui.button(app, s(R.string.action_track_wowi), R.drawable.ic_link, Ui.Kind.PRIMARY, v -> app.manager.adoptWowi(wid, r));
        }
        if (primary != null) primaryAction(col, primary);
        Ui.FlowRow more = secondaryActions(col);
        String page = e != null ? e.pageUrl : (r.catalogMatch != null ? r.catalogMatch.pageUrl : (r.main() != null ? r.main().website : null));
        if (page != null) {
            final String p = page;
            addSecondary(more, Ui.button(app, s(R.string.action_page), R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl(p)));
        }
        LinearLayout del = Ui.button(app, s(R.string.action_remove), R.drawable.ic_delete, Ui.Kind.DANGER, v -> app.manager.confirmRemove(r));
        addSecondary(more, del);
        final View focusTarget = primary != null ? primary : del;
        if (app.landscape) focusTarget.post(focusTarget::requestFocus);

        if (e != null && !Model.SRC_ZIP.equals(e.source) && app.store.autoMode() != UpdatePolicy.MODE_OFF) {
            LinearLayout pin = Ui.button(app, s(e.pinned ? R.string.auto_unpin : R.string.auto_pin), R.drawable.ic_upgrade, Ui.Kind.GHOST, v -> {
                e.pinned = !e.pinned;
                app.store.put(e);
                renderDetail();
            });
            col.addView(pin, Ui.margins(Ui.lp(-1, Ui.dp(40)), 0, 0, 0, 12));
            if (e.pinned) noteCard(col, s(R.string.auto_pinned_note), Ui.TEXT2, Ui.SURFACE2);
        }

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
        infoRow(col, s(R.string.info_version), r.version, true);
        if (e != null && e.latest != null) infoRow(col, s(R.string.info_latest), e.latest, true);
        if (e != null && e.installedAt > 0) infoRow(col, s(R.string.info_installed_at), Ui.formatDate(e.installedAt));
        if (r.main() != null) infoRow(col, s(R.string.info_interface), r.main().interfaceVersion, true);
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
