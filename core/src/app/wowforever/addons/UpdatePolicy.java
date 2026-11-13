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

    /**
     * The source offers a version other than the installed one. Sources do not order versions, so any
     * other tag counts, except a plain dotted number below the installed one: a stale index must never
     * offer a downgrade.
     */
    static boolean isNewer(String latest, String installed) {
        if (latest == null || latest.trim().isEmpty()) return false;
        if (sameVersion(latest, installed == null ? "" : installed)) return false;
        Integer c = compare(latest, installed);
        return c == null || c > 0;
    }

    /** True only when both are plain dotted numbers ("12.1.0") and a is below b. */
    static boolean older(String a, String b) {
        Integer c = compare(a, b);
        return c != null && c < 0;
    }

    /** Orders plain dotted numbers (a missing part counts as 0); null when either is something else. */
    private static Integer compare(String a, String b) {
        long[] x = numbers(a), y = numbers(b);
        if (x == null || y == null) return null;
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long m = i < x.length ? x[i] : 0, n = i < y.length ? y[i] : 0;
            if (m != n) return m < n ? -1 : 1;
        }
        return 0;
    }

    private static long[] numbers(String v) {
        if (v == null) return null;
        String s = normVersion(v);
        if (!s.matches("\\d{1,12}(\\.\\d{1,12})*")) return null;
        String[] p = s.split("\\.");
        long[] out = new long[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Long.parseLong(p[i]);
        return out;
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
