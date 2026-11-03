package app.wowforever.addons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hand-picked addons that publish a WoW Forever build on GitHub (their release.json lists the
 * "forever" flavor). They install and update without any API key.
 */
final class Catalog {

    static final String CAT_QUESTS = "Квесты и прокачка";
    static final String CAT_UI = "Интерфейс";
    static final String CAT_COMBAT = "Бой и боссы";
    static final String CAT_BAGS = "Сумки и предметы";
    static final String CAT_MAP = "Карта";
    static final String CAT_TOOLS = "Утилиты";

    static final List<String> CATEGORIES = Arrays.asList(CAT_QUESTS, CAT_UI, CAT_COMBAT, CAT_BAGS, CAT_MAP, CAT_TOOLS);

    private static List<Model.Item> items;

    static synchronized List<Model.Item> all() {
        if (items != null) return items;
        List<Model.Item> l = new ArrayList<>();
        add(l, "Questie/Questie", "Questie", "Questie Team", CAT_QUESTS,
                "Квест-помощник: задания, цели и NPC прямо на карте, миникарте и в подсказках. Must-have для прокачки.",
                "Questie", "QuestieDB");
        add(l, "RestedXP/RXPGuides", "RestedXP Guides", "RestedXP", CAT_QUESTS,
                "Пошаговые гайды прокачки со стрелкой-навигатором и подсказками, что делать дальше.",
                "RXPGuides");
        add(l, "Nevcairiel/Bartender4", "Bartender4", "Nevcairiel", CAT_UI,
                "Полная замена панелей действий: размеры, положение, видимость и пейджинг.",
                "Bartender4");
        add(l, "tullamods/Dominos", "Dominos", "Tuller", CAT_UI,
                "Лёгкие и гибкие панели действий, полоса заклинаний и опыта, поддержка Masque.",
                "Dominos", "Dominos_Cast", "Dominos_Config", "Dominos_Progress", "Dominos_Roll", "Masque_Dominos");
        add(l, "d4kir92/MoveAny", "MoveAny", "d4kir92", CAT_UI,
                "Перемещайте и масштабируйте почти любой элемент стандартного интерфейса.",
                "MoveAny");
        add(l, "Tercioo/Plater-Nameplates", "Plater Nameplates", "Terciob", CAT_UI,
                "Продвинутые неймплейты: угроза, касты, ауры, гибкая настройка и скрипты.",
                "Plater");
        add(l, "Backupiseasy/ThreatPlates", "Threat Plates", "Suicidal Katt, Blacksalsify", CAT_UI,
                "Неймплейты с отображением угрозы для танков и ДД, простая настройка.",
                "TidyPlates_ThreatPlates");
        add(l, "SFX-WoW/Masque", "Masque", "StormFX", CAT_UI,
                "Скины для кнопок действий, сумок и баффов. Работает с Dominos, Bartender и др.",
                "Masque");
        add(l, "Funkydude/SexyMap", "SexyMap", "Funkeh", CAT_MAP,
                "Красивая и настраиваемая миникарта: форма, рамки, кнопки аддонов.",
                "SexyMap");
        add(l, "Nevcairiel/Mapster", "Mapster", "Nevcairiel", CAT_MAP,
                "Улучшения карты мира: прозрачность, масштаб, координаты, туман войны.",
                "Mapster");
        add(l, "BigWigsMods/BigWigs", "BigWigs Bossmods", "BigWigs Team", CAT_COMBAT,
                "Таймеры и предупреждения на боссах рейдов. Лёгкая альтернатива DBM.",
                "BigWigs", "BigWigs_Core", "BigWigs_Options", "BigWigs_Plugins");
        add(l, "DeadlyBossMods/DeadlyBossMods", "Deadly Boss Mods", "DBM Team", CAT_COMBAT,
                "Классические таймеры и предупреждения на боссах. Ядро DBM.",
                "DBM-Core", "DBM-GUI", "DBM-StatusBarTimers", "DBM-VPVEM", "DBM-Brawlers",
                "DBM-Midnight", "DBM-Raids-Midnight", "DBM-Lairs-Midnight");
        Model.Item dungeons = add(l, "DeadlyBossMods/DBM-Dungeons", "DBM — Подземелья", "DBM Team", CAT_COMBAT,
                "Модули DBM для подземелий, испытаний и мировых событий (включая Forever).",
                "DBM-Party-Forever", "DBM-Party-Vanilla", "DBM-Party-BC", "DBM-Party-WotLK", "DBM-Party-Cataclysm",
                "DBM-Party-MoP", "DBM-Party-WoD", "DBM-Party-Legion", "DBM-Party-BfA", "DBM-Party-Shadowlands",
                "DBM-Party-Dragonflight", "DBM-Party-WarWithin", "DBM-Party-Midnight", "DBM-Challenges",
                "DBM-Delves-Midnight", "DBM-Delves-WarWithin", "DBM-WorldEvents");
        dungeons.deps.add("github:DeadlyBossMods/DeadlyBossMods");
        Model.Item vanilla = add(l, "DeadlyBossMods/DBM-Vanilla", "DBM — Классические рейды", "DBM Team", CAT_COMBAT,
                "Модули DBM для классических рейдов и мировых боссов Азерота.",
                "DBM-Raids-Vanilla", "DBM-Azeroth");
        vanilla.deps.add("github:DeadlyBossMods/DeadlyBossMods");
        add(l, "BigWigsMods/Capping", "Capping", "BigWigs Team", CAT_COMBAT,
                "Таймеры полей боя и арен: захват точек, флаги, очередь.",
                "Capping", "Capping_Options");
        Model.Item tmw = add(l, "ascott18/TellMeWhen", "TellMeWhen", "Cybeloras", CAT_COMBAT,
                "Иконки кулдаунов, баффов и дебаффов по вашим правилам.",
                "TellMeWhen", "TellMeWhen_Options");
        tmw.note = "В бою Forever скрывает часть данных от аддонов: сортировка и условия по кулдаунам там ограничены.";
        Model.Item musca = add(l, "dldvlpr-addons-wow/Musca-Auras", "Musca Auras (WeakAuras)", "dldvlpr", CAT_COMBAT,
                "Порт WeakAuras для Forever: свои индикаторы аур, кулдаунов и ресурсов.",
                "WeakAuras", "WeakAurasOptions", "WeakAurasArchive", "WeakAurasModelPaths", "WeakAurasTemplates");
        musca.note = "Триггеры журнала боя в Forever не срабатывают — импортированные ауры может понадобиться адаптировать.";
        add(l, "tullamods/tullaRange", "tullaRange", "Tuller", CAT_COMBAT,
                "Подсвечивает кнопки красным, когда цель вне досягаемости, и синим — когда не хватает маны.",
                "tullaRange", "tullaRange_Config");
        add(l, "Cidan/BetterBags", "BetterBags", "Cidan", CAT_BAGS,
                "Единая сумка с автоматическими категориями, поиском и сортировкой.",
                "BetterBags");
        add(l, "kemayo/wow-simpleitemlevel", "Simple Item Level", "kemayo", CAT_BAGS,
                "Показывает уровень предметов на иконках в сумках, экипировке и у торговцев.",
                "SimpleItemLevel");
        add(l, "TorelTwiddler/CanIMogIt", "Can I Mog It?", "TorelTwiddler", CAT_BAGS,
                "Отмечает предметы, внешний вид которых вы ещё не собрали для трансмогрификации.",
                "CanIMogIt");
        add(l, "kemayo/wow-silverdragon", "SilverDragon", "kemayo", CAT_MAP,
                "Оповещает о редких мобах рядом, показывает их на карте и ведёт историю.",
                "SilverDragon", "SilverDragon_History", "SilverDragon_Overlay", "SilverDragon_RangeExtender");
        add(l, "Stanzilla/AdvancedInterfaceOptions", "Advanced Interface Options", "Stanzilla", CAT_TOOLS,
                "Доступ к скрытым настройкам клиента (CVar) через удобное меню.",
                "AdvancedInterfaceOptions");
        add(l, "Hizuro/Broker_Everything", "Broker: Everything", "Hizuro", CAT_TOOLS,
                "Набор информеров: золото, FPS, задержка, друзья, гильдия, прочность и многое другое.",
                "Broker_Everything");
        Model.Item bugsack = add(l, "Funkydude/BugSack", "BugSack", "Funkeh", CAT_TOOLS,
                "Собирает ошибки Lua в «мешок», чтобы они не мешали играть.",
                "BugSack");
        bugsack.note = "Ошибки перехватывает !BugGrabber — его можно найти на CurseForge или WoWInterface.";
        items = l;
        return l;
    }

    private static Model.Item add(List<Model.Item> l, String repo, String name, String author, String cat,
                                  String summary, String... folders) {
        Model.Item it = new Model.Item();
        it.source = Model.SRC_GITHUB;
        it.id = repo;
        it.name = name;
        it.author = author;
        it.category = cat;
        it.summary = summary;
        it.pageUrl = "https://github.com/" + repo;
        it.iconUrl = "https://github.com/" + repo.substring(0, repo.indexOf('/')) + ".png?size=96";
        it.foreverVerified = true;
        it.folders.addAll(Arrays.asList(folders));
        l.add(it);
        return it;
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
