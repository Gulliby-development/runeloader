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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
            final List<Model.Installed> rows = group(locals);
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                // carry over update-check results
                for (Model.Installed r : rows) {
                    if (r.entry == null) continue;
                    for (Model.Installed o : app.installed) if (o.entry != null && o.entry.key.equals(r.entry.key) && o.entry != r.entry) {
                        r.entry.latest = o.entry.latest;
                        r.entry.checkError = o.entry.checkError;
                        r.entry.updateAvailable = o.entry.latest != null && !sameVersion(o.entry.latest, r.entry.version);
                    }
                }
                app.installed = rows;
                app.scanned = true;
                if (app.tab == MainActivity.TAB_INSTALLED) { app.render(); }
                else if (app.tab == MainActivity.TAB_BROWSE) app.refreshList();
                if (!app.checkedOnce && app.store.list().size() > 0) { app.checkedOnce = true; checkUpdates(false); }
            });
        });
    }

    private List<Model.Installed> group(List<Model.LocalAddon> locals) {
        Map<String, Model.LocalAddon> byFolder = new HashMap<>();
        for (Model.LocalAddon a : locals) byFolder.put(a.folder.toLowerCase(Locale.ROOT), a);
        List<Model.Installed> rows = new ArrayList<>();
        Set<String> owned = new HashSet<>();
        for (Model.Entry e : app.store.list()) {
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
            if (c == null) c = Index.byFolder(a.folder);
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

    void checkUpdates(final boolean manual) {
        if (app.checking) return;
        final List<Model.Entry> list = new ArrayList<>();
        for (Model.Entry e : app.store.list()) if (!Model.SRC_ZIP.equals(e.source)) list.add(e);
        if (list.isEmpty()) { if (manual) app.snack(app.getString(R.string.no_managed_addons)); return; }
        app.checking = true;
        app.renderIfTab(MainActivity.TAB_INSTALLED);
        final AtomicInteger left = new AtomicInteger(list.size());
        for (final Model.Entry e : list) {
            MainActivity.net.execute(() -> {
                String latest = null, err = null;
                try {
                    latest = Updater.latestVersion(e);
                } catch (Exception ex) {
                    err = MainActivity.friendly(ex);
                }
                final String fl = latest, fe = err;
                app.main.post(() -> {
                    if (app.isDestroyed()) return;
                    e.checkError = fe;
                    if (fl != null) { e.latest = fl; e.updateAvailable = !sameVersion(fl, e.version); }
                    if (left.decrementAndGet() == 0) {
                        app.checking = false;
                        app.store.setLastCheck(System.currentTimeMillis());
                        int n = 0;
                        for (Model.Entry x : app.store.list()) if (x.updateAvailable) n++;
                        if (manual || n > 0) app.snack(n == 0 ? app.getString(R.string.all_up_to_date) : app.getResources().getQuantityString(R.plurals.updates_available, n, n));
                    }
                    if (app.tab == MainActivity.TAB_INSTALLED) { app.render(); }
                });
            });
        }
    }

    void checkOne(final Model.Entry e) {
        app.snack(app.getString(R.string.checking_one, e.name));
        MainActivity.net.execute(() -> {
            String latest = null, err = null;
            try { latest = Updater.latestVersion(e); } catch (Exception ex) { err = MainActivity.friendly(ex); }
            final String fl = latest, fe = err;
            app.main.post(() -> {
                e.checkError = fe;
                if (fl != null) { e.latest = fl; e.updateAvailable = !sameVersion(fl, e.version); }
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

    void updateAll() {
        int n = 0;
        for (Model.Entry e : app.store.list()) if (e.updateAvailable && !app.busy.containsKey(e.key)) { update(e); n++; }
        if (n > 0) app.snack(app.getString(R.string.updating_n, n));
    }

    void install(final Model.Item item, final Runnable after) {
        if (!requireGame()) return;
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
                installWithDeps(item, addons, installedNames, new HashSet<String>(), 0);
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
                    app.snack(app.getString(R.string.installed_names, installedNames.size() > 1 ? String.join(", ", installedNames) : item.name));
                }
                rescan();
                app.refreshList();
                app.detail.renderDetail();
                if (after != null) after.run();
            });
        });
    }

    /** Runs on the ops thread. */
    private void installWithDeps(Model.Item item, File addons, List<String> done, Set<String> seen, int depth) throws IOException {
        new Updater(app, app.store).installWithDeps(item, addons, done, seen, depth, this::progress);
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

    void confirmRemove(final Model.Installed r) {
        final List<String> folders = new ArrayList<>();
        for (Model.LocalAddon a : r.folders) folders.add(a.folder);
        if (r.entry != null) for (String f : r.entry.folders) if (!containsIgnoreCase(folders, f)) folders.add(f);
        app.confirm(app.getString(R.string.remove_title, r.title), app.getString(R.string.remove_text, DetailPanel.joinShort(folders, 10)), app.getString(R.string.action_remove), () -> {
            final File addons = app.store.addonsDir();
            app.selected = null;
            if (!app.layout.docked) app.detail.closeDetailSheet(); else app.detail.renderDetail();
            app.snack(app.getString(R.string.removing, r.title));
            MainActivity.ops.execute(() -> {
                String err = null;
                try {
                    synchronized (Updater.LOCK) {
                        Installer.remove(addons, folders);
                        if (r.entry != null) app.store.remove(r.entry.key);
                    }
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
        for (Model.Installed x : app.installed) if (x.entry != null && x.entry.key.equals(e.key)) r = x;
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
