package app.wowforever.addons;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small HTTP helper: manual redirects so secrets never leak to a foreign host. */
final class Http {
    static final String UA = "wow-addons-android/1.0 (WoW Forever addon manager)";

    /** Hosts that may receive the CurseForge key. */
    private static final List<String> CURSE_HOSTS = Arrays.asList(
            "api.curseforge.com", "edge.forgecdn.net", "mediafilez.forgecdn.net");

    interface Progress { void on(long done, long total); }

    static final class HttpException extends IOException {
        final int code;
        HttpException(int code, String url, String body) {
            super("HTTP " + code + " — " + shortUrl(url) + (body == null || body.isEmpty() ? "" : ": " + trim(body)));
            this.code = code;
        }
    }

    private static String shortUrl(String url) {
        try { URL u = new URL(url); return u.getHost() + u.getPath(); } catch (Exception e) { return url; }
    }

    private static String trim(String s) {
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private static boolean secretAllowed(String header, String host) {
        String h = header.toLowerCase();
        if (h.equals("x-api-key")) return CURSE_HOSTS.contains(host);
        if (h.equals("authorization")) return host.equals("api.github.com") || host.equals("github.com");
        return true;
    }

    /** Opens a connection following up to 8 redirects; caller must disconnect. */
    static HttpURLConnection connect(String url, Map<String, String> headers, String method, byte[] body) throws IOException {
        String current = url;
        for (int hop = 0; hop < 8; hop++) {
            URL u = new URL(current);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(45000);
            c.setRequestProperty("User-Agent", UA);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (secretAllowed(e.getKey(), u.getHost())) c.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            if (body != null && hop == 0) {
                c.setRequestMethod(method);
                c.setDoOutput(true);
                OutputStream os = c.getOutputStream();
                os.write(body);
                os.close();
            } else if (method != null && hop == 0) {
                c.setRequestMethod(method);
            }
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && code != 304) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect without Location: " + shortUrl(current));
                current = new URL(u, loc).toString();
                continue;
            }
            return c;
        }
        throw new IOException("Too many redirects: " + shortUrl(url));
    }

    static String get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = connect(url, headers, "GET", null);
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new HttpException(code, url, readAll(c.getErrorStream()));
            return readAll(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String get(String url) throws IOException { return get(url, null); }

    /** Returns the Location header of the first hop (no follow), or null. */
    static String redirectTarget(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", UA);
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        try {
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) return c.getHeaderField("Location");
            if (code >= 400) throw new HttpException(code, url, null);
            return null;
        } finally {
            c.disconnect();
        }
    }

    static void download(String url, Map<String, String> headers, File dest, Progress p) throws IOException {
        HttpURLConnection c = connect(url, headers, "GET", null);
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new HttpException(code, url, readAll(c.getErrorStream()));
            long total = c.getContentLengthLong();
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                long lastTick = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (p != null && (done - lastTick > 96 * 1024 || done == total)) {
                        lastTick = done;
                        p.on(done, total);
                    }
                }
                if (p != null) p.on(done, total < 0 ? done : total);
            } finally {
                out.close();
                in.close();
            }
        } finally {
            c.disconnect();
        }
    }

    static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        try {
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        } finally {
            in.close();
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    static Map<String, String> headers(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null && !kv[i + 1].isEmpty()) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    private Http() {}
}
