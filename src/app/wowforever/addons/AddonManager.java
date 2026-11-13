package app.wowforever.addons;

import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Scans, installs, updates and removes addons, and reports progress. */
final class AddonManager {
    final MainActivity app;
    private final AtomicInteger queued = new AtomicInteger();

    AddonManager(MainActivity app) { this.app = app; }

    void rescan() {
        if (!app.hasStorageAccess() || !app.store.gameReady()) { app.scanned = true; app.installed = new ArrayList<>(); app.renderIfTab(MainActivity.TAB_INSTALLED); return; }
        final File addons = app.store.addonsDir();
        MainActivity.net.execute(() -> {
            List<Model.LocalAddon> locals = addons.isDirectory() ? Toc.scan(addons) : new ArrayList<Model.LocalAddon>();
            Index.loadCached();
            final List<Model.Installed> rows = Library.rows(app.store, locals);
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                Library.carryOver(app.installed, rows);
                app.installed = rows;
                app.scanned = true;
                if (app.tab == MainActivity.TAB_INSTALLED) { app.render(); }
                else if (app.tab == MainActivity.TAB_BROWSE) app.refreshList();
                if (!app.checkedOnce && app.store.list().size() > 0) { app.checkedOnce = true; checkUpdates(false); }
            });
        });
    }

    /** Every Library row, the modules of packages included. */
    static List<Model.Installed> all(List<Model.Installed> rows) { return Library.all(rows); }

    void checkUpdates(final boolean manual) {
        if (app.checking) return;
        final List<Model.Entry> list = Library.checkable(app.store);
        if (list.isEmpty()) { if (manual) app.snack(app.getString(R.string.no_managed_addons)); return; }
        app.checking = true;
        app.renderIfTab(MainActivity.TAB_INSTALLED);
        final AtomicInteger left = new AtomicInteger(list.size());
        MainActivity.net.execute(() -> {
            // One index download answers every GitHub addon it lists; the rest are asked one by one.
            Map<String, Model.Item> index = Updater.indexByKey(manual);
            for (final Model.Entry e : list) {
                final Model.Item ix = Updater.indexHit(index, e);
                if (ix != null) {
                    app.main.post(() -> finishCheck(e, ix.version, ix.changelog, null, left, manual));
                    continue;
                }
                MainActivity.net.execute(() -> {
                    String latest = null, err = null;
                    try {
                        latest = Updater.latestVersion(e);
                    } catch (Exception ex) {
                        err = MainActivity.friendly(ex);
                    }
                    final String fl = latest, fe = err;
                    app.main.post(() -> finishCheck(e, fl, null, fe, left, manual));
                });
            }
        });
    }

    private void finishCheck(Model.Entry e, String latest, String changelog, String err, AtomicInteger left, boolean manual) {
        if (app.isDestroyed()) return;
        Library.applyCheck(e, latest, changelog, err);
        if (left.decrementAndGet() == 0) {
            app.checking = false;
            app.store.setLastCheck(System.currentTimeMillis());
            int n = Library.updates(app.store);
            if (manual || n > 0) app.snack(n == 0 ? app.getString(R.string.all_up_to_date) : app.getResources().getQuantityString(R.plurals.updates_available, n, n));
        }
        if (app.tab == MainActivity.TAB_INSTALLED) app.render();
    }

    void checkOne(final Model.Entry e) {
        app.snack(app.getString(R.string.checking_one, e.name));
        MainActivity.net.execute(() -> {
            String latest = null, err = null;
            try { latest = Updater.latestVersion(e); } catch (Exception ex) { err = MainActivity.friendly(ex); }
            final String fl = latest, fe = err;
            app.main.post(() -> {
                e.checkError = fe;
                if (fl != null) { e.latest = fl; e.updateAvailable = UpdatePolicy.isNewer(fl, e.version); }
                app.snack(fe != null ? fe : (e.updateAvailable ? app.getString(R.string.version_available, fl) : app.getString(R.string.latest_installed)));
                if (app.tab == MainActivity.TAB_INSTALLED) app.render();
            });
        });
    }

    static boolean sameVersion(String a, String b) { return UpdatePolicy.sameVersion(a, b); }

    boolean requireGame() {
        if (!app.hasStorageAccess()) { app.requestStorage(); return false; }
        if (!app.store.gameReady()) {
            app.confirm(app.getString(R.string.game_not_set), app.getString(R.string.game_not_set_dialog),
                    app.getString(R.string.game_pick_folder), app.gameFolder::openFolderPicker);
            return false;
        }
        return true;
    }

    void update(Model.Entry e) {
        install(Sources.itemFor(e), null);
    }

    /** Updates every part of a package (or a single row) that has an update. */
    void updatePackage(Model.Installed r) {
        for (Model.Installed m : r.withModules())
            if (m.entry != null && m.entry.updateAvailable && !app.busy.containsKey(m.entry.key)) update(m.entry);
    }

    void updateAll() {
        int n = 0;
        for (Model.Entry e : app.store.list()) if (e.updateAvailable && !app.busy.containsKey(e.key)) { update(e); n++; }
        if (n > 0) app.snack(app.getString(R.string.updating_n, n));
    }

    /**
     * Installs an item. A package core that is not installed yet brings every module with it: people
     * want the whole addon and turn parts off in the game, or remove a module in the Library.
     */
    void install(final Model.Item item, final Runnable after) {
        if (!requireGame()) return;
        final List<Model.Item> parts = Library.parts(app.store, item);
        if (app.busy.containsKey(item.key())) return;
        app.busy.put(item.key(), app.getString(R.string.queued));
        queued.incrementAndGet();
        updateProgressCard();
        app.refreshList();
        app.detail.renderDetail();
        final File addons = app.store.addonsDir();
        MainActivity.ops.execute(() -> {
            String error = null;
            List<String> installedNames = new ArrayList<>();
            try {
                Set<String> seen = new HashSet<>();
                for (Model.Item part : parts) installWithDeps(part, addons, installedNames, seen, 0, item);
            } catch (Exception e) {
                error = MainActivity.friendly(e);
            }
            final String fe = error;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                app.busy.remove(item.key());
                queued.decrementAndGet();
                updateProgressCard();
                if (fe != null) {
                    app.confirm(app.getString(R.string.install_failed, item.name), fe, null, null);
                } else {
                    String names = parts.size() > 1 ? app.getResources().getQuantityString(R.plurals.package_installed, parts.size() - 1, item.name, parts.size() - 1)
                            : installedNames.size() > 1 ? String.join(", ", installedNames) : item.name;
                    app.snack(app.getString(R.string.installed_names, names));
                }
                rescan();
                app.refreshList();
                app.detail.renderDetail();
                if (after != null) after.run();
            });
        });
    }

    /** Runs on the ops thread. */
    /** The progress of a dependency or a module shows on the card of the item the user asked for. */
    private void installWithDeps(Model.Item item, File addons, List<String> done, Set<String> seen, int depth, final Model.Item asked) throws IOException {
        new Updater(app.store).installWithDeps(item, addons, done, seen, depth,
                (i, t, f) -> progress(asked, i.key().equals(asked.key()) ? t : i.name + "  ·  " + t, f));
    }

    private static boolean containsIgnoreCase(List<String> l, String s) { return Updater.containsIgnoreCase(l, s); }

    private static String safeName(String s) { return Updater.safeName(s); }

    private void progress(final Model.Item item, final String text, final float fraction) {
        app.main.post(() -> {
            if (!app.busy.containsKey(item.key())) return;
            app.busy.put(item.key(), text);
            app.progressTitle.setText(item.name + (queued.get() > 1 ? "  ·  " + app.getString(R.string.more_queued, queued.get() - 1) : ""));
            app.progressText.setText(text);
            if (fraction < 0) app.progressBar.setIndeterminate(true);
            else { app.progressBar.setIndeterminate(false); app.progressBar.setProgress((int) (fraction * 1000)); }
            app.progressCard.setVisibility(View.VISIBLE);
        });
    }

    void updateProgressCard() {
        if (app.progressCard == null) return;
        if (app.busy.isEmpty()) { app.progressCard.setVisibility(View.GONE); return; }
        app.progressCard.setVisibility(View.VISIBLE);
        Map.Entry<String, String> first = null;
        for (Map.Entry<String, String> en : app.busy.entrySet()) { if (!app.getString(R.string.queued).equals(en.getValue())) { first = en; break; } }
        if (first == null) first = app.busy.entrySet().iterator().next();
        app.progressTitle.setText(nameOfKey(first.getKey()) + (app.busy.size() > 1 ? "  ·  " + app.getString(R.string.more_queued, app.busy.size() - 1) : ""));
        app.progressText.setText(first.getValue());
        app.progressBar.setIndeterminate(true);
    }

    private String nameOfKey(String key) {
        Model.Item c = Catalog.byKey(key);
        if (c != null) return c.name;
        Model.Entry e = app.store.entry(key);
        if (e != null) return e.name;
        for (List<Model.Item> l : app.results.values()) for (Model.Item it : l) if (it.key().equals(key)) return it.name;
        return key;
    }

    void adoptWowi(final String id, final Model.Installed r) {
        Model.Item it = new Model.Item();
        it.source = Model.SRC_WOWI;
        it.id = id;
        it.name = r.title;
        it.author = r.author == null ? "" : r.author;
        it.pageUrl = "https://www.wowinterface.com/downloads/info" + id;
        install(it, null);
    }

    /** Removes a row; the core of a package takes its modules with it, since they cannot run without it. */
    void confirmRemove(final Model.Installed r) {
        final List<String> folders = Library.removeFolders(r);
        app.confirm(app.getString(R.string.remove_title, r.title), app.getString(R.string.remove_text, DetailPanel.joinShort(folders, 10)), app.getString(R.string.action_remove), () -> {
            app.selected = null;
            if (!app.layout.docked) app.detail.closeDetailSheet(); else app.detail.renderDetail();
            app.snack(app.getString(R.string.removing, r.title));
            MainActivity.ops.execute(() -> {
                String err = null;
                try {
                    Library.remove(app.store, r);
                } catch (IOException e) {
                    err = MainActivity.friendly(e);
                }
                final String fe = err;
                app.main.post(() -> {
                    if (app.isDestroyed()) return;
                    app.snack(fe != null ? fe : app.getString(R.string.removed, r.title));
                    rescan();
                });
            });
        });
    }

    void confirmRemoveEntry(final Model.Entry e) {
        Model.Installed r = null;
        for (Model.Installed x : all(app.installed)) if (x.entry != null && x.entry.key.equals(e.key)) r = x;
        if (r == null) { r = new Model.Installed(); r.entry = e; r.title = e.name; }
        confirmRemove(r);
    }

    void installZip(final Uri uri) {
        final String name = displayName(uri);
        final String key = "zip:" + name;
        if (app.busy.containsKey(key)) return;
        app.busy.put(key, app.getString(R.string.copying));
        queued.incrementAndGet();
        updateProgressCard();
        final File addons = app.store.addonsDir();
        MainActivity.ops.execute(() -> {
            String err = null;
            List<String> folders = null;
            try {
                File dl = new File(app.getCacheDir(), "dl");
                dl.mkdirs();
                File zip = new File(dl, safeName(name));
                InputStream in = app.getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException(app.getString(R.string.cannot_open_file));
                OutputStream out = new FileOutputStream(zip);
                try {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally { out.close(); in.close(); }
                synchronized (Updater.LOCK) {
                    folders = Installer.install(zip, addons, null);
                    zip.delete();
                    // folders taken over from managed entries stay managed by them only if unchanged — release them
                    for (String f : folders) {
                        Model.Entry other = app.store.owner(f);
                        if (other != null) {
                            other.folders = new ArrayList<>(other.folders);
                            other.folders.remove(f);
                            if (other.folders.isEmpty()) app.store.remove(other.key); else app.store.put(other);
                        }
                    }
                }
            } catch (Exception e) {
                err = MainActivity.friendly(e);
            }
            final String fe = err;
            final List<String> ff = folders;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                app.busy.remove(key);
                queued.decrementAndGet();
                updateProgressCard();
                if (fe != null) app.confirm(app.getString(R.string.zip_install_failed), fe, null, null);
                else app.snack(app.getString(R.string.zip_installed, DetailPanel.joinShort(ff, 4)));
                rescan();
            });
        });
    }

    private String displayName(Uri uri) {
        String n = null;
        try (android.database.Cursor c = app.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) n = c.getString(0);
        } catch (Exception ignored) {}
        if (n == null) n = uri.getLastPathSegment();
        return n == null ? "addon.zip" : n;
    }
}
