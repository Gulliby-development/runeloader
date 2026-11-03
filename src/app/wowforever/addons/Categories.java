package app.wowforever.addons;

import java.util.Locale;

/**
 * The addon categories of the Browse grid. The keys match the index's `category` values; an addon
 * without a known category falls into OTHER. A new category needs a key here, a `cat_<key>` string
 * in every language and a `cat_<key>` drawable.
 */
final class Categories {

    static final String OTHER = "other";
    /** Display order of the grid. */
    static final String[] KEYS = {"quests", "ui", "combat", "bags", "map", "tools", OTHER};

    /** The key for an index value, or "" when it is not one of ours. */
    static String key(String raw) {
        String k = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (String c : KEYS) if (!c.equals(OTHER) && c.equals(k)) return c;
        return "";
    }

    static String of(Model.Item it) {
        return it.categoryKey == null || it.categoryKey.isEmpty() ? OTHER : it.categoryKey;
    }

    /** True when the item is in the category; a null category takes everything. */
    static boolean matches(Model.Item it, String category) {
        return category == null || of(it).equals(category);
    }

    static int nameRes(String key) {
        switch (key) {
            case "quests": return R.string.cat_quests;
            case "ui": return R.string.cat_ui;
            case "combat": return R.string.cat_combat;
            case "bags": return R.string.cat_bags;
            case "map": return R.string.cat_map;
            case "tools": return R.string.cat_tools;
            default: return R.string.cat_other;
        }
    }

    static int iconRes(String key) {
        switch (key) {
            case "quests": return R.drawable.cat_quests;
            case "ui": return R.drawable.cat_ui;
            case "combat": return R.drawable.cat_combat;
            case "bags": return R.drawable.cat_bags;
            case "map": return R.drawable.cat_map;
            case "tools": return R.drawable.cat_tools;
            default: return R.drawable.cat_other;
        }
    }

    private Categories() {}
}
