package app.wowforever.addons;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings (Platform.Prefs) and the list of addons this app manages. */
public final class Store {
    static final String STATE_FILE = "wow-addons.json";
    static final String FAVORITES_FILE = "favorites.json";

    private final Platform platform;
    private final Platform.Prefs prefs;
    private final File appState;
    private final File favoritesFile;
    /** Starred addons by key, newest first; a snapshot so the list shows without the index. */
    private Map<String, Model.Item> favorites;
    final Map<String, Model.Entry> entries = new LinkedHashMap<>();

    private static Store instance;
    private boolean loaded;

    /** One Store per process, so the screens and the background job see the same addon list. */
    static synchronized Store get(Platform platform) {
        if (instance == null) instance = new Store(platform);
        return instance;
    }

    private Store(Platform platform) {
        this.platform = platform;
        prefs = platform.prefs();
        appState = new File(platform.filesDir(), STATE_FILE);
        favoritesFile = new File(platform.filesDir(), FAVORITES_FILE);
        Sources.cacheDir = platform.cacheDir();
        // CurseForge is not a source, and the GitHub token setting is gone; drop values saved by old versions.
        prefs.remove("curseKey");
        prefs.remove("githubToken");
    }

    // ── settings ──

    String gameRoot() { return prefs.getString("gameRoot", null); }
    void setGameRoot(String p) { prefs.putString("gameRoot", p); }
    long lastCheck() { return prefs.getLong("lastCheck", 0); }
    void setLastCheck(long t) { prefs.putLong("lastCheck", t); }

    // ── Runeloader's own update ──
    long selfCheckAt() { return prefs.getLong("selfCheckAt", 0); }
    void setSelfCheckAt(long t) { prefs.putLong("selfCheckAt", t); }
    /** The last version offered on start, so "Later" is not asked again for it. */
    String selfOffered() { return prefs.getString("selfOffered", ""); }
    void setSelfOffered(String v) { prefs.putString("selfOffered", v); }

    // ── background auto-update ──
    int autoMode() { return UpdatePolicy.mode(prefs.getInt("autoMode", UpdatePolicy.MODE_OFF)); }
    void setAutoMode(int m) { prefs.putInt("autoMode", UpdatePolicy.mode(m)); }
    int autoIntervalHours() { return UpdatePolicy.intervalHours(prefs.getInt("autoInterval", UpdatePolicy.DEFAULT_INTERVAL_H)); }
    void setAutoIntervalHours(int h) { prefs.putInt("autoInterval", UpdatePolicy.intervalHours(h)); }
    boolean autoWifiOnly() { return prefs.getBoolean("autoWifiOnly", true); }
    void setAutoWifiOnly(boolean b) { prefs.putBoolean("autoWifiOnly", b); }
    boolean autoChargingOnly() { return prefs.getBoolean("autoCharging", false); }
    void setAutoChargingOnly(boolean b) { prefs.putBoolean("autoCharging", b); }
    long autoLastRun() { return prefs.getLong("autoLastRun", 0); }
    int autoLastUpdated() { return prefs.getInt("autoLastUpdated", 0); }
    int autoLastFound() { return prefs.getInt("autoLastFound", 0); }
    String autoLastError() { return prefs.getString("autoLastError", null); }
    void setAutoLastRun(long t, int found, int updated, String error) {
        prefs.putLong("autoLastRun", t);
        prefs.putInt("autoLastFound", found);
        prefs.putInt("autoLastUpdated", updated);
        prefs.putString("autoLastError", error);
    }

    File root() { String r = gameRoot(); return r == null ? null : new File(r); }

    /** True when the user chose a plain folder and addons go straight into it. */
    /** Browse lists only addons whose source confirms a Forever build. */
    boolean verifiedOnly() { return prefs.getBoolean("verifiedOnly", false); }
    void setVerifiedOnly(boolean b) { prefs.putBoolean("verifiedOnly", b); }
    boolean addonsDirect() { return prefs.getBoolean("addonsDirect", false); }
    void setAddonsDirect(boolean b) { prefs.putBoolean("addonsDirect", b); }

