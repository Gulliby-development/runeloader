package app.wowforever.addons;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Library rules both apps share: rows, removes and update check results. */
public class LibraryTest {
    static int fails;

    static void eq(String what, Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) { fails++; System.out.println("FAIL " + what + ": want " + want + ", got " + got); }
        else System.out.println("ok   " + what);
    }

    static void addon(File addons, String folder, String toc) throws Exception {
        File d = new File(addons, folder);
        d.mkdirs();
        Files.write(new File(d, folder + ".toc").toPath(), toc.getBytes(StandardCharsets.UTF_8));
    }

    /** A platform in a temporary folder, with settings in memory. */
    static Platform platform(final File home) {
        final Map<String, Object> m = new HashMap<>();
        final Platform.Prefs prefs = new Platform.Prefs() {
            public String getString(String k, String d) { Object v = m.get(k); return v == null ? d : (String) v; }
            public long getLong(String k, long d) { Object v = m.get(k); return v == null ? d : (Long) v; }
            public int getInt(String k, int d) { Object v = m.get(k); return v == null ? d : (Integer) v; }
            public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v == null ? d : (Boolean) v; }
            public void putString(String k, String v) { m.put(k, v); }
            public void putLong(String k, long v) { m.put(k, v); }
            public void putInt(String k, int v) { m.put(k, v); }
            public void putBoolean(String k, boolean v) { m.put(k, v); }
            public void remove(String k) { m.remove(k); }
        };
        return new Platform() {
            public File filesDir() { return home; }
            public File cacheDir() { File c = new File(home, "cache"); c.mkdirs(); return c; }
            public Prefs prefs() { return prefs; }
            public List<File> searchRoots() { return new ArrayList<>(); }
        };
    }

    public static void main(String[] a) throws Exception {
        File t = Files.createTempDirectory("library").toFile();
        File game = new File(t, "Game");
        File addons = new File(game, "_forever_/Interface/AddOns");
        addons.mkdirs();
        addon(addons, "Questie", "## Interface: 16001\n## Title: Questie\n## Version: 1.0\n");
        addon(addons, "Questie_Options", "## Interface: 16001\n## Title: Questie Options\n");
        addon(addons, "HandMade", "## Interface: 11500\n## Title: Hand Made\n## Version: 2\n");
        addon(addons, "HandMade_Config", "## Interface: 11500\n## Title: Hand Made Config\n");

        Store store = Store.get(platform(new File(t, "app")));
        store.setGameRoot(game.getAbsolutePath());
        store.load();
        Model.Entry e = new Model.Entry();
        e.source = Model.SRC_GITHUB; e.id = "owner/questie"; e.key = e.source + ":" + e.id;
        e.name = "Questie"; e.version = "1.0";
        e.folders = new ArrayList<>(Arrays.asList("Questie", "Questie_Options"));
        store.put(e);

        List<Model.Installed> rows = Library.scan(store);
        eq("two rows", 2, rows.size());
        Model.Installed managed = rows.get(0), hand = rows.get(1);
        eq("managed first", e, managed.entry);
        eq("managed folders", 2, managed.folders.size());
        eq("main folder first", "Questie", managed.main().folder);
        eq("by hand grouped under its parent", 2, hand.folders.size());
        eq("by hand title", "Hand Made", hand.title);
        eq("by hand is not for Forever", false, hand.compatible);
        eq("remove takes every folder", Arrays.asList("HandMade", "HandMade_Config"), Library.removeFolders(hand));

        Library.applyCheck(e, "1.1", "notes", null);
        eq("newer version is an update", true, e.updateAvailable);
        eq("changelog kept", "notes", e.changelog);
        eq("updates counted", 1, Library.updates(store));
        Library.applyCheck(e, null, null, "offline");
        eq("an error keeps the last result", true, e.updateAvailable);
        eq("error noted", "offline", e.checkError);

        // a rescan makes new entry objects only after a reload; the check result carries over
        List<Model.Installed> again = Library.rows(store, Toc.scan(addons));
        Model.Entry copy = new Model.Entry();
        copy.key = e.key; copy.version = "1.0";
        again.get(0).entry = copy;
        Library.carryOver(rows, again);
        eq("result carried over", "1.1", copy.latest);

        // a ZIP that brings a managed folder takes it away from its entry
        Model.Entry hm = new Model.Entry();
        hm.source = Model.SRC_GITHUB; hm.id = "owner/handmade"; hm.key = hm.source + ":" + hm.id;
        hm.name = "Hand Made"; hm.folders = new ArrayList<>(Arrays.asList("HandMade", "HandMade_Config"));
        store.put(hm);
        File zip = new File(t, "HandMade.zip");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
            z.putNextEntry(new java.util.zip.ZipEntry("HandMade/HandMade.toc"));
            z.write("## Interface: 16001\n## Title: Hand Made\n## Version: 3\n".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        eq("zip installs its folder", Arrays.asList("HandMade"), Library.installZip(store, zip));
        eq("zip folder leaves the entry", Arrays.asList("HandMade_Config"), store.entry(hm.key).folders);
        store.remove(hm.key);

        Library.remove(store, managed);
        eq("folders deleted", false, new File(addons, "Questie_Options").exists());
        eq("entry forgotten", null, store.entry(e.key));
        eq("others stay", true, new File(addons, "HandMade").isDirectory());

        if (fails > 0) { System.out.println(fails + " failed"); System.exit(1); }
        System.out.println("LibraryTest: ok");
    }
}
