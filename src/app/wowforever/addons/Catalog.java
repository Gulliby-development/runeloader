package app.wowforever.addons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hand-picked addons that publish a WoW Forever build on GitHub (their release.json lists the
 * "forever" flavor). They install and update without any API key.
 */
final class Catalog {

    private static final int CAT_QUESTS = R.string.cat_quests, CAT_UI = R.string.cat_ui, CAT_COMBAT = R.string.cat_combat,
            CAT_BAGS = R.string.cat_bags, CAT_MAP = R.string.cat_map, CAT_TOOLS = R.string.cat_tools;
    private static final int[] CATEGORIES = {CAT_QUESTS, CAT_UI, CAT_COMBAT, CAT_BAGS, CAT_MAP, CAT_TOOLS};

    private static List<Model.Item> items;
    private static java.util.Locale itemsLocale;

    /** Category names in the UI language, in display order. */
    static List<String> categories() {
        List<String> l = new ArrayList<>();
        for (int c : CATEGORIES) l.add(Lang.s(c));
        return l;
    }

    /** Built again when the UI language changes, so names and summaries follow it. */
    static synchronized List<Model.Item> all() {
        if (items != null && Lang.locale().equals(itemsLocale)) return items;
        itemsLocale = Lang.locale();
        List<Model.Item> l = new ArrayList<>();
        add(l, "Questie/Questie", "Questie", "Questie Team", CAT_QUESTS,
                Lang.s(R.string.sum_questie),
                "Questie", "QuestieDB");
        add(l, "RestedXP/RXPGuides", "RestedXP Guides", "RestedXP", CAT_QUESTS,
                Lang.s(R.string.sum_rxp),
                "RXPGuides");
        add(l, "Nevcairiel/Bartender4", "Bartender4", "Nevcairiel", CAT_UI,
                Lang.s(R.string.sum_bartender),
                "Bartender4");
        add(l, "tullamods/Dominos", "Dominos", "Tuller", CAT_UI,
                Lang.s(R.string.sum_dominos),
                "Dominos", "Dominos_Cast", "Dominos_Config", "Dominos_Progress", "Dominos_Roll", "Masque_Dominos");
        add(l, "d4kir92/MoveAny", "MoveAny", "d4kir92", CAT_UI,
                Lang.s(R.string.sum_moveany),
                "MoveAny");
        add(l, "Tercioo/Plater-Nameplates", "Plater Nameplates", "Terciob", CAT_UI,
                Lang.s(R.string.sum_plater),
                "Plater");
        add(l, "Backupiseasy/ThreatPlates", "Threat Plates", "Suicidal Katt, Blacksalsify", CAT_UI,
                Lang.s(R.string.sum_threatplates),
                "TidyPlates_ThreatPlates");
        add(l, "SFX-WoW/Masque", "Masque", "StormFX", CAT_UI,
                Lang.s(R.string.sum_masque),
                "Masque");
        add(l, "Funkydude/SexyMap", "SexyMap", "Funkeh", CAT_MAP,
                Lang.s(R.string.sum_sexymap),
                "SexyMap");
        add(l, "Nevcairiel/Mapster", "Mapster", "Nevcairiel", CAT_MAP,
                Lang.s(R.string.sum_mapster),
                "Mapster");
        add(l, "BigWigsMods/BigWigs", "BigWigs Bossmods", "BigWigs Team", CAT_COMBAT,
                Lang.s(R.string.sum_bigwigs),
                "BigWigs", "BigWigs_Core", "BigWigs_Options", "BigWigs_Plugins");
        add(l, "DeadlyBossMods/DeadlyBossMods", "Deadly Boss Mods", "DBM Team", CAT_COMBAT,
                Lang.s(R.string.sum_dbm),
                "DBM-Core", "DBM-GUI", "DBM-StatusBarTimers", "DBM-VPVEM", "DBM-Brawlers",
                "DBM-Midnight", "DBM-Raids-Midnight", "DBM-Lairs-Midnight");
        Model.Item dungeons = add(l, "DeadlyBossMods/DBM-Dungeons", Lang.s(R.string.addon_dbm_dungeons), "DBM Team", CAT_COMBAT,
                Lang.s(R.string.sum_dbm_dungeons),
                "DBM-Party-Forever", "DBM-Party-Vanilla", "DBM-Party-BC", "DBM-Party-WotLK", "DBM-Party-Cataclysm",
                "DBM-Party-MoP", "DBM-Party-WoD", "DBM-Party-Legion", "DBM-Party-BfA", "DBM-Party-Shadowlands",
                "DBM-Party-Dragonflight", "DBM-Party-WarWithin", "DBM-Party-Midnight", "DBM-Challenges",
                "DBM-Delves-Midnight", "DBM-Delves-WarWithin", "DBM-WorldEvents");
        dungeons.deps.add("github:DeadlyBossMods/DeadlyBossMods");
        Model.Item vanilla = add(l, "DeadlyBossMods/DBM-Vanilla", Lang.s(R.string.addon_dbm_vanilla), "DBM Team", CAT_COMBAT,
                Lang.s(R.string.sum_dbm_vanilla),
                "DBM-Raids-Vanilla", "DBM-Azeroth");
        vanilla.deps.add("github:DeadlyBossMods/DeadlyBossMods");
        add(l, "BigWigsMods/Capping", "Capping", "BigWigs Team", CAT_COMBAT,
                Lang.s(R.string.sum_capping),
                "Capping", "Capping_Options");
        Model.Item tmw = add(l, "ascott18/TellMeWhen", "TellMeWhen", "Cybeloras", CAT_COMBAT,
                Lang.s(R.string.sum_tmw),
                "TellMeWhen", "TellMeWhen_Options");
        tmw.note = Lang.s(R.string.note_tmw);
        Model.Item musca = add(l, "dldvlpr-addons-wow/Musca-Auras", "Musca Auras (WeakAuras)", "dldvlpr", CAT_COMBAT,
                Lang.s(R.string.sum_musca),
                "WeakAuras", "WeakAurasOptions", "WeakAurasArchive", "WeakAurasModelPaths", "WeakAurasTemplates");
        musca.note = Lang.s(R.string.note_musca);
        add(l, "tullamods/tullaRange", "tullaRange", "Tuller", CAT_COMBAT,
                Lang.s(R.string.sum_tullarange),
                "tullaRange", "tullaRange_Config");
        add(l, "Cidan/BetterBags", "BetterBags", "Cidan", CAT_BAGS,
                Lang.s(R.string.sum_betterbags),
                "BetterBags");
        add(l, "kemayo/wow-simpleitemlevel", "Simple Item Level", "kemayo", CAT_BAGS,
                Lang.s(R.string.sum_simpleilvl),
                "SimpleItemLevel");
        add(l, "TorelTwiddler/CanIMogIt", "Can I Mog It?", "TorelTwiddler", CAT_BAGS,
                Lang.s(R.string.sum_canimogit),
                "CanIMogIt");
        add(l, "kemayo/wow-silverdragon", "SilverDragon", "kemayo", CAT_MAP,
                Lang.s(R.string.sum_silverdragon),
                "SilverDragon", "SilverDragon_History", "SilverDragon_Overlay", "SilverDragon_RangeExtender");
        add(l, "Stanzilla/AdvancedInterfaceOptions", "Advanced Interface Options", "Stanzilla", CAT_TOOLS,
                Lang.s(R.string.sum_aio),
                "AdvancedInterfaceOptions");
        add(l, "Hizuro/Broker_Everything", "Broker: Everything", "Hizuro", CAT_TOOLS,
                Lang.s(R.string.sum_broker),
                "Broker_Everything");
        Model.Item bugsack = add(l, "Funkydude/BugSack", "BugSack", "Funkeh", CAT_TOOLS,
                Lang.s(R.string.sum_bugsack),
                "BugSack");
        bugsack.note = Lang.s(R.string.note_bugsack);
        items = l;
        return l;
    }

    private static Model.Item add(List<Model.Item> l, String repo, String name, String author, int cat,
                                  String summary, String... folders) {
        Model.Item it = new Model.Item();
        it.source = Model.SRC_GITHUB;
        it.id = repo;
        it.name = name;
        it.author = author;
        it.category = Lang.s(cat);
        it.categoryKey = keyOf(cat);
        it.summary = summary;
        it.pageUrl = "https://github.com/" + repo;
        it.iconUrl = "https://github.com/" + repo.substring(0, repo.indexOf('/')) + ".png?size=96";
        it.foreverVerified = true;
        it.folders.addAll(Arrays.asList(folders));
        l.add(it);
        return it;
    }

    private static String keyOf(int cat) {
        for (String k : Categories.KEYS) if (Categories.nameRes(k) == cat) return k;
        return "";
    }

    static Model.Item byKey(String key) {
        for (Model.Item it : all()) if (it.key().equalsIgnoreCase(key)) return it;
        return null;
    }

    static Model.Item byFolder(String folder) {
        for (Model.Item it : all()) for (String f : it.folders) if (f.equalsIgnoreCase(folder)) return it;
        return null;
    }

    private Catalog() {}
}
