package app.wowforever.addons;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Installs and updates addons without a screen. The UI (AddonManager) and the background job
 * (UpdateJobService) both go through here, so there is one install path.
 */
final class Updater {
    /** One install at a time across the whole process: the UI queue and the background job share it. */
    static final Object LOCK = new Object();

    interface Progress { void on(Model.Item item, String text, float fraction); }

    private final Store store;

    Updater(Store store) {
        this.store = store;
    }

    /** The newest version the source offers for an installed entry. */
    static String latestVersion(Model.Entry e) throws IOException {
        Model.Release r = Sources.latest(Sources.itemFor(e));
        return r.version;
    }

    /** The GitHub index as "github:owner/repo" (lower case) to item; empty when it cannot be loaded. */
    static Map<String, Model.Item> indexByKey(boolean refresh) {
        Map<String, Model.Item> m = new HashMap<>();
        try {
            for (Model.Item it : Index.items(refresh)) m.put(it.key().toLowerCase(Locale.ROOT), it);
        } catch (Exception ignored) {
            // no index and no cache: every addon falls back to the direct check
        }
        return m;
    }

    /** The index entry that answers the update check for e, or null to ask the source directly. */
    static Model.Item indexHit(Map<String, Model.Item> index, Model.Entry e) {
        Model.Item ix = index.get(e.key.toLowerCase(Locale.ROOT));
        if (ix == null || ix.version == null || ix.version.isEmpty()) return null;
        // a package releases its parts many times a day, more often than the nightly index: ask GitHub,
        // which answers every part of one repo from one cached list of tags
        if (inPackage(index, e)) return null;
        // the index can be a day behind a release installed from GitHub: ask the source instead
        return UpdatePolicy.older(ix.version, e.version) ? null : ix;
    }

    /** Whether a GitHub entry is the core or a module of a repo that releases each module on its own tag. */
    static boolean inPackage(Map<String, Model.Item> index, Model.Entry e) {
        if (!Model.SRC_GITHUB.equals(e.source) || e.id == null) return false;
        if (Modules.moduleOf(e.id) != null) return true;
        String prefix = (e.source + ":" + Modules.repoOf(e.id) + "#").toLowerCase(Locale.ROOT);
        for (String k : index.keySet()) if (k.startsWith(prefix)) return true;
        return false;
    }

    /** Installs an item and its missing dependencies; adds the installed names to done. */
    void installWithDeps(Model.Item item, File addons, List<String> done, Set<String> seen, int depth, Progress p) throws IOException {
        synchronized (LOCK) {
            installLocked(item, addons, done, seen, depth, p == null ? (i, t, f) -> {} : p);
        }
    }

    private void installLocked(Model.Item item, File addons, List<String> done, Set<String> seen, int depth, Progress p) throws IOException {
        if (!seen.add(item.key()) || depth > 4) return;
        p.on(item, Texts.s("finding_build"), -1);
        Model.Release rel = Sources.latest(item);
        // dependencies first
        List<Model.Item> deps = new ArrayList<>(rel.deps);
        for (String d : item.deps) { Model.Item c = dependency(d); if (c != null) deps.add(c); }
        for (Model.Item d : deps) {
            if (store.entry(d.key()) != null) continue;
            installLocked(d, addons, done, seen, depth + 1, p);
        }
        File dl = new File(Sources.cacheDir, "dl");
        dl.mkdirs();
        File zip = new File(dl, safeName(rel.fileName == null ? item.name + ".zip" : rel.fileName));
        final Model.Item fItem = item;
        p.on(item, Texts.s("downloading", rel.version == null ? "" : rel.version), 0);
        Http.download(rel.url, rel.headers, zip, (d, t) -> p.on(fItem, t > 0 ? Texts.s("downloading_of", Texts.bytes(d), Texts.bytes(t)) : Texts.s("downloading", Texts.bytes(d)), t > 0 ? (float) d / t : -1));
        if (rel.md5 != null && rel.md5.length() == 32) {
            String got = Installer.md5(zip);
            if (!got.equalsIgnoreCase(rel.md5)) { zip.delete(); throw new IOException(Texts.s("md5_mismatch")); }
        }
        p.on(item, Texts.s("unpacking"), -1);
        Model.Entry old = store.entry(item.key());
        List<String> folders = Installer.install(zip, addons, text -> p.on(fItem, text, -1));
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
        e.pinned = old != null && old.pinned;
        store.put(e);
        done.add(item.name);
    }

    /** A dependency key ("github:owner/repo#Module") as an item: the catalog first, then the index. */
    static Model.Item dependency(String key) {
        Model.Item c = Catalog.byKey(key);
        if (c != null || key == null || !key.startsWith(Model.SRC_GITHUB + ":")) return c;
        Index.loadCached();
        Model.Item ix = Index.item(key.substring(Model.SRC_GITHUB.length() + 1));
        if (ix != null) return ix;
        // Not in the index (yet): a bare item still installs from GitHub.
        String id = key.substring(Model.SRC_GITHUB.length() + 1);
        String repo = Sources.GitHub.normalize(Modules.repoOf(id));
        if (repo == null) return null;
        Model.Item it = new Model.Item();
        it.source = Model.SRC_GITHUB;
        it.id = Modules.id(repo, Modules.moduleOf(id));
        it.name = it.id.substring(it.id.indexOf('/') + 1).replace("#", ": ");
        it.author = repo.substring(0, repo.indexOf('/'));
        it.pageUrl = "https://github.com/" + repo;
        return it;
    }

    static boolean containsIgnoreCase(List<String> l, String s) {
        for (String x : l) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    static String safeName(String s) {
        String n = s.replaceAll("[^A-Za-z0-9._+-]", "_");
        if (!n.toLowerCase(Locale.ROOT).endsWith(".zip")) n += ".zip";
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }
}
