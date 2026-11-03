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
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    final SetupScreen setup = new SetupScreen(this);
    final LibraryScreen library = new LibraryScreen(this);
    final BrowseScreen browse = new BrowseScreen(this);
    final SettingsScreen settingsScreen = new SettingsScreen(this);
    final DetailPanel detail = new DetailPanel(this);
    final AddonManager manager = new AddonManager(this);
    final GameFolder gameFolder = new GameFolder(this);

    static final int TAB_INSTALLED = 0, TAB_BROWSE = 1, TAB_SETTINGS = 2;
    static final int SRC_CATALOG = 0, SRC_CURSE = 1, SRC_WOWI = 2, SRC_GITHUB = 3;
    private static final int REQ_ZIP = 41, REQ_PERM = 42;

    final Handler main = new Handler(Looper.getMainLooper());
    static final ExecutorService net = Executors.newFixedThreadPool(4);
    static final ExecutorService ops = Executors.newSingleThreadExecutor();

    Store store;

    // ── UI state ──
    int tab = TAB_INSTALLED;
    boolean landscape;
    LayoutSpec layout;
    final Map<Integer, List<Model.Item>> results = new HashMap<>();

    List<Model.Installed> installed = new ArrayList<>();
    boolean scanned = false;
    boolean checking = false;
    boolean checkedOnce = false;

    Object selected;                 // Model.Item or Model.Installed
    boolean detailSheetOpen = false;
    final Map<String, String> busy = new LinkedHashMap<>();   // key -> status text

    // ── views ──
    FrameLayout root;
    LinearLayout headerBox;
    FrameLayout bodyBox;
    FrameLayout detailBox;
    FrameLayout detailSheet;
    private FrameLayout detailOverlay;
    private View rail;
    private LinearLayout shell;
    LinearLayout progressCard;
    TextView progressTitle, progressText;
    ProgressBar progressBar;
    TextView snack;
    private FrameLayout modalLayer;
    private final List<View> navItems = new ArrayList<>();
    EditText searchField;
    ListView currentList;
    BaseAdapter currentAdapter;

    private final Runnable hideSnack = () -> { if (snack != null) snack.animate().alpha(0f).setDuration(220).withEndAction(() -> snack.setVisibility(View.GONE)).start(); };

    // ─────────────────────────── lifecycle ───────────────────────────

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.density = getResources().getDisplayMetrics().density;
        android.view.WindowManager.LayoutParams wl = getWindow().getAttributes();
        wl.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(wl);
        ImageLoader.dir = new File(getCacheDir(), "img");
        ImageLoader.dir.mkdirs();
        store = new Store(this);
        if (store.gameRoot() == null && hasStorageAccess()) gameFolder.autoDetect(false);
        store.load();
        buildShell();
        manager.rescan();
    }

    private Boolean lastAccess;

    @Override protected void onResume() {
        super.onResume();
        applyImmersive();
        boolean access = hasStorageAccess();
        if (lastAccess != null && lastAccess != access && root != null) render();
        lastAccess = access;
        if (access && !scanned) {
            if (store.gameRoot() == null) gameFolder.autoDetect(false);
            store.load();
            manager.rescan();
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
        if (detailSheetOpen) { detail.closeDetailSheet(); return; }
        if (layout.docked && selected != null) { detail.select(null); return; }
        if (tab != TAB_INSTALLED) { switchTab(TAB_INSTALLED); return; }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (modalLayer != null && modalLayer.getChildCount() > 0) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) { switchTab((tab + 2) % 3); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) { switchTab((tab + 1) % 3); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_Y && tab == TAB_BROWSE && searchField != null) { searchField.requestFocus(); showKeyboard(searchField); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_X && tab == TAB_INSTALLED) { manager.checkUpdates(true); return true; }
        return super.onKeyDown(keyCode, event);
    }

    // ─────────────────────────── shell ───────────────────────────

    private void buildShell() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        layout = new LayoutSpec(Math.round(dm.widthPixels / dm.density), Math.round(dm.heightPixels / dm.density));
        landscape = layout.rail;
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        navItems.clear();

        shell = new LinearLayout(this);
        shell.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout mainCol = Ui.vbox(this);
        headerBox = Ui.vbox(this);
        bodyBox = new FrameLayout(this);
        mainCol.addView(headerBox, Ui.lp(-1, -2));
        mainCol.addView(bodyBox, Ui.lpw(-1, 0, 1));

        detailBox = null;
        detailSheet = null;
        detailOverlay = null;
        rail = null;
        if (landscape) {
            rail = buildRail();
            shell.addView(rail, Ui.lp(Ui.dp(LayoutSpec.RAIL_DP), -1));
            shell.addView(mainCol, Ui.lpw(0, -1, 1));
        } else {
            shell.addView(mainCol, Ui.lpw(-1, 0, 1));
            shell.addView(buildBottomNav(), Ui.lp(-1, Ui.dp(64)));
        }
        if (layout.docked) {
            detailBox = new FrameLayout(this);
            GradientDrawable dbg = new GradientDrawable();
            dbg.setColor(Ui.SURFACE);
            detailBox.setBackground(dbg);
            View edge = new View(this);
            edge.setBackgroundColor(Ui.STROKE);
            shell.addView(edge, Ui.lp(Math.max(1, Ui.dp(1)), -1));
            shell.addView(detailBox, Ui.lp(Ui.dp(layout.detailDp), -1));
            detailSheetOpen = false;
        } else {
            // Not enough room to dock: the detail opens over the list, as a side sheet
            // next to the rail or full screen in portrait.
            detailOverlay = new FrameLayout(this);
            detailSheet = new FrameLayout(this);
            detailSheet.setBackgroundColor(Ui.SURFACE);
            detailSheet.setClickable(true);
            if (layout.sheet) {
                View scrim = new View(this);
                scrim.setBackgroundColor(0x9E030807);
                scrim.setOnClickListener(v -> detail.closeDetailSheet());
                detailOverlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
                detailSheet.setElevation(Ui.dp(16));
                detailOverlay.addView(detailSheet, Ui.flp(Ui.dp(layout.detailDp), -1, Gravity.END));
            } else {
                detailOverlay.addView(detailSheet, new FrameLayout.LayoutParams(-1, -1));
            }
            detailOverlay.setVisibility(detailSheetOpen ? View.VISIBLE : View.GONE);
            root.addView(detailOverlay, new FrameLayout.LayoutParams(-1, -1));
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

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            applyCutout(insets.getDisplayCutout());
            return insets;
        });
        setContentView(root);
        applyImmersive();
        render();
        manager.updateProgressCard();
    }

    /** Shows or hides the detail sheet; only used when the detail is not docked. */
    void showDetailSheet(boolean show) {
        detailSheetOpen = show;
        if (detailOverlay != null) detailOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * The window draws under the camera cutout (see onCreate). The rail grows into the cutout
     * on the left; the content keeps clear of a cutout on the right.
     */
    private void applyCutout(android.view.DisplayCutout c) {
        int l = c == null ? 0 : c.getSafeInsetLeft();
        int r = c == null ? 0 : c.getSafeInsetRight();
        if (rail != null) {
            rail.setPadding(l, rail.getPaddingTop(), 0, rail.getPaddingBottom());
            rail.getLayoutParams().width = Ui.dp(LayoutSpec.RAIL_DP) + l;
            rail.requestLayout();
            shell.setPadding(0, 0, r, 0);
        } else {
            shell.setPadding(l, 0, r, 0);
        }
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

    void switchTab(int t) {
        if (t == tab) return;
        tab = t;
        selected = null;
        detailSheetOpen = false;
        hideKeyboard();
        buildShell();
        if (t == TAB_BROWSE) browse.ensureResults(false);
    }

    // ─────────────────────────── rendering ───────────────────────────

    void render() {
        headerBox.removeAllViews();
        bodyBox.removeAllViews();
        searchField = null;
        currentList = null;
        currentAdapter = null;
        if (!hasStorageAccess()) {
            setup.renderPermission();
        } else if (tab == TAB_SETTINGS) {
            settingsScreen.renderSettings();
        } else if (store.gameRoot() == null || !store.gameReady()) {
            if (tab == TAB_BROWSE) browse.renderBrowse(); else setup.renderNoGame();
        } else if (tab == TAB_BROWSE) {
            browse.renderBrowse();
        } else {
            library.renderInstalled();
        }
        detail.renderDetail();
    }

    void refreshList() {
        if (currentAdapter != null) currentAdapter.notifyDataSetChanged();
    }

    void renderIfTab(int t) { if (tab == t && root != null) render(); }

    // ── ZIP ──

    void pickZip() {
        if (!manager.requireGame()) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream"});
        try { startActivityForResult(i, REQ_ZIP); } catch (Exception e) { snack("Не найден файловый менеджер"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_ZIP && res == RESULT_OK && data != null && data.getData() != null) manager.installZip(data.getData());
    }

    // ─────────────────────────── storage & folders ───────────────────────────

    boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    void requestStorage() {
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
            if (store.gameRoot() == null) gameFolder.autoDetect(false);
            store.load();
            render();
            manager.rescan();
        }
    }

    // ─────────────────────────── modal / snack / misc ───────────────────────────

    void modal(String title, String message, View content, View buttons) {
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

    void closeModal() {
        if (modalLayer != null) modalLayer.removeAllViews();
    }

    void confirm(String title, String message, String okLabel, final Runnable ok) {
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

    void snack(String text) {
        if (snack == null) return;
        snack.setText(text);
        snack.setAlpha(0f);
        snack.setVisibility(View.VISIBLE);
        snack.animate().alpha(1f).setDuration(160).start();
        main.removeCallbacks(hideSnack);
        main.postDelayed(hideSnack, Math.max(2600, Math.min(6000, text.length() * 60)));
    }

    void openUrl(String url) {
        if (url == null) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { snack("Не найден браузер"); }
    }

    void hideKeyboard() {
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
