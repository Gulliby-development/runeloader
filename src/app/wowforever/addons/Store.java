package app.wowforever.addons;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings (SharedPreferences) and the list of addons this app manages. */
final class Store {
    static final String FLAVOR_DIR = "_forever_";
    static final String STATE_FILE = "wow-addons.json";

    private final SharedPreferences prefs;
    private final File appState;
    final Map<String, Model.Entry> entries = new LinkedHashMap<>();

    private static Store instance;
    private boolean loaded;

    /** One Store per process, so the screens and the background job see the same addon list. */
    static synchronized Store get(Context ctx) {
        if (instance == null) instance = new Store(ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx);
        return instance;
    }

    private Store(Context ctx) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        appState = new File(ctx.getFilesDir(), STATE_FILE);
        Sources.cacheDir = ctx.getCacheDir();
        prefs.edit().remove("curseKey").apply();   // CurseForge is not a source; drop a key saved by an old version
        Sources.githubToken = githubToken();
    }

    // ── settings ──

    String gameRoot() { return prefs.getString("gameRoot", null); }
    void setGameRoot(String p) { prefs.edit().putString("gameRoot", p).apply(); }
    String githubToken() { return prefs.getString("githubToken", ""); }
    void setGithubToken(String t) { prefs.edit().putString("githubToken", t == null ? "" : t.trim()).apply(); Sources.githubToken = githubToken(); }
    long lastCheck() { return prefs.getLong("lastCheck", 0); }
    void setLastCheck(long t) { prefs.edit().putLong("lastCheck", t).apply(); }

    // ── background auto-update ──
    int autoMode() { return UpdatePolicy.mode(prefs.getInt("autoMode", UpdatePolicy.MODE_OFF)); }
    void setAutoMode(int m) { prefs.edit().putInt("autoMode", UpdatePolicy.mode(m)).apply(); }
    int autoIntervalHours() { return UpdatePolicy.intervalHours(prefs.getInt("autoInterval", UpdatePolicy.DEFAULT_INTERVAL_H)); }
    void setAutoIntervalHours(int h) { prefs.edit().putInt("autoInterval", UpdatePolicy.intervalHours(h)).apply(); }
    boolean autoWifiOnly() { return prefs.getBoolean("autoWifiOnly", true); }
    void setAutoWifiOnly(boolean b) { prefs.edit().putBoolean("autoWifiOnly", b).apply(); }
    boolean autoChargingOnly() { return prefs.getBoolean("autoCharging", false); }
    void setAutoChargingOnly(boolean b) { prefs.edit().putBoolean("autoCharging", b).apply(); }
    long autoLastRun() { return prefs.getLong("autoLastRun", 0); }
    int autoLastUpdated() { return prefs.getInt("autoLastUpdated", 0); }
    int autoLastFound() { return prefs.getInt("autoLastFound", 0); }
    String autoLastError() { return prefs.getString("autoLastError", null); }
    void setAutoLastRun(long t, int found, int updated, String error) {
        prefs.edit().putLong("autoLastRun", t).putInt("autoLastFound", found).putInt("autoLastUpdated", updated).putString("autoLastError", error).apply();
    }

    File root() { String r = gameRoot(); return r == null ? null : new File(r); }

    File flavorDir() { File r = root(); return r == null ? null : new File(r, FLAVOR_DIR); }

    File addonsDir() { File f = flavorDir(); return f == null ? null : new File(f, "Interface/AddOns"); }

    boolean gameReady() {
        File r = root();
        return r != null && r.isDirectory() && (new File(r, FLAVOR_DIR).isDirectory() || new File(r, ".build.info").exists());
    }

    /** Accepts the game root, its _forever_ folder, Interface or AddOns and returns the game root. */
    static File normalizeRoot(File picked) {
        if (picked == null) return null;
        File f = picked;
        for (int i = 0; i < 4 && f != null; i++) {
            if (new File(f, FLAVOR_DIR).isDirectory() || new File(f, ".build.info").exists()) return f;
            String n = f.getName();
            if (!(n.equalsIgnoreCase("AddOns") || n.equalsIgnoreCase("Interface") || n.equalsIgnoreCase(FLAVOR_DIR))) break;
            f = f.getParentFile();
        }
        // An empty or unknown folder: use a "WoW Forever" child if one is there.
        File child = new File(picked, "WoW Forever");
        if (child.isDirectory() && (new File(child, FLAVOR_DIR).isDirectory() || new File(child, ".build.info").exists())) return child;
        return null;
    }

    /** Looks for "WoW Forever" on internal storage and SD cards. */
    static List<File> detect() {
        List<File> out = new ArrayList<>();
        List<File> bases = new ArrayList<>();
        bases.add(Environment.getExternalStorageDirectory());
        File[] vols = new File("/storage").listFiles();
        if (vols != null) for (File v : vols) {
            String n = v.getName();
            if (n.equals("emulated") || n.equals("self") || !v.isDirectory()) continue;
            bases.add(v);
        }
        for (File b : bases) {
            for (String name : new String[]{"WoW Forever", "World of Warcraft", "WoW", "Games/WoW Forever"}) {
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
        File f = flavorDir();
        return f == null ? null : new File(f, "Interface/" + STATE_FILE);
    }

    synchronized boolean isLoaded() { return loaded; }

    synchronized void load() {
        loaded = true;
        entries.clear();
        File gs = gameState();
        File src = gs != null && gs.exists() ? gs : appState;
        if (!src.exists()) return;
        try {
            JSONObject o = new JSONObject(Sources.readText(src));
            JSONArray arr = o.optJSONArray("addons");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
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
                JSONArray fs = j.optJSONArray("folders");
                if (fs != null) for (int k = 0; k < fs.length(); k++) e.folders.add(fs.optString(k));
                entries.put(e.key, e);
            }
        } catch (Exception ignored) {
        }
    }

    synchronized void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Model.Entry e : entries.values()) {
                JSONObject j = new JSONObject();
                j.put("source", e.source);
                j.put("id", e.id);
                j.put("name", e.name);
                j.put("author", e.author);
                j.put("version", e.version);
                if (e.iconUrl != null) j.put("icon", e.iconUrl);
                if (e.pageUrl != null) j.put("page", e.pageUrl);
                j.put("installedAt", e.installedAt);
                if (e.pinned) j.put("pinned", true);
                JSONArray fs = new JSONArray();
                for (String f : e.folders) fs.put(f);
                j.put("folders", fs);
                arr.put(j);
            }
            JSONObject o = new JSONObject();
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
}
