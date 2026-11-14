package app.wowforever.addons;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a real index.json with the app's own parser and checks what the app relies on: every
 * entry becomes an item with a version, every module has its core, and the requirements resolve.
 * Not a unit test (core/test.sh skips it); the Index contract workflow runs it every night.
 *   java -cp <core classes> app.wowforever.addons.IndexCheck index.json
 * Exits 1 on a problem that breaks the app, prints warnings for the rest.
 */
public class IndexCheck {
  public static void main(String[] args) throws Exception {
    String body = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
    Json.Obj root = new Json.Obj(body);
    Json.Arr raw = root.optJSONArray("addons");
    List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
    if (root.optInt("schema", 0) != Index.SCHEMA) errors.add("schema " + root.optInt("schema", 0) + ", the app reads " + Index.SCHEMA);
    if (raw == null || raw.length() < 100) errors.add("only " + (raw == null ? 0 : raw.length()) + " entries");
    Index.parse(body);
    List<Model.Item> items = new ArrayList<>();
    if (raw != null) for (int i = 0; i < raw.length(); i++) {
      Json.Obj a = raw.optJSONObject(i);
      Model.Item it = Index.toItem(a);
      if (it == null) errors.add("entry " + i + " (" + (a == null ? null : a.optString("repo", "")) + ") is dropped by the app");
      else items.add(it);
    }
    for (Model.Item it : items) {
      if (it.version == null || it.version.isEmpty()) errors.add(it.id + ": no version");
      if (Modules.moduleOf(it.id) != null && Index.item(Modules.repoOf(it.id)) == null)
        warnings.add(it.id + ": no core entry " + Modules.repoOf(it.id) + " (Browse shows the module alone)");
      for (String d : it.deps) {
        if (Catalog.byKey(d) == null && Index.item(d.substring(d.indexOf(':') + 1)) == null)
          warnings.add(it.id + " requires " + d + ", which is in neither the catalog nor the index (installed straight from GitHub)");
      }
    }
    for (String w : warnings) System.out.println("warning: " + w);
    for (String e : errors) System.out.println("error: " + e);
    System.out.println("index " + root.optString("generated", "?") + ": " + items.size() + " items, "
        + errors.size() + " errors, " + warnings.size() + " warnings");
    if (!errors.isEmpty()) System.exit(1);
  }
}
