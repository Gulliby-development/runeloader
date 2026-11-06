package app.wowforever.addons;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Updates Runeloader itself from the latest GitHub release: check, offer, download, verify, install.
 * The APK and checksum names come from release.yml (see AppVersion.apkName).
 */
final class SelfUpdate {
    static final String REPO = "Gulliby-development/runeloader";
    private static final long CHECK_EVERY_MS = 12L * 3600 * 1000;
    private static final String ACTION_STATUS = "app.wowforever.addons.SELF_UPDATE_STATUS";

    /** A release newer than the installed app. */
    static final class Release {
        String version, apkUrl, shaUrl, notes, page;
        long size;
    }

    private final MainActivity app;
    /** The newest release found in this process; null when up to date or not checked. */
    Release available;
    boolean checking, working;
    /** Set when the user went to allow installs from this app; the install goes on in onResume. */
    private Release afterPermission;
    private BroadcastReceiver receiver;

    SelfUpdate(MainActivity app) { this.app = app; }

    static String installedVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    // ── check ──

    /** On start: checks at most every 12 hours and offers the update once per found version. */
    void checkOnStart() {
        long now = System.currentTimeMillis();
        if (now - app.store.selfCheckAt() < CHECK_EVERY_MS) return;
        check(false);
    }

    /** From Settings: always checks and reports "up to date" too. */
    void check(final boolean manual) {
        if (checking) return;
        checking = true;
        if (manual) app.renderIfTab(MainActivity.TAB_SETTINGS);
        MainActivity.net.execute(() -> {
            Release r = null;
            Exception err = null;
            try { r = latest(installedVersion(app)); } catch (Exception e) { err = e; }
            final Release found = r;
            final Exception error = err;
            app.main.post(() -> {
                checking = false;
                if (app.isDestroyed()) return;
                if (error == null) app.store.setSelfCheckAt(System.currentTimeMillis());
                available = found;
                app.renderIfTab(MainActivity.TAB_SETTINGS);
                if (error != null) {
                    if (manual) app.snack(app.getString(R.string.self_check_failed, MainActivity.friendly(error)));
                } else if (found == null) {
                    if (manual) app.snack(app.getString(R.string.self_up_to_date, installedVersion(app)));
                } else if (manual || !found.version.equals(app.store.selfOffered())) {
                    app.store.setSelfOffered(found.version);
                    offer(found);
                }
            });
        });
    }

