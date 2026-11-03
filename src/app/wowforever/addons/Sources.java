package app.wowforever.addons;

import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Where addons come from: GitHub releases (found through Index, the catalog or a link) and WoWInterface. */
final class Sources {

    static final String[] FOREVER_FLAVORS = {"forever", "camelot"};
    private static final String[] OTHER_FLAVOR_MARKS = {"classic", "bcc", "tbc", "wrath", "wotlk", "cata", "mists", "vanilla", "titan", "mainline", "retail"};

    static File cacheDir;
    static String githubToken = "";

    static Model.Release latest(Model.Item item) throws IOException {
        if (Model.SRC_WOWI.equals(item.source)) return Wowi.latest(item.id);
        return GitHub.latest(item.id);
    }

    static Model.Item itemFor(Model.Entry e) {
        Model.Item c = Catalog.byKey(e.key);
        if (c != null) return c;
        Model.Item it = new Model.Item();
        it.source = e.source;
        it.id = e.id;
        it.name = e.name;
        it.author = e.author;
        it.iconUrl = e.iconUrl;
        it.pageUrl = e.pageUrl;
        return it;
    }

    // ───────────────────────────── GitHub ─────────────────────────────

    static final class GitHub {

        static String normalize(String input) {
            if (input == null) return null;
            String s = input.trim();
            s = s.replaceFirst("^(?i)https?://", "").replaceFirst("^(?i)www\\.", "");
            if (s.toLowerCase(Locale.ROOT).startsWith("github.com/")) s = s.substring("github.com/".length());
            String[] parts = s.split("/");
            if (parts.length < 2) return null;
            String owner = parts[0].trim();
            String repo = parts[1].trim().replaceFirst("\\.git$", "");
            if (!owner.matches("[A-Za-z0-9_.-]+") || !repo.matches("[A-Za-z0-9_.-]+")) return null;
            return owner + "/" + repo;
        }

        private static Map<String, String> apiHeaders() {
            return Http.headers("Accept", "application/vnd.github+json", "Authorization",
                    githubToken == null || githubToken.trim().isEmpty() ? null : "Bearer " + githubToken.trim());
        }

        static String latestTag(String repo) throws IOException {
            String loc = Http.redirectTarget("https://github.com/" + repo + "/releases/latest", null);
            if (loc == null) return null;
            int i = loc.indexOf("/releases/tag/");
            if (i < 0) return null;
            String tag = loc.substring(i + "/releases/tag/".length());
            try { tag = java.net.URLDecoder.decode(tag.replace("+", "%2B"), "UTF-8"); } catch (Exception ignored) {}
            return tag;
        }

