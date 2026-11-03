package app.wowforever.addons;
import java.io.File;
import java.util.*;
public class NetTest {
  public static void main(String[] a) throws Exception {
    File root = new File(a[0]); Installer.deleteRecursive(root);
    File addons = new File(root, "_forever_/Interface/AddOns"); addons.mkdirs();
    Sources.cacheDir = new File(root, "cache"); Sources.cacheDir.mkdirs();
    for (Model.Item it : Catalog.all()) {
      try {
        Model.Release r = Sources.latest(it);
        File z = new File(Sources.cacheDir, "x.zip");
        Http.download(r.url, r.headers, z, null);
        List<String> f = Installer.install(z, addons, null);
        Set<String> want = new HashSet<>(); for (String w : it.folders) want.add(w);
        Set<String> got = new HashSet<>(f);
        String miss = ""; for (String w : it.folders) if (!got.contains(w)) miss += w + " ";
        System.out.println("OK  " + it.id + " v=" + r.version + " file=" + r.fileName + " folders=" + f.size() + (miss.isEmpty() ? "" : " MISSING_IN_ZIP: " + miss));
      } catch (Exception e) { System.out.println("ERR " + it.id + " " + e); }
    }
    System.out.println("normalize: " + Sources.GitHub.normalize("https://github.com/Questie/Questie/releases") + " " + Sources.GitHub.normalize("Nevcairiel/Bartender4.git") + " " + Sources.GitHub.normalize("foo"));
  }
}
