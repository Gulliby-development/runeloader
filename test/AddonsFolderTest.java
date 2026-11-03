package app.wowforever.addons;

import java.io.File;
import java.nio.file.Files;

/** Checks that any client's game folder, a flavor, Interface or AddOns folder resolves to the right AddOns folder. */
public class AddonsFolderTest {
    static int fails;

    static void eq(String what, Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) { fails++; System.out.println("FAIL " + what + ": want " + want + ", got " + got); }
        else System.out.println("ok   " + what);
    }

    static File dir(File base, String path) { File f = new File(base, path); f.mkdirs(); return f; }

    public static void main(String[] a) throws Exception {
        File t = Files.createTempDirectory("addonsfolder").toFile();

        File forever = dir(t, "WoW Forever");
        dir(forever, "_forever_/Interface/AddOns");
        eq("forever root", new File(forever, "_forever_/Interface/AddOns"), AddonsFolder.addonsDir(forever));

        File retail = dir(t, "World of Warcraft");
        dir(retail, "_retail_/Interface/AddOns");
        dir(retail, "_classic_/Interface/AddOns");
        eq("retail root picks first flavor", new File(retail, "_classic_/Interface/AddOns"), AddonsFolder.addonsDir(retail));
        File classic = new File(retail, "_classic_era_");
        dir(classic, "Interface");
        eq("picked flavor", new File(classic, "Interface/AddOns"), AddonsFolder.addonsDir(classic));
        eq("picked flavor is kept", classic, AddonsFolder.normalize(classic));

        File vanilla = dir(t, "WoW 1.12");
        dir(vanilla, "Interface/AddOns");
        eq("root with Interface", new File(vanilla, "Interface/AddOns"), AddonsFolder.addonsDir(vanilla));

        File addons = new File(vanilla, "Interface/AddOns");
        eq("picked AddOns", addons, AddonsFolder.addonsDir(addons));
        eq("picked Interface", addons, AddonsFolder.addonsDir(new File(vanilla, "Interface")));

        File fresh = dir(t, "fresh");
        new File(fresh, ".build.info").createNewFile();
        eq("fresh install", new File(fresh, "_forever_/Interface/AddOns"), AddonsFolder.addonsDir(fresh));

        eq("known child", forever, AddonsFolder.normalize(t));
        File plain = dir(t, "Downloads");
        eq("plain folder", null, AddonsFolder.normalize(plain));

        eq("flavor names", true, AddonsFolder.isFlavorName("_classic_era_") && !AddonsFolder.isFlavorName("__") && !AddonsFolder.isFlavorName("_x y_"));

        if (fails > 0) { System.out.println(fails + " failed"); System.exit(1); }
        System.out.println("all passed");
    }
}
