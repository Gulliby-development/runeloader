package app.wowforever.addons;
import java.io.File;
import java.util.*;
public class InstallTest {
  public static void main(String[] a) throws Exception {
    File root = new File(a[1]); Installer.deleteRecursive(root);
    File addons = new File(root, "_forever_/Interface/AddOns"); addons.mkdirs();
    for (File z : new File(a[0]).listFiles()) {
      if (!z.getName().endsWith(".zip")) continue;
      List<String> f = Installer.install(z, addons, null);
      List<String> again = Installer.install(z, addons, null); // reinstall over existing
      if (!f.equals(again)) throw new RuntimeException("reinstall differs " + z);
      StringBuilder sb = new StringBuilder();
      for (String x : f) { Model.LocalAddon la = Toc.read(new File(addons, x)); sb.append(x).append(la==null?"(no toc)":"["+la.tocFile+" v"+la.version+" fv="+la.foreverToc+"]").append(' '); }
      System.out.println(z.getName()+" -> "+(sb.length()>300?sb.substring(0,300)+"…":sb));
    }
    File[] left = addons.getParentFile().listFiles();
    for (File l : left) if (l.getName().startsWith(".")) throw new RuntimeException("staging left: "+l);
    System.out.println("scan: "+Toc.scan(addons).size()+" folders");
  }
}
