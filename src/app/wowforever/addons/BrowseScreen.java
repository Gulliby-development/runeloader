package app.wowforever.addons;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The browse tab: GitHub index search, the Forever catalog and WoWInterface. */
final class BrowseScreen extends Screen {

    int source = MainActivity.SRC_GITHUB;
    private final String[] queries = {"", "", ""};
    private String catalogCategory = null;
    private final Map<Integer, String> resultErrors = new HashMap<>();
    private final Set<Integer> loading = new HashSet<>();
    private final Map<Integer, Boolean> hasMore = new HashMap<>();
    private final int[] searchGen = new int[3];
    private Runnable pendingSearch;

    BrowseScreen(MainActivity app) { super(app); }

    private static final int[] SOURCE_ICONS = {R.drawable.ic_link, R.drawable.ic_star, R.drawable.ic_apps};
    /** At this list width the sources fit as a segmented control; below it, a picker next to search. */
    private static final int SEGMENTED_DP = 560;
    /** Below this list width the row actions drop their labels. */
    private static final int ROW_LABELS_DP = 480;

    private String sourceLabel(int i) {
        switch (i) {
            case MainActivity.SRC_WOWI: return "WoWInterface";
            case MainActivity.SRC_GITHUB: return "GitHub";
            default: return s(R.string.browse_src_catalog);
        }
    }

    private void switchSource(int s) {
        if (source == s) return;
        source = s;
        app.selected = null;
        app.render();
        ensureResults(false);
    }

    /** Below this list width the subtitle gets its own line, as on 4:3. */
    private static final int INLINE_SUBTITLE_DP = 600;

    private int gutter() { return app.landscape ? 20 : 16; }

    /** Header with the title and subtitle on one line when the window is low and wide enough. */
    private LinearLayout browseHeader(String title, String subtitle) {
        if (!app.layout.compactHeight || app.layout.listDp < INLINE_SUBTITLE_DP) return header(title, subtitle);
        LinearLayout h = Ui.hbox(app);
        h.setPadding(Ui.dp(gutter()), Ui.dp(10), Ui.dp(app.landscape ? 16 : 12), Ui.dp(6));
        LinearLayout tbox = Ui.hbox(app);
        tbox.setBaselineAligned(true);
        tbox.setGravity(android.view.Gravity.BOTTOM);
        tbox.addView(Ui.oneLine(Ui.text(app, title, 19, Ui.TEXT, Ui.BOLD)), Ui.lp(-2, -2));
        tbox.addView(Ui.oneLine(Ui.text(app, subtitle, 12, Ui.TEXT3, Ui.REGULAR)), Ui.margins(Ui.lpw(0, -2, 1), 10, 0, 0, 0));
        h.addView(tbox, Ui.lpw(0, -2, 1));
        app.headerBox.addView(h);
        return h;
    }

    /** All sources side by side, the current one in gold. */
    private View segmentedSources() {
        HorizontalScrollView hs = new HorizontalScrollView(app);
        hs.setHorizontalScrollBarEnabled(false);
        Ui.scrollHint(hs);
        hs.setPadding(Ui.dp(gutter()), 0, Ui.dp(16), Ui.dp(8));
        hs.setClipToPadding(false);
        LinearLayout seg = Ui.hbox(app);
        seg.setBackground(Ui.round(Ui.SURFACE2, 12, Ui.STROKE, 1f));
        seg.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        for (int i = 0; i < SOURCE_ICONS.length; i++) {
            final int src = i;
            boolean on = i == source;
            LinearLayout part = Ui.hbox(app);
            part.setFocusable(true);
            part.setClickable(true);
            part.setBackground(Ui.selectable(on ? Ui.ACCENT : 0x00000000, 9, 0));
            part.setPadding(Ui.dp(11), 0, Ui.dp(12), 0);
            part.setContentDescription(sourceLabel(i));
            part.addView(Ui.icon(app, SOURCE_ICONS[i], on ? Ui.ACCENT_DARK : Ui.TEXT2, 15));
            TextView t = Ui.oneLine(Ui.text(app, sourceLabel(i), 12.5f, on ? Ui.ACCENT_DARK : Ui.TEXT2, Ui.MEDIUM));
            part.addView(t, Ui.margins(Ui.lp(-2, -2), 6, 0, 0, 0));
            part.setOnClickListener(v -> switchSource(src));
            seg.addView(part, Ui.margins(Ui.lp(-2, Ui.dp(32)), i == 0 ? 0 : 4, 0, 0, 0));
        }
        hs.addView(seg);
        return hs;
    }

