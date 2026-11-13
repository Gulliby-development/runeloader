package app.wowforever.addons;

import java.io.File;
import java.util.List;

/** What core needs from the app it runs in: folders, saved settings and where to look for the game. */
interface Platform {

    /** Private folder for files the app keeps (the addon list, favorites). */
    File filesDir();

    /** Folder for downloads and caches the system may clear. */
    File cacheDir();

    Prefs prefs();

    /** Folders to look for a game folder in (storage volumes, drives, home). */
    List<File> searchRoots();

    /** Small saved settings by key. Each put is saved; nothing waits for a commit. */
    interface Prefs {
        String getString(String key, String def);
        long getLong(String key, long def);
        int getInt(String key, int def);
        boolean getBoolean(String key, boolean def);
        void putString(String key, String value);
        void putLong(String key, long value);
        void putInt(String key, int value);
        void putBoolean(String key, boolean value);
        void remove(String key);
    }
}
