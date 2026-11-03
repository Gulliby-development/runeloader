package app.wowforever.addons;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** The periodic background check: finds addon updates and, in install mode, installs them. */
public class UpdateJobService extends JobService {
    private volatile boolean stopped;
    private Thread worker;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override public boolean onStartJob(final JobParameters params) {
        stopped = false;
        worker = new Thread(() -> {
            try {
                run();
            } catch (Throwable t) {
                Store.get(this).setAutoLastRun(System.currentTimeMillis(), 0, 0, String.valueOf(t.getMessage()));
            }
            jobFinished(params, false);
        }, "auto-update");
        worker.start();
        return true;
    }

    /** The system wants the job gone (constraints lost or time is up): stop after the addon in progress. */
    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }

    private void run() {
        Store store = Store.get(this);
        if (store.autoMode() == UpdatePolicy.MODE_OFF) { AutoUpdate.apply(this); return; }   // a stale job: cancel it
        if (!store.isLoaded()) store.load();
        long now = System.currentTimeMillis();
        if (!Environment.isExternalStorageManager() || !store.gameReady()) {
            String err = getString(Environment.isExternalStorageManager() ? R.string.auto_err_game : R.string.auto_err_access);
            boolean fresh = !err.equals(store.autoLastError());
            store.setAutoLastRun(now, 0, 0, err);
            if (fresh) AutoUpdate.notifyError(this, getString(R.string.auto_err_title), err);
            return;
        }
        List<Model.Entry> found = new ArrayList<>();
        java.util.Map<String, Model.Item> index = Updater.indexByKey(false);
        for (Model.Entry e : UpdatePolicy.candidates(store.list())) {
            if (stopped) break;
            try {
                Model.Item ix = Updater.indexHit(index, e);
                String latest = ix != null ? ix.version : Updater.latestVersion(e);
                if (ix != null && !ix.changelog.isEmpty()) e.changelog = ix.changelog;
                e.latest = latest;
                e.checkError = null;
                e.updateAvailable = UpdatePolicy.isNewer(latest, e.version);
                if (e.updateAvailable) found.add(e);
            } catch (Exception ex) {
                e.checkError = MainActivity.friendly(ex);
            }
        }
        store.setLastCheck(now);
        if (found.isEmpty()) { store.setAutoLastRun(now, 0, 0, null); return; }

        if (store.autoMode() == UpdatePolicy.MODE_NOTIFY) {
            store.setAutoLastRun(now, found.size(), 0, null);
            AutoUpdate.notifyResult(this, getResources().getQuantityString(R.plurals.auto_found, found.size(), found.size()), names(found));
            changed();
            return;
        }

        File addons = store.addonsDir();
        Updater updater = new Updater(this, store);
        List<String> done = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Model.Entry e : found) {
            if (stopped) break;
            try {
                updater.installWithDeps(Sources.itemFor(e), addons, done, new HashSet<String>(), 0, null);
            } catch (Exception ex) {
                failed.add(e.name + ": " + MainActivity.friendly(ex));
            }
        }
        store.setAutoLastRun(now, found.size(), done.size(), failed.isEmpty() ? null : failed.get(0));
        if (!done.isEmpty()) {
            String text = String.join(", ", done) + "\n" + getString(R.string.auto_reload_hint);
            if (!failed.isEmpty()) text += "\n" + String.join("\n", failed);
            AutoUpdate.notifyResult(this, getResources().getQuantityString(R.plurals.auto_updated, done.size(), done.size()), text);
        } else if (!failed.isEmpty()) {
            AutoUpdate.notifyError(this, getString(R.string.auto_err_update), String.join("\n", failed));
        }
        changed();
    }

    private static String names(List<Model.Entry> l) {
        List<String> n = new ArrayList<>();
        for (Model.Entry e : l) n.add(e.name);
        return String.join(", ", n);
    }

    private static void changed() {
        Runnable r = AutoUpdate.onChanged;
        if (r != null) r.run();
    }
}