    /** The current source as a chip; it opens a chooser with all of them. */
    private View sourcePicker() {
        LinearLayout chip = Ui.hbox(app);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setBackground(Ui.selectable(Ui.SURFACE2, 12, Ui.STROKE));
        chip.setPadding(Ui.dp(10), 0, Ui.dp(8), 0);
        chip.setContentDescription(s(R.string.browse_source) + ": " + sourceLabel(source));
        chip.setTooltipText(s(R.string.browse_source));
        chip.addView(Ui.icon(app, SOURCE_ICONS[source], Ui.ACCENT, 16));
        TextView t = Ui.oneLine(Ui.text(app, sourceLabel(source), 13, Ui.TEXT, Ui.MEDIUM));
        // A long source name must not squeeze the search field to nothing.
        t.setMaxWidth(Ui.dp(Math.max(72, app.layout.listDp * 2 / 5 - 54)));
        chip.addView(t, Ui.margins(Ui.lp(-2, -2), 7, 0, 0, 0));
        ImageView chev = Ui.icon(app, R.drawable.ic_chevron, Ui.TEXT3, 14);
        chev.setRotation(90);
        chip.addView(chev, Ui.margins(Ui.lp(Ui.dp(14), Ui.dp(14)), 5, 0, 0, 0));
        chip.setOnClickListener(v -> showSourceChooser());
        return chip;
    }

    private void showSourceChooser() {
        LinearLayout col = Ui.vbox(app);
        View current = null;
        for (int i = 0; i < SOURCE_ICONS.length; i++) {
            final int src = i;
            boolean on = i == source;
            LinearLayout b = Ui.button(app, sourceLabel(i), SOURCE_ICONS[i], on ? Ui.Kind.PRIMARY : Ui.Kind.SECONDARY, v -> { app.closeModal(); switchSource(src); });
            b.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            col.addView(b, Ui.margins(Ui.lp(-1, Ui.dp(44)), 0, 0, 0, 8));
            if (on) current = b;
        }
        app.modal(s(R.string.browse_source), null, col, null);
        final View f = current;
        if (f != null) f.post(f::requestFocus);
    }

    /** What is wrong, and a button that fixes it. The message wraps: some languages need two lines. */
    private void gameFolderBanner() {
        LinearLayout bar = Ui.hbox(app);
        bar.setBackground(Ui.round(Ui.ORANGE_SOFT, 12));
        bar.setPadding(Ui.dp(12), Ui.dp(5), Ui.dp(5), Ui.dp(5));
        bar.addView(Ui.icon(app, R.drawable.ic_warning, Ui.ORANGE, 17));
        TextView msg = Ui.text(app, s(R.string.browse_no_game), 12.5f, Ui.ORANGE, Ui.REGULAR);
        bar.addView(msg, Ui.margins(Ui.lpw(0, -2, 1), 10, 0, 8, 0));
        TextView fix = Ui.oneLine(Ui.text(app, s(R.string.browse_set_folder), 12, Ui.ORANGE, Ui.MEDIUM));
        fix.setGravity(android.view.Gravity.CENTER);
        fix.setBackground(Ui.selectable(Ui.ORANGE_SOFT, 10, 0));
        fix.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
        fix.setFocusable(true);
        fix.setClickable(true);
        fix.setOnClickListener(v -> app.gameFolder.openFolderPicker());
        bar.addView(fix, Ui.lp(-2, Ui.dp(30)));
        app.headerBox.addView(bar, Ui.margins(Ui.lp(-1, -2), gutter(), 0, 16, 8));
    }

