package app.wowforever.addons;

/** Self-update version rules. Run: core/test.sh */
public class AppVersionTest {
    public static void main(String[] a) {
        check(AppVersion.newer("v0.0.3", "0.0.2"), "patch bump");
        check(AppVersion.newer("0.1.0", "0.0.9"), "minor bump");
        check(AppVersion.newer("0.0.10", "0.0.9"), "numbers, not text");
        check(AppVersion.newer("1.0", "0.9.9"), "short tag");
        check(!AppVersion.newer("0.0.2", "0.0.2"), "same version");
        check(!AppVersion.newer("v0.0.2", "0.0.2"), "same with v");
        check(!AppVersion.newer("0.0.1", "0.0.2"), "older release");
        check(!AppVersion.newer("0.0.2.0", "0.0.2"), "trailing zero");
        check(!AppVersion.newer("nightly", "0.0.2"), "unreadable tag");
        check(!AppVersion.newer(null, "0.0.2"), "no tag");
        check("0.0.3".equals(AppVersion.clean("v0.0.3-beta")), "suffix dropped");
        check("runeloader-0.0.3.apk".equals(AppVersion.apkName("v0.0.3")), "asset name as release.yml makes it");
        System.out.println("AppVersionTest: ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
