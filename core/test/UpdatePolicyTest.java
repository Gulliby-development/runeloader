package app.wowforever.addons;

import java.util.Arrays;
import java.util.List;

/** Rules of the background auto-update. Run: core/test.sh */
public class UpdatePolicyTest {
    public static void main(String[] a) {
        Model.Entry gh = entry("github", "owner/repo", "1.0", false);
        Model.Entry pinned = entry("wowi", "123", "2.0", true);
        Model.Entry zip = entry("zip", "Foo.zip", "", false);
        List<Model.Entry> c = UpdatePolicy.candidates(Arrays.asList(gh, pinned, zip, null));
        check(c.size() == 1 && c.get(0) == gh, "only tracked, unpinned entries are candidates: " + c.size());

        check(!UpdatePolicy.isNewer("v1.0", "1.0"), "a leading v is ignored");
        check(!UpdatePolicy.isNewer("1.0 ", "1.0"), "spaces are ignored");
        check(!UpdatePolicy.isNewer("REL-A", "rel-a"), "case is ignored");
        check(UpdatePolicy.isNewer("1.1", "1.0"), "another version is an update");
        check(!UpdatePolicy.isNewer("bundle/v12.4.0+v1.0.10", "v12.4.0+v1.0.10"), "a tag path prefix is ignored (#47)");
        check(!UpdatePolicy.isNewer("v12.4.0+v1.0.10", "release/12.4.0+v1.0.10"), "either side may carry the tag path");
        check(UpdatePolicy.isNewer("bundle/v12.5.0", "v12.4.0"), "a newer tag with a path is still an update");
        check(!UpdatePolicy.isNewer("version", "Version"), "a word starting with v keeps its v");
        check(UpdatePolicy.isNewer("version-2", "ersion-2"), "only a v before a digit is dropped");
        check(UpdatePolicy.isNewer("1.1", null), "an unknown installed version takes the update");
        check(!UpdatePolicy.isNewer(null, "1.0"), "no latest version, no update");
        check(!UpdatePolicy.isNewer("  ", "1.0"), "an empty latest version, no update");
        check(!UpdatePolicy.isNewer("12.1.0", "12.1.1"), "an older version from a stale index is no update");
        check(!UpdatePolicy.isNewer("v1.9", "1.10"), "numbers compare as numbers, not text");
        check(UpdatePolicy.isNewer("1.10", "1.9"), "1.10 is newer than 1.9");
        check(!UpdatePolicy.isNewer("1.2", "1.2.0"), "a missing part counts as 0");
        check(UpdatePolicy.isNewer("1.2.0-beta", "1.2.1"), "tags that are not plain numbers are not ordered");
        check(UpdatePolicy.older("12.1.0", "v12.1.1") && !UpdatePolicy.older("12.1.1", "12.1.0"), "older() orders plain numbers");
        check(!UpdatePolicy.older("12.1.0", null) && !UpdatePolicy.older("r100", "r200"), "older() is false when either is not a plain number");

        check(UpdatePolicy.mode(2) == UpdatePolicy.MODE_INSTALL, "install mode kept");
        check(UpdatePolicy.mode(7) == UpdatePolicy.MODE_OFF, "unknown mode is off");
        check(UpdatePolicy.intervalHours(12) == 12, "12 h kept");
        check(UpdatePolicy.intervalHours(1) == UpdatePolicy.DEFAULT_INTERVAL_H, "unsupported interval falls back");
        System.out.println("UpdatePolicyTest: ok");
    }

    private static Model.Entry entry(String source, String id, String version, boolean pinned) {
        Model.Entry e = new Model.Entry();
        e.source = source;
        e.id = id;
        e.key = source + ":" + id;
        e.name = id;
        e.version = version;
        e.pinned = pinned;
        return e;
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