        static Model.Release latest(String repo) throws IOException {
            String tag = null;
            try { tag = latestTag(repo); } catch (Http.HttpException e) {
                if (e.code == 404) throw new IOException(Lang.s(R.string.err_gh_repo_no_releases, repo));
            }
            String base = "https://github.com/" + repo + "/releases/" + (tag == null ? "latest/download/" : "download/" + encPath(tag) + "/");
            JSONObject rj = null;
            try {
                rj = new JSONObject(Http.get(base + "release.json"));
            } catch (Http.HttpException e) {
                if (e.code != 404) throw e;
            } catch (org.json.JSONException ignored) {
            }
            Model.Release r = new Model.Release();
            if (rj != null) {
                JSONArray rels = rj.optJSONArray("releases");
                JSONObject pick = null;
                int bestScore = -1;
                if (rels != null) {
                    for (int i = 0; i < rels.length(); i++) {
                        JSONObject rel = rels.optJSONObject(i);
                        if (rel == null) continue;
                        JSONArray meta = rel.optJSONArray("metadata");
                        int score = -1;
                        if (meta != null) for (int j = 0; j < meta.length(); j++) {
                            String fl = meta.optJSONObject(j) == null ? "" : meta.optJSONObject(j).optString("flavor");
                            if (FOREVER_FLAVORS[0].equals(fl)) score = Math.max(score, 2);
                            else if (FOREVER_FLAVORS[1].equals(fl)) score = Math.max(score, 1);
                        }
                        if (score >= 0 && !rel.optBoolean("nolib", false)) score += 10;
                        if (score > bestScore && score >= 0) { bestScore = score; pick = rel; }
                    }
                }
                if (pick == null) throw new IOException(Lang.s(R.string.err_gh_no_forever_build, repo));
                r.fileName = pick.optString("filename");
                r.version = pick.optString("version", "");
                if (r.version.isEmpty()) r.version = versionFromFile(r.fileName);
                if (r.version.isEmpty() && tag != null) r.version = tag;
                r.url = base + Http.enc(r.fileName);
                r.changelogUrl = "https://github.com/" + repo + "/releases" + (tag == null ? "/latest" : "/tag/" + encPath(tag));
                return r;
            }
            // No release.json: ask the API for the asset list and pick by name.
            JSONObject rel;
            try {
                rel = new JSONObject(Http.get("https://api.github.com/repos/" + repo + "/releases/latest", apiHeaders()));
            } catch (Http.HttpException e) {
                if (e.code == 404) throw new IOException(Lang.s(R.string.err_gh_no_published, repo));
                if (e.code == 403 || e.code == 429) throw new IOException(Lang.s(R.string.err_gh_rate_limit));
                throw e;
            } catch (org.json.JSONException e) {
                throw new IOException(Lang.s(R.string.err_gh_unexpected));
            }
            JSONArray assets = rel.optJSONArray("assets");
            JSONObject pick = null;
            boolean guessed = false;
            List<JSONObject> zips = new ArrayList<>();
            if (assets != null) for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && a.optString("name").toLowerCase(Locale.ROOT).endsWith(".zip")
                        && !AssetName.isAppBinary(a.optString("name"))) zips.add(a);
            }
            for (JSONObject a : zips) {
                String n = a.optString("name").toLowerCase(Locale.ROOT);
                if (n.contains("forever") || n.contains("camelot")) { pick = a; break; }
            }
            if (pick == null) {
                List<JSONObject> plain = new ArrayList<>();
                for (JSONObject a : zips) {
                    String n = a.optString("name").toLowerCase(Locale.ROOT);
                    boolean other = false;
                    for (String m : OTHER_FLAVOR_MARKS) if (n.contains("-" + m) || n.contains("_" + m)) other = true;
                    if (!other && !n.contains("nolib")) plain.add(a);
                }
                if (!plain.isEmpty()) { pick = plain.get(0); guessed = true; }
            }
            String t = rel.optString("tag_name", tag == null ? "" : tag);
            if (pick == null) {
                // Last resort: the source archive of the release (works for repos that are the addon itself).
                r.fileName = repo.substring(repo.indexOf('/') + 1) + "-" + t + ".zip";
                r.url = "https://api.github.com/repos/" + repo + "/zipball/" + Http.enc(t);
                r.headers = apiHeaders();
                r.guessed = true;
            } else {
                r.fileName = pick.optString("name");
                r.url = pick.optString("browser_download_url");
                r.guessed = guessed;
            }
            r.version = t.isEmpty() ? versionFromFile(r.fileName) : t;
            r.changelogUrl = rel.optString("html_url", null);
            return r;
        }

        /** Repository card for a user-entered owner/repo. */
        static Model.Item info(String repo) throws IOException {
            Model.Item it = new Model.Item();
            it.source = Model.SRC_GITHUB;
            it.id = repo;
            it.name = repo.substring(repo.indexOf('/') + 1);
            it.author = repo.substring(0, repo.indexOf('/'));
            it.pageUrl = "https://github.com/" + repo;
            it.iconUrl = "https://github.com/" + it.author + ".png?size=96";
            try {
                JSONObject o = new JSONObject(Http.get("https://api.github.com/repos/" + repo, apiHeaders()));
                it.name = o.optString("name", it.name);
                String d = o.optString("description", "");
                it.summary = "null".equals(d) ? "" : d;
                it.downloads = -1;
                it.updated = parseIso(o.optString("pushed_at"));
            } catch (Http.HttpException e) {
                if (e.code == 404) throw new IOException(Lang.s(R.string.err_gh_repo_not_found, repo));
                // rate limit: keep the bare card
            } catch (org.json.JSONException ignored) {
            }
            Model.Item c = Catalog.byKey(it.key());
            return c != null ? c : it;
        }
    }

    /** Encodes each segment of a tag such as "bundle/v1.2+3" but keeps the slashes. */
    static String encPath(String tag) {
        String[] parts = tag.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) { if (i > 0) sb.append('/'); sb.append(Http.enc(parts[i])); }
        return sb.toString();
    }

    static String versionFromFile(String fileName) {
        if (fileName == null) return "";
        String n = fileName.replaceFirst("(?i)\\.zip$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("v?\\d+(\\.\\d+)+[^\\s]*").matcher(n);
        return m.find() ? m.group() : n;
    }

    // ───────────────────────────── WoWInterface ─────────────────────────────

    static final class Wowi {
        static final String LIST = "https://api.mmoui.com/v3/game/WOW/filelist.json";
        static final String DETAILS = "https://api.mmoui.com/v3/game/WOW/filedetails/";
        private static List<Model.Item> all;
        private static long loadedAt;

        static synchronized List<Model.Item> list(boolean forceRefresh) throws IOException {
            File f = new File(cacheDir, "wowi-filelist.json");
            boolean fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 24L * 3600 * 1000;
            if (all != null && !forceRefresh && System.currentTimeMillis() - loadedAt < 3600 * 1000) return all;
            if (!fresh || forceRefresh) {
                File tmp = new File(cacheDir, "wowi-filelist.tmp");
                try {
                    Http.download(LIST, null, tmp, null);
                    if (!tmp.renameTo(f)) { Installer.copyRecursive(tmp, f); tmp.delete(); }
                } catch (IOException e) {
                    if (!f.exists()) throw e;
                }
            }
            all = parse(f);
            loadedAt = System.currentTimeMillis();
            return all;
        }

        private static List<Model.Item> parse(File f) throws IOException {
            List<Model.Item> out = new ArrayList<>();
            JsonReader r = new JsonReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            try {
                r.beginArray();
                while (r.hasNext()) {
                    Model.Item it = new Model.Item();
                    it.source = Model.SRC_WOWI;
                    r.beginObject();
                    while (r.hasNext()) {
                        String k = r.nextName();
                        if (r.peek() == JsonToken.NULL) { r.skipValue(); continue; }
                        switch (k) {
                            case "UID": it.id = r.nextString(); break;
                            case "UIName": it.name = r.nextString(); break;
                            case "UIAuthorName": it.author = r.nextString(); break;
                            case "UIVersion": it.version = r.nextString(); break;
                            case "UIDate": it.updated = r.nextLong(); break;
                            case "UIFileInfoURL": it.pageUrl = r.nextString(); break;
                            case "UIDownloadTotal": it.downloads = parseLong(r.nextString()); break;
                            case "UIDir":
                                if (r.peek() == JsonToken.BEGIN_ARRAY) {
                                    r.beginArray();
                                    while (r.hasNext()) it.folders.add(r.nextString());
                                    r.endArray();
                                } else r.skipValue();
                                break;
                            case "UICompatibility":
                                if (r.peek() == JsonToken.BEGIN_ARRAY) {
                                    r.beginArray();
                                    while (r.hasNext()) {
                                        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue; }
                                        r.beginObject();
                                        while (r.hasNext()) {
                                            String ck = r.nextName();
                                            if ("version".equals(ck) && r.peek() == JsonToken.STRING) {
                                                String v = r.nextString();
                                                if (v.startsWith("1.6")) it.foreverVerified = true;
                                            } else r.skipValue();
                                        }
                                        r.endObject();
                                    }
                                    r.endArray();
                                } else r.skipValue();
                                break;
                            default: r.skipValue();
                        }
                    }
                    r.endObject();
                    if (it.id != null && it.name != null) {
                        it.name = Toc.clean(it.name);
                        if (it.pageUrl == null) it.pageUrl = "https://www.wowinterface.com/downloads/info" + it.id;
                        out.add(it);
                    }
                }
                r.endArray();
            } catch (IllegalStateException e) {
                throw new IOException(Lang.s(R.string.err_wowi_list));
            } finally {
                r.close();
            }
            Collections.sort(out, (a, b) -> Long.compare(b.downloads, a.downloads));
            return out;
        }

        static List<Model.Item> search(String q, int from, int count) throws IOException {
            List<Model.Item> src = list(false);
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            List<Model.Item> hits = new ArrayList<>();
            List<Model.Item> tail = new ArrayList<>();
            for (Model.Item it : src) {
                if (needle.isEmpty()) { hits.add(it); continue; }
                String n = it.name.toLowerCase(Locale.ROOT);
                if (n.startsWith(needle)) hits.add(it);
                else if (n.contains(needle) || it.author.toLowerCase(Locale.ROOT).contains(needle)) tail.add(it);
                else for (String d : it.folders) if (d.toLowerCase(Locale.ROOT).contains(needle)) { tail.add(it); break; }
            }
            hits.addAll(tail);
            if (from >= hits.size()) return new ArrayList<>();
            return new ArrayList<>(hits.subList(from, Math.min(hits.size(), from + count)));
        }

        static Model.Release latest(String id) throws IOException {
            JSONObject d;
            try {
                JSONArray arr = new JSONArray(Http.get(DETAILS + id + ".json"));
                d = arr.optJSONObject(0);
            } catch (org.json.JSONException e) {
                throw new IOException(Lang.s(R.string.err_wowi_unexpected));
            }
            if (d == null) throw new IOException(Lang.s(R.string.err_wowi_not_found));
            if ("1".equals(d.optString("UIPending"))) throw new IOException(Lang.s(R.string.err_wowi_pending));
            Model.Release r = new Model.Release();
            r.version = d.optString("UIVersion");
            r.fileName = d.optString("UIFileName", "addon.zip");
            r.url = d.optString("UIDownload");
            r.md5 = d.optString("UIMD5", null);
            r.guessed = true;
            if (r.url == null || r.url.isEmpty()) throw new IOException(Lang.s(R.string.err_no_download));
            return r;
        }

        static String description(String id) {
            try {
                JSONArray arr = new JSONArray(Http.get(DETAILS + id + ".json"));
                JSONObject d = arr.optJSONObject(0);
                if (d == null) return null;
                String s = d.optString("UIDescription", "");
                s = s.replaceAll("\\[/?[a-zA-Z*][^\\]]*\\]", "").replaceAll("\\r", "").replaceAll("\\n{3,}", "\n\n").trim();
                return s.length() > 1200 ? s.substring(0, 1200) + "…" : s;
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return -1; }
    }

    static long parseIso(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return 0;
        String[] fmts = {"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ss"};
        for (String f : fmts) {
            try {
                SimpleDateFormat df = new SimpleDateFormat(f, Locale.US);
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                String in = s;
                if (in.matches(".*\\.\\d{4,}Z$")) in = in.replaceFirst("\\.(\\d{3})\\d+Z$", ".$1Z");
                return df.parse(in).getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static void writeText(File f, String s) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
        try { w.write(s); } finally { w.close(); }
        if (!tmp.renameTo(f)) {
            Installer.copyRecursive(tmp, f);
            tmp.delete();
        }
    }

    static String readText(File f) throws IOException {
        return Http.readAll(new FileInputStream(f));
    }

    private Sources() {}
}
