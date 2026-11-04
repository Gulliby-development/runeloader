package app.wowforever.addons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Default Browse order. Run: java -cp /tmp/p app.wowforever.addons.PopularityTest */
public class PopularityTest {
    public static void main(String[] a) {
        Model.Item questie = item("questie", 6963, 1119);
        Model.Item bigwigs = item("bigwigs", 408, 275);
        Model.Item fresh = item("fresh", 3696, 61);
        Model.Item unknown = item("unknown", -1, -1);
        Model.Item unknown2 = item("unknown2", -1, -1);
        check(Popularity.score(unknown) == 0, "missing numbers count as zero");
        check(Popularity.score(bigwigs) == 408 + 2750, "a star counts as ten downloads");

        List<Model.Item> l = new ArrayList<>();
        l.add(unknown); l.add(bigwigs); l.add(unknown2); l.add(fresh); l.add(questie);
        Collections.sort(l, Popularity.ORDER);
        check(l.get(0) == questie && l.get(1) == fresh && l.get(2) == bigwigs, "most popular first");
        check(l.get(3) == unknown && l.get(4) == unknown2, "equal scores keep their order");
        Model.Item ended = item("ended", 9999, 9999);
        ended.discontinued = "listed";
        l.add(0, ended);
        Collections.sort(l, Popularity.ORDER);
        check(l.get(l.size() - 1) == ended && l.get(0) == questie, "a discontinued addon goes last");
        System.out.println("PopularityTest: ok");
    }

    private static Model.Item item(String name, long downloads, long stars) {
        Model.Item it = new Model.Item();
        it.name = name;
        it.downloads = downloads;
        it.stars = stars;
        return it;
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
