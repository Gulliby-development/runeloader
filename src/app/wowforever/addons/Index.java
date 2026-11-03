package app.wowforever.addons;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private static Map<String, JSONObject> byRepo = new HashMap<>();
    private static Map<String, Model.Item> byFolder = new HashMap<>();
    private static long loadedAt;

    /** All index entries, most starred first. Uses the disk cache when fresh or when offline. */
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
                        throw new IOException(Lang.s(R.string.err_index_missing));
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
                JSONObject probe = new JSONObject(body);
                if (probe.optJSONArray("addons") == null) throw new IOException(Lang.s(R.string.err_index_broken));
            } catch (JSONException e) {
                throw new IOException(Lang.s(R.string.err_index_broken));
            }
            Sources.writeText(f, body);
            String newTag = c.getHeaderField("ETag");
            if (newTag != null) Sources.writeText(etagFile, newTag); else etagFile.delete();
        } finally {
            c.disconnect();
        }
    }

    private static void parse(String body) throws IOException {
        JSONArray arr;
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("schema", SCHEMA) > SCHEMA) throw new IOException(Lang.s(R.string.err_index_newer));
            arr = root.optJSONArray("addons");
        } catch (JSONException e) {
            throw new IOException(Lang.s(R.string.err_index_broken));
        }
        List<Model.Item> out = new ArrayList<>();
        Map<String, JSONObject> repos = new HashMap<>();
        Map<String, Model.Item> folders = new HashMap<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            Model.Item it = toItem(a);
            if (it == null) continue;
            out.add(it);
            repos.put(it.id.toLowerCase(Locale.ROOT), a);
            for (String d : it.folders) {
                String k = d.toLowerCase(Locale.ROOT);
                if (!folders.containsKey(k)) folders.put(k, it);
            }
        }
        Collections.sort(out, Popularity.ORDER);
        items = out;
        byRepo = repos;
        byFolder = folders;
    }

    static Model.Item toItem(JSONObject a) {
        if (a == null) return null;
        String repo = Sources.GitHub.normalize(a.optString("repo", ""));
        if (repo == null) return null;
        Model.Item c = Catalog.byKey(Model.SRC_GITHUB + ":" + repo);
        Model.Item it = new Model.Item();
        it.source = Model.SRC_GITHUB;
        it.id = repo;
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
        JSONArray fs = a.optJSONArray("folders");
        if (fs != null) for (int i = 0; i < fs.length(); i++) {
            String d = fs.optString(i, "");
            if (!d.isEmpty() && !it.folders.contains(d)) it.folders.add(d);
        }
        if (c != null) {
            // The hand-written catalog knows every folder, the category and Forever caveats.
            for (String d : c.folders) if (!it.folders.contains(d)) it.folders.add(d);
            it.category = c.category;
            it.note = c.note;
            it.deps = c.deps;
            if (c.iconUrl != null) it.iconUrl = c.iconUrl;
        }
        IndexFields.fill(a, BASE, it);
        return it;
    }

    private static String str(JSONObject a, String k, String def) {
        if (!a.has(k) || a.isNull(k)) return def;
        String s = a.optString(k, "").trim();
        return s.isEmpty() ? def : s;
    }

    /** The raw index entry of owner/repo from the last loaded index, or null. */
    static synchronized JSONObject addon(String repo) {
        return repo == null ? null : byRepo.get(repo.toLowerCase(Locale.ROOT));
    }

    /** The index item for owner/repo from the last loaded index, or null. */
    static synchronized Model.Item item(String repo) {
        JSONObject a = addon(repo);
        return a == null ? null : toItem(a);
    }

    /** The index item that installs this AddOns folder, from the last loaded index, or null. */
    static synchronized Model.Item byFolder(String folder) {
        return folder == null ? null : byFolder.get(folder.toLowerCase(Locale.ROOT));
    }

    /** Name prefix first, then name, author, summary or folder contains the query. */
    static List<Model.Item> search(String q, int from, int count) throws IOException {
        List<Model.Item> src = items(false);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Model.Item> hits = new ArrayList<>();
        List<Model.Item> tail = new ArrayList<>();
        for (Model.Item it : src) {
            if (needle.isEmpty()) { hits.add(it); continue; }
            String n = it.name.toLowerCase(Locale.ROOT);
            if (n.startsWith(needle) || it.id.toLowerCase(Locale.ROOT).contains(needle)) hits.add(it);
            else if (n.contains(needle) || it.author.toLowerCase(Locale.ROOT).contains(needle)
                    || it.summary.toLowerCase(Locale.ROOT).contains(needle)) tail.add(it);
            else for (String d : it.folders) if (d.toLowerCase(Locale.ROOT).contains(needle)) { tail.add(it); break; }
        }
        hits.addAll(tail);
        if (from >= hits.size()) return new ArrayList<>();
        return new ArrayList<>(hits.subList(from, Math.min(hits.size(), from + count)));
    }

    private Index() {}
}
