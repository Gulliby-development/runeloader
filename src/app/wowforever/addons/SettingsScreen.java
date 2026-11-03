package app.wowforever.addons;

import android.text.InputType;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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

        // Game folder
        LinearLayout game = card(col, R.drawable.ic_folder, s(R.string.settings_game_folder));
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
        game.addView(Ui.text(app, status, 12.5f, Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 12));
        LinearLayout gb = Ui.hbox(app);
        gb.addView(Ui.button(app, s(R.string.game_pick_folder), R.drawable.ic_folder, Ui.Kind.PRIMARY, v -> app.gameFolder.openFolderPicker()), Ui.lp(-2, Ui.dp(40)));
        gb.addView(Ui.button(app, s(R.string.action_auto_detect), R.drawable.ic_search, Ui.Kind.SECONDARY, v -> app.gameFolder.autoDetect(true)), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        game.addView(gb);

        // Language
        LinearLayout lang = card(col, R.drawable.ic_language, s(R.string.settings_language));
        lang.addView(Ui.text(app, s(R.string.settings_language_text), 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        HorizontalScrollView ls = new HorizontalScrollView(app);
        ls.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.hbox(app);
        String current = Lang.saved(app);
        for (final String tag : Lang.TAGS) {
            boolean on = tag.equals(current);
            TextView chip = Ui.text(app, Lang.name(app, tag), 13, on ? Ui.ACCENT_DARK : Ui.TEXT2, Ui.MEDIUM);
            chip.setBackground(Ui.selectable(on ? Ui.ACCENT : Ui.SURFACE2, 16, on ? 0 : Ui.STROKE));
            chip.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                if (tag.equals(Lang.saved(app))) return;
                Lang.save(app, tag);
                app.recreate();
            });
            chips.addView(chip, Ui.margins(Ui.lp(-2, -2), 0, 0, 8, 0));
        }
        ls.addView(chips);
        lang.addView(ls);

        // CurseForge key
        LinearLayout cf = card(col, R.drawable.ic_key, s(R.string.settings_curse_key));
        cf.addView(Ui.text(app, s(R.string.settings_curse_text), 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText key = field(app.store.curseKey(), s(R.string.settings_paste_key), true);
        cf.addView(key, Ui.lp(-1, Ui.dp(42)));
        LinearLayout kb = Ui.hbox(app);
        kb.addView(Ui.button(app, s(R.string.action_save), R.drawable.ic_check, Ui.Kind.PRIMARY, v -> {
            app.store.setCurseKey(key.getText().toString());
            app.results.remove(MainActivity.SRC_CURSE);
            app.hideKeyboard();
            app.snack(s(Sources.hasCurseKey() ? R.string.curse_key_saved : R.string.curse_key_removed));
            if (Sources.hasCurseKey()) testCurseKey();
        }), Ui.lp(-2, Ui.dp(40)));
        kb.addView(Ui.button(app, s(R.string.action_get_key), R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl("https://console.curseforge.com/")), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        cf.addView(kb, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));

        // GitHub token
        LinearLayout gh = card(col, R.drawable.ic_link, s(R.string.settings_github_token));
        gh.addView(Ui.text(app, s(R.string.settings_github_text), 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText tok = field(app.store.githubToken(), "ghp_…", true);
        gh.addView(tok, Ui.lp(-1, Ui.dp(42)));
        gh.addView(Ui.button(app, s(R.string.action_save), R.drawable.ic_check, Ui.Kind.SECONDARY, v -> {
            app.store.setGithubToken(tok.getText().toString());
            app.hideKeyboard();
            app.snack(s(R.string.saved));
        }), Ui.margins(Ui.lp(-2, Ui.dp(40)), 0, 10, 0, 0));

        // About
        LinearLayout ab = card(col, R.drawable.ic_info, s(R.string.settings_about));
        int wmh = Math.min(40, Math.round((app.layout.listDp - 80) / Ui.WORDMARK_RATIO));
        ab.addView(Ui.wordmark(app, wmh), Ui.margins(Ui.lp(Math.round(Ui.dp(wmh) * Ui.WORDMARK_RATIO), Ui.dp(wmh)), 0, 2, 0, 12));
        ab.addView(Ui.text(app, s(R.string.settings_about_text), 12.5f, Ui.TEXT2, Ui.REGULAR));
        LinearLayout abb = Ui.hbox(app);
        abb.addView(Ui.button(app, s(R.string.action_forever_client), R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl("https://github.com/jaredgei/wow-forever-android")), Ui.lp(-2, Ui.dp(40)));
        abb.addView(Ui.button(app, s(R.string.action_clear_cache), R.drawable.ic_delete, Ui.Kind.GHOST, v -> {
            Installer.deleteRecursive(new File(app.getCacheDir(), "dl"));
            Installer.deleteRecursive(new File(app.getCacheDir(), "wowi-filelist.json"));
            app.results.clear();
            app.snack(s(R.string.cache_cleared));
        }), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        ab.addView(abb, Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));

        app.bodyBox.addView(sv, new FrameLayout.LayoutParams(-1, -1));
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
        c.setBackground(Ui.round(Ui.SURFACE, 16, Ui.STROKE, 1));
        c.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(16));
        LinearLayout h = Ui.hbox(app);
        h.addView(Ui.icon(app, icon, Ui.ACCENT, 20));
        h.addView(Ui.text(app, title, 15.5f, Ui.TEXT, Ui.BOLD), Ui.margins(Ui.lp(-2, -2), 10, 0, 0, 0));
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
