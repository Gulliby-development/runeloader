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

    BrowseScreen(MainActivity app) { super(app); }

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

        // search row
        LinearLayout sr = Ui.hbox(app);
        sr.setPadding(Ui.dp(gutter()), 0, Ui.dp(16), Ui.dp(8));
        app.searchField = new EditText(app);
        app.searchField.setSingleLine(true);
        app.searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        app.searchField.setTextColor(Ui.TEXT);
        app.searchField.setHintTextColor(Ui.TEXT3);
        app.searchField.setHint(s(R.string.browse_hint_github));
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
        sr.addView(app.searchField, Ui.lpw(0, Ui.dp(42), 1));
        app.headerBox.addView(sr);

        if (app.store.gameRoot() == null || !app.store.gameReady()) gameFolderBanner();

        renderResults();
    }

    private void renderResults() {
        app.bodyBox.removeAllViews();
        app.currentList = null;
        app.currentAdapter = null;
        List<Model.Item> r = app.results.get(RESULTS);
        final List<Model.Item> list = r == null ? new ArrayList<Model.Item>() : r;
        if (list.isEmpty()) {
            if (loading) {
                app.bodyBox.addView(centerSpinner());
            } else if (resultError != null) {
                app.bodyBox.addView(emptyState(R.drawable.ic_warning, s(R.string.load_failed), resultError,
                        Ui.button(app, s(R.string.action_retry), R.drawable.ic_refresh, Ui.Kind.PRIMARY, v -> runSearch(false))));
            } else {
                app.bodyBox.addView(emptyState(R.drawable.ic_search, s(R.string.nothing_found), s(R.string.github_empty_text), new View[0]));
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
        mid.addView(Ui.oneLine(Ui.text(app, it.name, 14, Ui.TEXT, Ui.MEDIUM)), Ui.lp(-1, -2));
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
        if (force || (app.results.get(RESULTS) == null && !loading)) runSearch(false);
    }

    private void runSearch(final boolean more) {
        if (more && loading) return;
        final String q = query;
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
                if (repo != null) {
                    // A pasted owner/repo or link: its index entry if listed, else the repository itself.
                    if (!more) {
                        try { Index.items(false); } catch (IOException ignored) {}
                        Model.Item hit = Index.item(repo);
                        got.add(hit != null ? hit : Sources.GitHub.info(repo));
                    }
                } else {
                    Search.Page page = Search.page(q, offset, PAGE);
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

    private void renderResultsKeepingScroll() {
        int pos = app.currentList == null ? 0 : app.currentList.getFirstVisiblePosition();
        View first = app.currentList == null ? null : app.currentList.getChildAt(0);
        int top = first == null ? 0 : first.getTop();
        renderResults();
        if (app.currentList != null) app.currentList.setSelectionFromTop(pos, top);
    }
}
