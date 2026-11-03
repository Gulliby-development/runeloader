package app.wowforever.addons;

/** GitHub asset filter. Run: java -cp /tmp/a app.wowforever.addons.AssetNameTest */
public class AssetNameTest {
    public static void main(String[] a) {
        check(AssetName.isAppBinary("wow-ui-sim-v0.1.39-forever-windows-x86_64.zip"), "windows build of a program");
        check(AssetName.isAppBinary("Launcher-Setup.zip"), "installer");
        check(AssetName.isAppBinary("tool_linux_arm64.zip"), "linux build");
        check(AssetName.isAppBinary("App.macOS.zip"), "mac build");
        check(!AssetName.isAppBinary("Questie-v12.4.0+v1.0.10.zip"), "addon with version");
        check(!AssetName.isAppBinary("Masque-12.1.8.zip"), "plain addon");
        check(!AssetName.isAppBinary("Details-forever.zip"), "forever addon");
        check(!AssetName.isAppBinary("WindowsTweaks-1.0.zip"), "token inside a word");
        check(!AssetName.isAppBinary(null), "no name");
        System.out.println("AssetNameTest: ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
