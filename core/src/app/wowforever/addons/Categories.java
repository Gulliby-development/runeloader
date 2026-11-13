package app.wowforever.addons;

import java.util.Locale;

/**
 * The addon categories of the Browse grid. The keys match the index's `category` values; an addon
 * without a known category falls into OTHER. A new category needs a key here, a `cat_<key>` string
 * in every language and a `cat_<key>` icon (CategoryIcons on Android).
 *
 * GAMEPAD is a tag, not a category: it comes from the index's `tags` list and holds addons from any
 * category. A new tag tile goes into TAGS and into the same three places.
 */
final class Categories {

    static final String OTHER = "other";
    static final String GAMEPAD = "gamepad";
    /** Tiles that select by an index tag instead of the category. */
    static final String[] TAGS = {GAMEPAD};
    /** Display order of the grid. */
    static final String[] KEYS = {"quests", "ui", "combat", "bags", "map", "tools", GAMEPAD, OTHER};

    /** The grid order; with a gamepad in hand, its tile comes first. */
    static String[] order(boolean gamepadFirst) {
        if (!gamepadFirst) return KEYS;
        String[] o = new String[KEYS.length];
        o[0] = GAMEPAD;
        int i = 1;
        for (String k : KEYS) if (!k.equals(GAMEPAD)) o[i++] = k;
        return o;
    }

    static boolean isTag(String key) {
        for (String t : TAGS) if (t.equals(key)) return true;
        return false;
    }

    /** The key for an index value, or "" when it is not one of ours. */
    static String key(String raw) {
        String k = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (String c : KEYS) if (!c.equals(OTHER) && !isTag(c) && c.equals(k)) return c;
        return "";
    }

    static String of(Model.Item it) {
        return it.categoryKey == null || it.categoryKey.isEmpty() ? OTHER : it.categoryKey;
    }

    /** True when the item is in the category; a null category takes everything. */
    static boolean matches(Model.Item it, String category) {
        if (category == null) return true;
        if (isTag(category)) return it.tags.contains(category);
        return of(it).equals(category);
    }

    /** The string name of a category or tag tile: cat_<key>, cat_other for anything unknown. */
    static String nameKey(String key) {
        for (String k : KEYS) if (k.equals(key)) return "cat_" + k;
        return "cat_" + OTHER;
    }

    private Categories() {}
}
