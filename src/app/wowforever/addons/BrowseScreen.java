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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The browse tab: one search over every source (see Search); the source shows as a tag on each row. */
final class BrowseScreen extends Screen {

    /** Key of the browse results in app.results. */
    static final int RESULTS = 0;
    private static final int PAGE = 60;
    private String query = "";
    private String resultError;
    private boolean loading;
    private boolean hasMore;
    private int searchGen;
    private Runnable pendingSearch;
    static final int MODE_POPULAR = 0, MODE_CATEGORIES = 1, MODE_FAVORITES = 2;
    /** Categories mode: the grid, then one category's list. Popular mode lists everything; Favorites the starred addons. */
    private int mode = MODE_POPULAR;
    private boolean categoriesMode;
    /** The open category (a Categories key), or null for the grid. */
    private String category;
    private java.util.Map<String, Integer> counts;
    private boolean countsLoading;

    BrowseScreen(MainActivity app) { super(app); }

    /** Keeps the mode, category and query over a recreate (a language change). */
    void save(android.os.Bundle out) {
        out.putInt("browseMode", mode);
        out.putString("browseCategory", category);
        out.putString("browseQuery", query);
    }

    void restore(android.os.Bundle in) {
        mode = in.getInt("browseMode", MODE_POPULAR);
        categoriesMode = mode == MODE_CATEGORIES;
        category = in.getString("browseCategory");
        query = in.getString("browseQuery", "");
    }

    /** Below this list width the row actions drop their labels. */
    private static final int ROW_LABELS_DP = 480;

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

        modeSwitch();

