package app.wowforever.addons;
import org.json.JSONObject;
/** Run with an android.jar that has a real org.json (e.g. Robolectric android-all) on the classpath. */
public class IndexFieldsTest {
  static void check(boolean ok, String what) { if (!ok) throw new RuntimeException("FAIL: " + what); System.out.println("ok  " + what); }
  public static void main(String[] a) throws Exception {
    String base = "https://example.org/index/";
    JSONObject o = new JSONObject("{\"repo\":\"o/r\",\"icon\":\"icons/o__r.png\",\"description\":\"Long text\","
        + "\"screenshots\":[\"https://i/1.png\",\"http://insecure/2.png\",\"javascript:x\",\"https://i/3.png\"],"
        + "\"stars\":42,\"releases\":[{\"tag\":\"v2.0\",\"version\":\"2.0\",\"released\":\"2026-10-01T10:00:00Z\","
        + "\"prerelease\":true,\"url\":\"https://x/a.zip\",\"size\":10,\"changelog\":\"Fixed\"},{\"tag\":\"v1.0\"}]}");
    Model.Item it = new Model.Item(); it.id = "o/r"; it.iconUrl = "avatar";
    IndexFields.fill(o, base, it);
    check(it.iconUrl.equals(base + "icons/o__r.png"), "relative icon resolves against the index");
    check(it.screenshots.size() == 2, "only https screenshots kept");
    check(IndexFields.categoryRes("MAP") == R.string.cat_map && IndexFields.categoryRes("weird") == 0, "category keys map to the app categories");
    check(it.stars == 42, "stars");
    check(it.releases.size() == 2 && it.releases.get(0).prerelease && it.releases.get(0).released > 0, "releases parsed");
    check(it.releases.get(1).version.equals("v1.0"), "version falls back to the tag");
    check(it.changelog.equals("Fixed"), "changelog falls back to the newest release notes");
    check(it.releases.get(0).changelogUrl.equals("https://github.com/o/r/releases/tag/v2.0"), "release page link");
    check(IndexFields.resolve(base, "../secret") == null && IndexFields.resolve(base, "/abs") == null
        && IndexFields.resolve(base, "file:/x") == null, "unsafe icon paths dropped");
    Model.Item bare = new Model.Item(); bare.iconUrl = "avatar"; bare.category = "";
    IndexFields.fill(new JSONObject("{\"repo\":\"x/y\",\"category\":\"weird\"}"), base, bare);
    check(bare.iconUrl.equals("avatar") && bare.category.isEmpty() && bare.description.isEmpty() && bare.stars == -1, "missing keys leave defaults");
    StringBuilder big = new StringBuilder(); for (int i = 0; i < 1000; i++) big.append("word ");
    Model.Item capped = new Model.Item();
    IndexFields.fill(new JSONObject().put("description", big.toString()), base, capped);
    check(capped.description.length() <= IndexFields.MAX_TEXT + 1, "description capped");
  }
}
