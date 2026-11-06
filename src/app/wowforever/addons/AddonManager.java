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

    AddonManager(MainActivity app) { this.app = app; }

    void rescan() {
        if (!app.hasStorageAccess() || !app.store.gameReady()) { app.scanned = true; app.installed = new ArrayList<>(); app.renderIfTab(MainActivity.TAB_INSTALLED); return; }
        final File addons = app.store.addonsDir();
        final long started = System.currentTimeMillis();
        MainActivity.net.execute(() -> {
            List<Model.LocalAddon> locals = addons.isDirectory() ? Toc.scan(addons) : new ArrayList<Model.LocalAddon>();
            Index.loadCached();
            final List<Model.Installed> rows = group(locals);
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                // carry over update-check results
                for (Model.Installed r : all(rows)) {
                    if (r.entry == null) continue;
                    for (Model.Installed o : all(app.installed)) if (o.entry != null && o.entry.key.equals(r.entry.key) && o.entry != r.entry) {
                        r.entry.latest = o.entry.latest;
                        r.entry.checkError = o.entry.checkError;
                        r.entry.updateAvailable = UpdatePolicy.isNewer(o.entry.latest, r.entry.version);
                    }
                }
                app.installed = rows;
                app.scanned = true;
                app.library.scanStartedAt = Math.max(app.library.scanStartedAt, started);
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
        return Packages.fold(rows);
    }

    /** Every Library row, the modules of packages included. */
    static List<Model.Installed> all(List<Model.Installed> rows) {
        List<Model.Installed> l = new ArrayList<>();
        for (Model.Installed r : rows) l.addAll(r.withModules());
        return l;
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
        e.checkError = err;
        if (latest != null) {
            if (!sameVersion(latest, e.latest)) e.changelog = null;
            e.latest = latest;
            e.updateAvailable = UpdatePolicy.isNewer(latest, e.version);
            if (changelog != null && !changelog.isEmpty()) e.changelog = changelog;
        }
        if (left.decrementAndGet() == 0) {
            app.checking = false;
            app.store.setLastCheck(System.currentTimeMillis());
            int n = 0;
            for (Model.Entry x : app.store.list()) if (x.updateAvailable) n++;
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
            if (m.entry != null && m.entry.updateAvailable && !app.queue.busy(m.entry.key)) update(m.entry);
    }

    void updateAll() {
        int n = 0;
        for (Model.Entry e : app.store.list()) if (e.updateAvailable && !app.queue.busy(e.key)) { update(e); n++; }
        if (n > 0) app.snack(app.getString(R.string.updating_n, n));
    }

    /**
     * Installs an item. A package core that is not installed yet brings every module with it: people
     * want the whole addon and turn parts off in the game, or remove a module in the Library.
     */
    void install(final Model.Item item, final Runnable after) {
        if (!requireGame()) return;
        if (app.queue.busy(item.key())) return;
        final List<Model.Item> parts = new ArrayList<>();
        parts.add(item);
        final Model.Entry existing = app.store.entry(item.key());
        if (existing == null && Model.SRC_GITHUB.equals(item.source))
            for (Model.Item m : Index.modulesOf(item.id)) if (app.store.entry(m.key()) == null) parts.add(m);
        String icon = item.iconUrl != null ? item.iconUrl : existing != null ? existing.iconUrl : null;
        final Queue.Op op = app.queue.add(existing == null ? Queue.Kind.INSTALL : Queue.Kind.UPDATE, item.key(), item.name, icon);
        for (Model.Item p : parts) op.keys.add(p.key());
        op.modules = parts.size() - 1;
        op.status = app.getString(R.string.queued);
        queueChanged();
        final File addons = app.store.addonsDir();
        MainActivity.ops.execute(() -> {
            if (!app.queue.start(op)) return;
            app.main.post(this::queueChanged);
            String error = null;
            List<String> installedNames = new ArrayList<>();
            try {
                Set<String> seen = new HashSet<>();
                for (Model.Item part : parts) installWithDeps(part, addons, installedNames, seen, 0, item, op);
            } catch (Exception e) {
                error = MainActivity.friendly(e);
            }
            app.queue.finish(op, error);
            final String fe = error;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                if (fe != null) {
                    app.confirm(app.getString(R.string.install_failed, item.name), fe, null, null);
                } else {
                    String names = parts.size() > 1 ? app.getResources().getQuantityString(R.plurals.package_installed, parts.size() - 1, item.name, parts.size() - 1)
                            : installedNames.size() > 1 ? String.join(", ", installedNames) : item.name;
                    app.snack(app.getString(R.string.installed_names, names));
                }
                queueChanged();
                rescan();
                if (after != null) after.run();
            });
        });
    }

    /** Runs on the ops thread. */
    /** The progress of a dependency or a module shows on the card of the item the user asked for. */
    private void installWithDeps(Model.Item item, File addons, List<String> done, Set<String> seen, int depth, final Model.Item asked, final Queue.Op op) throws IOException {
        new Updater(app, app.store).installWithDeps(item, addons, done, seen, depth,
                (i, t, f) -> progress(op, i.key().equals(asked.key()) ? t : i.name + "  ·  " + t, f));
    }

    private static boolean containsIgnoreCase(List<String> l, String s) { return Updater.containsIgnoreCase(l, s); }

    private static String safeName(String s) { return Updater.safeName(s); }

    /** Runs on the ops thread: a progress step updates the views in place, without a new list. */
    private void progress(final Queue.Op op, final String text, final float fraction) {
        app.queue.progress(op, text, fraction);
        app.main.post(() -> {
            if (app.isDestroyed()) return;
            updateProgressCard();
            app.library.tick(op);
            app.queueSheet.tick(op);
        });
    }

    /** The queue gained, started, lost or finished an operation: every view that shows it follows. */
    void queueChanged() {
        updateProgressCard();
        if (app.tab == MainActivity.TAB_INSTALLED) app.library.refresh(); else app.refreshList();
        app.detail.renderDetail();
        app.queueSheet.refresh();
    }

    void updateProgressCard() {
        if (app.progressCard == null) return;
        Queue.Op cur = app.queue.current();
        if (cur == null) { app.progressCard.setVisibility(View.GONE); return; }
        app.progressCard.setVisibility(View.VISIBLE);
        int more = app.queue.activeCount() - 1;
        app.progressTitle.setText(cur.name + (more > 0 ? "  ·  " + app.getString(R.string.more_queued, more) : ""));
        app.progressText.setText(cur.status);
        boolean remove = cur.kind == Queue.Kind.REMOVE;
        int color = remove ? Ui.RED : Ui.ACCENT;
        app.progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        app.progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(color));
        if (cur.state != Queue.State.RUNNING || cur.fraction < 0) app.progressBar.setIndeterminate(true);
        else { app.progressBar.setIndeterminate(false); app.progressBar.setProgress((int) (cur.fraction * 1000)); }
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
        final List<String> folders = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        for (Model.Installed m : r.withModules()) {
            for (Model.LocalAddon a : m.folders) if (!containsIgnoreCase(folders, a.folder)) folders.add(a.folder);
            if (m.entry != null) {
                keys.add(m.entry.key);
                for (String f : m.entry.folders) if (!containsIgnoreCase(folders, f)) folders.add(f);
            }
        }
        app.confirm(app.getString(R.string.remove_title, r.title), app.getString(R.string.remove_text, DetailPanel.joinShort(folders, 10)), app.getString(R.string.action_remove), () -> remove(r, folders, keys));
    }

    /** Queues the removal; the Library shows the row in red until the files are gone. */
    private void remove(final Model.Installed r, final List<String> folders, final List<String> keys) {
        String key = keys.isEmpty() ? (r.catalogMatch != null ? r.catalogMatch.key() : "folders:" + (folders.isEmpty() ? r.title : folders.get(0))) : keys.get(0);
        String icon = r.entry != null ? r.entry.iconUrl : (r.catalogMatch != null ? r.catalogMatch.iconUrl : null);
        final Queue.Op op = app.queue.add(Queue.Kind.REMOVE, key, r.title, icon);
        op.keys.addAll(keys);
        for (String f : folders) op.addFolder(f);
        op.modules = r.modules.size();
        op.status = app.getString(R.string.queued);
        final File addons = app.store.addonsDir();
        app.selected = null;
        if (!app.layout.docked) app.detail.closeDetailSheet();
        queueChanged();
        MainActivity.ops.execute(() -> {
            if (!app.queue.start(op)) return;
            app.queue.progress(op, app.getString(R.string.queue_removing_files), -1);
            app.main.post(this::queueChanged);
            String err = null;
            try {
                synchronized (Updater.LOCK) {
                    Installer.remove(addons, folders);
                    for (String k : keys) app.store.remove(k);
                }
            } catch (IOException e) {
                err = MainActivity.friendly(e);
            }
            app.queue.finish(op, err);
            final String fe = err;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                app.snack(fe != null ? fe : app.getString(R.string.removed, r.title));
                queueChanged();
                rescan();
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
        if (app.queue.busy(key)) return;
        final Queue.Op op = app.queue.add(Queue.Kind.ZIP, key, name, null);
        op.status = app.getString(R.string.queued);
        queueChanged();
        final File addons = app.store.addonsDir();
        MainActivity.ops.execute(() -> {
            if (!app.queue.start(op)) return;
            app.queue.progress(op, app.getString(R.string.copying), -1);
            app.main.post(this::queueChanged);
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
            app.queue.finish(op, err);
            final String fe = err;
            final List<String> ff = folders;
            app.main.post(() -> {
                if (app.isDestroyed()) return;
                queueChanged();
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
