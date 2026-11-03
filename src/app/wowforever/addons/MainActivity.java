package app.wowforever.addons;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    private static final int TAB_INSTALLED = 0, TAB_BROWSE = 1, TAB_SETTINGS = 2;
    private static final int SRC_CATALOG = 0, SRC_CURSE = 1, SRC_WOWI = 2, SRC_GITHUB = 3;
    private static final int REQ_ZIP = 41, REQ_PERM = 42;

    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService net = Executors.newFixedThreadPool(4);
    private static final ExecutorService ops = Executors.newSingleThreadExecutor();

    private Store store;

    // ── UI state ──
    private int tab = TAB_INSTALLED;
    private int source = SRC_CATALOG;
    private boolean landscape;
    private final String[] queries = {"", "", "", ""};
    private String catalogCategory = null;
    private final Map<Integer, List<Model.Item>> results = new HashMap<>();
    private final Map<Integer, String> resultErrors = new HashMap<>();
    private final Set<Integer> loading = new HashSet<>();
    private final Map<Integer, Boolean> hasMore = new HashMap<>();
    private final int[] searchGen = new int[4];

    private List<Model.Installed> installed = new ArrayList<>();
    private boolean scanned = false;
    private boolean checking = false;
    private boolean checkedOnce = false;

    private Object selected;                 // Model.Item or Model.Installed
    private boolean portraitDetailOpen = false;
    private final Map<String, String> busy = new LinkedHashMap<>();   // key -> status text
    private final AtomicInteger queued = new AtomicInteger();

    // ── views ──
    private FrameLayout root;
    private LinearLayout headerBox;
    private FrameLayout bodyBox;
    private FrameLayout detailBox;
    private FrameLayout portraitDetail;
    private LinearLayout progressCard;
    private TextView progressTitle, progressText;
    private ProgressBar progressBar;
    private TextView snack;
    private FrameLayout modalLayer;
    private final List<View> navItems = new ArrayList<>();
    private EditText searchField;
    private ListView currentList;
    private BaseAdapter currentAdapter;

    private final Runnable hideSnack = () -> { if (snack != null) snack.animate().alpha(0f).setDuration(220).withEndAction(() -> snack.setVisibility(View.GONE)).start(); };
    private Runnable pendingSearch;

    // ─────────────────────────── lifecycle ───────────────────────────

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.density = getResources().getDisplayMetrics().density;
        ImageLoader.dir = new File(getCacheDir(), "img");
        ImageLoader.dir.mkdirs();
        store = new Store(this);
        if (store.gameRoot() == null && hasStorageAccess()) autoDetect(false);
        store.load();
        buildShell();
        rescan();
    }

    private Boolean lastAccess;

    @Override protected void onResume() {
        super.onResume();
        applyImmersive();
        boolean access = hasStorageAccess();
        if (lastAccess != null && lastAccess != access && root != null) render();
        lastAccess = access;
        if (access && !scanned) {
            if (store.gameRoot() == null) autoDetect(false);
            store.load();
            rescan();
        }
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        Ui.density = getResources().getDisplayMetrics().density;
        buildShell();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
    }

    private void applyImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            if (isLandscape()) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            int flags = isLandscape()
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    : 0;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private boolean isLandscape() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        return m.widthPixels > m.heightPixels;
    }

    @Override public void onBackPressed() {
        if (modalLayer != null && modalLayer.getChildCount() > 0) { closeModal(); return; }
        if (portraitDetailOpen) { closePortraitDetail(); return; }
        if (landscape && selected != null) { select(null); return; }
        if (tab != TAB_INSTALLED) { switchTab(TAB_INSTALLED); return; }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (modalLayer != null && modalLayer.getChildCount() > 0) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) { switchTab((tab + 2) % 3); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) { switchTab((tab + 1) % 3); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_Y && tab == TAB_BROWSE && searchField != null) { searchField.requestFocus(); showKeyboard(searchField); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_X && tab == TAB_INSTALLED) { checkUpdates(true); return true; }
        return super.onKeyDown(keyCode, event);
    }

    // ─────────────────────────── shell ───────────────────────────

    private void buildShell() {
        landscape = isLandscape();
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        navItems.clear();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout mainCol = Ui.vbox(this);
        headerBox = Ui.vbox(this);
        bodyBox = new FrameLayout(this);
        mainCol.addView(headerBox, Ui.lp(-1, -2));
        mainCol.addView(bodyBox, Ui.lpw(-1, 0, 1));

        if (landscape) {
            shell.addView(buildRail(), Ui.lp(Ui.dp(84), -1));
            shell.addView(mainCol, Ui.lpw(0, -1, 1));
            detailBox = new FrameLayout(this);
            GradientDrawable dbg = new GradientDrawable();
            dbg.setColor(Ui.SURFACE);
            detailBox.setBackground(dbg);
            int w = getResources().getDisplayMetrics().widthPixels - Ui.dp(84);
            int dw = Math.max(Ui.dp(290), Math.min(Ui.dp(460), (int) (w * 0.40f)));
            View edge = new View(this);
            edge.setBackgroundColor(Ui.STROKE);
            shell.addView(edge, Ui.lp(Math.max(1, Ui.dp(1)), -1));
            shell.addView(detailBox, Ui.lp(dw, -1));
            portraitDetail = null;
            portraitDetailOpen = false;
        } else {
            shell.addView(mainCol, Ui.lpw(-1, 0, 1));
            shell.addView(buildBottomNav(), Ui.lp(-1, Ui.dp(64)));
            detailBox = null;
            portraitDetail = new FrameLayout(this);
            portraitDetail.setBackgroundColor(Ui.SURFACE);
            portraitDetail.setVisibility(portraitDetailOpen ? View.VISIBLE : View.GONE);
            portraitDetail.setClickable(true);
            root.addView(portraitDetail, new FrameLayout.LayoutParams(-1, -1));
        }

        // progress card
        progressCard = Ui.vbox(this);
        progressCard.setBackground(Ui.round(Ui.SURFACE2, 16, Ui.STROKE, 1));
        progressCard.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(14));
        progressCard.setElevation(Ui.dp(12));
        LinearLayout pr = Ui.hbox(this);
        progressTitle = Ui.oneLine(Ui.text(this, "", 14, Ui.TEXT, Ui.MEDIUM));
        pr.addView(progressTitle, Ui.lpw(0, -2, 1));
        progressCard.addView(pr);
        progressText = Ui.oneLine(Ui.text(this, "", 12.5f, Ui.TEXT2, Ui.REGULAR));
        progressCard.addView(progressText, Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 8));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.SURFACE3));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        progressBar.setMax(1000);
        progressCard.addView(progressBar, Ui.lp(-1, Ui.dp(6)));
        progressCard.setVisibility(busy.isEmpty() ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams pcl = Ui.flp(landscape ? Ui.dp(330) : -1, -2, Gravity.BOTTOM | (landscape ? Gravity.START : Gravity.CENTER_HORIZONTAL));
        pcl.setMargins(landscape ? Ui.dp(100) : Ui.dp(12), 0, Ui.dp(12), landscape ? Ui.dp(16) : Ui.dp(76));
        root.addView(progressCard, pcl);

        snack = Ui.text(this, "", 14, Ui.TEXT, Ui.MEDIUM);
        snack.setBackground(Ui.round(0xFF2A3241, 14, Ui.STROKE, 1));
        snack.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        snack.setElevation(Ui.dp(14));
        snack.setVisibility(View.GONE);
        snack.setMaxWidth(Ui.dp(520));
        FrameLayout.LayoutParams sl = Ui.flp(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sl.bottomMargin = landscape ? Ui.dp(20) : Ui.dp(84);
        root.addView(snack, sl);

        modalLayer = new FrameLayout(this);
        root.addView(modalLayer, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        applyImmersive();
        render();
        updateProgressCard();
    }

    private View buildRail() {
        LinearLayout rail = Ui.vbox(this);
        rail.setBackgroundColor(Ui.RAIL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        rail.addView(logo, Ui.lp(Ui.dp(46), Ui.dp(46)));
        TextView brand = Ui.text(this, "Addons", 11, Ui.ACCENT, Ui.CONDENSED);
        brand.setLetterSpacing(0.08f);
        rail.addView(brand, Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 18));
        rail.addView(navItem(TAB_INSTALLED, R.drawable.ic_extension, "Мои", true), Ui.margins(Ui.lp(Ui.dp(68), Ui.dp(60)), 0, 0, 0, 6));
        rail.addView(navItem(TAB_BROWSE, R.drawable.ic_apps, "Обзор", true), Ui.margins(Ui.lp(Ui.dp(68), Ui.dp(60)), 0, 0, 0, 6));
        View spacer = new View(this);
        rail.addView(spacer, Ui.lpw(1, 0, 1));
        rail.addView(navItem(TAB_SETTINGS, R.drawable.ic_settings, "Настройки", true), Ui.lp(Ui.dp(68), Ui.dp(60)));
        return rail;
    }

    private View buildBottomNav() {
        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundColor(Ui.RAIL);
        bar.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        bar.addView(navItem(TAB_INSTALLED, R.drawable.ic_extension, "Мои аддоны", false), Ui.lpw(0, -1, 1));
        bar.addView(navItem(TAB_BROWSE, R.drawable.ic_apps, "Обзор", false), Ui.lpw(0, -1, 1));
        bar.addView(navItem(TAB_SETTINGS, R.drawable.ic_settings, "Настройки", false), Ui.lpw(0, -1, 1));
        return bar;
    }

    private View navItem(final int t, int iconRes, String label, boolean rail) {
        LinearLayout item = Ui.vbox(this);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        boolean active = t == tab;
        item.setBackground(Ui.selectable(active ? Ui.ACCENT_SOFT : 0x00000000, 14, 0));
        ImageView iv = Ui.icon(this, iconRes, active ? Ui.ACCENT : Ui.TEXT3, 24);
        item.addView(iv);
        TextView tv = Ui.text(this, label, 11, active ? Ui.ACCENT : Ui.TEXT3, Ui.MEDIUM);
        tv.setGravity(Gravity.CENTER);
        item.addView(tv, Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        item.setOnClickListener(v -> switchTab(t));
        item.setTag(t);
        navItems.add(item);
        return item;
    }

    private void switchTab(int t) {
        if (t == tab) return;
        tab = t;
        selected = null;
        portraitDetailOpen = false;
        hideKeyboard();
        buildShell();
        if (t == TAB_BROWSE) ensureResults(false);
    }

    // ─────────────────────────── rendering ───────────────────────────

    private void render() {
        headerBox.removeAllViews();
        bodyBox.removeAllViews();
        searchField = null;
        currentList = null;
        currentAdapter = null;
        if (!hasStorageAccess()) {
            renderPermission();
        } else if (tab == TAB_SETTINGS) {
            renderSettings();
        } else if (store.gameRoot() == null || !store.gameReady()) {
            if (tab == TAB_BROWSE) renderBrowse(); else renderNoGame();
        } else if (tab == TAB_BROWSE) {
            renderBrowse();
        } else {
            renderInstalled();
        }
        renderDetail();
    }

    private void refreshList() {
        if (currentAdapter != null) currentAdapter.notifyDataSetChanged();
    }

    private LinearLayout header(String title, String subtitle) {
        LinearLayout h = Ui.hbox(this);
        h.setPadding(Ui.dp(landscape ? 20 : 16), Ui.dp(landscape ? 14 : 16), Ui.dp(landscape ? 16 : 12), Ui.dp(10));
        LinearLayout tbox = Ui.vbox(this);
        TextView tt = Ui.oneLine(Ui.text(this, title, landscape ? 21 : 22, Ui.TEXT, Ui.BOLD));
        tbox.addView(tt);
        if (subtitle != null) {
            TextView st = Ui.oneLine(Ui.text(this, subtitle, 12.5f, Ui.TEXT3, Ui.REGULAR));
            tbox.addView(st, Ui.margins(Ui.lp(-2, -2), 0, 4, 0, 0));
        }
        h.addView(tbox, Ui.lpw(0, -2, 1));
        headerBox.addView(h);
        return h;
    }

    private void addHeaderButton(LinearLayout h, String label, int icon, Ui.Kind kind, View.OnClickListener l) {
        LinearLayout b = Ui.button(this, landscape || label == null ? label : null, icon, kind, l);
        if (!landscape && label != null) b.setContentDescription(label);
        h.addView(b, Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
    }

    private View emptyState(int icon, String title, String text, View... actions) {
        LinearLayout box = Ui.vbox(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(28), Ui.dp(20), Ui.dp(28), Ui.dp(20));
        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(Ui.round(Ui.SURFACE2, 28));
        ImageView iv = Ui.icon(this, icon, Ui.ACCENT, 30);
        circle.addView(iv, Ui.flp(Ui.dp(30), Ui.dp(30), Gravity.CENTER));
        box.addView(circle, Ui.lp(Ui.dp(64), Ui.dp(64)));
        TextView t = Ui.text(this, title, 17, Ui.TEXT, Ui.BOLD);
        t.setGravity(Gravity.CENTER);
        box.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 14, 0, 6));
        if (text != null) {
            TextView s = Ui.text(this, text, 13.5f, Ui.TEXT2, Ui.REGULAR);
            s.setGravity(Gravity.CENTER);
            s.setMaxWidth(Ui.dp(440));
            box.addView(s);
        }
        if (actions.length > 0) {
            LinearLayout row = Ui.hbox(this);
            row.setGravity(Gravity.CENTER);
            for (View a : actions) row.addView(a, Ui.margins(Ui.lp(-2, Ui.dp(42)), 4, 0, 4, 0));
            box.addView(row, Ui.margins(Ui.lp(-2, -2), 0, 18, 0, 0));
        }
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        return sv;
    }

    private void renderPermission() {
        header("WoW Addons", "Менеджер аддонов для WoW Forever");
        bodyBox.addView(emptyState(R.drawable.ic_folder, "Нужен доступ к файлам",
                "Чтобы устанавливать аддоны в папку игры, приложению нужен «Доступ ко всем файлам» — так же, как самому клиенту WoW Forever.",
                Ui.button(this, "Разрешить доступ", R.drawable.ic_check, Ui.Kind.PRIMARY, v -> requestStorage())));
    }

    private void renderNoGame() {
        header("Мои аддоны", "Папка игры не выбрана");
        bodyBox.addView(emptyState(R.drawable.ic_folder, "Где установлен WoW Forever?",
                "Обычно это «WoW Forever» во внутренней памяти или на SD-карте. Выберите папку, в которой лежат .build.info и _forever_.",
                Ui.button(this, "Найти автоматически", R.drawable.ic_search, Ui.Kind.SECONDARY, v -> autoDetect(true)),
                Ui.button(this, "Выбрать папку", R.drawable.ic_folder, Ui.Kind.PRIMARY, v -> openFolderPicker())));
    }

    // ── Installed ──

    private void renderInstalled() {
        int updates = 0;
        int managed = 0;
        for (Model.Installed r : installed) {
            if (r.entry != null) managed++;
            if (r.entry != null && r.entry.updateAvailable) updates++;
        }
        String sub = installed.size() + " " + Ui.plural(installed.size(), "аддон", "аддона", "аддонов")
                + " · " + managed + " под управлением" + (checking ? " · проверка обновлений…" : "");
        LinearLayout h = header("Мои аддоны", sub);
        addHeaderButton(h, "Из ZIP", R.drawable.ic_zip, Ui.Kind.GHOST, v -> pickZip());
        addHeaderButton(h, checking ? "Проверка…" : "Проверить", R.drawable.ic_refresh, Ui.Kind.SECONDARY, v -> checkUpdates(true));
        if (updates > 0) {
            addHeaderButton(h, "Обновить все · " + updates, R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> updateAll());
        }

        if (!scanned) {
            bodyBox.addView(centerSpinner());
            return;
        }
        if (installed.isEmpty()) {
            bodyBox.addView(emptyState(R.drawable.ic_extension, "Аддонов пока нет",
                    "Откройте каталог: там собраны аддоны со сборкой для WoW Forever — Questie, Bartender4, DBM, Plater и другие.",
                    Ui.button(this, "Открыть каталог", R.drawable.ic_apps, Ui.Kind.PRIMARY, v -> switchTab(TAB_BROWSE)),
                    Ui.button(this, "Установить ZIP", R.drawable.ic_zip, Ui.Kind.SECONDARY, v -> pickZip())));
            return;
        }
        final List<Model.Installed> rows = new ArrayList<>(installed);
        Collections.sort(rows, (a, b) -> {
            int ua = a.entry != null && a.entry.updateAvailable ? 0 : 1;
            int ub = b.entry != null && b.entry.updateAvailable ? 0 : 1;
            if (ua != ub) return ua - ub;
            return a.title.compareToIgnoreCase(b.title);
        });
        ListView lv = makeList();
        currentAdapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int p) { return rows.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) { return installedRow(rows.get(p)); }
        };
        lv.setAdapter(currentAdapter);
        lv.setOnItemClickListener((parent, view, position, id) -> select(rows.get(position)));
        bodyBox.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        currentList = lv;
    }

    private ListView makeList() {
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(Ui.dp(6));
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0));
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(landscape ? 16 : 12), Ui.dp(2), Ui.dp(landscape ? 16 : 12), Ui.dp(96));
        lv.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        lv.setItemsCanFocus(false);
        lv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return lv;
    }

    private View centerSpinner() {
        FrameLayout f = new FrameLayout(this);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        f.addView(pb, Ui.flp(Ui.dp(40), Ui.dp(40), Gravity.CENTER));
        return f;
    }

    private View installedRow(Model.Installed r) {
        LinearLayout row = rowShell(r == selected);
        ImageView av = avatar(r.entry != null ? r.entry.iconUrl : (r.catalogMatch != null ? r.catalogMatch.iconUrl : null), r.title, 44);
        row.addView(av);
        LinearLayout mid = Ui.vbox(this);
        LinearLayout tl = Ui.hbox(this);
        TextView title = Ui.oneLine(Ui.text(this, r.title, 15, Ui.TEXT, Ui.MEDIUM));
        tl.addView(title, Ui.lpw(0, -2, 1));
        mid.addView(tl);
        StringBuilder sub = new StringBuilder();
        if (r.version != null && !r.version.isEmpty()) sub.append(r.version);
        String src = r.entry != null ? sourceName(r.entry.source) : "установлен вручную";
        if (sub.length() > 0) sub.append("  ·  ");
        sub.append(src);
        if (r.folders.size() > 1) sub.append("  ·  ").append(r.folders.size()).append(" ").append(Ui.plural(r.folders.size(), "папка", "папки", "папок"));
        TextView st = Ui.oneLine(Ui.text(this, sub.toString(), 12.5f, Ui.TEXT3, Ui.REGULAR));
        mid.addView(st, Ui.margins(Ui.lp(-1, -2), 0, 5, 0, 0));
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        String key = r.entry != null ? r.entry.key : (r.catalogMatch != null ? r.catalogMatch.key() : null);
        if (key != null && busy.containsKey(key)) {
            row.addView(Ui.pill(this, "Установка…", Ui.ACCENT, Ui.ACCENT_SOFT));
        } else if (r.missing) {
            row.addView(Ui.pill(this, "Папки удалены", Ui.RED, Ui.RED_SOFT));
        } else if (r.entry != null && r.entry.updateAvailable) {
            final Model.Installed fr = r;
            LinearLayout up = Ui.button(this, "Обновить", R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> update(fr.entry));
            up.setFocusable(false);
            up.setMinimumHeight(Ui.dp(34));
            up.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            row.addView(up);
        } else if (r.entry != null && r.entry.checkError != null) {
            row.addView(Ui.pill(this, "Не проверено", Ui.TEXT2, Ui.SURFACE3));
        } else if (r.entry != null && r.entry.latest != null) {
            row.addView(Ui.pill(this, "Актуально", Ui.GREEN, Ui.GREEN_SOFT));
        } else if (r.entry == null && r.catalogMatch != null) {
            row.addView(Ui.pill(this, "Есть в каталоге", Ui.BLUE, Ui.BLUE_SOFT));
        } else if (r.entry == null) {
            row.addView(Ui.pill(this, "Вручную", Ui.TEXT2, Ui.SURFACE3));
        }
        if (!r.compatible && !r.missing) {
            ImageView w = Ui.icon(this, R.drawable.ic_warning, Ui.ORANGE, 18);
            row.addView(w, Ui.margins(Ui.lp(Ui.dp(18), Ui.dp(18)), 8, 0, 0, 0));
        }
        return row;
    }

    private LinearLayout rowShell(boolean active) {
        LinearLayout row = Ui.hbox(this);
        StateListShell(row, active);
        row.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        row.setMinimumHeight(Ui.dp(64));
        row.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        return row;
    }

    private void StateListShell(View v, boolean active) {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_selected}, Ui.round(Ui.SURFACE2, 14, Ui.FOCUS, 2f));
        s.addState(new int[]{android.R.attr.state_pressed}, Ui.round(Ui.SURFACE3, 14));
        s.addState(new int[]{}, active ? Ui.round(Ui.SURFACE2, 14, 0x80F2B640, 1.2f) : Ui.round(Ui.SURFACE, 14, Ui.STROKE, 1f));
        v.setBackground(s);
    }

    private ImageView avatar(String url, String name, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setLayoutParams(Ui.lp(Ui.dp(sizeDp), Ui.dp(sizeDp)));
        ImageLoader.load(iv, url, name);
        return iv;
    }

    private String sourceName(String src) {
        if (Model.SRC_CURSE.equals(src)) return "CurseForge";
        if (Model.SRC_WOWI.equals(src)) return "WoWInterface";
        if (Model.SRC_ZIP.equals(src)) return "ZIP-файл";
        return "GitHub";
    }

    // ── Browse ──

    private void renderBrowse() {
        LinearLayout h = header("Обзор аддонов", "Только сборки для WoW Forever · интерфейс " + Toc.FOREVER_INTERFACE_PREFIX + "01");
        addHeaderButton(h, "Из ZIP", R.drawable.ic_zip, Ui.Kind.GHOST, v -> pickZip());

        // source segmented control
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout seg = Ui.hbox(this);
        seg.setPadding(Ui.dp(landscape ? 20 : 16), 0, Ui.dp(16), Ui.dp(8));
        String[] names = {"Forever-каталог", "CurseForge", "WoWInterface", "GitHub"};
        int[] icons = {R.drawable.ic_star, R.drawable.ic_download, R.drawable.ic_apps, R.drawable.ic_link};
        for (int i = 0; i < names.length; i++) {
            final int s = i;
            boolean on = i == source;
            LinearLayout chip = Ui.hbox(this);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setBackground(Ui.selectable(on ? Ui.ACCENT : Ui.SURFACE2, 20, on ? 0 : Ui.STROKE));
            chip.setPadding(Ui.dp(12), Ui.dp(7), Ui.dp(14), Ui.dp(7));
            chip.addView(Ui.icon(this, icons[i], on ? Ui.ACCENT_DARK : Ui.TEXT2, 16));
            TextView t = Ui.text(this, names[i], 13.5f, on ? Ui.ACCENT_DARK : Ui.TEXT, Ui.MEDIUM);
            chip.addView(t, Ui.margins(Ui.lp(-2, -2), 7, 0, 0, 0));
            chip.setOnClickListener(v -> {
                if (source == s) return;
                source = s;
                selected = null;
                render();
                ensureResults(false);
            });
            seg.addView(chip, Ui.margins(Ui.lp(-2, -2), 0, 0, 8, 0));
        }
        hs.addView(seg);
        headerBox.addView(hs);

        // search row
        LinearLayout sr = Ui.hbox(this);
        sr.setPadding(Ui.dp(landscape ? 20 : 16), 0, Ui.dp(16), Ui.dp(10));
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        searchField.setTextColor(Ui.TEXT);
        searchField.setHintTextColor(Ui.TEXT3);
        searchField.setHint(source == SRC_GITHUB ? "owner/repo или ссылка на GitHub" : "Поиск аддонов…");
        searchField.setText(queries[source]);
        searchField.setSelection(searchField.getText().length());
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | (source == SRC_GITHUB ? InputType.TYPE_TEXT_VARIATION_URI : 0));
        android.graphics.drawable.StateListDrawable fbg = new android.graphics.drawable.StateListDrawable();
        fbg.addState(new int[]{android.R.attr.state_focused}, Ui.round(Ui.SURFACE2, 12, Ui.ACCENT, 1.5f));
        fbg.addState(new int[]{}, Ui.round(Ui.SURFACE2, 12, Ui.STROKE, 1f));
        searchField.setBackground(fbg);
        android.graphics.drawable.Drawable sIcon = getDrawable(R.drawable.ic_search).mutate();
        sIcon.setTint(Ui.TEXT3);
        sIcon.setBounds(0, 0, Ui.dp(20), Ui.dp(20));
        searchField.setCompoundDrawables(sIcon, null, null, null);
        searchField.setCompoundDrawablePadding(Ui.dp(10));
        searchField.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
        final int srcNow = source;
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                queries[srcNow] = e.toString();
                if (srcNow == SRC_GITHUB) return;
                if (pendingSearch != null) main.removeCallbacks(pendingSearch);
                pendingSearch = () -> runSearch(srcNow, false);
                main.postDelayed(pendingSearch, srcNow == SRC_CURSE ? 650 : 250);
            }
        });
        searchField.setOnEditorActionListener((v, actionId, ev) -> {
            if (pendingSearch != null) main.removeCallbacks(pendingSearch);
            hideKeyboard();
            runSearch(srcNow, false);
            return true;
        });
        sr.addView(searchField, Ui.lpw(0, Ui.dp(42), 1));
        if (source == SRC_GITHUB) {
            LinearLayout go = Ui.button(this, "Найти", R.drawable.ic_search, Ui.Kind.PRIMARY, v -> { hideKeyboard(); runSearch(SRC_GITHUB, false); });
            sr.addView(go, Ui.margins(Ui.lp(-2, Ui.dp(42)), 8, 0, 0, 0));
        }
        headerBox.addView(sr);

        if (source == SRC_CATALOG) {
            HorizontalScrollView cs = new HorizontalScrollView(this);
            cs.setHorizontalScrollBarEnabled(false);
            LinearLayout cats = Ui.hbox(this);
            cats.setPadding(Ui.dp(landscape ? 20 : 16), 0, Ui.dp(16), Ui.dp(8));
            List<String> all = new ArrayList<>();
            all.add(null);
            all.addAll(Catalog.CATEGORIES);
            for (final String c : all) {
                boolean on = c == null ? catalogCategory == null : c.equals(catalogCategory);
                TextView chip = Ui.text(this, c == null ? "Все" : c, 12.5f, on ? Ui.TEXT : Ui.TEXT2, Ui.MEDIUM);
                chip.setBackground(Ui.selectable(on ? Ui.SURFACE3 : 0x00000000, 16, on ? 0 : Ui.STROKE));
                chip.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
                chip.setFocusable(true);
                chip.setClickable(true);
                chip.setOnClickListener(v -> { catalogCategory = c; selected = null; render(); });
                cats.addView(chip, Ui.margins(Ui.lp(-2, -2), 0, 0, 6, 0));
            }
            cs.addView(cats);
            headerBox.addView(cs);
        }

        if (store.gameRoot() == null || !store.gameReady()) {
            TextView warn = Ui.text(this, "Папка игры не выбрана — установка будет недоступна. Укажите её в настройках.", 12.5f, Ui.ORANGE, Ui.REGULAR);
            warn.setBackground(Ui.round(Ui.ORANGE_SOFT, 10));
            warn.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
            headerBox.addView(warn, Ui.margins(Ui.lp(-1, -2), landscape ? 20 : 16, 0, 16, 8));
        }

        renderResults();
    }

    private List<Model.Item> catalogFiltered() {
        String q = queries[SRC_CATALOG].trim().toLowerCase(Locale.ROOT);
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
        bodyBox.removeAllViews();
        currentList = null;
        currentAdapter = null;
        final int src = source;
        if (src == SRC_CURSE && !Sources.hasCurseKey()) {
            bodyBox.addView(emptyState(R.drawable.ic_key, "Подключите CurseForge",
                    "CurseForge выдаёт файлы только по личному API-ключу. Получите его бесплатно на console.curseforge.com и вставьте в настройках — после этого здесь появится поиск по всем аддонам с версией для Forever.\n\nБез ключа можно скачать zip на сайте CurseForge в браузере и установить его кнопкой «Из ZIP».",
                    Ui.button(this, "Ввести ключ", R.drawable.ic_key, Ui.Kind.PRIMARY, v -> switchTab(TAB_SETTINGS)),
                    Ui.button(this, "Открыть сайт", R.drawable.ic_open, Ui.Kind.SECONDARY, v -> openUrl("https://www.curseforge.com/wow/search?class=addons&gameVersionTypeId=" + Sources.CURSE_FOREVER_TYPE))));
            return;
        }
        final List<Model.Item> list;
        if (src == SRC_CATALOG) {
            list = catalogFiltered();
        } else {
            List<Model.Item> r = results.get(src);
            list = r == null ? new ArrayList<Model.Item>() : r;
        }
        if (list.isEmpty()) {
            if (loading.contains(src)) {
                bodyBox.addView(centerSpinner());
            } else if (resultErrors.get(src) != null) {
                bodyBox.addView(emptyState(R.drawable.ic_warning, "Не удалось загрузить", resultErrors.get(src),
                        Ui.button(this, "Повторить", R.drawable.ic_refresh, Ui.Kind.PRIMARY, v -> runSearch(src, false))));
            } else if (src == SRC_GITHUB) {
                bodyBox.addView(emptyState(R.drawable.ic_link, "Аддон с GitHub",
                        "Вставьте ссылку на репозиторий, например github.com/Questie/Questie. Будет выбрана сборка для Forever из release.json, а обновления пойдут без ключей и лимитов.", new View[0]));
            } else {
                bodyBox.addView(emptyState(R.drawable.ic_search, "Ничего не найдено", "Попробуйте другой запрос.", new View[0]));
            }
            return;
        }
        ListView lv = makeList();
        if (src == SRC_WOWI) {
            TextView note = Ui.text(this, "WoWInterface не помечает версии для Forever — после установки приложение проверит TOC и предупредит, если аддон не для Forever.", 12, Ui.TEXT3, Ui.REGULAR);
            note.setPadding(Ui.dp(4), Ui.dp(2), Ui.dp(4), Ui.dp(8));
            lv.addHeaderView(note, null, false);
        }
        currentAdapter = new BaseAdapter() {
            @Override public int getCount() { return list.size(); }
            @Override public Object getItem(int p) { return list.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) {
                if (p == list.size() - 1 && Boolean.TRUE.equals(hasMore.get(src)) && !loading.contains(src)) {
                    main.post(() -> runSearch(src, true));
                }
                return itemRow(list.get(p));
            }
        };
        lv.setAdapter(currentAdapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Object o = parent.getItemAtPosition(position);
            if (o instanceof Model.Item) select(o);
        });
        bodyBox.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        currentList = lv;
    }

    private View itemRow(final Model.Item it) {
        LinearLayout row = rowShell(it == selected || (selected instanceof Model.Item && ((Model.Item) selected).key().equals(it.key())));
        row.addView(avatar(it.iconUrl, it.name, 46));
        LinearLayout mid = Ui.vbox(this);
        LinearLayout tl = Ui.hbox(this);
        tl.addView(Ui.oneLine(Ui.text(this, it.name, 15, Ui.TEXT, Ui.MEDIUM)), Ui.lpw(0, -2, 1));
        mid.addView(tl);
        StringBuilder meta = new StringBuilder();
        if (!it.author.isEmpty()) meta.append(it.author);
        if (it.downloads > 0) { if (meta.length() > 0) meta.append("  ·  "); meta.append("↓ ").append(Ui.formatCount(it.downloads)); }
        if (source == SRC_CATALOG && !it.category.isEmpty()) { if (meta.length() > 0) meta.append("  ·  "); meta.append(it.category); }
        if (meta.length() > 0) mid.addView(Ui.oneLine(Ui.text(this, meta.toString(), 12, Ui.TEXT3, Ui.REGULAR)), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
        if (!it.summary.isEmpty()) {
            TextView s = Ui.text(this, it.summary, 12.5f, Ui.TEXT2, Ui.REGULAR);
            s.setMaxLines(landscape ? 1 : 2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(s, Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 0));
        }
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        Model.Entry e = store.entry(it.key());
        if (busy.containsKey(it.key())) {
            row.addView(Ui.pill(this, "Установка…", Ui.ACCENT, Ui.ACCENT_SOFT));
        } else if (e != null) {
            row.addView(Ui.pill(this, "Установлено", Ui.GREEN, Ui.GREEN_SOFT));
        } else {
            LinearLayout b = Ui.button(this, "Установить", R.drawable.ic_download, Ui.Kind.SECONDARY, v -> install(it, null));
            b.setFocusable(false);
            b.setMinimumHeight(Ui.dp(34));
            b.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(12), Ui.dp(6));
            row.addView(b);
        }
        return row;
    }

    private void ensureResults(boolean force) {
        if (source == SRC_CATALOG || source == SRC_GITHUB) return;
        if (source == SRC_CURSE && !Sources.hasCurseKey()) return;
        if (force || (results.get(source) == null && !loading.contains(source))) runSearch(source, false);
    }

    private void runSearch(final int src, final boolean more) {
        if (src == SRC_CATALOG) { if (tab == TAB_BROWSE && source == src) renderResults(); return; }
        if (src == SRC_CURSE && !Sources.hasCurseKey()) return;
        if (more && loading.contains(src)) return;
        final String q = queries[src];
        final int gen = ++searchGen[src];
        final List<Model.Item> prev = results.get(src);
        final int offset = more && prev != null ? prev.size() : 0;
        loading.add(src);
        resultErrors.remove(src);
        if (!more) { results.remove(src); if (tab == TAB_BROWSE && source == src) renderResults(); }
        net.execute(() -> {
            List<Model.Item> got = null;
            String err = null;
            boolean moreAvail = false;
            try {
                if (src == SRC_CURSE) {
                    got = Sources.Curse.search(q, offset);
                    moreAvail = got.size() >= 40 && offset < 9000;
                } else if (src == SRC_WOWI) {
                    got = Sources.Wowi.search(q, offset, 60);
                    moreAvail = got.size() >= 60;
                } else if (src == SRC_GITHUB) {
                    String repo = Sources.GitHub.normalize(q);
                    if (repo == null) throw new IOException("Введите репозиторий в виде owner/repo или ссылку github.com/owner/repo");
                    got = new ArrayList<>();
                    got.add(Sources.GitHub.info(repo));
                }
            } catch (Exception e) {
                err = friendly(e);
            }
            final List<Model.Item> fGot = got;
            final String fErr = err;
            final boolean fMore = moreAvail;
            main.post(() -> {
                if (isDestroyed()) return;
                if (gen != searchGen[src]) return;   // a newer search of this source owns "loading"
                loading.remove(src);
                if (fErr != null) {
                    if (!more) resultErrors.put(src, fErr); else snack(fErr);
                } else {
                    List<Model.Item> base = more && results.get(src) != null ? new ArrayList<>(results.get(src)) : new ArrayList<Model.Item>();
                    base.addAll(fGot);
                    results.put(src, base);
                    hasMore.put(src, fMore);
                    if (src == SRC_GITHUB && fGot.size() == 1 && landscape) selected = fGot.get(0);
                }
                if (tab == TAB_BROWSE && source == src) {
                    if (more && currentAdapter != null && fErr == null) {
                        // keep the list and its scroll position
                        renderResultsKeepingScroll();
                    } else {
                        renderResults();
                        renderDetail();
                    }
                }
            });
        });
    }

    private void renderResultsKeepingScroll() {
        int pos = currentList == null ? 0 : currentList.getFirstVisiblePosition();
        View first = currentList == null ? null : currentList.getChildAt(0);
        int top = first == null ? 0 : first.getTop();
        renderResults();
        if (currentList != null) currentList.setSelectionFromTop(pos, top);
    }

    // ── Settings ──

    private void renderSettings() {
        header("Настройки", "WoW Addons " + versionName());
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(landscape ? 20 : 16), Ui.dp(4), Ui.dp(landscape ? 20 : 16), Ui.dp(96));
        sv.addView(col);

        // Game folder
        LinearLayout game = card(col, R.drawable.ic_folder, "Папка игры");
        String root = store.gameRoot();
        boolean ok = store.gameReady();
        TextView path = Ui.text(this, root == null ? "Не выбрана" : root, 14, ok ? Ui.TEXT : Ui.ORANGE, Ui.MEDIUM);
        game.addView(path);
        String status;
        if (root == null) status = "Выберите папку с WoW Forever (где лежат .build.info и _forever_).";
        else if (!ok) status = "В этой папке не найден WoW Forever.";
        else {
            File ad = store.addonsDir();
            status = "Аддоны ставятся в " + Store.FLAVOR_DIR + "/Interface/AddOns" + (ad != null && ad.isDirectory() ? "" : " (папка будет создана)");
        }
        game.addView(Ui.text(this, status, 12.5f, Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 12));
        LinearLayout gb = Ui.hbox(this);
        gb.addView(Ui.button(this, "Выбрать папку", R.drawable.ic_folder, Ui.Kind.PRIMARY, v -> openFolderPicker()), Ui.lp(-2, Ui.dp(40)));
        gb.addView(Ui.button(this, "Автопоиск", R.drawable.ic_search, Ui.Kind.SECONDARY, v -> autoDetect(true)), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        game.addView(gb);

        // CurseForge key
        LinearLayout cf = card(col, R.drawable.ic_key, "CurseForge API-ключ");
        cf.addView(Ui.text(this, "Даёт поиск и обновления по всем аддонам CurseForge с версией для Forever. Ключ бесплатный: войдите на console.curseforge.com → API keys. Он хранится только на этом устройстве и отправляется только на api.curseforge.com и forgecdn.net.", 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText key = field(store.curseKey(), "Вставьте ключ", true);
        cf.addView(key, Ui.lp(-1, Ui.dp(42)));
        LinearLayout kb = Ui.hbox(this);
        kb.addView(Ui.button(this, "Сохранить", R.drawable.ic_check, Ui.Kind.PRIMARY, v -> {
            store.setCurseKey(key.getText().toString());
            results.remove(SRC_CURSE);
            hideKeyboard();
            snack(Sources.hasCurseKey() ? "Ключ CurseForge сохранён" : "Ключ удалён");
            if (Sources.hasCurseKey()) testCurseKey();
        }), Ui.lp(-2, Ui.dp(40)));
        kb.addView(Ui.button(this, "Получить ключ", R.drawable.ic_open, Ui.Kind.GHOST, v -> openUrl("https://console.curseforge.com/")), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        cf.addView(kb, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));

        // GitHub token
        LinearLayout gh = card(col, R.drawable.ic_link, "GitHub-токен (необязательно)");
        gh.addView(Ui.text(this, "Каталог работает без токена. Токен нужен, только если вы часто добавляете репозитории без release.json и упираетесь в лимит 60 запросов в час.", 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText tok = field(store.githubToken(), "ghp_…", true);
        gh.addView(tok, Ui.lp(-1, Ui.dp(42)));
        gh.addView(Ui.button(this, "Сохранить", R.drawable.ic_check, Ui.Kind.SECONDARY, v -> {
            store.setGithubToken(tok.getText().toString());
            hideKeyboard();
            snack("Сохранено");
        }), Ui.margins(Ui.lp(-2, Ui.dp(40)), 0, 10, 0, 0));

        // About
        LinearLayout ab = card(col, R.drawable.ic_info, "О приложении");
        ab.addView(Ui.text(this, "Менеджер аддонов для клиента WoW Forever на Android. Ставит только сборки для Forever (TOC _Forever / _Camelot, интерфейс 16001), обновляет их и ничего не трогает в игре, кроме Interface/AddOns.\n\nУправление с геймпада: L1/R1 — вкладки, X — проверить обновления, Y — поиск, B — назад.", 12.5f, Ui.TEXT2, Ui.REGULAR));
        LinearLayout abb = Ui.hbox(this);
        abb.addView(Ui.button(this, "Клиент WoW Forever", R.drawable.ic_open, Ui.Kind.GHOST, v -> openUrl("https://github.com/jaredgei/wow-forever-android")), Ui.lp(-2, Ui.dp(40)));
        abb.addView(Ui.button(this, "Очистить кэш", R.drawable.ic_delete, Ui.Kind.GHOST, v -> {
            Installer.deleteRecursive(new File(getCacheDir(), "dl"));
            Installer.deleteRecursive(new File(getCacheDir(), "wowi-filelist.json"));
            results.clear();
            snack("Кэш очищен");
        }), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        ab.addView(abb, Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));

        bodyBox.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private void testCurseKey() {
        net.execute(() -> {
            String msg;
            try {
                Sources.Curse.search("", 0);
                msg = "Ключ CurseForge работает ✓";
            } catch (Exception e) {
                msg = friendly(e);
            }
            final String m = msg;
            main.post(() -> snack(m));
        });
    }

    private LinearLayout card(LinearLayout parent, int icon, String title) {
        LinearLayout c = Ui.vbox(this);
        c.setBackground(Ui.round(Ui.SURFACE, 16, Ui.STROKE, 1));
        c.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(16));
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.icon(this, icon, Ui.ACCENT, 20));
        h.addView(Ui.text(this, title, 15.5f, Ui.TEXT, Ui.BOLD), Ui.margins(Ui.lp(-2, -2), 10, 0, 0, 0));
        c.addView(h, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        parent.addView(c, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
        return c;
    }

    private EditText field(String value, String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.TEXT3);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : 0) | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        android.graphics.drawable.StateListDrawable fbg = new android.graphics.drawable.StateListDrawable();
        fbg.addState(new int[]{android.R.attr.state_focused}, Ui.round(Ui.SURFACE2, 12, Ui.ACCENT, 1.5f));
        fbg.addState(new int[]{}, Ui.round(Ui.SURFACE2, 12, Ui.STROKE, 1f));
        e.setBackground(fbg);
        e.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        return e;
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    // ─────────────────────────── detail panel ───────────────────────────

    private void select(Object o) {
        selected = o;
        if (!landscape) {
            if (o != null) { portraitDetailOpen = true; portraitDetail.setVisibility(View.VISIBLE); }
            else closePortraitDetail();
        }
        refreshList();
        renderDetail();
    }

    private void closePortraitDetail() {
        portraitDetailOpen = false;
        selected = null;
        if (portraitDetail != null) { portraitDetail.removeAllViews(); portraitDetail.setVisibility(View.GONE); }
        refreshList();
        if (landscape) renderDetail();
    }

    private void renderDetail() {
        FrameLayout target = landscape ? detailBox : portraitDetail;
        if (target == null) return;
        target.removeAllViews();
        if (!landscape && !portraitDetailOpen) return;
        if (selected == null) {
            if (landscape) target.addView(detailPlaceholder());
            return;
        }
        // Keep "Installed" rows fresh after a rescan.
        if (selected instanceof Model.Installed) {
            Model.Installed s = (Model.Installed) selected;
            Model.Installed fresh = null;
            for (Model.Installed r : installed) {
                if (s.entry != null && r.entry != null && r.entry.key.equals(s.entry.key)) fresh = r;
                else if (s.entry == null && r.entry == null && r.main() != null && s.main() != null && r.main().folder.equals(s.main().folder)) fresh = r;
                else if (s.entry == null && r.entry != null && s.catalogMatch != null && r.entry.key.equals(s.catalogMatch.key())) fresh = r;
            }
            if (fresh == null && tab == TAB_INSTALLED) { selected = null; if (landscape) target.addView(detailPlaceholder()); else closePortraitDetail(); return; }
            if (fresh != null) selected = fresh;
        }
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(20), Ui.dp(landscape ? 18 : 12), Ui.dp(20), Ui.dp(96));
        sv.addView(col);
        if (!landscape) {
            LinearLayout back = Ui.button(this, "Назад", R.drawable.ic_back, Ui.Kind.GHOST, v -> closePortraitDetail());
            col.addView(back, Ui.margins(Ui.lp(-2, Ui.dp(38)), 0, 0, 0, 14));
        }
        if (selected instanceof Model.Item) buildItemDetail(col, (Model.Item) selected);
        else buildInstalledDetail(col, (Model.Installed) selected);
        target.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private View detailPlaceholder() {
        LinearLayout box = Ui.vbox(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(24), 0, Ui.dp(24), 0);
        ImageView iv = Ui.icon(this, tab == TAB_INSTALLED ? R.drawable.ic_extension : R.drawable.ic_apps, Ui.SURFACE3, 56);
        box.addView(iv, Ui.lp(Ui.dp(56), Ui.dp(56)));
        String t;
        String s;
        if (tab == TAB_SETTINGS) {
            t = "WoW Forever";
            s = "Аддоны ставятся в _forever_/Interface/AddOns выбранной папки игры. Закройте игру перед установкой или обновлением.";
        } else if (tab == TAB_INSTALLED) {
            t = "Выберите аддон";
            s = "Здесь будут версия, папки и кнопки обновления и удаления.";
        } else {
            t = "Выберите аддон";
            s = "Нажмите на аддон в списке, чтобы увидеть подробности и установить его.";
        }
        TextView tt = Ui.text(this, t, 16, Ui.TEXT2, Ui.BOLD);
        tt.setGravity(Gravity.CENTER);
        box.addView(tt, Ui.margins(Ui.lp(-2, -2), 0, 14, 0, 6));
        TextView st = Ui.text(this, s, 13, Ui.TEXT3, Ui.REGULAR);
        st.setGravity(Gravity.CENTER);
        box.addView(st);
        return box;
    }

    private void detailHeader(LinearLayout col, String icon, String name, String author, View... pills) {
        LinearLayout h = Ui.hbox(this);
        h.setGravity(Gravity.TOP);
        h.addView(avatar(icon, name, 64));
        LinearLayout tb = Ui.vbox(this);
        TextView t = Ui.text(this, name, 19, Ui.TEXT, Ui.BOLD);
        t.setMaxLines(2);
        tb.addView(t);
        if (author != null && !author.isEmpty()) tb.addView(Ui.oneLine(Ui.text(this, author, 13, Ui.TEXT2, Ui.REGULAR)), Ui.margins(Ui.lp(-2, -2), 0, 5, 0, 0));
        LinearLayout pr = Ui.hbox(this);
        for (View p : pills) pr.addView(p, Ui.margins(Ui.lp(-2, -2), 0, 0, 6, 0));
        tb.addView(pr, Ui.margins(Ui.lp(-2, -2), 0, 8, 0, 0));
        h.addView(tb, Ui.margins(Ui.lpw(0, -2, 1), 14, 0, 0, 0));
        col.addView(h, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 16));
    }

    private void infoRow(LinearLayout col, String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout r = Ui.hbox(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(7), 0, Ui.dp(7));
        r.addView(Ui.text(this, label, 13, Ui.TEXT3, Ui.REGULAR), Ui.lp(Ui.dp(110), -2));
        TextView v = Ui.text(this, value, 13, Ui.TEXT, Ui.MEDIUM);
        v.setTextIsSelectable(false);
        r.addView(v, Ui.lpw(0, -2, 1));
        col.addView(r);
        col.addView(Ui.divider(this));
    }

    private void noteCard(LinearLayout col, String text, int fg, int bg) {
        LinearLayout n = Ui.hbox(this);
        n.setGravity(Gravity.TOP);
        n.setBackground(Ui.round(bg, 12));
        n.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        n.addView(Ui.icon(this, R.drawable.ic_info, fg, 18));
        n.addView(Ui.text(this, text, 12.5f, fg, Ui.REGULAR), Ui.margins(Ui.lpw(0, -2, 1), 10, 0, 0, 0));
        col.addView(n, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
    }

    private LinearLayout actionRow(LinearLayout col) {
        LinearLayout a = Ui.hbox(this);
        col.addView(a, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 16));
        return a;
    }

    private void buildItemDetail(LinearLayout col, final Model.Item it) {
        View src = Ui.pill(this, sourceName(it.source), Ui.TEXT2, Ui.SURFACE3);
        View fv = it.foreverVerified ? Ui.pill(this, "✓ Forever", Ui.GREEN, Ui.GREEN_SOFT) : Ui.pill(this, "Forever не подтверждён", Ui.ORANGE, Ui.ORANGE_SOFT);
        detailHeader(col, it.iconUrl, it.name, it.author, src, fv);

        final Model.Entry e = store.entry(it.key());
        LinearLayout actions = actionRow(col);
        if (busy.containsKey(it.key())) {
            LinearLayout b = Ui.button(this, busy.get(it.key()), 0, Ui.Kind.SECONDARY, null);
            b.setEnabled(false);
            actions.addView(b, Ui.lpw(0, Ui.dp(44), 1));
        } else if (it.distributionOff) {
            actions.addView(Ui.button(this, "Скачать на сайте", R.drawable.ic_open, Ui.Kind.PRIMARY, v -> openUrl(it.pageUrl)), Ui.lpw(0, Ui.dp(44), 1));
        } else {
            String label = e == null ? "Установить" : "Переустановить";
            LinearLayout b = Ui.button(this, label, R.drawable.ic_download, e == null ? Ui.Kind.PRIMARY : Ui.Kind.SECONDARY, v -> install(it, null));
            actions.addView(b, Ui.lpw(0, Ui.dp(44), 1));
            if (landscape) b.post(b::requestFocus);
        }
        if (it.pageUrl != null) {
            actions.addView(Ui.button(this, null, R.drawable.ic_open, Ui.Kind.GHOST, v -> openUrl(it.pageUrl)), Ui.margins(Ui.lp(-2, Ui.dp(44)), 8, 0, 0, 0));
        }
        if (e != null && !busy.containsKey(it.key())) {
            actions.addView(Ui.button(this, null, R.drawable.ic_delete, Ui.Kind.DANGER, v -> confirmRemoveEntry(e)), Ui.margins(Ui.lp(-2, Ui.dp(44)), 8, 0, 0, 0));
        }

        if (it.note != null) noteCard(col, it.note, Ui.ORANGE, Ui.ORANGE_SOFT);
        if (it.distributionOff) noteCard(col, "Автор разрешает скачивание только с сайта CurseForge. Скачайте zip в браузере и установите его кнопкой «Из ZIP».", Ui.ORANGE, Ui.ORANGE_SOFT);
        if (Model.SRC_WOWI.equals(it.source)) noteCard(col, "WoWInterface не указывает версию для Forever. Убедитесь, что аддон поддерживает Forever (интерфейс 16001).", Ui.BLUE, Ui.BLUE_SOFT);

        if (!it.summary.isEmpty()) {
            TextView s = Ui.text(this, it.summary, 14, Ui.TEXT2, Ui.REGULAR);
            col.addView(s, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
        }
        final TextView desc = Ui.text(this, "", 13, Ui.TEXT2, Ui.REGULAR);
        desc.setVisibility(View.GONE);
        col.addView(desc, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
        if (Model.SRC_WOWI.equals(it.source)) {
            net.execute(() -> {
                final String d = Sources.Wowi.description(it.id);
                main.post(() -> { if (d != null && !d.isEmpty() && selected == it) { desc.setText(d); desc.setVisibility(View.VISIBLE); } });
            });
        }

        if (e != null) infoRow(col, "Установлено", e.version);
        if (it.version != null && !Model.SRC_CURSE.equals(it.source)) infoRow(col, "Версия", it.version);
        if (!it.category.isEmpty()) infoRow(col, "Категория", it.category);
        if (it.downloads > 0) infoRow(col, "Загрузки", Ui.formatCount(it.downloads));
        if (it.updated > 0) infoRow(col, "Обновлён", Ui.formatDate(it.updated));
        if (!it.folders.isEmpty()) infoRow(col, "Папки", joinShort(it.folders, 6));
        if (!it.deps.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String d : it.deps) { Model.Item c = Catalog.byKey(d); names.add(c != null ? c.name : d); }
            infoRow(col, "Зависимости", String.join(", ", names) + " (ставятся автоматически)");
        }
        infoRow(col, "Источник", Model.SRC_GITHUB.equals(it.source) ? "github.com/" + it.id : sourceName(it.source) + " #" + it.id);
    }

    private void buildInstalledDetail(LinearLayout col, final Model.Installed r) {
        final Model.Entry e = r.entry;
        String icon = e != null ? e.iconUrl : (r.catalogMatch != null ? r.catalogMatch.iconUrl : null);
        List<View> pills = new ArrayList<>();
        pills.add(Ui.pill(this, e != null ? sourceName(e.source) : "Вручную", Ui.TEXT2, Ui.SURFACE3));
        if (r.missing) pills.add(Ui.pill(this, "Папки удалены", Ui.RED, Ui.RED_SOFT));
        else if (r.compatible) pills.add(Ui.pill(this, "✓ Forever", Ui.GREEN, Ui.GREEN_SOFT));
        else pills.add(Ui.pill(this, "Не для Forever?", Ui.ORANGE, Ui.ORANGE_SOFT));
        detailHeader(col, icon, r.title, r.author, pills.toArray(new View[0]));

        String busyKey = e != null ? e.key : (r.catalogMatch != null ? r.catalogMatch.key() : null);
        LinearLayout actions = actionRow(col);
        LinearLayout primary = null;
        if (busyKey != null && busy.containsKey(busyKey)) {
            LinearLayout b = Ui.button(this, busy.get(busyKey), 0, Ui.Kind.SECONDARY, null);
            actions.addView(b, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e != null && !Model.SRC_ZIP.equals(e.source)) {
            if (e.updateAvailable) {
                primary = Ui.button(this, "Обновить до " + shortVer(e.latest), R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> update(e));
            } else if (r.missing) {
                primary = Ui.button(this, "Установить заново", R.drawable.ic_download, Ui.Kind.PRIMARY, v -> update(e));
            } else {
                primary = Ui.button(this, "Проверить обновление", R.drawable.ic_refresh, Ui.Kind.SECONDARY, v -> checkOne(e));
            }
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e == null && r.catalogMatch != null) {
            primary = Ui.button(this, "Обновлять через каталог", R.drawable.ic_link, Ui.Kind.PRIMARY, v -> install(r.catalogMatch, null));
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e == null && r.main() != null && r.main().curseId != null && Sources.hasCurseKey()) {
            final String cid = r.main().curseId;
            primary = Ui.button(this, "Обновлять с CurseForge", R.drawable.ic_link, Ui.Kind.PRIMARY, v -> adoptCurse(cid, r));
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        } else if (e == null && r.main() != null && r.main().wowiId != null) {
            final String wid = r.main().wowiId;
            primary = Ui.button(this, "Обновлять с WoWInterface", R.drawable.ic_link, Ui.Kind.PRIMARY, v -> adoptWowi(wid, r));
            actions.addView(primary, Ui.lpw(0, Ui.dp(44), 1));
        }
        String page = e != null ? e.pageUrl : (r.catalogMatch != null ? r.catalogMatch.pageUrl : (r.main() != null ? r.main().website : null));
        if (page != null) {
            final String p = page;
            actions.addView(Ui.button(this, primary == null ? "Страница" : null, R.drawable.ic_open, Ui.Kind.GHOST, v -> openUrl(p)), Ui.margins(Ui.lp(primary == null ? 0 : -2, Ui.dp(44)), primary == null ? 0 : 8, 0, 0, 0));
            if (primary == null) ((LinearLayout.LayoutParams) actions.getChildAt(actions.getChildCount() - 1).getLayoutParams()).weight = 1;
        }
        LinearLayout del = Ui.button(this, primary == null && page == null ? "Удалить" : null, R.drawable.ic_delete, Ui.Kind.DANGER, v -> confirmRemove(r));
        actions.addView(del, Ui.margins(Ui.lp(primary == null && page == null ? 0 : -2, Ui.dp(44)), actions.getChildCount() == 0 ? 0 : 8, 0, 0, 0));
        if (primary == null && page == null) ((LinearLayout.LayoutParams) del.getLayoutParams()).weight = 1;
        final View focusTarget = primary != null ? primary : del;
        if (landscape) focusTarget.post(focusTarget::requestFocus);

        if (!r.compatible && !r.missing) {
            noteCard(col, "В аддоне нет TOC для Forever (_Forever / _Camelot) и интерфейса 160xx. Он может не загрузиться или считаться устаревшим. Ищите версию «Forever» у автора.", Ui.ORANGE, Ui.ORANGE_SOFT);
        }
        if (e != null && e.checkError != null) noteCard(col, "Проверка обновления: " + e.checkError, Ui.TEXT2, Ui.SURFACE2);
        Model.Item cat = e != null ? Catalog.byKey(e.key) : r.catalogMatch;
        if (cat != null && cat.note != null) noteCard(col, cat.note, Ui.ORANGE, Ui.ORANGE_SOFT);
        if (e == null && r.catalogMatch != null) noteCard(col, "Этот аддон установлен вручную, но есть в Forever-каталоге. Нажмите «Обновлять через каталог», чтобы поставить свежую версию поверх и получать обновления.", Ui.BLUE, Ui.BLUE_SOFT);

        if (r.notes != null && !r.notes.isEmpty()) {
            col.addView(Ui.text(this, r.notes, 14, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
        }
        infoRow(col, "Версия", r.version);
        if (e != null && e.latest != null) infoRow(col, "Последняя", e.latest);
        if (e != null && e.installedAt > 0) infoRow(col, "Установлен", Ui.formatDate(e.installedAt));
        if (r.main() != null) infoRow(col, "Интерфейс", r.main().interfaceVersion);
        List<String> fs = new ArrayList<>();
        for (Model.LocalAddon a : r.folders) fs.add(a.folder);
        if (fs.isEmpty() && e != null) fs.addAll(e.folders);
        infoRow(col, "Папки", joinShort(fs, 8));
        if (e != null && !Model.SRC_ZIP.equals(e.source)) infoRow(col, "Источник", Model.SRC_GITHUB.equals(e.source) ? "github.com/" + e.id : sourceName(e.source) + " #" + e.id);
    }

    private static String shortVer(String v) {
        if (v == null) return "";
        return v.length() > 22 ? v.substring(0, 22) + "…" : v;
    }

    private static String joinShort(List<String> l, int max) {
        if (l.size() <= max) return String.join(", ", l);
        return String.join(", ", l.subList(0, max)) + " и ещё " + (l.size() - max);
    }

    // ─────────────────────────── scanning & updates ───────────────────────────

    private void rescan() {
        if (!hasStorageAccess() || !store.gameReady()) { scanned = true; installed = new ArrayList<>(); renderIfTab(TAB_INSTALLED); return; }
        final File addons = store.addonsDir();
        net.execute(() -> {
            List<Model.LocalAddon> locals = addons.isDirectory() ? Toc.scan(addons) : new ArrayList<Model.LocalAddon>();
            final List<Model.Installed> rows = group(locals);
            main.post(() -> {
                if (isDestroyed()) return;
                // carry over update-check results
                for (Model.Installed r : rows) {
                    if (r.entry == null) continue;
                    for (Model.Installed o : installed) if (o.entry != null && o.entry.key.equals(r.entry.key) && o.entry != r.entry) {
                        r.entry.latest = o.entry.latest;
                        r.entry.checkError = o.entry.checkError;
                        r.entry.updateAvailable = o.entry.latest != null && !sameVersion(o.entry.latest, r.entry.version);
                    }
                }
                installed = rows;
                scanned = true;
                if (tab == TAB_INSTALLED) { render(); }
                else if (tab == TAB_BROWSE) refreshList();
                if (!checkedOnce && store.list().size() > 0) { checkedOnce = true; checkUpdates(false); }
            });
        });
    }

    private void renderIfTab(int t) { if (tab == t && root != null) render(); }

    private List<Model.Installed> group(List<Model.LocalAddon> locals) {
        Map<String, Model.LocalAddon> byFolder = new HashMap<>();
        for (Model.LocalAddon a : locals) byFolder.put(a.folder.toLowerCase(Locale.ROOT), a);
        List<Model.Installed> rows = new ArrayList<>();
        Set<String> owned = new HashSet<>();
        for (Model.Entry e : store.list()) {
            Model.Installed r = new Model.Installed();
            r.entry = e;
            for (String f : e.folders) {
                Model.LocalAddon a = byFolder.get(f.toLowerCase(Locale.ROOT));
                owned.add(f.toLowerCase(Locale.ROOT));
                if (a != null) r.folders.add(a);
            }
            // put the folder that matches the name first
            Collections.sort(r.folders, (x, y) -> x.folder.length() - y.folder.length());
            r.missing = r.folders.isEmpty();
            r.title = e.name;
            Model.LocalAddon m = r.main();
            r.version = e.version;
            r.author = e.author.isEmpty() && m != null ? m.author : e.author;
            r.notes = m != null ? m.notes : "";
            r.compatible = m == null || anyForever(r.folders);
            rows.add(r);
        }
        Map<String, Model.Installed> catalogGroups = new LinkedHashMap<>();
        List<Model.LocalAddon> rest = new ArrayList<>();
        for (Model.LocalAddon a : locals) {
            if (owned.contains(a.folder.toLowerCase(Locale.ROOT))) continue;
            Model.Item c = Catalog.byFolder(a.folder);
            if (c != null) {
                Model.Installed g = catalogGroups.get(c.key());
                if (g == null) { g = new Model.Installed(); g.catalogMatch = c; catalogGroups.put(c.key(), g); }
                g.folders.add(a);
            } else rest.add(a);
        }
        for (Model.Installed g : catalogGroups.values()) {
            Collections.sort(g.folders, (x, y) -> {
                int ix = g.catalogMatch.folders.indexOf(x.folder), iy = g.catalogMatch.folders.indexOf(y.folder);
                return ix - iy;
            });
            Model.LocalAddon m = g.main();
            g.title = g.catalogMatch.name;
            g.version = m.version;
            g.author = m.author.isEmpty() ? g.catalogMatch.author : m.author;
            g.notes = m.notes.isEmpty() ? g.catalogMatch.summary : m.notes;
            g.compatible = anyForever(g.folders);
            rows.add(g);
        }
        // group "Addon_Options", "Addon-Module" under "Addon"
        Collections.sort(rest, (x, y) -> x.folder.length() - y.folder.length());
        Map<String, Model.Installed> parents = new LinkedHashMap<>();
        for (Model.LocalAddon a : rest) {
            Model.Installed parent = null;
            String lower = a.folder.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Model.Installed> p : parents.entrySet()) {
                if (lower.startsWith(p.getKey() + "_") || lower.startsWith(p.getKey() + "-")) { parent = p.getValue(); break; }
            }
            if (parent != null) { parent.folders.add(a); continue; }
            Model.Installed g = new Model.Installed();
            g.folders.add(a);
            g.title = a.title;
            g.version = a.version;
            g.author = a.author;
            g.notes = a.notes;
            parents.put(lower, g);
        }
        for (Model.Installed g : parents.values()) {
            g.compatible = anyForever(g.folders);
            rows.add(g);
        }
        return rows;
    }

    private static boolean anyForever(List<Model.LocalAddon> folders) {
        if (folders.isEmpty()) return true;
        // the first folder is the main one; libraries without TOC metadata are fine
        Model.LocalAddon m = folders.get(0);
        if (m.tocFile.isEmpty()) return true;
        for (Model.LocalAddon a : folders) if (a.foreverToc) return true;
        return false;
    }

    private void checkUpdates(final boolean manual) {
        if (checking) return;
        final List<Model.Entry> list = new ArrayList<>();
        for (Model.Entry e : store.list()) if (!Model.SRC_ZIP.equals(e.source)) list.add(e);
        if (list.isEmpty()) { if (manual) snack("Нет аддонов, установленных через менеджер"); return; }
        checking = true;
        renderIfTab(TAB_INSTALLED);
        final AtomicInteger left = new AtomicInteger(list.size());
        for (final Model.Entry e : list) {
            net.execute(() -> {
                String latest = null, err = null;
                try {
                    Model.Release r = Sources.latest(Sources.itemFor(e));
                    latest = r.version;
                } catch (Exception ex) {
                    err = friendly(ex);
                }
                final String fl = latest, fe = err;
                main.post(() -> {
                    if (isDestroyed()) return;
                    e.checkError = fe;
                    if (fl != null) { e.latest = fl; e.updateAvailable = !sameVersion(fl, e.version); }
                    if (left.decrementAndGet() == 0) {
                        checking = false;
                        store.setLastCheck(System.currentTimeMillis());
                        int n = 0;
                        for (Model.Entry x : store.list()) if (x.updateAvailable) n++;
                        if (manual || n > 0) snack(n == 0 ? "Все аддоны актуальны" : "Доступно обновлений: " + n);
                    }
                    if (tab == TAB_INSTALLED) { render(); }
                });
            });
        }
    }

    private void checkOne(final Model.Entry e) {
        snack("Проверяю " + e.name + "…");
        net.execute(() -> {
            String latest = null, err = null;
            try { latest = Sources.latest(Sources.itemFor(e)).version; } catch (Exception ex) { err = friendly(ex); }
            final String fl = latest, fe = err;
            main.post(() -> {
                e.checkError = fe;
                if (fl != null) { e.latest = fl; e.updateAvailable = !sameVersion(fl, e.version); }
                snack(fe != null ? fe : (e.updateAvailable ? "Доступна версия " + fl : "Установлена последняя версия"));
                if (tab == TAB_INSTALLED) render();
            });
        });
    }

    static boolean sameVersion(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.trim().replaceFirst("^[vV]", ""), y = b.trim().replaceFirst("^[vV]", "");
        return x.equalsIgnoreCase(y);
    }

    // ─────────────────────────── install / update / remove ───────────────────────────

    private boolean requireGame() {
        if (!hasStorageAccess()) { requestStorage(); return false; }
        if (!store.gameReady()) {
            confirm("Папка игры не выбрана", "Укажите, где установлен WoW Forever, чтобы приложение знало, куда ставить аддоны.",
                    "Выбрать папку", this::openFolderPicker);
            return false;
        }
        return true;
    }

    private void update(Model.Entry e) {
        install(Sources.itemFor(e), null);
    }

    private void updateAll() {
        int n = 0;
        for (Model.Entry e : store.list()) if (e.updateAvailable && !busy.containsKey(e.key)) { update(e); n++; }
        if (n > 0) snack("Обновляю: " + n);
    }

    private void install(final Model.Item item, final Runnable after) {
        if (!requireGame()) return;
        if (busy.containsKey(item.key())) return;
        busy.put(item.key(), "В очереди…");
        queued.incrementAndGet();
        updateProgressCard();
        refreshList();
        renderDetail();
        final File addons = store.addonsDir();
        ops.execute(() -> {
            String error = null;
            List<String> installedNames = new ArrayList<>();
            try {
                installWithDeps(item, addons, installedNames, new HashSet<String>(), 0);
            } catch (Exception e) {
                error = friendly(e);
            }
            final String fe = error;
            main.post(() -> {
                if (isDestroyed()) return;
                busy.remove(item.key());
                queued.decrementAndGet();
                updateProgressCard();
                if (fe != null) {
                    confirm("Не удалось установить " + item.name, fe, null, null);
                } else {
                    snack((installedNames.size() > 1 ? "Установлено: " + String.join(", ", installedNames) : "Установлено: " + item.name)
                            + ". Если игра запущена — введите /reload");
                }
                rescan();
                refreshList();
                renderDetail();
                if (after != null) after.run();
            });
        });
    }

    /** Runs on the ops thread. */
    private void installWithDeps(Model.Item item, File addons, List<String> done, Set<String> seen, int depth) throws IOException {
        if (!seen.add(item.key()) || depth > 4) return;
        progress(item, "Поиск сборки для Forever…", -1);
        Model.Release rel = Sources.latest(item);
        // dependencies first
        List<Model.Item> deps = new ArrayList<>(rel.deps);
        for (String d : item.deps) { Model.Item c = Catalog.byKey(d); if (c != null) deps.add(c); }
        for (Model.Item d : deps) {
            if (store.entry(d.key()) != null) continue;
            if (Model.SRC_CURSE.equals(d.source)) {
                try { Model.Item full = Sources.Curse.mod(d.id); if (full != null) d = full; } catch (IOException ignored) {}
            }
            installWithDeps(d, addons, done, seen, depth + 1);
        }
        File dl = new File(getCacheDir(), "dl");
        dl.mkdirs();
        File zip = new File(dl, safeName(rel.fileName == null ? item.name + ".zip" : rel.fileName));
        final Model.Item fItem = item;
        progress(item, "Загрузка " + (rel.version == null ? "" : rel.version), 0);
        Http.download(rel.url, rel.headers, zip, (d, t) -> progress(fItem, "Загрузка " + Ui.formatBytes(d) + (t > 0 ? " из " + Ui.formatBytes(t) : ""), t > 0 ? (float) d / t : -1));
        if (rel.md5 != null && rel.md5.length() == 32) {
            String got = Installer.md5(zip);
            if (!got.equalsIgnoreCase(rel.md5)) { zip.delete(); throw new IOException("Файл повреждён при загрузке (MD5 не совпал). Попробуйте ещё раз."); }
        }
        progress(item, "Распаковка…", -1);
        Model.Entry old = store.entry(item.key());
        List<String> folders = Installer.install(zip, addons, text -> progress(fItem, text, -1));
        zip.delete();
        if (old != null) {
            List<String> gone = new ArrayList<>();
            for (String f : old.folders) if (!containsIgnoreCase(folders, f)) gone.add(f);
            Installer.remove(addons, gone);
        }
        // folders taken over from another managed entry
        for (String f : folders) {
            Model.Entry other = store.owner(f);
            if (other != null && !other.key.equals(item.key())) {
                other.folders = new ArrayList<>(other.folders);
                other.folders.remove(f);
                if (other.folders.isEmpty()) store.remove(other.key); else store.put(other);
            }
        }
        Model.Entry e = new Model.Entry();
        e.key = item.key();
        e.source = item.source;
        e.id = item.id;
        e.name = item.name;
        e.author = item.author == null ? "" : item.author;
        e.iconUrl = item.iconUrl;
        e.pageUrl = item.pageUrl;
        e.version = rel.version == null ? "" : rel.version;
        e.folders = folders;
        e.installedAt = System.currentTimeMillis();
        e.latest = e.version;
        e.updateAvailable = false;
        store.put(e);
        done.add(item.name);
    }

    private static boolean containsIgnoreCase(List<String> l, String s) {
        for (String x : l) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    private static String safeName(String s) {
        String n = s.replaceAll("[^A-Za-z0-9._+-]", "_");
        if (!n.toLowerCase(Locale.ROOT).endsWith(".zip")) n += ".zip";
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    private void progress(final Model.Item item, final String text, final float fraction) {
        main.post(() -> {
            if (!busy.containsKey(item.key())) return;
            busy.put(item.key(), text);
            progressTitle.setText(item.name + (queued.get() > 1 ? "  ·  ещё " + (queued.get() - 1) + " в очереди" : ""));
            progressText.setText(text);
            if (fraction < 0) progressBar.setIndeterminate(true);
            else { progressBar.setIndeterminate(false); progressBar.setProgress((int) (fraction * 1000)); }
            progressCard.setVisibility(View.VISIBLE);
        });
    }

    private void updateProgressCard() {
        if (progressCard == null) return;
        if (busy.isEmpty()) { progressCard.setVisibility(View.GONE); return; }
        progressCard.setVisibility(View.VISIBLE);
        Map.Entry<String, String> first = null;
        for (Map.Entry<String, String> en : busy.entrySet()) { if (!"В очереди…".equals(en.getValue())) { first = en; break; } }
        if (first == null) first = busy.entrySet().iterator().next();
        progressTitle.setText(nameOfKey(first.getKey()) + (busy.size() > 1 ? "  ·  ещё " + (busy.size() - 1) + " в очереди" : ""));
        progressText.setText(first.getValue());
        progressBar.setIndeterminate(true);
    }

    private String nameOfKey(String key) {
        Model.Item c = Catalog.byKey(key);
        if (c != null) return c.name;
        Model.Entry e = store.entry(key);
        if (e != null) return e.name;
        for (List<Model.Item> l : results.values()) for (Model.Item it : l) if (it.key().equals(key)) return it.name;
        return key;
    }

    private void adoptCurse(final String id, final Model.Installed r) {
        snack("Ищу на CurseForge…");
        net.execute(() -> {
            try {
                final Model.Item it = Sources.Curse.mod(id);
                if (it == null) throw new IOException("Аддон не найден на CurseForge");
                main.post(() -> install(it, null));
            } catch (Exception e) {
                final String m = friendly(e);
                main.post(() -> snack(m));
            }
        });
    }

    private void adoptWowi(final String id, final Model.Installed r) {
        Model.Item it = new Model.Item();
        it.source = Model.SRC_WOWI;
        it.id = id;
        it.name = r.title;
        it.author = r.author == null ? "" : r.author;
        it.pageUrl = "https://www.wowinterface.com/downloads/info" + id;
        install(it, null);
    }

    private void confirmRemove(final Model.Installed r) {
        final List<String> folders = new ArrayList<>();
        for (Model.LocalAddon a : r.folders) folders.add(a.folder);
        if (r.entry != null) for (String f : r.entry.folders) if (!containsIgnoreCase(folders, f)) folders.add(f);
        confirm("Удалить " + r.title + "?", "Будут удалены папки: " + joinShort(folders, 10) + ".\nНастройки аддона в WTF останутся.", "Удалить", () -> {
            final File addons = store.addonsDir();
            selected = null;
            if (!landscape) closePortraitDetail(); else renderDetail();
            snack("Удаляю " + r.title + "…");
            ops.execute(() -> {
                String err = null;
                try {
                    Installer.remove(addons, folders);
                    if (r.entry != null) store.remove(r.entry.key);
                } catch (IOException e) {
                    err = friendly(e);
                }
                final String fe = err;
                main.post(() -> {
                    if (isDestroyed()) return;
                    snack(fe != null ? fe : "Удалено: " + r.title);
                    rescan();
                });
            });
        });
    }

    private void confirmRemoveEntry(final Model.Entry e) {
        Model.Installed r = null;
        for (Model.Installed x : installed) if (x.entry != null && x.entry.key.equals(e.key)) r = x;
        if (r == null) { r = new Model.Installed(); r.entry = e; r.title = e.name; }
        confirmRemove(r);
    }

    // ── ZIP ──

    private void pickZip() {
        if (!requireGame()) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream"});
        try { startActivityForResult(i, REQ_ZIP); } catch (Exception e) { snack("Не найден файловый менеджер"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ZIP && res == RESULT_OK && data != null && data.getData() != null) installZip(data.getData());
    }

    private void installZip(final Uri uri) {
        final String name = displayName(uri);
        final String key = "zip:" + name;
        if (busy.containsKey(key)) return;
        busy.put(key, "Копирование…");
        queued.incrementAndGet();
        updateProgressCard();
        final File addons = store.addonsDir();
        ops.execute(() -> {
            String err = null;
            List<String> folders = null;
            try {
                File dl = new File(getCacheDir(), "dl");
                dl.mkdirs();
                File zip = new File(dl, safeName(name));
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException("Не удалось открыть файл");
                OutputStream out = new FileOutputStream(zip);
                try {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally { out.close(); in.close(); }
                folders = Installer.install(zip, addons, null);
                zip.delete();
                // folders taken over from managed entries stay managed by them only if unchanged — release them
                for (String f : folders) {
                    Model.Entry other = store.owner(f);
                    if (other != null) {
                        other.folders = new ArrayList<>(other.folders);
                        other.folders.remove(f);
                        if (other.folders.isEmpty()) store.remove(other.key); else store.put(other);
                    }
                }
            } catch (Exception e) {
                err = friendly(e);
            }
            final String fe = err;
            final List<String> ff = folders;
            main.post(() -> {
                if (isDestroyed()) return;
                busy.remove(key);
                queued.decrementAndGet();
                updateProgressCard();
                if (fe != null) confirm("Не удалось установить ZIP", fe, null, null);
                else snack("Установлено из ZIP: " + joinShort(ff, 4));
                rescan();
            });
        });
    }

    private String displayName(Uri uri) {
        String n = null;
        try (android.database.Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) n = c.getString(0);
        } catch (Exception ignored) {}
        if (n == null) n = uri.getLastPathSegment();
        return n == null ? "addon.zip" : n;
    }

    // ─────────────────────────── storage & folders ───────────────────────────

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorage() {
        scanned = false;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception e2) {
                    snack("Откройте Настройки → Приложения → WoW Addons → Доступ ко всем файлам");
                }
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQ_PERM && hasStorageAccess()) {
            if (store.gameRoot() == null) autoDetect(false);
            store.load();
            render();
            rescan();
        }
    }

    private void autoDetect(boolean interactive) {
        List<File> found = Store.detect();
        if (found.isEmpty()) {
            if (interactive) confirm("WoW Forever не найден", "Не нашёл папку с игрой во внутренней памяти и на SD-картах. Выберите её вручную.", "Выбрать папку", this::openFolderPicker);
            return;
        }
        if (found.size() == 1 || !interactive) {
            setGame(found.get(0), interactive);
            return;
        }
        // several installs: let the user choose
        LinearLayout col = Ui.vbox(this);
        for (final File f : found) {
            LinearLayout b = Ui.button(this, f.getPath(), R.drawable.ic_folder, Ui.Kind.SECONDARY, v -> { closeModal(); setGame(f, true); });
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            col.addView(b, Ui.margins(Ui.lp(-1, Ui.dp(44)), 0, 0, 0, 8));
        }
        modal("Найдено несколько установок", null, col, null);
    }

    private void setGame(File root, boolean announce) {
        store.setGameRoot(root.getPath());
        store.load();
        scanned = false;
        installed = new ArrayList<>();
        checkedOnce = false;
        if (announce) snack("Папка игры: " + root.getPath());
        if (this.root != null) render();
        rescan();
    }

    private File pickerDir;

    private void openFolderPicker() {
        if (!hasStorageAccess()) { requestStorage(); return; }
        File start = store.root();
        if (start == null || !start.isDirectory()) start = Environment.getExternalStorageDirectory();
        pickerDir = start;
        showPicker();
    }

    private void showPicker() {
        final LinearLayout col = Ui.vbox(this);
        // quick roots
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout roots = Ui.hbox(this);
        List<File> vols = new ArrayList<>();
        vols.add(Environment.getExternalStorageDirectory());
        File[] st = new File("/storage").listFiles();
        if (st != null) for (File v : st) if (!v.getName().equals("emulated") && !v.getName().equals("self") && v.isDirectory()) vols.add(v);
        for (final File v : vols) {
            boolean internal = v.equals(Environment.getExternalStorageDirectory());
            LinearLayout b = Ui.button(this, internal ? "Внутренняя память" : "SD " + v.getName(), internal ? R.drawable.ic_folder : R.drawable.ic_sd, Ui.Kind.GHOST, x -> { pickerDir = v; closeModal(); showPicker(); });
            roots.addView(b, Ui.margins(Ui.lp(-2, Ui.dp(38)), 0, 0, 8, 0));
        }
        hs.addView(roots);
        col.addView(hs, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));

        LinearLayout pathRow = Ui.hbox(this);
        pathRow.setBackground(Ui.round(Ui.SURFACE2, 10));
        pathRow.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        TextView p = Ui.text(this, pickerDir.getPath(), 13, Ui.TEXT, Ui.MEDIUM);
        p.setSingleLine(true);
        p.setEllipsize(android.text.TextUtils.TruncateAt.START);
        pathRow.addView(p, Ui.lpw(0, -2, 1));
        final File parent = pickerDir.getParentFile();
        if (parent != null && parent.canRead() && !pickerDir.equals(Environment.getExternalStorageDirectory())) {
            pathRow.addView(Ui.button(this, null, R.drawable.ic_up, Ui.Kind.SECONDARY, v -> { pickerDir = parent; closeModal(); showPicker(); }), Ui.lp(-2, Ui.dp(36)));
        }
        col.addView(pathRow, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));

        File detected = Store.normalizeRoot(pickerDir);
        if (detected != null) {
            TextView ok = Ui.text(this, "✓ Здесь найден WoW Forever: " + detected.getName(), 12.5f, Ui.GREEN, Ui.MEDIUM);
            col.addView(ok, Ui.margins(Ui.lp(-1, -2), 2, 0, 0, 8));
        }

        final List<File> dirs = new ArrayList<>();
        File[] kids = pickerDir.listFiles();
        if (kids != null) for (File k : kids) if (k.isDirectory() && !k.getName().startsWith(".")) dirs.add(k);
        Collections.sort(dirs, (a, b) -> {
            boolean wa = Store.normalizeRoot(a) != null, wb = Store.normalizeRoot(b) != null;
            if (wa != wb) return wa ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(Ui.dp(4));
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0));
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return dirs.size(); }
            @Override public Object getItem(int i) { return dirs.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parentV) {
                File d = dirs.get(i);
                LinearLayout row = Ui.hbox(MainActivity.this);
                StateListShell(row, false);
                row.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
                row.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                boolean wow = Store.normalizeRoot(d) != null;
                row.addView(Ui.icon(MainActivity.this, R.drawable.ic_folder, wow ? Ui.ACCENT : Ui.TEXT3, 20));
                TextView t = Ui.oneLine(Ui.text(MainActivity.this, d.getName(), 14, Ui.TEXT, Ui.MEDIUM));
                row.addView(t, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 0, 0));
                if (wow) row.addView(Ui.pill(MainActivity.this, "WoW Forever", Ui.GREEN, Ui.GREEN_SOFT));
                row.addView(Ui.icon(MainActivity.this, R.drawable.ic_chevron, Ui.TEXT3, 18));
                return row;
            }
        });
        lv.setOnItemClickListener((pa, v, pos, id) -> { pickerDir = dirs.get(pos); closeModal(); showPicker(); });
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int h = landscape ? Math.max(Ui.dp(110), screenH - Ui.dp(detected != null ? 300 : 276)) : (int) (screenH * 0.5f);
        col.addView(lv, Ui.lp(-1, h));
        if (dirs.isEmpty()) {
            TextView empty = Ui.text(this, "Пусто", 13, Ui.TEXT3, Ui.REGULAR);
            col.addView(empty, Ui.margins(Ui.lp(-2, -2), 4, 4, 0, 0));
        }

        LinearLayout btns = Ui.hbox(this);
        btns.setGravity(Gravity.END);
        btns.addView(Ui.button(this, "Отмена", 0, Ui.Kind.GHOST, v -> closeModal()), Ui.lp(-2, Ui.dp(42)));
        LinearLayout choose = Ui.button(this, "Выбрать эту папку", R.drawable.ic_check, Ui.Kind.PRIMARY, v -> {
            File r = Store.normalizeRoot(pickerDir);
            closeModal();
            if (r == null) {
                confirm("Это не папка WoW Forever", "Здесь нет .build.info или _forever_. Если игра ещё не установлена, сначала установите её в приложении WoW Forever.", "Всё равно выбрать", () -> {
                    new File(pickerDir, Store.FLAVOR_DIR + "/Interface/AddOns").mkdirs();
                    setGame(pickerDir, true);
                });
            } else {
                setGame(r, true);
            }
        });
        btns.addView(choose, Ui.margins(Ui.lp(-2, Ui.dp(42)), 8, 0, 0, 0));
        col.addView(btns, Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));
        modal("Папка игры", null, col, null);
        if (detected != null) choose.post(choose::requestFocus); else lv.post(lv::requestFocus);
    }

    // ─────────────────────────── modal / snack / misc ───────────────────────────

    private void modal(String title, String message, View content, View buttons) {
        closeModal();
        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(0xB3000000);
        dim.setClickable(true);
        dim.setOnClickListener(v -> closeModal());
        LinearLayout card = Ui.vbox(this);
        card.setBackground(Ui.round(Ui.SURFACE, 20, Ui.STROKE, 1));
        card.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(20), Ui.dp(16));
        card.setClickable(true);
        card.setElevation(Ui.dp(20));
        TextView t = Ui.text(this, title, 18, Ui.TEXT, Ui.BOLD);
        card.addView(t, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, message == null ? 12 : 8));
        if (message != null) {
            TextView m = Ui.text(this, message, 14, Ui.TEXT2, Ui.REGULAR);
            ScrollView ms = new ScrollView(this);
            ms.addView(m);
            card.addView(ms, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 16));
        }
        if (content != null) card.addView(content, Ui.lp(-1, -2));
        if (buttons != null) card.addView(buttons, Ui.lp(-1, -2));
        int w = Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(32), Ui.dp(landscape ? 560 : 520));
        FrameLayout.LayoutParams cl = Ui.flp(w, -2, Gravity.CENTER);
        dim.addView(card, cl);
        modalLayer.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        card.setAlpha(0f);
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    private void closeModal() {
        if (modalLayer != null) modalLayer.removeAllViews();
    }

    private void confirm(String title, String message, String okLabel, final Runnable ok) {
        LinearLayout btns = Ui.hbox(this);
        btns.setGravity(Gravity.END);
        LinearLayout cancel = Ui.button(this, ok == null ? "Понятно" : "Отмена", 0, ok == null ? Ui.Kind.PRIMARY : Ui.Kind.GHOST, v -> closeModal());
        btns.addView(cancel, Ui.lp(-2, Ui.dp(42)));
        LinearLayout okb = null;
        if (ok != null) {
            boolean danger = okLabel != null && okLabel.startsWith("Удал");
            okb = Ui.button(this, okLabel, 0, danger ? Ui.Kind.DANGER : Ui.Kind.PRIMARY, v -> { closeModal(); ok.run(); });
            btns.addView(okb, Ui.margins(Ui.lp(-2, Ui.dp(42)), 8, 0, 0, 0));
        }
        modal(title, message, null, btns);
        final View f = okb != null ? okb : cancel;
        f.post(f::requestFocus);
    }

    private void snack(String text) {
        if (snack == null) return;
        snack.setText(text);
        snack.setAlpha(0f);
        snack.setVisibility(View.VISIBLE);
        snack.animate().alpha(1f).setDuration(160).start();
        main.removeCallbacks(hideSnack);
        main.postDelayed(hideSnack, Math.max(2600, Math.min(6000, text.length() * 60)));
    }

    private void openUrl(String url) {
        if (url == null) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { snack("Не найден браузер"); }
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (v != null && imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void showKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    static String friendly(Throwable e) {
        if (e instanceof java.net.UnknownHostException) return "Нет подключения к интернету";
        if (e instanceof java.net.SocketTimeoutException) return "Сервер не ответил вовремя. Попробуйте ещё раз.";
        if (e instanceof javax.net.ssl.SSLException) return "Ошибка защищённого соединения";
        if (e instanceof java.util.zip.ZipException) return "Файл не является zip-архивом или повреждён";
        if (e instanceof Http.HttpException) {
            int c = ((Http.HttpException) e).code;
            if (c == 404) return "Файл не найден на сервере (404)";
            if (c == 403 || c == 429) return "Сервер ограничил запросы (" + c + "). Попробуйте позже.";
        }
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