        // search row
        LinearLayout sr = Ui.hbox(app);
        sr.setPadding(Ui.dp(gutter()), 0, Ui.dp(16), Ui.dp(8));
        app.searchField = new EditText(app);
        app.searchField.setSingleLine(true);
        app.searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        app.searchField.setTextColor(Ui.TEXT);
        app.searchField.setHintTextColor(Ui.TEXT3);
        app.searchField.setId(View.generateViewId());
        // Inside a category the back button takes width: a short hint keeps it whole at 4:3.
        app.searchField.setHint(s(mode == MODE_FAVORITES ? R.string.browse_hint_favorites
                : categoriesMode && category != null ? R.string.browse_hint_category : R.string.browse_hint_github));
        app.searchField.setText(query);
        app.searchField.setSelection(app.searchField.getText().length());
        app.searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        app.searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
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
        app.searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                query = e.toString();
                if (pendingSearch != null) app.main.removeCallbacks(pendingSearch);
                pendingSearch = () -> runSearch(false);
                app.main.postDelayed(pendingSearch, 250);
            }
        });
        app.searchField.setOnEditorActionListener((v, actionId, ev) -> {
            if (pendingSearch != null) app.main.removeCallbacks(pendingSearch);
            app.hideKeyboard();
            runSearch(false);
            return true;
        });
        if (categoriesMode && category != null) sr.addView(categoryBack(), Ui.margins(Ui.lp(-2, Ui.dp(42)), 0, 0, 8, 0));
        sr.addView(app.searchField, Ui.lpw(0, Ui.dp(42), 1));
        app.headerBox.addView(sr);

        if (app.store.gameRoot() == null || !app.store.gameReady()) gameFolderBanner();

        renderResults();
    }

    /** The category grid shows in categories mode until a category is open or a query is typed. */
    private boolean showGrid() {
        return categoriesMode && category == null && query.trim().isEmpty();
    }

    /** Below this list width the mode switch drops its icons, so three labels fit. */
    private static final int SEGMENT_ICONS_DP = 420;

    /** Popular / Categories / Favorites, a three-segment switch under the header. */
    private void modeSwitch() {
        LinearLayout box = Ui.hbox(app);
        box.setBackground(Ui.round(Ui.SURFACE, 11, Ui.SURFACE3, 1f));
        box.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        box.addView(segment(R.string.browse_mode_popular, R.drawable.ic_trending, MODE_POPULAR), Ui.lpw(0, Ui.dp(32), 1));
        box.addView(segment(R.string.browse_mode_categories, R.drawable.ic_apps, MODE_CATEGORIES), Ui.margins(Ui.lpw(0, Ui.dp(32), 1), 2, 0, 0, 0));
        box.addView(segment(R.string.browse_mode_favorites, R.drawable.ic_star, MODE_FAVORITES), Ui.margins(Ui.lpw(0, Ui.dp(32), 1), 2, 0, 0, 0));
        app.headerBox.addView(box, Ui.margins(Ui.lp(-1, -2), gutter(), 0, 16, 8));
    }

    private View segment(int label, int icon, final int segMode) {
        boolean on = mode == segMode;
        LinearLayout seg = Ui.hbox(app);
        seg.setGravity(android.view.Gravity.CENTER);
        seg.setBackground(Ui.selectable(on ? Ui.SURFACE3 : 0x00000000, 8, 0));
        seg.setPadding(Ui.dp(6), 0, Ui.dp(6), 0);
        seg.setFocusable(true);
        seg.setClickable(true);
        int fg = on ? Ui.TEXT : Ui.TEXT2;
        boolean icons = app.layout.listDp >= SEGMENT_ICONS_DP;
        if (icons) seg.addView(Ui.icon(app, icon, segMode == MODE_FAVORITES && on ? Ui.ACCENT : fg, 15));
        seg.addView(Ui.oneLine(Ui.text(app, s(label), 13, fg, on ? Ui.MEDIUM : Ui.REGULAR)), Ui.margins(Ui.lp(-2, -2), icons ? 6 : 0, 0, 0, 0));
        seg.setContentDescription(s(label));
        seg.setOnClickListener(v -> setMode(segMode, null));
        return seg;
    }

    /** Back to the grid, named after the open category; the search then looks inside it. */
    private View categoryBack() {
        LinearLayout b = Ui.button(app, s(Categories.nameRes(category)), R.drawable.ic_back, Ui.Kind.GHOST, v -> {
            query = "";   // back means the grid, not a search over everything
            setMode(MODE_CATEGORIES, null);
        });
        b.setContentDescription(s(R.string.browse_all_categories));
        b.setTooltipText(s(R.string.browse_all_categories));
        b.setPadding(Ui.dp(10), 0, Ui.dp(14), 0);
        return b;
    }

    private void setMode(int m, String cat) {
        if (m == mode && (cat == null ? category == null : cat.equals(category))) return;
        mode = m;
        categoriesMode = m == MODE_CATEGORIES;
        category = cat;
        app.selected = null;
        app.results.remove(RESULTS);
        app.render();
        if (!showGrid()) runSearch(false);
    }

    private void renderGrid() {
        if (counts == null) loadCounts();
        android.widget.ScrollView sv = new android.widget.ScrollView(app);
        LinearLayout col = Ui.vbox(app);
        col.setPadding(Ui.dp(gutter()), Ui.dp(2), Ui.dp(16), Ui.dp(96));
        sv.addView(col);
        int cols = app.layout.listDp >= 600 ? 4 : app.layout.listDp >= 400 ? 3 : 2;
        String[] keys = Categories.order(gamepadConnected());
        LinearLayout row = null;
        for (int i = 0; i < keys.length; i++) {
            if (i % cols == 0) {
                row = Ui.hbox(app);
                row.setBaselineAligned(false);
                col.addView(row, Ui.margins(Ui.lp(-1, -2), 0, i == 0 ? 0 : 8, 0, 0));
            }
            View tile = categoryTile(keys[i]);
            // DPAD Up from the first row goes to the search field, not past it to the switch.
            if (i < cols && app.searchField != null) tile.setNextFocusUpId(app.searchField.getId());
            row.addView(tile, Ui.margins(Ui.lpw(0, -2, 1), i % cols == 0 ? 0 : 8, 0, 0, 0));
        }
        // Keep the last row's tiles the same width as the others.
        for (int i = keys.length % cols; i > 0 && i < cols; i++)
            row.addView(new View(app), Ui.margins(Ui.lpw(0, 0, 1), 8, 0, 0, 0));
        app.bodyBox.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    /** A game controller is attached, or built in as on Android handhelds. */
    private static boolean gamepadConnected() {
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice d = android.view.InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int s = d.getSources();
            if ((s & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
                    || (s & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) return true;
        }
        return false;
    }

    private View categoryTile(final String key) {
        boolean other = Categories.OTHER.equals(key);
        // Icon above the text, so long names ("Quests and leveling") get the whole tile width.
        LinearLayout t = Ui.vbox(app);
        t.setBackground(Ui.selectable(other ? 0x00000000 : Ui.SURFACE, 14, other ? Ui.STROKE : Ui.SURFACE3));
        t.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        FrameLayout iconBox = new FrameLayout(app);
        iconBox.setBackground(Ui.round(Ui.SURFACE3, 10));
        android.widget.ImageView iv = new android.widget.ImageView(app);
        iv.setImageResource(Categories.iconRes(key));
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        iconBox.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        t.addView(iconBox, Ui.lp(Ui.dp(36), Ui.dp(36)));
        LinearLayout txt = Ui.vbox(app);
        String name = s(Categories.nameRes(key));
        TextView title = Ui.text(app, name, 13.5f, Ui.TEXT, Ui.MEDIUM);
        title.setLines(2);   // the same height for every tile in a row
        txt.addView(title, Ui.lp(-1, -2));
        Integer n = counts == null ? null : counts.get(key);
        String sub = n != null ? q(R.plurals.addons_count, n) : countsLoading ? "…" : " ";
        txt.addView(Ui.oneLine(Ui.text(app, sub, 11.5f, Ui.TEXT3, Ui.REGULAR)), Ui.margins(Ui.lp(-1, -2), 0, 2, 0, 0));
        t.addView(txt, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        t.setContentDescription(n == null ? name : name + ", " + sub);
        t.setOnClickListener(v -> setMode(MODE_CATEGORIES, key));
        return t;
    }

    private void loadCounts() {
        if (countsLoading) return;
        countsLoading = true;
        MainActivity.net.execute(() -> {
            java.util.Map<String, Integer> got = null;
            String err = null;
            try {
                got = Search.counts();
            } catch (Exception e) {
                err = MainActivity.friendly(e);
            }
            final java.util.Map<String, Integer> fGot = got;
            final String fErr = err;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                countsLoading = false;
                if (fGot != null) counts = fGot; else app.snack(fErr);
                if (app.tab == MainActivity.TAB_BROWSE && showGrid()) renderResults();
            });
        });
    }

    private void renderResults() {
        app.bodyBox.removeAllViews();
        app.currentList = null;
        app.currentAdapter = null;
        if (showGrid()) { renderGrid(); return; }
        List<Model.Item> r = app.results.get(RESULTS);
        final List<Model.Item> list = r == null ? new ArrayList<Model.Item>() : r;
        if (list.isEmpty()) {
            if (loading) {
                app.bodyBox.addView(centerSpinner());
            } else if (resultError != null) {
                app.bodyBox.addView(emptyState(R.drawable.ic_warning, s(R.string.load_failed), resultError,
                        Ui.button(app, s(R.string.action_retry), R.drawable.ic_refresh, Ui.Kind.PRIMARY, v -> runSearch(false))));
            } else if (mode == MODE_FAVORITES && query.trim().isEmpty()) {
                app.bodyBox.addView(emptyState(R.drawable.ic_star_outline, s(R.string.favorites_empty_title), s(R.string.favorites_empty_text), new View[0]));
            } else if (mode == MODE_FAVORITES) {
                app.bodyBox.addView(emptyState(R.drawable.ic_search, s(R.string.nothing_found), s(R.string.favorites_no_match), new View[0]));
            } else {
                app.bodyBox.addView(emptyState(R.drawable.ic_search, s(R.string.nothing_found),
                        s(categoriesMode && category != null ? R.string.category_empty_text : R.string.github_empty_text), new View[0]));
            }
            return;
        }
        ListView lv = makeList();
        app.currentAdapter = new BaseAdapter() {
            @Override public int getCount() { return list.size(); }
            @Override public Object getItem(int p) { return list.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) {
                if (p == list.size() - 1 && hasMore && !loading) app.main.post(() -> runSearch(true));
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
        LinearLayout nameRow = Ui.hbox(app);
        nameRow.addView(Ui.oneLine(Ui.text(app, it.name, 14, Ui.TEXT, Ui.MEDIUM)), Ui.lpw(0, -2, 1));
        if (app.store.isFavorite(it.key())) {
            View star = Ui.icon(app, R.drawable.ic_star, Ui.ACCENT, 14);
            star.setContentDescription(s(R.string.favorite_on));
            nameRow.addView(star, Ui.margins(Ui.lp(Ui.dp(14), Ui.dp(14)), 6, 0, 0, 0));
        }
        mid.addView(nameRow, Ui.lp(-1, -2));
        StringBuilder meta = new StringBuilder();
        // The numbers behind the popularity order come first, so a long author list cannot cut them off.
        if (it.stars > 0) meta.append("★ ").append(Ui.formatCount(it.stars));
        if (it.downloads > 0) { if (meta.length() > 0) meta.append(" · "); meta.append("↓ ").append(Ui.formatCount(it.downloads)); }
        if (!it.author.isEmpty()) { if (meta.length() > 0) meta.append(" · "); meta.append(it.author); }
        if (meta.length() == 0 && !it.summary.isEmpty()) meta.append(it.summary);
        // Where the addon comes from, as a tag in front of the details.
        LinearLayout metaRow = Ui.hbox(app);
        TextView tag = Ui.oneLine(Ui.text(app, sourceName(it.source), 10, Ui.TEXT2, Ui.MEDIUM));
        tag.setBackground(Ui.round(Ui.SURFACE3, 6));
        tag.setPadding(Ui.dp(6), Ui.dp(1), Ui.dp(6), Ui.dp(1));
        metaRow.addView(tag, Ui.lp(-2, -2));
        if (it.tags.contains(Categories.GAMEPAD)) {
            TextView pad = Ui.oneLine(Ui.text(app, s(R.string.tag_gamepad), 10, Ui.ACCENT, Ui.MEDIUM));
            pad.setBackground(Ui.round(Ui.ACCENT_SOFT, 6));
            pad.setPadding(Ui.dp(6), Ui.dp(1), Ui.dp(6), Ui.dp(1));
            metaRow.addView(pad, Ui.margins(Ui.lp(-2, -2), 4, 0, 0, 0));
        }
        if (meta.length() > 0) metaRow.addView(Ui.oneLine(Ui.text(app, meta.toString(), 11.5f, Ui.TEXT3, Ui.REGULAR)), Ui.margins(Ui.lpw(0, -2, 1), 6, 0, 0, 0));
        mid.addView(metaRow, Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
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
        if (showGrid()) { if (force) { counts = null; if (app.tab == MainActivity.TAB_BROWSE) renderResults(); } return; }
        if (force || (app.results.get(RESULTS) == null && !loading)) runSearch(false);
    }

    private void runSearch(final boolean more) {
        if (showGrid()) {
            searchGen++;   // drops a search still running for the list
            loading = false;
            if (app.tab == MainActivity.TAB_BROWSE) renderResults();
            return;
        }
        if (more && loading) return;
        final String q = query;
        final String cat = categoriesMode ? category : null;
        final boolean favs = mode == MODE_FAVORITES;
        final int gen = ++searchGen;
        final List<Model.Item> prev = app.results.get(RESULTS);
        final int offset = more && prev != null ? prev.size() : 0;
        loading = true;
        resultError = null;
        if (!more) { app.results.remove(RESULTS); if (app.tab == MainActivity.TAB_BROWSE) renderResults(); }
        MainActivity.net.execute(() -> {
            List<Model.Item> got = new ArrayList<>();
            String err = null, warn = null;
            boolean moreAvail = false;
            try {
                String repo = q.contains("/") ? Sources.GitHub.normalize(q) : null;
                if (favs) {
                    if (!more) got = favorites(q);
                } else if (repo != null) {
                    // A pasted owner/repo or link: its index entry if listed, else the repository itself.
                    if (!more) {
                        try { Index.items(false); } catch (IOException ignored) {}
                        Model.Item hit = Index.item(repo);
                        got.add(hit != null ? hit : Sources.GitHub.info(repo));
                    }
                } else {
                    Search.Page page = Search.page(q, cat, offset, PAGE);
                    got = page.items;
                    moreAvail = page.more;
                    warn = page.warning;
                }
            } catch (Exception e) {
                err = MainActivity.friendly(e);
            }
            final List<Model.Item> fGot = got;
            final String fErr = err, fWarn = warn;
            final boolean fMore = moreAvail;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                if (gen != searchGen) return;   // a newer search owns "loading"
                loading = false;
                if (fErr != null) {
                    if (!more) resultError = fErr; else app.snack(fErr);
                } else {
                    List<Model.Item> base = more && app.results.get(RESULTS) != null ? new ArrayList<>(app.results.get(RESULTS)) : new ArrayList<Model.Item>();
                    base.addAll(fGot);
                    app.results.put(RESULTS, base);
                    hasMore = fMore;
                    if (fWarn != null && !more) app.snack(fWarn);
                    if (!more && fGot.size() == 1 && q.contains("/") && app.layout.docked) app.selected = fGot.get(0);
                }
                if (app.tab == MainActivity.TAB_BROWSE) {
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

    /** The starred addons, with fresh index data when the cached index has them; the query filters by name, author or summary. */
    private List<Model.Item> favorites(String q) {
        Index.loadCached();
        String needle = q.trim().toLowerCase(java.util.Locale.ROOT);
        List<Model.Item> out = new ArrayList<>();
        for (Model.Item f : app.store.favorites()) {
            Model.Item ix = Model.SRC_GITHUB.equals(f.source) ? Index.item(f.id) : null;
            Model.Item it = ix != null ? ix : f;
            if (!needle.isEmpty() && !(it.name + "\n" + it.author + "\n" + it.summary).toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            out.add(it);
        }
        return out;
    }

    /** A star was toggled: the Favorites list drops or gains the addon, other lists redraw the row. */
    void favoritesChanged() {
        if (app.tab != MainActivity.TAB_BROWSE) return;
        if (mode == MODE_FAVORITES) runSearch(false); else app.refreshList();
    }

    private void renderResultsKeepingScroll() {
        int pos = app.currentList == null ? 0 : app.currentList.getFirstVisiblePosition();
        View first = app.currentList == null ? null : app.currentList.getChildAt(0);
        int top = first == null ? 0 : first.getTop();
        renderResults();
        if (app.currentList != null) app.currentList.setSelectionFromTop(pos, top);
    }
}
