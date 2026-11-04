package app.wowforever.addons;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repos that release each module on its own tag (Core-v0.12.0, Wheel-v0.10.0, ...): their latest
 * release is one module without the core it needs, so the addon is the newest core release.
 * Same rule as runeloader-index (indexer/forever.py, core_release).
 */
final class Modules {

    private static final Pattern TAG = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)-v?\\d.*");

    /** "Wheel-v0.10.0" gives "Wheel"; a plain "v1.2" or "bundle/v1" gives null. */
    static String of(String tag) {
        if (tag == null) return null;
        Matcher m = TAG.matcher(tag);
        return m.matches() ? m.group(1) : null;
    }

    static boolean isCore(String module, String repoName) {
        String m = module == null ? "" : module.toLowerCase(Locale.ROOT);
        return m.equals("core") || m.equals(repoName == null ? "" : repoName.toLowerCase(Locale.ROOT));
    }

    /** Tags newest first; null when the repo does not release by module or has no published core. */
    static String coreTag(List<String> tags, List<Boolean> prerelease, String repoName) {
        Set<String> names = new HashSet<>();
        for (String t : tags) { String m = of(t); if (m != null) names.add(m.toLowerCase(Locale.ROOT)); }
        if (names.size() < 2) return null;
        for (int i = 0; i < tags.size(); i++)
            if (!Boolean.TRUE.equals(prerelease.get(i)) && isCore(of(tags.get(i)), repoName)) return tags.get(i);
        return null;
    }

    private Modules() {}
}