    void renderBrowse() {
        LinearLayout h = browseHeader(s(R.string.browse_title), s(R.string.browse_subtitle, Toc.FOREVER_INTERFACE_PREFIX + "01"));
        addHeaderButton(h, s(R.string.action_from_zip), R.drawable.ic_zip, Ui.Kind.GHOST, v -> app.pickZip());

        boolean segmented = app.layout.listDp >= SEGMENTED_DP;
        if (segmented) app.headerBox.addView(segmentedSources());

        // search row
        LinearLayout sr = Ui.hbox(app);
        sr.setPadding(Ui.dp(gutter()), 0, Ui.dp(16), Ui.dp(8));
        if (!segmented) sr.addView(sourcePicker(), Ui.margins(Ui.lp(-2, Ui.dp(42)), 0, 0, 8, 0));
        app.searchField = new EditText(app);
        app.searchField.setSingleLine(true);
        app.searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        app.searchField.setTextColor(Ui.TEXT);
        app.searchField.setHintTextColor(Ui.TEXT3);
        app.searchField.setHint(source == MainActivity.SRC_GITHUB ? s(R.string.browse_hint_github) : s(R.string.browse_hint_search));
        app.searchField.setText(queries[source]);
        app.searchField.setSelection(app.searchField.getText().length());
        app.searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        app.searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | (source == MainActivity.SRC_GITHUB ? InputType.TYPE_TEXT_VARIATION_URI : 0));
        android.graphics.drawable.StateListDrawable fbg = new android.graphics.drawable.StateListDrawable();
        fbg.addState(new int[]{android.R.attr.state_focused}, Ui.round(Ui.SURFACE2, 12, Ui.FOCUS, 1.5f));
        fbg.addState(new int[]{}, Ui.round(Ui.SURFACE2, 12, Ui.STROKE, 1f));
        app.searchField.setBackground(fbg);
        android.graphics.drawable.Drawable sIcon = app.getDrawable(R.drawable.ic_search).mutate();
        sIcon.setTint(Ui.TEXT3);
        sIcon.setBounds(0, 0, Ui.dp(20), Ui.dp(20));
        app.searchField.setCompoundDrawables(sIcon, null, null, null);
        app.searchField.setCompoundDrawablePadding(Ui.dp(10));
        app.searchField.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
        final int srcNow = source;
        app.searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                queries[srcNow] = e.toString();
                if (pendingSearch != null) app.main.removeCallbacks(pendingSearch);
                pendingSearch = () -> runSearch(srcNow, false);
                app.main.postDelayed(pendingSearch, 250);
            }
        });
        app.searchField.setOnEditorActionListener((v, actionId, ev) -> {
            if (pendingSearch != null) app.main.removeCallbacks(pendingSearch);
            app.hideKeyboard();
            runSearch(srcNow, false);
            return true;
        });
        sr.addView(app.searchField, Ui.lpw(0, Ui.dp(42), 1));
        app.headerBox.addView(sr);

        if (source == MainActivity.SRC_CATALOG) {
            HorizontalScrollView cs = new HorizontalScrollView(app);
            cs.setHorizontalScrollBarEnabled(false);
            Ui.scrollHint(cs);
            LinearLayout cats = Ui.hbox(app);
            cats.setPadding(Ui.dp(gutter()), 0, Ui.dp(16), Ui.dp(8));
            List<String> all = new ArrayList<>();
            all.add(null);
            all.addAll(Catalog.categories());
            for (final String c : all) {
                boolean on = c == null ? catalogCategory == null : c.equals(catalogCategory);
                TextView chip = Ui.oneLine(Ui.text(app, c == null ? s(R.string.browse_cat_all) : c, 12, on ? Ui.TEXT : Ui.TEXT2, Ui.MEDIUM));
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setBackground(Ui.selectable(on ? Ui.SURFACE3 : 0x00000000, 14, on ? 0 : Ui.STROKE));
                chip.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
                chip.setFocusable(true);
                chip.setClickable(true);
                chip.setOnClickListener(v -> { catalogCategory = c; app.selected = null; app.render(); });
                cats.addView(chip, Ui.margins(Ui.lp(-2, Ui.dp(28)), 0, 0, 6, 0));
            }
            cs.addView(cats);
            app.headerBox.addView(cs);
        }

        if (app.store.gameRoot() == null || !app.store.gameReady()) gameFolderBanner();

        renderResults();
    }

    private List<Model.Item> catalogFiltered() {
        String q = queries[MainActivity.SRC_CATALOG].trim().toLowerCase(Locale.ROOT);
        List<Model.Item> out = new ArrayList<>();
        for (Model.Item it : Catalog.all()) {
            if (catalogCategory != null && !catalogCategory.equals(it.category)) continue;
            if (!q.isEmpty() && !(it.name.toLowerCase(Locale.ROOT).contains(q) || it.summary.toLowerCase(Locale.ROOT).contains(q)
                    || it.author.toLowerCase(Locale.ROOT).contains(q))) continue;
            out.add(it);
        }
        return out;
    }

    private void renderResults() {
        app.bodyBox.removeAllViews();
        app.currentList = null;
        app.currentAdapter = null;
        final int src = source;
        final List<Model.Item> list;
        if (src == MainActivity.SRC_CATALOG) {
            list = catalogFiltered();
        } else {
            List<Model.Item> r = app.results.get(src);
            list = r == null ? new ArrayList<Model.Item>() : r;
        }
        if (list.isEmpty()) {
            if (loading.contains(src)) {
                app.bodyBox.addView(centerSpinner());
            } else if (resultErrors.get(src) != null) {
                app.bodyBox.addView(emptyState(R.drawable.ic_warning, s(R.string.load_failed), resultErrors.get(src),
                        Ui.button(app, s(R.string.action_retry), R.drawable.ic_refresh, Ui.Kind.PRIMARY, v -> runSearch(src, false))));
            } else if (src == MainActivity.SRC_GITHUB) {
                app.bodyBox.addView(emptyState(R.drawable.ic_link, s(R.string.github_empty_title),
                        s(R.string.github_empty_text), new View[0]));
            } else {
                app.bodyBox.addView(emptyState(R.drawable.ic_search, s(R.string.nothing_found), s(R.string.nothing_found_text), new View[0]));
            }
            return;
        }
        ListView lv = makeList();
        if (src == MainActivity.SRC_WOWI) {
            TextView note = Ui.text(app, s(R.string.wowi_note), 12, Ui.TEXT3, Ui.REGULAR);
            note.setPadding(Ui.dp(4), Ui.dp(2), Ui.dp(4), Ui.dp(8));
            lv.addHeaderView(note, null, false);
        }
        app.currentAdapter = new BaseAdapter() {
            @Override public int getCount() { return list.size(); }
            @Override public Object getItem(int p) { return list.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) {
                if (p == list.size() - 1 && Boolean.TRUE.equals(hasMore.get(src)) && !loading.contains(src)) {
                    app.main.post(() -> runSearch(src, true));
                }
                return itemRow(list.get(p));
            }
        };
        lv.setAdapter(app.currentAdapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Object o = parent.getItemAtPosition(position);
            if (o instanceof Model.Item) app.detail.select(o);
        });
        app.bodyBox.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        app.currentList = lv;
    }

    private View itemRow(final Model.Item it) {
        boolean active = it == app.selected || (app.selected instanceof Model.Item && ((Model.Item) app.selected).key().equals(it.key()));
        LinearLayout row = rowShell(active);
        if (active) row.setBackground(activeRowBackground());
        row.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        row.setMinimumHeight(Ui.dp(60));
        row.addView(avatar(it.iconUrl, it.name, 40));
        LinearLayout mid = Ui.vbox(app);
        mid.addView(Ui.oneLine(Ui.text(app, it.name, 14, Ui.TEXT, Ui.MEDIUM)), Ui.lp(-1, -2));
        StringBuilder meta = new StringBuilder();
        if (!it.author.isEmpty()) meta.append(it.author);
        if (it.downloads > 0) { if (meta.length() > 0) meta.append(" · "); meta.append("↓ ").append(Ui.formatCount(it.downloads)); }
        if (source == MainActivity.SRC_CATALOG && !it.category.isEmpty()) { if (meta.length() > 0) meta.append(" · "); meta.append(it.category); }
        if (meta.length() == 0 && !it.summary.isEmpty()) meta.append(it.summary);
        if (meta.length() > 0) mid.addView(Ui.oneLine(Ui.text(app, meta.toString(), 11.5f, Ui.TEXT3, Ui.REGULAR)), Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        Model.Entry e = app.store.entry(it.key());
        if (app.busy.containsKey(it.key())) {
            row.addView(Ui.pill(app, s(R.string.pill_installing), Ui.ACCENT, Ui.ACCENT_SOFT));
        } else if (e != null) {
            row.addView(installedPill());
        } else if (app.layout.listDp < ROW_LABELS_DP) {
            LinearLayout b = Ui.button(app, null, R.drawable.ic_download, Ui.Kind.SECONDARY, v -> app.manager.install(it, null));
            b.setBackground(Ui.selectable(Ui.SURFACE3, 18, 0));
            b.setPadding(0, 0, 0, 0);
            b.setMinimumHeight(0);
            b.setFocusable(false);
            b.setContentDescription(s(R.string.action_install));
            b.setTooltipText(s(R.string.action_install));
            row.addView(b, Ui.lp(Ui.dp(36), Ui.dp(36)));
        } else {
            LinearLayout b = Ui.button(app, s(R.string.action_install), R.drawable.ic_download, Ui.Kind.SECONDARY, v -> app.manager.install(it, null));
            b.setFocusable(false);
            b.setMinimumHeight(Ui.dp(36));
            b.setPadding(Ui.dp(11), 0, Ui.dp(14), 0);
            row.addView(b, Ui.lp(-2, Ui.dp(36)));
        }
        return row;
    }

    /** Selected row: raised surface with a gold edge on the left; the gamepad ring still wins. */
    private android.graphics.drawable.Drawable activeRowBackground() {
        android.graphics.drawable.LayerDrawable edge = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                Ui.round(Ui.ACCENT, 14), Ui.round(Ui.SURFACE2, 14, Ui.STROKE, 1f)});
        edge.setLayerInset(1, Ui.dp(3), 0, 0, 0);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_selected}, Ui.round(Ui.SURFACE2, 14, Ui.FOCUS, 2f));
        s.addState(new int[]{android.R.attr.state_pressed}, Ui.round(Ui.SURFACE3, 14));
        s.addState(new int[]{}, edge);
        return s;
    }

    private View installedPill() {
        LinearLayout p = Ui.hbox(app);
        p.setBackground(Ui.round(Ui.GREEN_SOFT, 12));
        p.setPadding(Ui.dp(8), 0, Ui.dp(9), 0);
        p.setMinimumHeight(Ui.dp(24));
        p.addView(Ui.icon(app, R.drawable.ic_check, Ui.GREEN, 13));
        p.addView(Ui.oneLine(Ui.text(app, s(R.string.pill_installed), 11, Ui.GREEN, Ui.MEDIUM)), Ui.margins(Ui.lp(-2, -2), 5, 0, 0, 0));
        return p;
    }

    void ensureResults(boolean force) {
        if (source == MainActivity.SRC_CATALOG) return;
        if (force || (app.results.get(source) == null && !loading.contains(source))) runSearch(source, false);
    }

    private void runSearch(final int src, final boolean more) {
        if (src == MainActivity.SRC_CATALOG) { if (app.tab == MainActivity.TAB_BROWSE && source == src) renderResults(); return; }
        if (more && loading.contains(src)) return;
        final String q = queries[src];
        final int gen = ++searchGen[src];
        final List<Model.Item> prev = app.results.get(src);
        final int offset = more && prev != null ? prev.size() : 0;
        loading.add(src);
        resultErrors.remove(src);
        if (!more) { app.results.remove(src); if (app.tab == MainActivity.TAB_BROWSE && source == src) renderResults(); }
        MainActivity.net.execute(() -> {
            List<Model.Item> got = null;
            String err = null;
            boolean moreAvail = false;
            try {
                if (src == MainActivity.SRC_WOWI) {
                    got = Sources.Wowi.search(q, offset, 60);
                    moreAvail = got.size() >= 60;
                } else if (src == MainActivity.SRC_GITHUB) {
                    String repo = q.contains("/") ? Sources.GitHub.normalize(q) : null;
                    if (repo != null) {
                        // A pasted owner/repo or link: its index entry if listed, else the repository itself.
                        got = new ArrayList<>();
                        if (!more) {
                            try { Index.items(false); } catch (IOException ignored) {}
                            Model.Item hit = Index.item(repo);
                            got.add(hit != null ? hit : Sources.GitHub.info(repo));
                        }
                    } else {
                        got = Index.search(q, offset, 60);
                        moreAvail = got.size() >= 60;
                    }
                }
            } catch (Exception e) {
                err = MainActivity.friendly(e);
            }
            final List<Model.Item> fGot = got;
            final String fErr = err;
            final boolean fMore = moreAvail;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                if (gen != searchGen[src]) return;   // a newer search of this source owns "loading"
                loading.remove(src);
                if (fErr != null) {
                    if (!more) resultErrors.put(src, fErr); else app.snack(fErr);
                } else {
                    List<Model.Item> base = more && app.results.get(src) != null ? new ArrayList<>(app.results.get(src)) : new ArrayList<Model.Item>();
                    base.addAll(fGot);
                    app.results.put(src, base);
                    hasMore.put(src, fMore);
                    if (src == MainActivity.SRC_GITHUB && !more && fGot.size() == 1 && q.contains("/") && app.layout.docked) app.selected = fGot.get(0);
                }
                if (app.tab == MainActivity.TAB_BROWSE && source == src) {
                    if (more && app.currentAdapter != null && fErr == null) {
                        // keep the list and its scroll position
                        renderResultsKeepingScroll();
                    } else {
                        renderResults();
                        app.detail.renderDetail();
                    }
                }
            });
        });
    }

    private void renderResultsKeepingScroll() {
        int pos = app.currentList == null ? 0 : app.currentList.getFirstVisiblePosition();
        View first = app.currentList == null ? null : app.currentList.getChildAt(0);
        int top = first == null ? 0 : first.getTop();
        renderResults();
        if (app.currentList != null) app.currentList.setSelectionFromTop(pos, top);
    }
}
