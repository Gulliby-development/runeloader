package app.wowforever.addons;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Core's Platform on Android: app folders, SharedPreferences and the storage volumes. */
final class AndroidPlatform implements Platform {

    private final Context ctx;
    private final Prefs prefs;

    private AndroidPlatform(Context ctx) {
        this.ctx = ctx;
        SharedPreferences sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        prefs = new Prefs() {
            @Override public String getString(String k, String def) { return sp.getString(k, def); }
            @Override public long getLong(String k, long def) { return sp.getLong(k, def); }
            @Override public int getInt(String k, int def) { return sp.getInt(k, def); }
            @Override public boolean getBoolean(String k, boolean def) { return sp.getBoolean(k, def); }
            @Override public void putString(String k, String v) { sp.edit().putString(k, v).apply(); }
            @Override public void putLong(String k, long v) { sp.edit().putLong(k, v).apply(); }
            @Override public void putInt(String k, int v) { sp.edit().putInt(k, v).apply(); }
            @Override public void putBoolean(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
            @Override public void remove(String k) { sp.edit().remove(k).apply(); }
        };
    }

    /** The process-wide Store, shared by the screens and the background job. */
    static Store store(Context ctx) {
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        return Store.get(new AndroidPlatform(app));
    }

    @Override public File filesDir() { return ctx.getFilesDir(); }
    @Override public File cacheDir() { return ctx.getCacheDir(); }
    @Override public Prefs prefs() { return prefs; }

    /** Internal storage and every SD card or USB volume. */
    @Override public List<File> searchRoots() {
        List<File> bases = new ArrayList<>();
        bases.add(Environment.getExternalStorageDirectory());
        File[] vols = new File("/storage").listFiles();
        if (vols != null) for (File v : vols) {
            String n = v.getName();
            if (n.equals("emulated") || n.equals("self") || !v.isDirectory()) continue;
            bases.add(v);
        }
        return bases;
    }
}
