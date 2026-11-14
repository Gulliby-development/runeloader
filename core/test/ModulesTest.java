package app.wowforever.addons;

import java.util.Arrays;
import java.util.List;

public class ModulesTest {
    static void check(boolean ok, String what) { if (!ok) throw new RuntimeException("FAIL: " + what); System.out.println("ok  " + what); }

    public static void main(String[] a) {
        check("Wheel".equals(Modules.of("Wheel-v0.10.0")) && "Core".equals(Modules.of("Core-v1")), "module tags");
        check(Modules.of("v1.2") == null && Modules.of("bundle/v12.4.0") == null && Modules.of(null) == null, "plain tags");
        check(Modules.of("release-1.2") == null && Modules.of("Build-7") == null && Modules.of("beta-v2") == null,
                "release channels are not modules");
        List<String> tags = Arrays.asList("Camp-v0.1.0", "Core-v0.13.0-beta1", "Wheel-v0.10.0", "Core-v0.12.0", "Wheel-v0.9.0", "v0.1");
        List<Boolean> pre = Arrays.asList(false, true, false, false, false, false);
        check("Core-v1.12.0".equals(Modules.coreTag(Arrays.asList("Core-v1.9.1", "XP-v1.8.0", "Core-v1.12.0", "Core-v1.11.0"),
                Arrays.asList(false, false, false, false), "gulli-toolkit")), "highest core version, whatever the list order");
        check("Attune-v1.6.10".equals(Modules.tagOf(Arrays.asList("Attune-v1.6.2", "Attune-v1.6.10", "Core-v1"),
                Arrays.asList(false, false, false), "gulli-toolkit", "Attune")), "highest module version");
        check("v0.10.0".equals(Modules.versionOf("Wheel-v0.10.0")) && "v1.2".equals(Modules.versionOf("v1.2")), "version part of a tag");
        check("Core-v0.12.0".equals(Modules.coreTag(tags, pre, "ImprovedForever")), "newest published core");
        check("Wheel-v0.10.0".equals(Modules.tagOf(tags, pre, "ImprovedForever", "wheel")), "newest release of a module");
        check(Modules.tagOf(tags, pre, "ImprovedForever", "Junk") == null, "module without a release");
        check("ImprovedForever-v1".equals(Modules.coreTag(Arrays.asList("Wheel-v2", "ImprovedForever-v1"),
                Arrays.asList(false, false), "ImprovedForever")), "core named after the repo");
        check("v3.0".equals(Modules.coreTag(Arrays.asList("unitframes-v1", "v3.0", "nameplates-v2"),
                Arrays.asList(false, false, false), "classicuiforever")), "plain tags are the core");
        check(Modules.coreTag(Arrays.asList("Questie-v12", "Questie-v11"), Arrays.asList(false, false), "Questie") == null,
                "one name is not a module repo");
        check(Modules.coreTag(Arrays.asList("release-2", "v1"), Arrays.asList(false, false), "GoatQuest") == null,
                "release- tags are not a module repo");
        check(Modules.coreTag(Arrays.asList("Camp-v1", "Wheel-v2"), Arrays.asList(false, false), "Pack") == null, "no core");
        check("Camp-v1".equals(Modules.tagOf(Arrays.asList("Camp-v1", "Wheel-v2"), Arrays.asList(false, false), "Pack", "Camp")),
                "module of a repo without a core");
        check("o/R".equals(Modules.repoOf("o/R#Wheel")) && "Wheel".equals(Modules.moduleOf("o/R#Wheel"))
                && "o/R".equals(Modules.repoOf("o/R")) && Modules.moduleOf("o/R") == null && Modules.moduleOf("o/R#") == null, "ids");
        check("o/R#Wheel".equals(Modules.id("o/R", "Wheel")) && "o/R".equals(Modules.id("o/R", null))
                && "o/R".equals(Modules.id("o/R", "../x")), "module ids");
        check(Modules.groups(Arrays.asList("Core-v2", "Legacy-v1.1.0", "Attune-v3", "Legacy-v1.0.0", "Beta-v9", "Wheel-v1"),
                Arrays.asList(false, false, false, false, false, true), "kit").equals(Arrays.asList("Legacy", "Attune")),
                "groups: modules once each, no core, channel or pre-release");
        check(Modules.groups(Arrays.asList("v1", "v2"), Arrays.asList(false, false), "x").isEmpty(), "no groups in a plain repo");

        // a module released after the index: found in the tags, named like the listed modules
        Model.Item core = new Model.Item(); core.author = "Gulliby-development";
        Model.Item att = new Model.Item(); att.id = "o/kit#Attune"; att.name = "Gulli-Attune";
        java.util.List<Model.Item> found = Updater.newModules("o/kit",
                Arrays.asList("Legacy-v1.0.0", "Core-v1.12.0", "Attune-v1.8.0", "Legacy-v1.1.0"),
                Arrays.asList(false, false, false, false), core, Arrays.asList(att));
        check(found.size() == 1 && "o/kit#Legacy".equals(found.get(0).id) && "Gulli-Legacy".equals(found.get(0).name)
                && "1.1.0".equals(found.get(0).version) && "github".equals(found.get(0).source)
                && "Gulliby-development".equals(found.get(0).author), "new module from the tags");
    }
}