    /** The latest release when it is newer than {@code installed} and has the APK, else null. */
    static Release latest(String installed) throws Exception {
        JSONObject rel = new JSONObject(Http.get("https://api.github.com/repos/" + REPO + "/releases/latest",
                Http.headers("Accept", "application/vnd.github+json")));
        String version = AppVersion.clean(rel.optString("tag_name", ""));
        if (version == null || !AppVersion.newer(version, installed)) return null;
        String apk = AppVersion.apkName(version);
        Release r = new Release();
        r.version = version;
        r.page = rel.optString("html_url", "https://github.com/" + REPO + "/releases/latest");
        String notes = rel.optString("body", "").trim();
        r.notes = notes.length() > 4000 ? notes.substring(0, 4000) + "…" : notes;
        JSONArray assets = rel.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name", "");
            if (name.equals(apk)) { r.apkUrl = a.optString("browser_download_url", null); r.size = a.optLong("size", 0); }
            else if (name.equals(apk + ".sha256")) r.shaUrl = a.optString("browser_download_url", null);
        }
        if (!fromThisRepo(r.shaUrl)) r.shaUrl = null;
        return fromThisRepo(r.apkUrl) ? r : null;
    }

    // ── offer ──

    void offer(final Release r) {
        String text = Markdown.render(r.notes).text.toString().trim();
        String msg = app.getString(R.string.self_offer_text, installedVersion(app), r.version)
                + (r.size > 0 ? "  ·  " + Ui.formatBytes(r.size) : "")
                + (text.isEmpty() ? "" : "\n\n" + text);
        LinearLayout btns = Ui.hbox(app);
        btns.setGravity(Gravity.END);
        btns.addView(Ui.button(app, app.getString(R.string.self_later), 0, Ui.Kind.GHOST, v -> app.closeModal()), Ui.lp(-2, Ui.dp(42)));
        final LinearLayout ok = Ui.button(app, app.getString(R.string.self_update_now), R.drawable.ic_upgrade, Ui.Kind.PRIMARY, v -> {
            app.closeModal();
            start(r);
        });
        btns.addView(ok, Ui.margins(Ui.lp(-2, Ui.dp(42)), 8, 0, 0, 0));
        app.modal(app.getString(R.string.self_offer_title, r.version), msg, null, btns);
        ok.post(ok::requestFocus);
    }

    // ── download and install ──

    void start(final Release r) {
        if (working) return;
        if (!app.getPackageManager().canRequestPackageInstalls()) {
            app.confirm(app.getString(R.string.self_allow_title), app.getString(R.string.self_allow_text), app.getString(R.string.self_allow_action), () -> {
                afterPermission = r;
                try {
                    app.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.getPackageName())));
                } catch (Exception e) {
                    afterPermission = null;
                    app.snack(MainActivity.friendly(e));
                }
            });
            return;
        }
        working = true;
        final TextView status = Ui.text(app, app.getString(R.string.self_downloading), 13, Ui.TEXT2, Ui.REGULAR);
        final ProgressBar bar = new ProgressBar(app, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.SURFACE3));
        bar.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        bar.setMax(1000);
        bar.setIndeterminate(true);
        LinearLayout box = Ui.vbox(app);
        box.addView(status, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
        box.addView(bar, Ui.margins(Ui.lp(-1, Ui.dp(6)), 0, 0, 0, 8));
        app.modal(app.getString(R.string.self_offer_title, r.version), null, box, null);
        MainActivity.net.execute(() -> {
            try {
                File apk = download(r, (done, total) -> app.main.post(() -> {
                    if (total <= 0) return;
                    bar.setIndeterminate(false);
                    bar.setProgress((int) (done * 1000 / total));
                    status.setText(app.getString(R.string.self_downloading_bytes, Ui.formatBytes(done), Ui.formatBytes(total)));
                }));
                app.main.post(() -> status.setText(app.getString(R.string.self_installing)));
                install(apk);
            } catch (final Exception e) {
                app.main.post(() -> fail(MainActivity.friendly(e)));
            }
        });
    }

    /** Goes on with the install the user allowed in the system settings. */
    void onResume() {
        Release r = afterPermission;
        if (r == null) return;
        afterPermission = null;
        if (app.getPackageManager().canRequestPackageInstalls()) start(r);
        else app.snack(app.getString(R.string.self_not_allowed));
    }

    private void fail(String why) {
        working = false;
        if (app.isDestroyed()) return;
        app.closeModal();
        app.confirm(app.getString(R.string.self_failed_title), why, null, null);
    }

    private File download(Release r, Http.Progress p) throws Exception {
        File dir = new File(app.getCacheDir(), "self");
        Installer.deleteRecursive(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new java.io.IOException("cache");
        File apk = new File(dir, "runeloader.apk");
        // Release files redirect to signed storage URLs, so no token goes with them.
        Http.download(r.apkUrl, null, apk, p);
        if (r.shaUrl != null) {
            String want = Http.get(r.shaUrl).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (!want.equals(sha256(apk))) throw new java.io.IOException(app.getString(R.string.self_bad_checksum));
        }
        PackageInfo pi = app.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        PackageInfo me = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
        if (pi == null || !app.getPackageName().equals(pi.packageName)) throw new java.io.IOException(app.getString(R.string.self_bad_apk));
        if (pi.getLongVersionCode() <= me.getLongVersionCode()) throw new java.io.IOException(app.getString(R.string.self_not_newer, r.version));
        return apk;
    }

    private void install(File apk) throws Exception {
        PackageInstaller pkg = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        params.setSize(apk.length());
        int id = pkg.createSession(params);
        PackageInstaller.Session s = pkg.openSession(id);
        try {
            OutputStream out = s.openWrite("runeloader.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            } finally {
                in.close();
                out.close();
            }
            listen();
            Intent status = new Intent(ACTION_STATUS).setPackage(app.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(app, id, status, flags).getIntentSender());
        } catch (Exception e) {
            s.abandon();
            throw e;
        } finally {
            s.close();
        }
    }

    /** Receives the installer's answer: show the system prompt, or the error. On success Android restarts the app. */
    private synchronized void listen() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm == null) { fail(app.getString(R.string.self_bad_apk)); return; }
                    // The system dialog owns the install now; a retry stays possible if it is dismissed.
                    working = false;
                    app.closeModal();
                    try { app.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                    catch (Exception e) { fail(MainActivity.friendly(e)); }
                } else if (st == PackageInstaller.STATUS_SUCCESS) {
                    working = false;
                    app.closeModal();
                } else if (st == PackageInstaller.STATUS_FAILURE_ABORTED) {
                    working = false;
                    app.closeModal();
                    app.snack(app.getString(R.string.self_cancelled));
                } else {
                    String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    if (st == PackageInstaller.STATUS_FAILURE_CONFLICT || st == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) m = app.getString(R.string.self_conflict);
                    fail(m == null || m.isEmpty() ? app.getString(R.string.self_failed_title) : m);
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(receiver, f);
    }

    void onDestroy() {
        if (receiver == null) return;
        try { app.unregisterReceiver(receiver); } catch (Exception ignored) {}
        receiver = null;
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    /** Only files from this repository's releases are downloaded. */
    static boolean fromThisRepo(String url) {
        return url != null && url.startsWith("https://github.com/" + REPO + "/releases/download/");
    }
}
