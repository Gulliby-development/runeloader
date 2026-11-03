package app.wowforever.addons;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Where addons go for a chosen folder. Plain Java so test/ can run it on a JVM.
 * Any client works: the folder only has to be a WoW game folder, one of its
 * _flavor_ or Interface folders, or an AddOns folder.
 */
final class AddonsFolder {
    private AddonsFolder() {}

    /** Picked first when a game folder holds several flavors. */
    static final String PREFERRED_FLAVOR = "_forever_";
    /** Game folder names that auto-detect looks for on each storage volume. */
    static final String[] KNOWN_NAMES = {"WoW Forever", "World of Warcraft", "WoW", "Games/WoW Forever", "Games/World of Warcraft"};

    /** A flavor folder such as _forever_, _retail_ or _classic_era_. */
    static boolean isFlavorName(String n) {
        return n.length() > 2 && n.startsWith("_") && n.endsWith("_") && n.substring(1, n.length() - 1).matches("[A-Za-z0-9_]+");
    }

    /** The flavor folder of a game root, or null. */
    static File flavorOf(File root) {
        File pref = new File(root, PREFERRED_FLAVOR);
        if (pref.isDirectory()) return pref;
        File[] kids = root.listFiles();
        if (kids == null) return null;
        Arrays.sort(kids);
        for (File k : kids) if (k.isDirectory() && isFlavorName(k.getName()) && new File(k, "Interface").isDirectory()) return k;
        return null;
    }

    /** True when the folder is a game root, a flavor, Interface or AddOns folder. */
    static boolean recognized(File f) {
        if (f == null || !f.isDirectory()) return false;
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.equals("addons") || n.equals("interface") || isFlavorName(f.getName())) return true;
        return flavorOf(f) != null || new File(f, "Interface").isDirectory() || new File(f, ".build.info").exists();
    }

    /** The AddOns folder of a recognized folder. */
    static File addonsDir(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.equals("addons")) return f;
        if (n.equals("interface")) return new File(f, "AddOns");
        if (isFlavorName(f.getName())) return new File(f, "Interface/AddOns");
        File flavor = flavorOf(f);
        if (flavor != null) return new File(flavor, "Interface/AddOns");
        if (new File(f, "Interface").isDirectory()) return new File(f, "Interface/AddOns");
        // A game root without a flavor folder yet (a fresh install): the client creates _forever_ on first start.
        return new File(f, PREFERRED_FLAVOR + "/Interface/AddOns");
    }

    /** Turns a picked folder into the one to store: itself when recognized, else a known game folder inside it, else null. */
    static File normalize(File picked) {
        if (picked == null) return null;
        if (recognized(picked)) return picked;
        for (String name : KNOWN_NAMES) {
            File c = new File(picked, name);
            if (recognized(c)) return c;
        }
        return null;
    }
}
