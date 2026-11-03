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

/** The browse tab: catalog, CurseForge, WoWInterface and GitHub search. */
final class BrowseScreen extends Screen {

    int source = MainActivity.SRC_CATALOG;
    private final String[] queries = {"", "", "", ""};
    private String catalogCategory = null;
    private final Map<Integer, String> resultErrors = new HashMap<>();
    private final Set<Integer> loading = new HashSet<>();
    private final Map<Integer, Boolean> hasMore = new HashMap<>();
    private final int[] searchGen = new int[4];
    private Runnable pendingSearch;

    BrowseScreen(MainActivity app) { super(app); }

    void renderBrowse() {
        LinearLayout h = header(s(R.string.browse_title), s(R.string.browse_subtitle, Toc.FOREVER_INTERFACE_PREFIX + "01"));
        addHeaderButton(h, s(R.string.action_from_zip), R.drawable.ic_zip, Ui.Kind.GHOST, v -> app.pickZip());

        // source segmented control
        HorizontalScrollView hs = new HorizontalScrollView(app);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout seg = Ui.hbox(app);
        seg.setPadding(Ui.dp(app.landscape ? 20 : 16), 0, Ui.dp(16), Ui.dp(8));
        String[] names = {s(R.string.browse_src_catalog), "CurseForge", "WoWInterface", "GitHub"};
        int[] icons = {R.drawable.ic_star, R.drawable.ic_download, R.drawable.ic_apps, R.drawable.ic_link};
        for (int i = 0; i < names.length; i++) {
            final int s = i;
            boolean on = i == source;
            LinearLayout chip = Ui.hbox(app);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setBackground(Ui.selectable(on ? Ui.ACCENT : Ui.SURFACE2, 20, on ? 0 : Ui.STROKE));
            chip.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(14), Ui.dp(7));
            chip.addView(Ui.icon(app, icons[i], on ? Ui.ACCENT_DARK : Ui.TEXT2, 16));
            TextView t = Ui.text(app, names[i], 13.5f, on ? Ui.ACCENT_DARK : Ui.TEXT, Ui.MEDIUM);
            chip.addView(t, Ui.margins(Ui.lp(-2, -2), 7, 0, 0, 0));
            chip.setOnClickListener(v -> {
                if (source == s) return;
                source = s;
                app.selected = null;
                app.render();
                ensureResults(false);
            });
            seg.addView(chip, Ui.margins(Ui.lp(-2, -2), 0, 0, 8, 0));
        }
        hs.addView(seg);
        app.headerBox.addView(hs);

