package app.wowforever.addons;

import java.util.ArrayList;
import java.util.List;

/** Plain-Java rules of the background auto-update, kept free of Android so test/ can run them on a JVM. */
final class UpdatePolicy {
    static final int MODE_OFF = 0, MODE_NOTIFY = 1, MODE_INSTALL = 2;
    static final int[] INTERVALS_H = {6, 12, 24};
    static final int DEFAULT_INTERVAL_H = 6;

    /** Entries the background job may check: tracked through a source and not pinned by the user. */
    static List<Model.Entry> candidates(List<Model.Entry> all) {
        List<Model.Entry> out = new ArrayList<>();
        for (Model.Entry e : all) {
            if (e == null || e.pinned) continue;
            if (Model.SRC_ZIP.equals(e.source)) continue;
            out.add(e);
        }
        return out;
    }

    static boolean sameVersion(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.trim().replaceFirst("^[vV]", ""), y = b.trim().replaceFirst("^[vV]", "");
        return x.equalsIgnoreCase(y);
    }

    /** The source offers a version other than the installed one (sources do not order versions). */
    static boolean isNewer(String latest, String installed) {
        if (latest == null || latest.trim().isEmpty()) return false;
        return !sameVersion(latest, installed == null ? "" : installed);
    }

    static int mode(int stored) {
        return stored == MODE_NOTIFY || stored == MODE_INSTALL ? stored : MODE_OFF;
    }

    /** One of INTERVALS_H; anything else falls back to the default. */
    static int intervalHours(int stored) {
        for (int h : INTERVALS_H) if (h == stored) return h;
        return DEFAULT_INTERVAL_H;
    }

    private UpdatePolicy() {}
}
