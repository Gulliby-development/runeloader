package app.wowforever.addons;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Maps the optional metadata keys of a runeloader-index entry onto an Item: icon, description,
 * screenshots, category, tags, stars, changelog and recent releases. Every key may be missing.
 */
final class IndexFields {

    static final int MAX_TEXT = 4000;
    static final int MAX_SCREENSHOTS = 6;
    static final int MAX_RELEASES = 5;
    static final int MAX_TAGS = 10;

    /** @param base the index URL's directory, ending in "/", for relative icon paths. */
    static void fill(JSONObject a, String base, Model.Item it) {
        if (a == null || it == null) return;
        String icon = resolve(base, str(a, "icon"));
        if (icon != null) it.iconUrl = icon;
        it.description = cap(str(a, "description"));
        it.screenshots.clear();
        JSONArray shots = a.optJSONArray("screenshots");
        if (shots != null) for (int i = 0; i < shots.length() && it.screenshots.size() < MAX_SCREENSHOTS; i++) {
            String u = shots.optString(i, "");
            if (isHttps(u)) it.screenshots.add(u);
        }
        int cat = categoryRes(str(a, "category"));
        if (cat != 0 && (it.category == null || it.category.isEmpty())) {   // a catalog category wins
            it.category = Lang.s(cat);
            it.categoryKey = Categories.key(str(a, "category"));
        }
        it.tags.clear();
        JSONArray tags = a.optJSONArray("tags");
        if (tags != null) for (int i = 0; i < tags.length() && it.tags.size() < MAX_TAGS; i++) {
            String t = tags.optString(i, "").trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && !it.tags.contains(t)) it.tags.add(t);
        }
        if (a.has("stars")) it.stars = a.optLong("stars", -1);
        it.changelog = cap(str(a, "changelog"));
        it.releases.clear();
        JSONArray rels = a.optJSONArray("releases");
        if (rels != null) for (int i = 0; i < rels.length() && it.releases.size() < MAX_RELEASES; i++) {
            JSONObject r = rels.optJSONObject(i);
            if (r != null) it.releases.add(release(r, it.id));
        }
        if (it.changelog.isEmpty() && !it.releases.isEmpty()) it.changelog = it.releases.get(0).changelog;
    }

    static Model.Release release(JSONObject r, String repo) {
        Model.Release x = new Model.Release();
        x.tag = str(r, "tag");
        x.version = str(r, "version");
        if (x.version.isEmpty()) x.version = x.tag;
        x.fileName = str(r, "file");
        String url = str(r, "url");
        x.url = isHttps(url) ? url : null;
        x.size = r.optLong("size", -1);
        x.released = Sources.parseIso(str(r, "released"));
        x.prerelease = r.optBoolean("prerelease", false);
        x.changelog = cap(str(r, "changelog"));
        if (repo != null && !x.tag.isEmpty()) x.changelogUrl = "https://github.com/" + repo + "/releases/tag/" + Http.enc(x.tag);
        return x;
    }

    /** The index's category keys, mapped onto the app's own categories; 0 when unknown. */
    static int categoryRes(String key) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "quests": return R.string.cat_quests;
            case "ui": return R.string.cat_ui;
            case "combat": return R.string.cat_combat;
            case "bags": return R.string.cat_bags;
            case "map": return R.string.cat_map;
            case "tools": return R.string.cat_tools;
            default: return 0;
        }
    }

    /** A relative path inside the index site, or an absolute https URL; anything else is dropped. */
    static String resolve(String base, String path) {
        if (path == null || path.isEmpty()) return null;
        if (isHttps(path)) return path;
        if (base == null || path.contains("..") || path.contains(":") || path.startsWith("/") || path.contains("\\")) return null;
        return base + path;
    }

    static boolean isHttps(String u) {
        return u != null && u.regionMatches(true, 0, "https://", 0, 8) && u.length() > 8;
    }

    private static String str(JSONObject o, String k) {
        if (o.isNull(k)) return "";
        String s = o.optString(k, "");
        return s == null ? "" : s.trim();
    }

    private static String cap(String s) {
        if (s.length() <= MAX_TEXT) return s;
        int cut = s.lastIndexOf(' ', MAX_TEXT);
        return s.substring(0, cut > MAX_TEXT / 2 ? cut : MAX_TEXT) + "…";
    }

    private IndexFields() {}
}