        // search row
        LinearLayout sr = Ui.hbox(app);
        sr.setPadding(Ui.dp(app.landscape ? 20 : 16), 0, Ui.dp(16), Ui.dp(10));
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
        fbg.addState(new int[]{android.R.attr.state_focused}, Ui.round(Ui.SURFACE2, 12, Ui.ACCENT, 1.5f));
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
                if (srcNow == MainActivity.SRC_GITHUB) return;
                if (pendingSearch != null) app.main.removeCallbacks(pendingSearch);
                pendingSearch = () -> runSearch(srcNow, false);
                app.main.postDelayed(pendingSearch, srcNow == MainActivity.SRC_CURSE ? 650 : 250);
            }
        });
        app.searchField.setOnEditorActionListener((v, actionId, ev) -> {
            if (pendingSearch != null) app.main.removeCallbacks(pendingSearch);
            app.hideKeyboard();
            runSearch(srcNow, false);
            return true;
        });
        sr.addView(app.searchField, Ui.lpw(0, Ui.dp(42), 1));
        if (source == MainActivity.SRC_GITHUB) {
            LinearLayout go = Ui.button(app, s(R.string.action_find), R.drawable.ic_search, Ui.Kind.PRIMARY, v -> { app.hideKeyboard(); runSearch(MainActivity.SRC_GITHUB, false); });
            sr.addView(go, Ui.margins(Ui.lp(-2, Ui.dp(42)), 8, 0, 0, 0));
        }
        app.headerBox.addView(sr);

        if (source == MainActivity.SRC_CATALOG) {
            HorizontalScrollView cs = new HorizontalScrollView(app);
            cs.setHorizontalScrollBarEnabled(false);
            LinearLayout cats = Ui.hbox(app);
            cats.setPadding(Ui.dp(app.landscape ? 20 : 16), 0, Ui.dp(16), Ui.dp(8));
            List<String> all = new ArrayList<>();
            all.add(null);
            all.addAll(Catalog.categories());
            for (final String c : all) {
                boolean on = c == null ? catalogCategory == null : c.equals(catalogCategory);
                TextView chip = Ui.text(app, c == null ? s(R.string.browse_cat_all) : c, 12.5f, on ? Ui.TEXT : Ui.TEXT2, Ui.MEDIUM);
                chip.setBackground(Ui.selectable(on ? Ui.SURFACE3 : 0x00000000, 16, on ? 0 : Ui.STROKE));
                chip.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
                chip.setFocusable(true);
                chip.setClickable(true);
                chip.setOnClickListener(v -> { catalogCategory = c; app.selected = null; app.render(); });
                cats.addView(chip, Ui.margins(Ui.lp(-2, -2), 0, 0, 6, 0));
            }
            cs.addView(cats);
            app.headerBox.addView(cs);
        }

        if (app.store.gameRoot() == null || !app.store.gameReady()) {
            TextView warn = Ui.text(app, s(R.string.browse_no_game), 12.5f, Ui.ORANGE, Ui.REGULAR);
            warn.setBackground(Ui.round(Ui.ORANGE_SOFT, 10));
            warn.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
            app.headerBox.addView(warn, Ui.margins(Ui.lp(-1, -2), app.landscape ? 20 : 16, 0, 16, 8));
        }

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
        if (src == MainActivity.SRC_CURSE && !Sources.hasCurseKey()) {
            app.bodyBox.addView(emptyState(R.drawable.ic_key, s(R.string.curse_connect_title),
                    s(R.string.curse_connect_text),
                    Ui.button(app, s(R.string.action_enter_key), R.drawable.ic_key, Ui.Kind.PRIMARY, v -> app.switchTab(MainActivity.TAB_SETTINGS)),
                    Ui.button(app, s(R.string.action_open_site), R.drawable.ic_open, Ui.Kind.SECONDARY, v -> app.openUrl("https://www.curseforge.com/wow/search?class=addons&gameVersionTypeId=" + Sources.CURSE_FOREVER_TYPE))));
            return;
        }
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
        LinearLayout row = rowShell(it == app.selected || (app.selected instanceof Model.Item && ((Model.Item) app.selected).key().equals(it.key())));
        row.addView(avatar(it.iconUrl, it.name, 46));
        LinearLayout mid = Ui.vbox(app);
        LinearLayout tl = Ui.hbox(app);
        tl.addView(Ui.oneLine(Ui.text(app, it.name, 15, Ui.TEXT, Ui.MEDIUM)), Ui.lpw(0, -2, 1));
        mid.addView(tl);
        StringBuilder meta = new StringBuilder();
        if (!it.author.isEmpty()) meta.append(it.author);
        if (it.downloads > 0) { if (meta.length() > 0) meta.append("  ·  "); meta.append("↓ ").append(Ui.formatCount(it.downloads)); }
        if (source == MainActivity.SRC_CATALOG && !it.category.isEmpty()) { if (meta.length() > 0) meta.append("  ·  "); meta.append(it.category); }
        if (meta.length() > 0) mid.addView(Ui.oneLine(Ui.text(app, meta.toString(), 12, Ui.TEXT3, Ui.REGULAR)), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
        if (!it.summary.isEmpty()) {
            TextView s = Ui.text(app, it.summary, 12.5f, Ui.TEXT2, Ui.REGULAR);
            s.setMaxLines(app.landscape ? 1 : 2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(s, Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
        }
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        Model.Entry e = app.store.entry(it.key());
        if (app.busy.containsKey(it.key())) {
            row.addView(Ui.pill(app, s(R.string.pill_installing), Ui.ACCENT, Ui.ACCENT_SOFT));
        } else if (e != null) {
            row.addView(Ui.pill(app, s(R.string.pill_installed), Ui.GREEN, Ui.GREEN_SOFT));
        } else {
            LinearLayout b = Ui.button(app, s(R.string.action_install), R.drawable.ic_download, Ui.Kind.SECONDARY, v -> app.manager.install(it, null));
            b.setFocusable(false);
            b.setMinimumHeight(Ui.dp(34));
            b.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            row.addView(b);
        }
        return row;
    }

    void ensureResults(boolean force) {
        if (source == MainActivity.SRC_CATALOG || source == MainActivity.SRC_GITHUB) return;
        if (source == MainActivity.SRC_CURSE && !Sources.hasCurseKey()) return;
        if (force || (app.results.get(source) == null && !loading.contains(source))) runSearch(source, false);
    }

    private void runSearch(final int src, final boolean more) {
        if (src == MainActivity.SRC_CATALOG) { if (app.tab == MainActivity.TAB_BROWSE && source == src) renderResults(); return; }
        if (src == MainActivity.SRC_CURSE && !Sources.hasCurseKey()) return;
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
                if (src == MainActivity.SRC_CURSE) {
                    got = Sources.Curse.search(q, offset);
                    moreAvail = got.size() >= 40 && offset < 9000;
                } else if (src == MainActivity.SRC_WOWI) {
                    got = Sources.Wowi.search(q, offset, 60);
                    moreAvail = got.size() >= 60;
                } else if (src == MainActivity.SRC_GITHUB) {
                    String repo = Sources.GitHub.normalize(q);
                    if (repo == null) throw new IOException(s(R.string.github_repo_needed));
                    got = new ArrayList<>();
                    got.add(Sources.GitHub.info(repo));
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
                    if (src == MainActivity.SRC_GITHUB && fGot.size() == 1 && app.landscape) app.selected = fGot.get(0);
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
