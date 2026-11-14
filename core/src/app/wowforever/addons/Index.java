package app.wowforever.addons;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Runeloader index: GitHub repositories whose latest release has a Forever build,
 * built daily by Gulliby-development/runeloader-index. Used only for search; installs and
 * update checks still read the repository's release directly (Sources.GitHub).
 */
final class Index {

    static final String BASE = "https://raw.githubusercontent.com/Gulliby-development/runeloader-index/gh-pages/";
    static final String URL = BASE + "index.json";
    static final int SCHEMA = 1;
    private static final long FRESH_MS = 6L * 3600 * 1000;

    private static List<Model.Item> items;
    private static Map<String, Json.Obj> byRepo = new HashMap<>();
    private static Map<String, Model.Item> byFolder = new HashMap<>();
    /** owner/repo (lower case) of a module repo to its module entries, in index order. */
    private static Map<String, List<Json.Obj>> modules = new HashMap<>();
    /** owner/repo (lower case) of a package to modules found in its GitHub releases that the index lacks yet. */
    private static Map<String, List<Model.Item>> found = new HashMap<>();
    private static long loadedAt;

    /** All index entries, most popular first (see Popularity). Uses the disk cache when fresh or when offline. */
    static synchronized List<Model.Item> items(boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        if (items != null && !forceRefresh && now - loadedAt < FRESH_MS) return items;
        File f = new File(Sources.cacheDir, "index.json");
        File etag = new File(Sources.cacheDir, "index.etag");
        boolean fresh = f.exists() && now - f.lastModified() < FRESH_MS;
        if (!fresh || forceRefresh || items == null) {
            try {
                if (!fresh || forceRefresh) fetch(f, etag);
            } catch (IOException e) {
                if (!f.exists()) {
                    if (e instanceof Http.HttpException && ((Http.HttpException) e).code == 404)
                        throw new IOException(Texts.s("err_index_missing"));
                    throw e;
                }
            }
            parse(Sources.readText(f));
            loadedAt = now;
        }
        return items;
    }

    /** Reads the disk cache if nothing is loaded yet; never touches the network. */
    static synchronized void loadCached() {
        if (items != null) return;
        File f = new File(Sources.cacheDir, "index.json");
        if (!f.exists()) return;
        try {
            parse(Sources.readText(f));
            loadedAt = f.lastModified();
        } catch (IOException ignored) {
        }
    }

    /** Downloads index.json with If-None-Match; a 304 only touches the cached file. */
    private static void fetch(File f, File etagFile) throws IOException {
        String tag = f.exists() && etagFile.exists() ? Sources.readText(etagFile).trim() : null;
        HttpURLConnection c = Http.connect(URL, Http.headers("Accept", "application/json", "If-None-Match", tag), "GET", null);
        try {
            int code = c.getResponseCode();
            if (code == 304) { f.setLastModified(System.currentTimeMillis()); return; }
            if (code >= 400) throw new Http.HttpException(code, URL, Http.readAll(c.getErrorStream()));
            String body = Http.readAll(c.getInputStream());
            try {
                Json.Obj probe = new Json.Obj(body);
                if (probe.optJSONArray("addons") == null) throw new IOException(Texts.s("err_index_broken"));
            } catch (Json.Error e) {
                throw new IOException(Texts.s("err_index_broken"));
            }
            Sources.writeText(f, body);
            String newTag = c.getHeaderField("ETag");
            if (newTag != null) Sources.writeText(etagFile, newTag); else etagFile.delete();
        } finally {
            c.disconnect();
        }
    }

