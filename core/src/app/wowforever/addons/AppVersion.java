package app.wowforever.addons;

/** Runeloader's own version rules for the self-update. Plain Java, so test/ can run it. */
final class AppVersion {

    /** "v0.1.2" or "0.1.2-beta" to "0.1.2"; null when the text has no leading number. */
    static String clean(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int end = 0;
        while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.')) end++;
        s = s.substring(0, end);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.isEmpty() || !Character.isDigit(s.charAt(0)) ? null : s;
    }

    /** Compares dotted numbers part by part; a missing part counts as 0. Unreadable versions are equal. */
    static int compare(String a, String b) {
        String x = clean(a), y = clean(b);
        if (x == null || y == null) return 0;
        String[] p = x.split("\\."), q = y.split("\\.");
        for (int i = 0; i < Math.max(p.length, q.length); i++) {
            long m = i < p.length ? num(p[i]) : 0, n = i < q.length ? num(q[i]) : 0;
            if (m != n) return m < n ? -1 : 1;
        }
        return 0;
    }

    static boolean newer(String candidate, String installed) { return compare(candidate, installed) > 0; }

    /** The APK name that release.yml publishes for a version. */
    static String apkName(String version) { return "runeloader-" + clean(version) + ".apk"; }

    private static long num(String s) {
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s.length() > 12 ? s.substring(0, 12) : s); } catch (NumberFormatException e) { return 0; }
    }

    private AppVersion() {}
}
