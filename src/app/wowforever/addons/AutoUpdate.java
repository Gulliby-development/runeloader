package app.wowforever.addons;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Schedules the background update job and posts its notifications. */
final class AutoUpdate {
    static final int JOB_ID = 1901;
    private static final String CHANNEL = "addon_updates";
    private static final int NOTE_RESULT = 1, NOTE_ERROR = 2;

    /** Runs (on the job thread) after the job changed installed addons; MainActivity sets it while it is visible. */
    static volatile Runnable onChanged;

    /** Applies the saved settings: schedules the job, replaces it after a settings change, or cancels it. */
    static void apply(Context ctx) {
        Store store = Store.get(ctx);
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (store.autoMode() == UpdatePolicy.MODE_OFF) { js.cancel(JOB_ID); return; }
        long period = store.autoIntervalHours() * 3600_000L;
        JobInfo.Builder b = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, UpdateJobService.class))
                .setPeriodic(period, Math.max(JobInfo.getMinFlexMillis(), period / 4))
                .setRequiredNetworkType(store.autoWifiOnly() ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(store.autoChargingOnly())
                .setPersisted(true);
        JobInfo want = b.build();
        JobInfo have = js.getPendingJob(JOB_ID);
        // Rescheduling an unchanged job would restart its period, so leave it alone.
        if (have != null && same(have, want)) return;
        js.schedule(want);
    }

    private static boolean same(JobInfo a, JobInfo b) {
        return a.getIntervalMillis() == b.getIntervalMillis()
                && a.getNetworkType() == b.getNetworkType()
                && a.isRequireCharging() == b.isRequireCharging()
                && a.isRequireBatteryNotLow() == b.isRequireBatteryNotLow()
                && a.isPersisted() == b.isPersisted();
    }

    static boolean canNotify(Context ctx) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    static void notifyResult(Context ctx, String title, String text) { post(ctx, NOTE_RESULT, title, text); }

    static void notifyError(Context ctx, String title, String text) { post(ctx, NOTE_ERROR, title, text); }

    private static void post(Context ctx, int id, String title, String text) {
        if (!canNotify(ctx)) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, ctx.getString(R.string.auto_channel), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(ctx.getString(R.string.auto_channel_text));
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_upgrade)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(id, n);
    }

    private AutoUpdate() {}
}
