package app.wowforever.addons;

/** Android resources of the Categories tiles. */
final class CategoryIcons {

    static int nameRes(String key) {
        switch (key) {
            case "quests": return R.string.cat_quests;
            case "ui": return R.string.cat_ui;
            case "combat": return R.string.cat_combat;
            case "bags": return R.string.cat_bags;
            case "map": return R.string.cat_map;
            case "tools": return R.string.cat_tools;
            case Categories.GAMEPAD: return R.string.cat_gamepad;
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
            case Categories.GAMEPAD: return R.drawable.cat_gamepad;
            default: return R.drawable.cat_other;
        }
    }

    private CategoryIcons() {}
}
