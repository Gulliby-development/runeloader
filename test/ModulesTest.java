package app.wowforever.addons;

import java.util.Arrays;

public class ModulesTest {
    static void check(boolean ok, String what) { if (!ok) throw new RuntimeException("FAIL: " + what); System.out.println("ok  " + what); }

    public static void main(String[] a) {
        check("Wheel".equals(Modules.of("Wheel-v0.10.0")) && "Core".equals(Modules.of("Core-v1")), "module tags");
        check(Modules.of("v1.2") == null && Modules.of("bundle/v12.4.0") == null && Modules.of(null) == null, "plain tags");
        check("Core-v0.12.0".equals(Modules.coreTag(
                Arrays.asList("Camp-v0.1.0", "Core-v0.13.0-beta1", "Wheel-v0.10.0", "Core-v0.12.0", "v0.1"),
                Arrays.asList(false, true, false, false, false), "ImprovedForever")), "newest published core");
        check("ImprovedForever-v1".equals(Modules.coreTag(Arrays.asList("Wheel-v2", "ImprovedForever-v1"),
                Arrays.asList(false, false), "ImprovedForever")), "core named after the repo");
        check(Modules.coreTag(Arrays.asList("Questie-v12", "Questie-v11"), Arrays.asList(false, false), "Questie") == null,
                "one name is not a module repo");
        check(Modules.coreTag(Arrays.asList("Camp-v1", "Wheel-v2"), Arrays.asList(false, false), "Pack") == null, "no core");
    }
}
