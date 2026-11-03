package app.wowforever.addons;

import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** The settings tab. */
final class SettingsScreen extends Screen {

    SettingsScreen(MainActivity app) { super(app); }

    void renderSettings() {
        header(s(R.string.settings_title), s(R.string.settings_version, s(R.string.app_name), versionName()));
        ScrollView sv = new ScrollView(app);
        LinearLayout col = Ui.vbox(app);
        col.setPadding(Ui.dp(app.landscape ? 20 : 16), Ui.dp(4), Ui.dp(app.landscape ? 20 : 16), Ui.dp(96));
        sv.addView(col);

        // Two columns where the list column is wide; the left one holds the taller cards.
        LinearLayout left = col, right = col;
        if (app.layout.listDp >= TWO_COLUMNS_DP) {
            LinearLayout cols = Ui.hbox(app);
            cols.setGravity(Gravity.TOP);
            left = Ui.vbox(app);
            right = Ui.vbox(app);
            cols.addView(left, Ui.lpw(0, -2, 1f));
            cols.addView(right, Ui.margins(Ui.lpw(0, -2, 1f), 12, 0, 0, 0));
            col.addView(cols, Ui.lp(-1, -2));
        }

        gameCard(left);
        curseCard(right);
        languageCard(left);
        githubCard(right);
        aboutCard(left);

        app.bodyBox.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    /** At or above this list width the cards sit in two columns. */
    private static final int TWO_COLUMNS_DP = 640;

    private void gameCard(LinearLayout parent) {
        LinearLayout game = card(parent, R.drawable.ic_folder, s(R.string.settings_game_folder));
        String root = app.store.gameRoot();
        boolean ok = app.store.gameReady();
        TextView path = Ui.text(app, root == null ? s(R.string.settings_not_selected) : root, 14, ok ? Ui.TEXT : Ui.ORANGE, Ui.MEDIUM);
        game.addView(path);
        String status;
        if (root == null) status = s(R.string.settings_pick_game);
        else if (!ok) status = s(R.string.settings_game_missing);
        else {
            File ad = app.store.addonsDir();
            status = s(ad != null && ad.isDirectory() ? R.string.settings_addons_to : R.string.settings_addons_to_new, Store.FLAVOR_DIR + "/Interface/AddOns");
        }
        game.addView(Ui.text(app, status, 12.5f, ok ? Ui.GREEN : Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        game.addView(actions(
                Ui.button(app, s(R.string.game_pick_folder), R.drawable.ic_folder, ok ? Ui.Kind.SECONDARY : Ui.Kind.PRIMARY, v -> app.gameFolder.openFolderPicker()),
                Ui.button(app, s(R.string.action_auto_detect), R.drawable.ic_search, Ui.Kind.GHOST, v -> app.gameFolder.autoDetect(true))),
                Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));
    }

    private void languageCard(LinearLayout parent) {
        LinearLayout lang = card(parent, R.drawable.ic_language, s(R.string.settings_language));
        lang.addView(Ui.text(app, s(R.string.settings_language_text), 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        Ui.FlowRow chips = new Ui.FlowRow(app, 8, 8);
        String current = Lang.saved(app);
        for (final String tag : Lang.TAGS) {
            boolean on = tag.equals(current);
            TextView chip = Ui.text(app, Lang.name(app, tag), 13, on ? Ui.ACCENT_DARK : Ui.TEXT2, Ui.MEDIUM);
            chip.setSingleLine(true);
            chip.setBackground(Ui.selectable(on ? Ui.ACCENT : Ui.SURFACE2, 16, on ? 0 : Ui.STROKE));
            chip.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                if (tag.equals(Lang.saved(app))) return;
                Lang.save(app, tag);
                app.recreate();
            });
            chips.addView(chip);
        }
        lang.addView(chips, Ui.lp(-1, -2));
    }

    private void curseCard(LinearLayout parent) {
        LinearLayout cf = card(parent, R.drawable.ic_key, s(R.string.settings_curse_key));
        cf.addView(Ui.text(app, s(R.string.settings_curse_text), 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText key = field(app.store.curseKey(), s(R.string.settings_paste_key), true);
        cf.addView(key, Ui.lp(-1, Ui.dp(42)));
        cf.addView(actions(
                Ui.button(app, s(R.string.action_save), R.drawable.ic_check, Ui.Kind.PRIMARY, v -> {
                    app.store.setCurseKey(key.getText().toString());
                    app.results.remove(MainActivity.SRC_CURSE);
                    app.hideKeyboard();
                    app.snack(s(Sources.hasCurseKey() ? R.string.curse_key_saved : R.string.curse_key_removed));
                    if (Sources.hasCurseKey()) testCurseKey();
                }),
                Ui.button(app, s(R.string.action_get_key), R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl("https://console.curseforge.com/"))),
                Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
    }

    private void githubCard(LinearLayout parent) {
        LinearLayout gh = card(parent, R.drawable.ic_link, s(R.string.settings_github_token));
        gh.addView(Ui.text(app, s(R.string.settings_github_text), 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText tok = field(app.store.githubToken(), "ghp_…", true);
        gh.addView(tok, Ui.lp(-1, Ui.dp(42)));
        gh.addView(actions(
                Ui.button(app, s(R.string.action_save), R.drawable.ic_check, Ui.Kind.SECONDARY, v -> {
                    app.store.setGithubToken(tok.getText().toString());
                    app.hideKeyboard();
                    app.snack(s(R.string.saved));
                })),
                Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
    }

    private void aboutCard(LinearLayout parent) {
        LinearLayout ab = card(parent, R.drawable.ic_info, s(R.string.settings_about));
        int col = app.layout.listDp >= TWO_COLUMNS_DP ? app.layout.listDp / 2 : app.layout.listDp;
        int wmh = Math.min(40, Math.round((col - 80) / Ui.WORDMARK_RATIO));
        ab.addView(Ui.wordmark(app, wmh), Ui.margins(Ui.lp(Math.round(Ui.dp(wmh) * Ui.WORDMARK_RATIO), Ui.dp(wmh)), 0, 2, 0, 12));
        ab.addView(Ui.text(app, s(R.string.settings_about_text), 12.5f, Ui.TEXT2, Ui.REGULAR));
        ab.addView(actions(
                Ui.button(app, s(R.string.action_forever_client), R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl("https://github.com/jaredgei/wow-forever-android")),
                Ui.button(app, s(R.string.action_clear_cache), R.drawable.ic_delete, Ui.Kind.GHOST, v -> {
                    Installer.deleteRecursive(new File(app.getCacheDir(), "dl"));
                    Installer.deleteRecursive(new File(app.getCacheDir(), "wowi-filelist.json"));
                    app.results.clear();
                    app.snack(s(R.string.cache_cleared));
                })),
                Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));
    }

    /** Card buttons wrap to a new line instead of being cut. */
    private View actions(View... buttons) {
        Ui.FlowRow row = new Ui.FlowRow(app, 8, 8);
        for (View b : buttons) row.addView(b, new ViewGroup.LayoutParams(-2, Ui.dp(40)));
        return row;
    }

    // ── Auto-update ──

    private void autoUpdateCard(LinearLayout col) {
        final Store st = app.store;
        LinearLayout c = card(col, R.drawable.ic_upgrade, s(R.string.auto_title));
        c.addView(Ui.text(app, s(R.string.auto_text), 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final int mode = st.autoMode();
        c.addView(chipRow(new String[]{s(R.string.auto_mode_off), s(R.string.auto_mode_notify), s(R.string.auto_mode_install)},
                new int[]{UpdatePolicy.MODE_OFF, UpdatePolicy.MODE_NOTIFY, UpdatePolicy.MODE_INSTALL}, mode, m -> {
                    st.setAutoMode(m);
                    if (m != UpdatePolicy.MODE_OFF) askNotifications();
                    AutoUpdate.apply(app);
                    app.render();
                }));
        if (mode == UpdatePolicy.MODE_OFF) return;

        c.addView(Ui.text(app, s(R.string.auto_frequency), 13, Ui.TEXT2, Ui.MEDIUM), Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 6));
        String[] hours = new String[UpdatePolicy.INTERVALS_H.length];
        for (int i = 0; i < hours.length; i++) hours[i] = s(R.string.auto_every_hours, UpdatePolicy.INTERVALS_H[i]);
        c.addView(chipRow(hours, UpdatePolicy.INTERVALS_H, st.autoIntervalHours(), h -> {
            st.setAutoIntervalHours(h);
            AutoUpdate.apply(app);
            app.render();
        }));

        c.addView(Ui.text(app, s(R.string.auto_conditions), 13, Ui.TEXT2, Ui.MEDIUM), Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 6));
        LinearLayout toggles = Ui.hbox(app);
        toggles.addView(chip(s(R.string.auto_wifi_only), st.autoWifiOnly(), v -> { st.setAutoWifiOnly(!st.autoWifiOnly()); AutoUpdate.apply(app); app.render(); }), Ui.margins(Ui.lp(-2, -2), 0, 0, 8, 0));
        toggles.addView(chip(s(R.string.auto_charging_only), st.autoChargingOnly(), v -> { st.setAutoChargingOnly(!st.autoChargingOnly()); AutoUpdate.apply(app); app.render(); }), Ui.margins(Ui.lp(-2, -2), 0, 0, 8, 0));
        HorizontalScrollView ts = new HorizontalScrollView(app);
        ts.setHorizontalScrollBarEnabled(false);
        ts.addView(toggles);
        c.addView(ts);

        c.addView(Ui.text(app, autoStatus(st), 12.5f, st.autoLastError() != null ? Ui.ORANGE : Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));
        if (!AutoUpdate.canNotify(app)) c.addView(Ui.text(app, s(R.string.auto_no_notifications), 12.5f, Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 0));
        android.os.PowerManager pm = (android.os.PowerManager) app.getSystemService(android.content.Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(app.getPackageName())) {
            c.addView(Ui.text(app, s(R.string.auto_battery_text), 12.5f, Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 8));
            c.addView(Ui.button(app, s(R.string.auto_battery_action), R.drawable.ic_settings, Ui.Kind.GHOST, v -> {
                try { app.startActivity(new android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                catch (Exception e) { app.snack(MainActivity.friendly(e)); }
            }), Ui.lp(-2, Ui.dp(40)));
        }
    }

    private String autoStatus(Store st) {
        long t = st.autoLastRun();
        if (t == 0) return s(R.string.auto_status_never);
        String when = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, Lang.locale()).format(new java.util.Date(t));
        if (st.autoLastError() != null) return s(R.string.auto_status_error, when, st.autoLastError());
        if (st.autoLastUpdated() > 0) return s(R.string.auto_status_updated, when, st.autoLastUpdated());
        if (st.autoLastFound() > 0) return s(R.string.auto_status_found, when, st.autoLastFound());
        return s(R.string.auto_status_none, when);
    }

    private void askNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !AutoUpdate.canNotify(app)) {
            app.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, MainActivity.REQ_NOTIFY);
        }
    }

    private interface IntPick { void pick(int value); }

    private View chipRow(String[] labels, int[] values, int current, IntPick onPick) {
        HorizontalScrollView hs = new HorizontalScrollView(app);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.hbox(app);
        for (int i = 0; i < labels.length; i++) {
            final int v = values[i];
            row.addView(chip(labels[i], v == current, x -> { if (v != current) onPick.pick(v); }), Ui.margins(Ui.lp(-2, -2), 0, 0, 8, 0));
        }
        hs.addView(row);
        return hs;
    }

    private TextView chip(String label, boolean on, View.OnClickListener click) {
        TextView chip = Ui.text(app, label, 13, on ? Ui.ACCENT_DARK : Ui.TEXT2, Ui.MEDIUM);
        chip.setBackground(Ui.selectable(on ? Ui.ACCENT : Ui.SURFACE2, 16, on ? 0 : Ui.STROKE));
        chip.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnClickListener(click);
        return chip;
    }

    private void testCurseKey() {
        MainActivity.net.execute(() -> {
            String msg;
            try {
                Sources.Curse.search("", 0);
                msg = s(R.string.curse_key_works);
            } catch (Exception e) {
                msg = MainActivity.friendly(e);
            }
            final String m = msg;
            app.main.post(() -> app.snack(m));
        });
    }

    private LinearLayout card(LinearLayout parent, int icon, String title) {
        LinearLayout c = Ui.vbox(app);
        c.setBackground(Ui.round(Ui.SURFACE, 14, Ui.STROKE, 1));
        c.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(16));
        LinearLayout h = Ui.hbox(app);
        h.addView(Ui.icon(app, icon, Ui.ACCENT, 18));
        h.addView(Ui.text(app, title, 15, Ui.TEXT, Ui.BOLD), Ui.margins(Ui.lpw(0, -2, 1f), 10, 0, 0, 0));
        c.addView(h, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        parent.addView(c, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 12));
        return c;
    }

    private EditText field(String value, String hint, boolean secret) {
        EditText e = new EditText(app);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.TEXT3);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : 0) | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        android.graphics.drawable.StateListDrawable fbg = new android.graphics.drawable.StateListDrawable();
        fbg.addState(new int[]{android.R.attr.state_focused}, Ui.round(Ui.SURFACE2, 12, Ui.ACCENT, 1.5f));
        fbg.addState(new int[]{}, Ui.round(Ui.SURFACE2, 12, Ui.STROKE, 1f));
        e.setBackground(fbg);
        e.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        return e;
    }

    private String versionName() {
        try { return app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }
}