    private static void parse(String body) throws IOException {
        Json.Arr arr;
        try {
            Json.Obj root = new Json.Obj(body);
            if (root.optInt("schema", SCHEMA) > SCHEMA) throw new IOException(Texts.s("err_index_newer"));
            arr = root.optJSONArray("addons");
        } catch (Json.Error e) {
            throw new IOException(Texts.s("err_index_broken"));
        }
        List<Model.Item> out = new ArrayList<>();
        Map<String, Json.Obj> repos = new HashMap<>();
        Map<String, Model.Item> folders = new HashMap<>();
        Map<String, List<Json.Obj>> mods = new HashMap<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            Json.Obj a = arr.optJSONObject(i);
            Model.Item it = toItem(a);
            if (it == null) continue;
            out.add(it);
            repos.put(it.id.toLowerCase(Locale.ROOT), a);
            if (Modules.moduleOf(it.id) != null) {
                String core = Modules.repoOf(it.id).toLowerCase(Locale.ROOT);
                List<Json.Obj> l = mods.get(core);
                if (l == null) mods.put(core, l = new ArrayList<>());
                l.add(a);
            }
            for (String d : it.folders) {
                String k = d.toLowerCase(Locale.ROOT);
                if (!folders.containsKey(k)) folders.put(k, it);
            }
        }
        Collections.sort(out, Popularity.ORDER);
        items = out;
        byRepo = repos;
        byFolder = folders;
        modules = mods;
    }

    static Model.Item toItem(Json.Obj a) {
        if (a == null) return null;
        String repo = Sources.GitHub.normalize(a.optString("repo", ""));
        if (repo == null) return null;
        String id = Modules.id(repo, str(a, "module", null));
        Model.Item c = Catalog.byKey(Model.SRC_GITHUB + ":" + id);
        Model.Item it = new Model.Item();
        it.source = Model.SRC_GITHUB;
        it.id = id;
        String owner = repo.substring(0, repo.indexOf('/'));
        it.name = Toc.clean(str(a, "name", repo.substring(repo.indexOf('/') + 1)));
        it.author = str(a, "author", owner);
        it.summary = str(a, "summary", "");
        it.version = str(a, "version", str(a, "tag", null));
        it.updated = Sources.parseIso(str(a, "released", null));
        it.downloads = a.optLong("downloads", -1);
        it.foreverVerified = a.optBoolean("verified", false);
        it.pageUrl = "https://github.com/" + repo;
        it.iconUrl = "https://github.com/" + owner + ".png?size=96";
        Json.Arr fs = a.optJSONArray("folders");
        if (fs != null) for (int i = 0; i < fs.length(); i++) {
            String d = fs.optString(i, "");
            if (!d.isEmpty() && !it.folders.contains(d)) it.folders.add(d);
        }
        if (c != null) {
            // The hand-written catalog knows every folder, the category and Forever caveats.
            for (String d : c.folders) if (!it.folders.contains(d)) it.folders.add(d);
            it.category = c.category;
            it.categoryKey = c.categoryKey;
            it.note = c.note;
            it.deps = new ArrayList<>(c.deps);
            if (c.iconUrl != null) it.iconUrl = c.iconUrl;
        }
        IndexFields.fill(a, BASE, it);
        return it;
    }

    private static String str(Json.Obj a, String k, String def) {
        if (!a.has(k) || a.isNull(k)) return def;
        String s = a.optString(k, "").trim();
        return s.isEmpty() ? def : s;
    }

    /** The raw index entry of owner/repo (or owner/repo#Module) from the last loaded index, or null. */
    static synchronized Json.Obj addon(String repo) {
        return repo == null ? null : byRepo.get(repo.toLowerCase(Locale.ROOT));
    }

    /** The index item for owner/repo (or owner/repo#Module) from the last loaded index, or null. */
    static synchronized Model.Item item(String repo) {
        Json.Obj a = addon(repo);
        return a == null ? null : toItem(a);
    }

    /**
     * The module entries of owner/repo when the index lists it as a package (its core plus modules
     * released on their own tags), sorted by name; empty for an ordinary addon.
     */
    static synchronized List<Model.Item> modulesOf(String repo) {
        List<Model.Item> out = new ArrayList<>();
        List<Json.Obj> l = repo == null || Modules.moduleOf(repo) != null ? null : modules.get(repo.toLowerCase(Locale.ROOT));
        if (l == null || byRepo.get(repo.toLowerCase(Locale.ROOT)) == null) return out;
        for (Json.Obj a : l) { Model.Item it = toItem(a); if (it != null) out.add(it); }
        List<Model.Item> extra = found.get(repo.toLowerCase(Locale.ROOT));
        if (extra != null) for (Model.Item x : extra) if (byRepo.get(x.id.toLowerCase(Locale.ROOT)) == null) out.add(x);
        Collections.sort(out, (x, y) -> x.name.compareToIgnoreCase(y.name));
        return out;
    }

    /**
     * Notes the modules a package released on GitHub after the index was built (see
     * Updater.discoverModules), so the package lists them and installs them until the index has them.
     */
    static synchronized void setFound(String repo, List<Model.Item> mods) {
        found.put(repo.toLowerCase(Locale.ROOT), new ArrayList<>(mods));
    }

    /** How many modules the package owner/repo has; 0 for an ordinary addon or a repo the index lacks. */
    static synchronized int moduleCount(String repo) {
        List<Json.Obj> l = repo == null || Modules.moduleOf(repo) != null ? null : modules.get(repo.toLowerCase(Locale.ROOT));
        return l == null || byRepo.get(repo.toLowerCase(Locale.ROOT)) == null ? 0 : modulesOf(repo).size();
    }

    /** The core of a module entry when the index lists that core, else the item itself: Browse shows one row per package. */
    static Model.Item packageOf(Model.Item it) {
        if (it == null || !Model.SRC_GITHUB.equals(it.source) || Modules.moduleOf(it.id) == null) return it;
        Model.Item core = item(Modules.repoOf(it.id));
        return core != null ? core : it;
    }

    /** The index item that installs this AddOns folder, from the last loaded index, or null. */
    static synchronized Model.Item byFolder(String folder) {
        return folder == null ? null : byFolder.get(folder.toLowerCase(Locale.ROOT));
    }

    /**
     * The exact name first, then names and repo names that start with the query, then a word of the
     * name, then anything that contains it (name, repo, author, summary, folder). Equal ranks keep the index order.
     */
    static List<Model.Item> search(String q, int from, int count) throws IOException {
        List<Model.Item> src = items(false);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<List<Model.Item>> ranks = new ArrayList<>();
        for (int i = 0; i < 5; i++) ranks.add(new ArrayList<>());
        for (Model.Item it : src) {
            int r = needle.isEmpty() ? 0 : rank(it, needle);
            if (r >= 0) ranks.get(r).add(it);
        }
        List<Model.Item> hits = new ArrayList<>();
        for (List<Model.Item> l : ranks) hits.addAll(l);
        if (from >= hits.size()) return new ArrayList<>();
        return new ArrayList<>(hits.subList(from, Math.min(hits.size(), from + count)));
    }

    /** 0 (best) to 4, or -1 when the item does not match the lower-case query. */
    static int rank(Model.Item it, String needle) {
        String n = it.name.toLowerCase(Locale.ROOT);
        String id = it.id.toLowerCase(Locale.ROOT);
        String repoName = id.substring(id.indexOf('/') + 1);
        String bare = needle.replaceAll("[^\\p{L}\\p{N}]", "");
        if (n.equals(needle) || (!bare.isEmpty() && n.replaceAll("[^\\p{L}\\p{N}]", "").equals(bare))) return 0;
        if (n.startsWith(needle) || repoName.startsWith(needle)) return 1;
        if (n.contains(" " + needle) || n.contains("-" + needle) || n.contains("_" + needle)) return 2;
        if (n.contains(needle) || id.contains(needle)) return 3;
        if (it.author.toLowerCase(Locale.ROOT).contains(needle) || it.summary.toLowerCase(Locale.ROOT).contains(needle)) return 4;
        for (String d : it.folders) if (d.toLowerCase(Locale.ROOT).contains(needle)) return 4;
        return -1;
    }

    private Index() {}
}
