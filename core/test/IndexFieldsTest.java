package app.wowforever.addons;
/** Run: core/test.sh */
public class IndexFieldsTest {
  static void check(boolean ok, String what) { if (!ok) throw new RuntimeException("FAIL: " + what); System.out.println("ok  " + what); }
  public static void main(String[] a) throws Exception {
    String base = "https://example.org/index/";
    Json.Obj o = new Json.Obj("{\"repo\":\"o/r\",\"icon\":\"icons/o__r.png\",\"description\":\"Long text\","
        + "\"screenshots\":[\"https://i/1.png\",\"http://insecure/2.png\",\"javascript:x\",\"https://i/3.png\"],"
        + "\"stars\":42,\"releases\":[{\"tag\":\"v2.0\",\"version\":\"2.0\",\"released\":\"2026-10-01T10:00:00Z\","
        + "\"prerelease\":true,\"url\":\"https://x/a.zip\",\"size\":10,\"changelog\":\"Fixed\"},{\"tag\":\"v1.0\"}]}");
    Model.Item it = new Model.Item(); it.id = "o/r"; it.iconUrl = "avatar";
    IndexFields.fill(o, base, it);
    check(it.iconUrl.equals(base + "icons/o__r.png"), "relative icon resolves against the index");
    check(it.screenshots.size() == 2, "only https screenshots kept");
    check(Categories.key("MAP").equals("map") && Categories.key("weird").isEmpty(), "category keys normalized");
    check(Categories.nameKey("map").equals("cat_map") && Categories.nameKey("weird").equals("cat_other"), "category keys map to the app categories");
    Model.Item pad = new Model.Item();
    IndexFields.fill(new Json.Obj("{\"repo\":\"o/p\",\"category\":\"bags\",\"tags\":[\"Gamepad\",\"gamepad\",\"\"]}"), base, pad);
    check(pad.tags.size() == 1 && Categories.matches(pad, Categories.GAMEPAD) && Categories.matches(pad, "bags")
        && !Categories.matches(it, Categories.GAMEPAD), "gamepad tag selects across categories");
    check(Categories.key("gamepad").isEmpty(), "gamepad is a tag, never a category value");
    check(Categories.order(true)[0].equals(Categories.GAMEPAD) && Categories.order(true).length == Categories.KEYS.length, "gamepad tile first with a controller");
    Model.Item mod = new Model.Item(); mod.source = "github"; mod.id = "o/IF#Wheel";
    IndexFields.fill(new Json.Obj("{\"repo\":\"o/IF\",\"module\":\"Wheel\",\"requires\":[\"o/IF\",\"x/Lib#Core2\",\"o/IF#Wheel\",\"bad\",\"a/b/c\",\"o/IF\"]}"), base, mod);
    check(mod.deps.size() == 2 && mod.deps.get(0).equals("github:o/IF") && mod.deps.get(1).equals("github:x/Lib#Core2"),
        "requires become dependency keys, without itself or malformed keys");
    check(IndexFields.requirement("o/r#../x") == null, "unsafe module names dropped");
    Model.Item ding = new Model.Item(); ding.source = "github"; ding.id = "m/Ding";
    IndexFields.fill(new Json.Obj("{\"repo\":\"m/Ding\",\"discontinued\":\"Listed\",\"discontinued_note\":\"Ported to Polestar.\",\"successor\":\"f/polestar\"}"), base, ding);
    check(ding.isDiscontinued() && ding.discontinued.equals("listed") && ding.discontinuedNote.equals("Ported to Polestar.")
        && "f/polestar".equals(ding.successor), "discontinued mark, note and successor");
    IndexFields.fill(new Json.Obj("{\"repo\":\"m/Ding\",\"successor\":\"m/Ding\"}"), base, ding);
    check(!ding.isDiscontinued() && ding.successor == null && ding.discontinuedNote.isEmpty(), "no mark: no successor, refilled clean");
    IndexFields.fill(new Json.Obj("{\"repo\":\"m/Ding\",\"discontinued\":\"archived\",\"successor\":\"m/Ding\"}"), base, ding);
    check(ding.isDiscontinued() && ding.successor == null, "an addon is never its own successor");
    check(it.stars == 42, "stars");
    check(it.releases.size() == 2 && it.releases.get(0).prerelease && it.releases.get(0).released > 0, "releases parsed");
    check(it.releases.get(1).version.equals("v1.0"), "version falls back to the tag");
    check(it.changelog.equals("Fixed"), "changelog falls back to the newest release notes");
    check(it.releases.get(0).changelogUrl.equals("https://github.com/o/r/releases/tag/v2.0"), "release page link");
    check(IndexFields.resolve(base, "../secret") == null && IndexFields.resolve(base, "/abs") == null
        && IndexFields.resolve(base, "file:/x") == null, "unsafe icon paths dropped");
    Model.Item bare = new Model.Item(); bare.iconUrl = "avatar"; bare.category = "";
    IndexFields.fill(new Json.Obj("{\"repo\":\"x/y\",\"category\":\"weird\"}"), base, bare);
    check(bare.iconUrl.equals("avatar") && bare.category.isEmpty() && bare.description.isEmpty() && bare.stars == -1, "missing keys leave defaults");
    StringBuilder big = new StringBuilder(); for (int i = 0; i < 1000; i++) big.append("word ");
    Model.Item capped = new Model.Item();
    IndexFields.fill(new Json.Obj().put("description", big.toString()), base, capped);
    check(capped.description.length() <= IndexFields.MAX_TEXT + 1, "description capped");
  }
}
