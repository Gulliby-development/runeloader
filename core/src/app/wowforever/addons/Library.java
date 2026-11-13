package app.wowforever.addons;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Library rules both apps share: the rows of the AddOns folder, what an install brings, what a
 * remove deletes, and how an update check result lands on an entry. No threads, no UI.
 */
public final class Library {

    /** Groups the scanned folders into rows: managed entries, known catalog or index addons, then by hand. */
    static List<Model.Installed> rows(Store store, List<Model.LocalAddon> locals) {
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

    /** Scans the AddOns folder of the store's game and groups it. */
    static List<Model.Installed> scan(Store store) {
        File addons = store.gameReady() ? store.addonsDir() : null;
        List<Model.LocalAddon> locals = addons != null && addons.isDirectory() ? Toc.scan(addons) : new ArrayList<Model.LocalAddon>();
        Index.loadCached();
        return rows(store, locals);
    }

    /** Keeps the update-check results of the old rows on the new ones after a rescan. */
    static void carryOver(List<Model.Installed> from, List<Model.Installed> to) {
        for (Model.Installed r : all(to)) {
            if (r.entry == null) continue;
            for (Model.Installed o : all(from)) if (o.entry != null && o.entry.key.equals(r.entry.key) && o.entry != r.entry) {
                r.entry.latest = o.entry.latest;
                r.entry.checkError = o.entry.checkError;
                r.entry.changelog = o.entry.changelog;
                r.entry.updateAvailable = UpdatePolicy.isNewer(o.entry.latest, r.entry.version);
            }
        }
    }

    /** Every Library row, the modules of packages included. */
    static List<Model.Installed> all(List<Model.Installed> rows) {
        List<Model.Installed> l = new ArrayList<>();
        for (Model.Installed r : rows) l.addAll(r.withModules());
        return l;
    }

    static boolean anyForever(List<Model.LocalAddon> folders) {
        if (folders.isEmpty()) return true;
        // the first folder is the main one; libraries without TOC metadata are fine
        Model.LocalAddon m = folders.get(0);
        if (m.tocFile.isEmpty()) return true;
        for (Model.LocalAddon a : folders) if (a.foreverToc) return true;
        return false;
    }

    /**
     * What an install of the item brings. A package core that is not installed yet brings every module
     * with it: people want the whole addon and turn parts off in the game, or remove a module later.
     */
    static List<Model.Item> parts(Store store, Model.Item item) { return parts(store, item, false); }

    /** @param alone only the item and its dependencies: taking over an addon installed by hand must not add modules. */
    static List<Model.Item> parts(Store store, Model.Item item, boolean alone) {
        List<Model.Item> parts = new ArrayList<>();
        parts.add(item);
        if (!alone && store.entry(item.key()) == null && Model.SRC_GITHUB.equals(item.source))
            for (Model.Item m : Index.modulesOf(item.id)) if (store.entry(m.key()) == null) parts.add(m);
        return parts;
    }

    /** Installs the item and what it brings; returns the names installed. Call off the UI thread. */
    static List<String> install(Store store, Model.Item item, Updater.Progress progress) throws IOException {
        return install(store, item, false, progress);
    }

    static List<String> install(Store store, Model.Item item, boolean alone, Updater.Progress progress) throws IOException {
        File addons = store.addonsDir();
        List<String> done = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Updater updater = new Updater(store);
        for (Model.Item part : parts(store, item, alone)) updater.installWithDeps(part, addons, done, seen, 0, progress);
        return done;
    }

    /** The folders a remove deletes: the core of a package takes its modules with it. */
    static List<String> removeFolders(Model.Installed r) {
        List<String> folders = new ArrayList<>();
        for (Model.Installed m : r.withModules()) {
            for (Model.LocalAddon a : m.folders) if (!Updater.containsIgnoreCase(folders, a.folder)) folders.add(a.folder);
            if (m.entry != null) for (String f : m.entry.folders) if (!Updater.containsIgnoreCase(folders, f)) folders.add(f);
        }
        return folders;
    }

    /** Deletes the row's folders and forgets its entries, inside the install lock. Call off the UI thread. */
    static void remove(Store store, Model.Installed r) throws IOException {
        List<String> folders = removeFolders(r);
        File addons = store.addonsDir();
        synchronized (Updater.LOCK) {
            Installer.remove(addons, folders);
            for (Model.Installed m : r.withModules()) if (m.entry != null) store.remove(m.entry.key);
        }
    }

    /**
     * Unpacks a ZIP the player picked into the AddOns folder, inside the install lock. A folder it
     * replaces stops belonging to the managed entry that had it. Returns the folders. Call off the UI thread.
     */
    static List<String> installZip(Store store, File zip) throws IOException {
        synchronized (Updater.LOCK) {
            List<String> folders = Installer.install(zip, store.addonsDir(), null);
            for (String f : folders) {
                Model.Entry other = store.owner(f);
                if (other == null) continue;
                other.folders = new ArrayList<>(other.folders);
                other.folders.remove(f);
                if (other.folders.isEmpty()) store.remove(other.key); else store.put(other);
            }
            return folders;
        }
    }

    /** The managed entries an update check asks about (a ZIP install has nothing to ask). */
    static List<Model.Entry> checkable(Store store) {
        List<Model.Entry> list = new ArrayList<>();
        for (Model.Entry e : store.list()) if (!Model.SRC_ZIP.equals(e.source)) list.add(e);
        return list;
    }

    /** Puts one check result on its entry: the latest version, its notes or the error. */
    static void applyCheck(Model.Entry e, String latest, String changelog, String err) {
        e.checkError = err;
        if (latest == null) return;
        if (!UpdatePolicy.sameVersion(latest, e.latest)) e.changelog = null;
        e.latest = latest;
        e.updateAvailable = UpdatePolicy.isNewer(latest, e.version);
        if (changelog != null && !changelog.isEmpty()) e.changelog = changelog;
    }

    /** Checks every entry: one index download answers the GitHub addons it lists, the rest are asked one by one. */
    static void checkAll(Store store, boolean refreshIndex) {
        Map<String, Model.Item> index = Updater.indexByKey(refreshIndex);
        for (Model.Entry e : checkable(store)) {
            Model.Item ix = Updater.indexHit(index, e);
            if (ix != null) { applyCheck(e, ix.version, ix.changelog, null); continue; }
            try {
                applyCheck(e, Updater.latestVersion(e), null, null);
            } catch (Exception ex) {
                applyCheck(e, null, null, Texts.friendly(ex));
            }
        }
        store.setLastCheck(System.currentTimeMillis());
    }

    /** The number of entries with an update. */
    static int updates(Store store) {
        int n = 0;
        for (Model.Entry x : store.list()) if (x.updateAvailable) n++;
        return n;
    }

    /**
     * The newer version the index or the catalog offers for an addon Runeloader does not manage yet
     * (the modules of a package installed by hand), or null. Installing that item updates it and
     * makes Runeloader manage it.
     */
    static String untrackedUpdate(Model.Installed r) {
        if (r == null || r.entry != null || r.missing || r.catalogMatch == null) return null;
        String v = r.catalogMatch.version;
        return v != null && AppVersion.newer(v, r.version) ? v : null;
    }

    /** Whether one row (not its modules) has an update, managed or not. */
    static boolean needsUpdate(Model.Installed r) {
        if (r == null) return false;
        return r.entry != null ? r.entry.updateAvailable && !r.missing : untrackedUpdate(r) != null;
    }

    /** Rows with an update, counting each module of a package. */
    static int updates(List<Model.Installed> rows) {
        int n = 0;
        for (Model.Installed r : rows) {
            if (needsUpdate(r)) n++;
            for (Model.Installed m : r.modules) if (needsUpdate(m)) n++;
        }
        return n;
    }

    private Library() {}
}
