package app.wowforever.addons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A package is a GitHub repo that releases its core and each module on its own tag (see Modules):
 * owner/repo plus owner/repo#Module entries. The Library lists it as one row with its modules under it.
 */
final class Packages {

    /** The GitHub item id behind a Library row (managed, or a manual install the index recognizes), or null. */
    static String idOf(Model.Installed r) {
        if (r.entry != null) return Model.SRC_GITHUB.equals(r.entry.source) ? r.entry.id : null;
        if (r.catalogMatch != null && Model.SRC_GITHUB.equals(r.catalogMatch.source)) return r.catalogMatch.id;
        return null;
    }

    /**
     * Moves every module row (owner/repo#Module) under the row of its core (owner/repo), sorted by
     * title. A module whose core is not installed stays a row of its own.
     */
    static List<Model.Installed> fold(List<Model.Installed> rows) {
        Map<String, Model.Installed> cores = new HashMap<>();
        for (Model.Installed r : rows) {
            r.modules = new ArrayList<>();
            r.parent = null;
            String id = idOf(r);
            if (id != null && Modules.moduleOf(id) == null) cores.put(id.toLowerCase(Locale.ROOT), r);
        }
        List<Model.Installed> out = new ArrayList<>();
        for (Model.Installed r : rows) {
            String id = idOf(r);
            Model.Installed core = id != null && Modules.moduleOf(id) != null ? cores.get(Modules.repoOf(id).toLowerCase(Locale.ROOT)) : null;
            if (core == null || core == r) { out.add(r); continue; }
            r.parent = core;
            core.modules.add(r);
        }
        for (Model.Installed r : out) r.modules.sort((a, b) -> moduleName(a.title, r.title).compareToIgnoreCase(moduleName(b.title, r.title)));
        return out;
    }

    /** "Improved Forever: Camp" under "Improved Forever" gives "Camp"; another title stays as it is. */
    static String moduleName(String title, String coreTitle) {
        if (title == null) return "";
        if (coreTitle != null && !coreTitle.isEmpty() && title.length() > coreTitle.length()
                && title.regionMatches(true, 0, coreTitle, 0, coreTitle.length())) {
            String rest = title.substring(coreTitle.length()).replaceFirst("^[\\s:\\-–—_|·]+", "");
            if (!rest.isEmpty()) return rest;
        }
        return title;
    }

    private Packages() {}
}