    File addonsDir() {
        File r = root();
        if (r == null) return null;
        return addonsDirect() ? r : AddonsFolder.addonsDir(r);
    }

    boolean gameReady() {
        File r = root();
        return r != null && r.isDirectory() && (addonsDirect() || AddonsFolder.recognized(r));
    }

    /** Accepts a game folder of any client, its flavor, Interface or AddOns folder. */
    static File normalizeRoot(File picked) { return AddonsFolder.normalize(picked); }

    /** Looks for a game folder in the platform's search roots (storage volumes, drives). */
    List<File> detect() {
        List<File> out = new ArrayList<>();
        for (File b : platform.searchRoots()) {
            for (String name : AddonsFolder.KNOWN_NAMES) {
                File c = normalizeRoot(new File(b, name));
                if (c != null && !out.contains(c)) out.add(c);
            }
            File c = normalizeRoot(b);
            if (c != null && !out.contains(c)) out.add(c);
        }
        return out;
    }

    // ── managed addons ──

    private File gameState() {
        // Kept next to AddOns only inside an Interface folder; a plain folder keeps it in the app.
        File a = addonsDir();
        File parent = a == null ? null : a.getParentFile();
        return parent != null && parent.getName().equalsIgnoreCase("Interface") ? new File(parent, STATE_FILE) : null;
    }

    synchronized boolean isLoaded() { return loaded; }

