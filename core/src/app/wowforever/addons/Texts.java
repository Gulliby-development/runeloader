package app.wowforever.addons;

import java.util.Locale;

/**
 * UI text for core code. Keys are the string names of res/values/strings.xml, which both apps use:
 * Android looks them up as resources (Lang), the desktop app reads the same files.
 */
final class Texts {

    interface Source {
        /** The text of the key in the UI language, formatted with args; the key itself when unknown. */
        String get(String key, Object... args);
        /** The UI language, for number and date formats. */
        Locale locale();
    }

    /** Set by the app at start; until then texts are empty, as on Android before the first activity. */
    static volatile Source source;

    static String s(String key, Object... args) {
        Source src = source;
        return src == null ? "" : src.get(key, args);
    }

    static Locale locale() {
        Source src = source;
        return src == null ? Locale.getDefault() : src.locale();
    }

    /** A size for people: 512 B, 12 KB, 3.4 MB. */
    static String bytes(long b) {
        if (b < 1024) return s("fmt_bytes", (int) b);
        if (b < 1024 * 1024) return s("fmt_kb", decimal(b / 1024.0, 0));
        return s("fmt_mb", decimal(b / (1024.0 * 1024), 1));
    }

    static String decimal(double v, int maxFraction) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(locale());
        f.setMaximumFractionDigits(maxFraction);
        f.setGroupingUsed(false);
        return f.format(v);
    }

    /** A short message for a failed network or install step. */
    static String friendly(Throwable e) {
        if (e instanceof java.net.UnknownHostException) return s("err_no_internet");
        if (e instanceof java.net.SocketTimeoutException) return s("err_timeout");
        if (e instanceof javax.net.ssl.SSLException) return s("err_ssl");
        if (e instanceof java.util.zip.ZipException) return s("err_not_zip");
        if (e instanceof Http.HttpException) {
            int c = ((Http.HttpException) e).code;
            if (c == 404) return s("err_http_404");
            boolean github = String.valueOf(e.getMessage()).contains("api.github.com");
            if (c == 401 && github) return s("github_token_invalid");
            if ((c == 403 || c == 429) && github) return Http.rateLimitText();
            if (c == 403 || c == 429) return s("err_rate_limited", c);
        }
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private Texts() {}
}
