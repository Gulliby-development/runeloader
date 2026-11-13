package app.wowforever.addons;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repos that release each module on its own tag (Core-v0.12.0, Wheel-v0.10.0, ...). The index lists
 * the core as owner/repo and each module as owner/repo#Module; each installs the newest published
 * release of its own group. Same rules as runeloader-index (indexer/forever.py, module_groups).
 */
final class Modules {

    private static final Pattern TAG = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)-v?\\d.*");
    /** Tag prefixes that mark a release channel, not a module ("release-1.2", "build-7"). */
    static final Set<String> NOT_MODULES = new HashSet<>(Arrays.asList("release", "build", "beta", "alpha", "rc",
            "nightly", "version", "ver", "dev", "test", "pre", "prerelease", "hotfix", "patch", "snapshot", "stable",
            "latest", "tag", "forever"));

    /** "Wheel-v0.10.0" gives "Wheel"; a plain "v1.2", "bundle/v1" or "release-1.2" gives null. */
    static String of(String tag) {
        if (tag == null) return null;
        Matcher m = TAG.matcher(tag);
        return m.matches() && !NOT_MODULES.contains(m.group(1).toLowerCase(Locale.ROOT)) ? m.group(1) : null;
    }

    static boolean isCore(String module, String repoName) {
        String m = module == null ? "" : module.toLowerCase(Locale.ROOT);
        return m.equals("core") || m.equals(repoName == null ? "" : repoName.toLowerCase(Locale.ROOT));
    }

    /** The group a release tag belongs to: "" for the core (plain "v1.2", "Core-v1", "<Repo>-v1"), else the module. */
    static String group(String tag, String repoName) {
        String m = of(tag);
        return m == null || isCore(m, repoName) ? "" : m;
    }

    /**
     * Tags newest first. The newest published tag of a group ("" for the core), or null when the repo
     * does not release by module (fewer than two groups) or that group has no published release.
     */
    static String tagOf(List<String> tags, List<Boolean> prerelease, String repoName, String group) {
        Set<String> groups = new HashSet<>();
        for (String t : tags) groups.add(group(t, repoName).toLowerCase(Locale.ROOT));
        if (groups.size() < 2) return null;
        String want = group == null ? "" : group.toLowerCase(Locale.ROOT);
        for (int i = 0; i < tags.size(); i++)
            if (!Boolean.TRUE.equals(prerelease.get(i)) && group(tags.get(i), repoName).toLowerCase(Locale.ROOT).equals(want))
                return tags.get(i);
        return null;
    }

    static String coreTag(List<String> tags, List<Boolean> prerelease, String repoName) {
        return tagOf(tags, prerelease, repoName, "");
    }

    /** "owner/repo#Wheel" gives "owner/repo"; an id without a module is returned as is. */
    static String repoOf(String id) {
        if (id == null) return null;
        int i = id.indexOf('#');
        return i < 0 ? id : id.substring(0, i);
    }

    /** "owner/repo#Wheel" gives "Wheel"; null without a module. */
    static String moduleOf(String id) {
        int i = id == null ? -1 : id.indexOf('#');
        return i < 0 || i == id.length() - 1 ? null : id.substring(i + 1);
    }

    /** The app id of an index entry: owner/repo, plus #Module for a module entry with a safe name. */
    static String id(String repo, String module) {
        return module == null || !module.matches("[A-Za-z][A-Za-z0-9_]*") ? repo : repo + "#" + module;
    }

    private Modules() {}
}
