package app.wowforever.addons;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * UI language. The empty tag follows the system: Android picks the first supported language from
 * the system list, and res/values (English) is the fallback. Any other tag overrides it for this app.
 */
final class Lang {
    /** Tags in picker order. Each one needs a res/values-<tag> folder, except English (res/values). */
    static final String[] TAGS = {"", "en", "de", "es", "fr", "pl", "pt", "ru", "zh"};
    /** Each language by its own name, so a user can find theirs whatever the current UI language. */
    private static final String[] NAMES = {null, "English", "Deutsch", "Español", "Français", "Polski", "Português", "Русский", "中文（简体）"};

    private static final String PREFS = "settings";
    private static final String KEY = "language";

    /** Localized app context for code outside an activity (sources, catalog, formatting). */
    private static volatile Context res;

    static String saved(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    static void save(Context ctx, String tag) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag == null ? "" : tag).commit();
    }

    /** Wraps an activity's base context in the chosen language; call from attachBaseContext. */
    static Context wrap(Context base) {
        String tag = saved(base);
        Locale l = tag.isEmpty() ? android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0) : Locale.forLanguageTag(tag);
        Locale.setDefault(l);
        Context app = base.getApplicationContext() != null ? base.getApplicationContext() : base;
        res = tag.isEmpty() ? app : withLocale(app, l);
        Installer.texts = INSTALLER;
        return tag.isEmpty() ? base : withLocale(base, l);
    }

    private static Context withLocale(Context ctx, Locale l) {
        // Only the locale: a full copy would pin orientation and screen size across rotations.
        Configuration c = new Configuration();
        c.setLocale(l);
        return ctx.createConfigurationContext(c);
    }

    static String name(Context ctx, String tag) {
        for (int i = 0; i < TAGS.length; i++) if (TAGS[i].equals(tag)) return NAMES[i] != null ? NAMES[i] : ctx.getString(R.string.language_system);
        return tag;
    }

    /** Current locale for number and date formats. */
    static Locale locale() {
        Context c = res;
        return c != null ? c.getResources().getConfiguration().getLocales().get(0) : Locale.getDefault();
    }

    static String s(int id) {
        Context c = res;
        return c == null ? "" : c.getString(id);
    }

    static String s(int id, Object... args) {
        Context c = res;
        return c == null ? "" : c.getString(id, args);
    }

    static String q(int id, int n) {
        Context c = res;
        return c == null ? String.valueOf(n) : c.getResources().getQuantityString(id, n, n);
    }

    /** Installer messages from the string resources. */
    static final Installer.Texts INSTALLER = new Installer.Texts() {
        @Override public String noAddons() { return s(R.string.inst_no_addons); }
        @Override public String cannotCreate(Object path) { return s(R.string.inst_cannot_create, path); }
        @Override public String noWrite(Object path) { return s(R.string.inst_no_write, path); }
        @Override public String unpacking(String names) { return s(R.string.inst_unpacking, names); }
        @Override public String unpackingFiles(int index, int total) { return s(R.string.inst_unpacking_files, index, total); }
        @Override public String unsafePath(String name) { return s(R.string.inst_unsafe_path, name); }
        @Override public String installing() { return s(R.string.inst_installing); }
        @Override public String cannotWrite(String name) { return s(R.string.inst_cannot_write, name); }
        @Override public String cannotDelete(Object path) { return s(R.string.inst_cannot_delete, path); }
    };

    private Lang() {}
}
