package app.wowforever.addons;

import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds and picks the WoW Forever game folder. */
final class GameFolder extends Screen {

    GameFolder(MainActivity app) { super(app); }

    void autoDetect(boolean interactive) {
        List<File> found = Store.detect();
        if (found.isEmpty()) {
            if (interactive) app.confirm(s(R.string.game_not_found_title), s(R.string.game_not_found_text), s(R.string.game_pick_folder), this::openFolderPicker);
            return;
        }
        if (found.size() == 1 || !interactive) {
            setGame(found.get(0), interactive);
            return;
        }
        // several installs: let the user choose
        LinearLayout col = Ui.vbox(app);
        for (final File f : found) {
            LinearLayout b = Ui.button(app, f.getPath(), R.drawable.ic_folder, Ui.Kind.SECONDARY, v -> { app.closeModal(); setGame(f, true); });
            b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            col.addView(b, Ui.margins(Ui.lp(-1, Ui.dp(44)), 0, 0, 0, 8));
        }
        app.modal(s(R.string.game_multiple_found), null, col, null);
    }

    private void setGame(File root, boolean announce) {
        app.store.setGameRoot(root.getPath());
        app.store.load();
        app.scanned = false;
        app.installed = new ArrayList<>();
        app.checkedOnce = false;
        if (announce) app.snack(s(R.string.game_folder_set, root.getPath()));
        if (app.root != null) app.render();
        app.manager.rescan();
    }

    private File pickerDir;

    void openFolderPicker() {
        if (!app.hasStorageAccess()) { app.requestStorage(); return; }
        File start = app.store.root();
        if (start == null || !start.isDirectory()) start = Environment.getExternalStorageDirectory();
        pickerDir = start;
        showPicker();
    }

    private void showPicker() {
        final LinearLayout col = Ui.vbox(app);
        // quick roots
        HorizontalScrollView hs = new HorizontalScrollView(app);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout roots = Ui.hbox(app);
        List<File> vols = new ArrayList<>();
        vols.add(Environment.getExternalStorageDirectory());
        File[] st = new File("/storage").listFiles();
        if (st != null) for (File v : st) if (!v.getName().equals("emulated") && !v.getName().equals("self") && v.isDirectory()) vols.add(v);
        for (final File v : vols) {
            boolean internal = v.equals(Environment.getExternalStorageDirectory());
            boolean here = pickerDir.equals(v) || pickerDir.getPath().startsWith(v.getPath() + "/");
            LinearLayout b = Ui.button(app, internal ? s(R.string.picker_internal) : s(R.string.picker_sd, v.getName()), internal ? R.drawable.ic_folder : R.drawable.ic_sd, here ? Ui.Kind.SECONDARY : Ui.Kind.GHOST, x -> { pickerDir = v; app.closeModal(); showPicker(); });
            roots.addView(b, Ui.margins(Ui.lp(-2, Ui.dp(38)), 0, 0, 8, 0));
        }
        hs.addView(roots);
        col.addView(hs, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));

