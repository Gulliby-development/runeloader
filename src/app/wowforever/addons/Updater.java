package app.wowforever.addons;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Installs and updates addons without a screen. The UI (AddonManager) and the background job
 * (UpdateJobService) both go through here, so there is one install path.
 */
final class Updater {
    /** One install at a time across the whole process: the UI queue and the background job share it. */
    static final Object LOCK = new Object();

    interface Progress { void on(Model.Item item, String text, float fraction); }

    private final Context ctx;
    private final Store store;

    Updater(Context ctx, Store store) {
        this.ctx = ctx;
        this.store = store;
    }

    /** The newest version the source offers for an installed entry. */
    static String latestVersion(Model.Entry e) throws IOException {
        Model.Release r = Sources.latest(Sources.itemFor(e));
        return r.version;
    }

    /** Installs an item and its missing dependencies; adds the installed names to done. */
    void installWithDeps(Model.Item item, File addons, List<String> done, Set<String> seen, int depth, Progress p) throws IOException {
        synchronized (LOCK) {
            installLocked(item, addons, done, seen, depth, p == null ? (i, t, f) -> {} : p);
        }
    }

    private void installLocked(Model.Item item, File addons, List<String> done, Set<String> seen, int depth, Progress p) throws IOException {
        if (!seen.add(item.key()) || depth > 4) return;
        p.on(item, ctx.getString(R.string.finding_build), -1);
        Model.Release rel = Sources.latest(item);
        // dependencies first
        List<Model.Item> deps = new ArrayList<>(rel.deps);
        for (String d : item.deps) { Model.Item c = Catalog.byKey(d); if (c != null) deps.add(c); }
        for (Model.Item d : deps) {
            if (store.entry(d.key()) != null) continue;
            installLocked(d, addons, done, seen, depth + 1, p);
        }
        File dl = new File(ctx.getCacheDir(), "dl");
        dl.mkdirs();
        File zip = new File(dl, safeName(rel.fileName == null ? item.name + ".zip" : rel.fileName));
        final Model.Item fItem = item;
        p.on(item, ctx.getString(R.string.downloading, rel.version == null ? "" : rel.version), 0);
        Http.download(rel.url, rel.headers, zip, (d, t) -> p.on(fItem, t > 0 ? ctx.getString(R.string.downloading_of, Ui.formatBytes(d), Ui.formatBytes(t)) : ctx.getString(R.string.downloading, Ui.formatBytes(d)), t > 0 ? (float) d / t : -1));
        if (rel.md5 != null && rel.md5.length() == 32) {
            String got = Installer.md5(zip);
            if (!got.equalsIgnoreCase(rel.md5)) { zip.delete(); throw new IOException(ctx.getString(R.string.md5_mismatch)); }
        }
        p.on(item, ctx.getString(R.string.unpacking), -1);
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
