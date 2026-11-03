package app.wowforever.addons;

import android.text.InputType;
import android.util.TypedValue;
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
        header("Настройки", "WoW Addons " + versionName());
        ScrollView sv = new ScrollView(app);
        LinearLayout col = Ui.vbox(app);
        col.setPadding(Ui.dp(app.landscape ? 20 : 16), Ui.dp(4), Ui.dp(app.landscape ? 20 : 16), Ui.dp(96));
        sv.addView(col);

        // Game folder
        LinearLayout game = card(col, R.drawable.ic_folder, "Папка игры");
        String root = app.store.gameRoot();
        boolean ok = app.store.gameReady();
        TextView path = Ui.text(app, root == null ? "Не выбрана" : root, 14, ok ? Ui.TEXT : Ui.ORANGE, Ui.MEDIUM);
        game.addView(path);
        String status;
        if (root == null) status = "Выберите папку с WoW Forever (где лежат .build.info и _forever_).";
        else if (!ok) status = "В этой папке не найден WoW Forever.";
        else {
            File ad = app.store.addonsDir();
            status = "Аддоны ставятся в " + Store.FLAVOR_DIR + "/Interface/AddOns" + (ad != null && ad.isDirectory() ? "" : " (папка будет создана)");
        }
        game.addView(Ui.text(app, status, 12.5f, Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 6, 0, 12));
        LinearLayout gb = Ui.hbox(app);
        gb.addView(Ui.button(app, "Выбрать папку", R.drawable.ic_folder, Ui.Kind.PRIMARY, v -> app.gameFolder.openFolderPicker()), Ui.lp(-2, Ui.dp(40)));
        gb.addView(Ui.button(app, "Автопоиск", R.drawable.ic_search, Ui.Kind.SECONDARY, v -> app.gameFolder.autoDetect(true)), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        game.addView(gb);

        // CurseForge key
        LinearLayout cf = card(col, R.drawable.ic_key, "CurseForge API-ключ");
        cf.addView(Ui.text(app, "Даёт поиск и обновления по всем аддонам CurseForge с версией для Forever. Ключ бесплатный: войдите на console.curseforge.com → API keys. Он хранится только на этом устройстве и отправляется только на api.curseforge.com и forgecdn.net.", 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText key = field(app.store.curseKey(), "Вставьте ключ", true);
        cf.addView(key, Ui.lp(-1, Ui.dp(42)));
        LinearLayout kb = Ui.hbox(app);
        kb.addView(Ui.button(app, "Сохранить", R.drawable.ic_check, Ui.Kind.PRIMARY, v -> {
            app.store.setCurseKey(key.getText().toString());
            app.results.remove(MainActivity.SRC_CURSE);
            app.hideKeyboard();
            app.snack(Sources.hasCurseKey() ? "Ключ CurseForge сохранён" : "Ключ удалён");
            if (Sources.hasCurseKey()) testCurseKey();
        }), Ui.lp(-2, Ui.dp(40)));
        kb.addView(Ui.button(app, "Получить ключ", R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl("https://console.curseforge.com/")), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        cf.addView(kb, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));

        // GitHub token
        LinearLayout gh = card(col, R.drawable.ic_link, "GitHub-токен (необязательно)");
        gh.addView(Ui.text(app, "Каталог работает без токена. Токен нужен, только если вы часто добавляете репозитории без release.json и упираетесь в лимит 60 запросов в час.", 12.5f, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 10));
        final EditText tok = field(app.store.githubToken(), "ghp_…", true);
        gh.addView(tok, Ui.lp(-1, Ui.dp(42)));
        gh.addView(Ui.button(app, "Сохранить", R.drawable.ic_check, Ui.Kind.SECONDARY, v -> {
            app.store.setGithubToken(tok.getText().toString());
            app.hideKeyboard();
            app.snack("Сохранено");
        }), Ui.margins(Ui.lp(-2, Ui.dp(40)), 0, 10, 0, 0));

        // About
        LinearLayout ab = card(col, R.drawable.ic_info, "О приложении");
        ab.addView(Ui.text(app, "Менеджер аддонов для клиента WoW Forever на Android. Ставит только сборки для Forever (TOC _Forever / _Camelot, интерфейс 16001), обновляет их и ничего не трогает в игре, кроме Interface/AddOns.\n\nУправление с геймпада: L1/R1 — вкладки, X — проверить обновления, Y — поиск, B — назад.", 12.5f, Ui.TEXT2, Ui.REGULAR));
        LinearLayout abb = Ui.hbox(app);
        abb.addView(Ui.button(app, "Клиент WoW Forever", R.drawable.ic_open, Ui.Kind.GHOST, v -> app.openUrl("https://github.com/jaredgei/wow-forever-android")), Ui.lp(-2, Ui.dp(40)));
        abb.addView(Ui.button(app, "Очистить кэш", R.drawable.ic_delete, Ui.Kind.GHOST, v -> {
            Installer.deleteRecursive(new File(app.getCacheDir(), "dl"));
            Installer.deleteRecursive(new File(app.getCacheDir(), "wowi-filelist.json"));
            app.results.clear();
            app.snack("Кэш очищен");
        }), Ui.margins(Ui.lp(-2, Ui.dp(40)), 8, 0, 0, 0));
        ab.addView(abb, Ui.margins(Ui.lp(-1, -2), 0, 12, 0, 0));

        app.bodyBox.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private void testCurseKey() {
        MainActivity.net.execute(() -> {
            String msg;
            try {
                Sources.Curse.search("", 0);
                msg = "Ключ CurseForge работает ✓";
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
