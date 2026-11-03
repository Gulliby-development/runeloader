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
        return normVersion(a).equalsIgnoreCase(normVersion(b));
    }

    /**
     * The comparable part of a version: release tags may carry a path ("bundle/v1.2"), while
     * release.json and the TOC give the bare version ("v1.2"); a leading v is not significant either.
     */
    static String normVersion(String v) {
        String s = v.trim();
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash < s.length() - 1) s = s.substring(slash + 1);
        return s.replaceFirst("^[vV](?=\\d)", "").trim();
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
