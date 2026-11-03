package app.wowforever.addons;

import android.view.View;
import android.view.ViewGroup;

/** Screens shown before the app can work: storage permission and the game folder. */
final class SetupScreen extends Screen {

    SetupScreen(MainActivity app) { super(app); }

    void renderPermission() {
        header(s(R.string.app_name), s(R.string.app_tagline));
        app.bodyBox.addView(branded(emptyState(R.drawable.ic_folder, s(R.string.perm_title),
                s(R.string.perm_text),
                Ui.button(app, s(R.string.perm_grant), R.drawable.ic_check, Ui.Kind.PRIMARY, v -> app.requestStorage()))));
    }

    void renderNoGame() {
        header(s(R.string.nav_installed), s(R.string.game_not_set));
        app.bodyBox.addView(branded(emptyState(R.drawable.ic_folder, s(R.string.game_where_title),
                s(R.string.game_where_text),
                Ui.button(app, s(R.string.game_auto_detect), R.drawable.ic_search, Ui.Kind.SECONDARY, v -> app.gameFolder.autoDetect(true)),
                Ui.button(app, s(R.string.game_pick_folder), R.drawable.ic_folder, Ui.Kind.PRIMARY, v -> app.gameFolder.openFolderPicker()))));
    }

    /** Puts the wordmark at the top of an empty state (a ScrollView holding the column). */
    private View branded(View empty) {
        ViewGroup box = (ViewGroup) ((ViewGroup) empty).getChildAt(0);
        int h = Math.min(app.layout.compactHeight ? 40 : 48, Math.round((app.layout.listDp - 56) / Ui.WORDMARK_RATIO));
        box.addView(Ui.wordmark(app, h), 0, Ui.margins(Ui.lp(Math.round(Ui.dp(h) * Ui.WORDMARK_RATIO), Ui.dp(h)), 0, 0, 0, app.layout.compactHeight ? 14 : 22));
        return empty;
    }
}