    synchronized void load() {
        loaded = true;
        entries.clear();
        File gs = gameState();
        File src = gs != null && gs.exists() ? gs : appState;
        if (!src.exists()) return;
        try {
            Json.Obj o = new Json.Obj(Sources.readText(src));
            Json.Arr arr = o.optJSONArray("addons");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                Json.Obj j = arr.optJSONObject(i);
                if (j == null) continue;
                Model.Entry e = new Model.Entry();
                e.source = j.optString("source");
                e.id = j.optString("id");
                e.key = e.source + ":" + e.id;
                e.name = j.optString("name", e.id);
                e.author = j.optString("author", "");
                e.version = j.optString("version", "");
                e.iconUrl = j.isNull("icon") ? null : j.optString("icon", null);
                e.pageUrl = j.isNull("page") ? null : j.optString("page", null);
                e.installedAt = j.optLong("installedAt");
                e.pinned = j.optBoolean("pinned", false);
                Json.Arr fs = j.optJSONArray("folders");
                if (fs != null) for (int k = 0; k < fs.length(); k++) e.folders.add(fs.optString(k));
                entries.put(e.key, e);
            }
        } catch (Exception ignored) {
        }
    }

    synchronized void save() {
        try {
            Json.Arr arr = new Json.Arr();
            for (Model.Entry e : entries.values()) {
                Json.Obj j = new Json.Obj();
                j.put("source", e.source);
                j.put("id", e.id);
                j.put("name", e.name);
                j.put("author", e.author);
                j.put("version", e.version);
                if (e.iconUrl != null) j.put("icon", e.iconUrl);
                if (e.pageUrl != null) j.put("page", e.pageUrl);
                j.put("installedAt", e.installedAt);
                if (e.pinned) j.put("pinned", true);
                Json.Arr fs = new Json.Arr();
                for (String f : e.folders) fs.put(f);
                j.put("folders", fs);
                arr.put(j);
            }
            Json.Obj o = new Json.Obj();
            o.put("format", "wow-addons-android");
            o.put("schema", 1);
            o.put("flavor", "wowf");
            o.put("addons", arr);
            String text = o.toString(2);
            Sources.writeText(appState, text);
            File gs = gameState();
            if (gs != null && gs.getParentFile().isDirectory()) Sources.writeText(gs, text);
        } catch (Exception ignored) {
        }
    }

    synchronized Model.Entry entry(String key) { return entries.get(key); }

    synchronized List<Model.Entry> list() { return new ArrayList<>(entries.values()); }

    synchronized void put(Model.Entry e) { entries.put(e.key, e); save(); }

    synchronized void remove(String key) { entries.remove(key); save(); }

    /** Which managed entry owns a folder. */
    synchronized Model.Entry owner(String folder) {
        for (Model.Entry e : entries.values()) for (String f : e.folders) if (f.equalsIgnoreCase(folder)) return e;
        return null;
    }

    // ── favorites ──
    // Kept in the app, not next to AddOns: a favorite is not installed and belongs to no game folder.

    synchronized boolean isFavorite(String key) { return favs().containsKey(key); }

    /** Newest first. */
    synchronized List<Model.Item> favorites() { return new ArrayList<>(favs().values()); }

    /** Stars or unstars the item; returns true when it is a favorite now. */
    synchronized boolean toggleFavorite(Model.Item it) {
        Map<String, Model.Item> f = favs();
        boolean on = f.remove(it.key()) == null;
        if (on) {
            Map<String, Model.Item> next = new LinkedHashMap<>();
            next.put(it.key(), it);
            next.putAll(f);
            favorites = next;
        }
        saveFavorites();
        return on;
    }

    private Map<String, Model.Item> favs() {
        if (favorites != null) return favorites;
        favorites = new LinkedHashMap<>();
        if (!favoritesFile.exists()) return favorites;
        try {
            Json.Arr arr = new Json.Obj(Sources.readText(favoritesFile)).optJSONArray("addons");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                Json.Obj j = arr.optJSONObject(i);
                if (j == null || j.optString("id").isEmpty()) continue;
                Model.Item it = new Model.Item();
                it.source = j.optString("source", Model.SRC_GITHUB);
                it.id = j.optString("id");
                it.name = j.optString("name", it.id);
                it.author = j.optString("author", "");
                it.summary = j.optString("summary", "");
                it.category = j.optString("category", "");
                it.categoryKey = j.optString("categoryKey", "");
                it.iconUrl = j.isNull("icon") ? null : j.optString("icon", null);
                it.pageUrl = j.isNull("page") ? null : j.optString("page", null);
                it.version = j.isNull("version") ? null : j.optString("version", null);
                it.stars = j.optLong("stars", -1);
                it.downloads = j.optLong("downloads", -1);
                it.updated = j.optLong("updated", 0);
                it.foreverVerified = j.optBoolean("verified", false);
                strings(j.optJSONArray("tags"), it.tags);
                strings(j.optJSONArray("folders"), it.folders);
                strings(j.optJSONArray("deps"), it.deps);
                favorites.put(it.key(), it);
            }
        } catch (Exception ignored) {
        }
        return favorites;
    }

    private static void strings(Json.Arr a, List<String> out) {
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
    }

    private void saveFavorites() {
        try {
            Json.Arr arr = new Json.Arr();
            for (Model.Item it : favorites.values()) {
                Json.Obj j = new Json.Obj();
                j.put("source", it.source);
                j.put("id", it.id);
                j.put("name", it.name);
                j.put("author", it.author);
                j.put("summary", it.summary);
                j.put("category", it.category);
                j.put("categoryKey", it.categoryKey);
                if (it.iconUrl != null) j.put("icon", it.iconUrl);
                if (it.pageUrl != null) j.put("page", it.pageUrl);
                if (it.version != null) j.put("version", it.version);
                j.put("stars", it.stars);
                j.put("downloads", it.downloads);
                j.put("updated", it.updated);
                j.put("verified", it.foreverVerified);
                j.put("tags", new Json.Arr(it.tags));
                j.put("folders", new Json.Arr(it.folders));
                j.put("deps", new Json.Arr(it.deps));
                arr.put(j);
            }
            Json.Obj o = new Json.Obj();
            o.put("schema", 1);
            o.put("addons", arr);
            Sources.writeText(favoritesFile, o.toString(2));
        } catch (Exception ignored) {
        }
    }
}
