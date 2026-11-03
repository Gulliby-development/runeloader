package app.wowforever.addons;

import java.util.Comparator;

/**
 * The default Browse order. Downloads count only the latest Forever release, so a fresh release
 * starts near zero; stars keep a well-known addon up while its new release catches up.
 */
final class Popularity {

    /** One star counts as this many downloads of the latest release. */
    static final long STAR_WEIGHT = 10;

    static long score(Model.Item it) {
        return Math.max(0, it.downloads) + STAR_WEIGHT * Math.max(0, it.stars);
    }

    /** Most popular first; equal scores keep their order (the sort is stable). */
    static final Comparator<Model.Item> ORDER = (a, b) -> Long.compare(score(b), score(a));

    private Popularity() {}
}
