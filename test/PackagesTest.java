package app.wowforever.addons;

import java.util.ArrayList;
import java.util.List;

public class PackagesTest {
    static void check(boolean ok, String what) { if (!ok) throw new RuntimeException("FAIL: " + what); System.out.println("ok  " + what); }

    static Model.Installed row(String source, String id, String title) {
        Model.Installed r = new Model.Installed();
        r.entry = new Model.Entry();
        r.entry.source = source;
        r.entry.id = id;
        r.entry.key = source + ":" + id;
        r.title = title;
        return r;
    }

    public static void main(String[] a) {
        List<Model.Installed> rows = new ArrayList<>();
        Model.Installed core = row("github", "markgravity/ImprovedForever", "Improved Forever");
        rows.add(row("github", "markgravity/ImprovedForever#Wheel", "Improved Forever: Wheel"));
        rows.add(core);
        rows.add(row("github", "markgravity/improvedforever#Camp", "Improved Forever: Camp"));
        rows.add(row("github", "other/Pack#Wheel", "Pack: Wheel"));
        rows.add(row("wowi", "123", "Questie"));
        Model.Installed manual = new Model.Installed();
        manual.title = "Manual";
        rows.add(manual);
        List<Model.Installed> out = Packages.fold(rows);
        check(out.size() == 4, "modules leave the top list");
        check(core.isPackage() && core.modules.size() == 2, "modules sit under their core, repo case ignored");
        check(core.modules.get(0).title.endsWith("Camp") && core.modules.get(0).parent == core, "modules sorted, parent set");
        check(out.get(1).title.equals("Pack: Wheel") && out.get(1).parent == null, "a module without its core stays a row");
        check(core.withModules().size() == 3, "withModules is the core and its modules");
        check(Packages.fold(out).size() == 4 && core.modules.size() == 0, "fold again starts over from the rows given");

        Model.Installed manualModule = new Model.Installed();
        manualModule.catalogMatch = new Model.Item();
        manualModule.catalogMatch.source = "github";
        manualModule.catalogMatch.id = "markgravity/ImprovedForever#Junk";
        manualModule.title = "Improved Forever: Junk";
        List<Model.Installed> r2 = new ArrayList<>();
        r2.add(core);
        r2.add(manualModule);
        check(Packages.fold(r2).size() == 1 && core.modules.contains(manualModule), "a manual install of a module joins its package");

        check("Camp".equals(Packages.moduleName("Improved Forever: Camp", "Improved Forever")), "short module name");
        check("Quest Tracker".equals(Packages.moduleName("improved forever - Quest Tracker", "Improved Forever")), "separator and case");
        check("Wheel".equals(Packages.moduleName("Wheel", "Improved Forever")), "other titles stay");
        check("Improved Forever".equals(Packages.moduleName("Improved Forever", "Improved Forever")), "same title stays");
    }
}