        LinearLayout pathRow = Ui.hbox(app);
        pathRow.setBackground(Ui.round(Ui.SURFACE2, 12, Ui.STROKE, 1));
        pathRow.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        pathRow.setMinimumHeight(Ui.dp(44));
        TextView p = Ui.text(app, pickerDir.getPath(), 13, Ui.TEXT, Ui.MEDIUM);
        p.setSingleLine(true);
        p.setEllipsize(android.text.TextUtils.TruncateAt.START);
        pathRow.addView(p, Ui.lpw(0, -2, 1));
        final File parent = pickerDir.getParentFile();
        if (parent != null && parent.canRead() && !pickerDir.equals(Environment.getExternalStorageDirectory())) {
            pathRow.addView(Ui.button(app, null, R.drawable.ic_up, Ui.Kind.SECONDARY, v -> { pickerDir = parent; app.closeModal(); showPicker(); }), Ui.lp(-2, Ui.dp(36)));
        }
        col.addView(pathRow, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));

        File detected = Store.normalizeRoot(pickerDir);
        if (detected != null) {
            LinearLayout found = Ui.hbox(app);
            found.setBackground(Ui.round(Ui.GREEN_SOFT, 12));
            found.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
            found.addView(Ui.icon(app, R.drawable.ic_check, Ui.GREEN, 16));
            TextView ok = Ui.text(app, s(R.string.picker_found, detected.getName()), 12.5f, Ui.GREEN, Ui.MEDIUM);
            found.addView(ok, Ui.margins(Ui.lpw(0, -2, 1f), 8, 0, 0, 0));
            col.addView(found, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        }

        final List<File> dirs = new ArrayList<>();
        File[] kids = pickerDir.listFiles();
        if (kids != null) for (File k : kids) if (k.isDirectory() && !k.getName().startsWith(".")) dirs.add(k);
        Collections.sort(dirs, (a, b) -> {
            boolean wa = Store.normalizeRoot(a) != null, wb = Store.normalizeRoot(b) != null;
            if (wa != wb) return wa ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        ListView lv = new ListView(app);
        lv.setDivider(null);
        lv.setDividerHeight(Ui.dp(6));
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0));
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return dirs.size(); }
            @Override public Object getItem(int i) { return dirs.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parentV) {
                File d = dirs.get(i);
                LinearLayout row = Ui.hbox(app);
                StateListShell(row, false);
                row.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(10), Ui.dp(10));
                row.setMinimumHeight(Ui.dp(48));
                row.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                boolean wow = Store.normalizeRoot(d) != null;
                row.addView(Ui.icon(app, R.drawable.ic_folder, wow ? Ui.ACCENT : Ui.TEXT3, 20));
                TextView t = Ui.oneLine(Ui.text(app, d.getName(), 14, Ui.TEXT, Ui.MEDIUM));
                row.addView(t, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 0, 0));
                if (wow) row.addView(Ui.pill(app, "WoW Forever", Ui.GREEN, Ui.GREEN_SOFT), Ui.margins(Ui.lp(-2, -2), 8, 0, 4, 0));
                row.addView(Ui.icon(app, R.drawable.ic_chevron, Ui.TEXT3, 18));
                return row;
            }
        });
        lv.setOnItemClickListener((pa, v, pos, id) -> { pickerDir = dirs.get(pos); app.closeModal(); showPicker(); });
        int screenH = app.getResources().getDisplayMetrics().heightPixels;
        int h = app.landscape ? Math.max(Ui.dp(110), screenH - Ui.dp(detected != null ? 316 : 276)) : (int) (screenH * 0.5f);
        col.addView(lv, Ui.lp(-1, h));
        if (dirs.isEmpty()) {
            TextView empty = Ui.text(app, s(R.string.picker_empty), 13, Ui.TEXT3, Ui.REGULAR);
            col.addView(empty, Ui.margins(Ui.lp(-2, -2), 4, 4, 0, 0));
        }

        LinearLayout btns = Ui.hbox(app);
        btns.setGravity(Gravity.END);
        btns.addView(Ui.button(app, s(R.string.action_cancel), 0, Ui.Kind.GHOST, v -> app.closeModal()), Ui.lp(-2, Ui.dp(42)));
        LinearLayout choose = Ui.button(app, s(R.string.action_choose_this), R.drawable.ic_check, Ui.Kind.PRIMARY, v -> {
            File r = Store.normalizeRoot(pickerDir);
            app.closeModal();
            if (r == null) {
                app.confirm(s(R.string.picker_not_game_title), s(R.string.picker_not_game_text), s(R.string.action_choose_anyway), () -> {
                    new File(pickerDir, Store.FLAVOR_DIR + "/Interface/AddOns").mkdirs();
                    setGame(pickerDir, true);
                });
            } else {
                setGame(r, true);
            }
        });
        btns.addView(choose, Ui.margins(Ui.lp(-2, Ui.dp(42)), 8, 0, 0, 0));
        col.addView(btns, Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));
        app.modal(s(R.string.settings_game_folder), null, col, null);
        if (detected != null) choose.post(choose::requestFocus); else lv.post(lv::requestFocus);
    }
}
