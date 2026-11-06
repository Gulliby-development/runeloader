package app.wowforever.addons;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Detail panel sections fed by the GitHub index: description, screenshots, changelog, versions, links. */
final class DetailExtras {

    private static final int COLLAPSED_CHARS = 420;

    /** Long text shown short first, with a focusable toggle so a gamepad can open it too. */
    static void description(MainActivity app, LinearLayout col, String text) {
        if (text == null || text.isEmpty()) return;
        col.addView(sectionTitle(app, app.getString(R.string.detail_description)), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 8));
        expandable(app, col, text, Ui.TEXT2);
    }

    static void screenshots(final MainActivity app, LinearLayout col, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        col.addView(sectionTitle(app, app.getString(R.string.detail_screenshots)), Ui.margins(Ui.lp(-1, -2), 0, 4, 0, 8));
        HorizontalScrollView hs = new HorizontalScrollView(app);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout row = Ui.hbox(app);
        int h = Ui.dp(app.landscape ? 120 : 150);
        int w = h * 16 / 9;
        for (int i = 0; i < urls.size(); i++) {
            final String u = urls.get(i);
            ImageView iv = new ImageView(app);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.selectable(Ui.SURFACE2, 10, Ui.STROKE));
            iv.setPadding(Ui.dp(2), Ui.dp(2), Ui.dp(2), Ui.dp(2));
            iv.setClipToOutline(true);
            iv.setFocusable(true);
            iv.setClickable(true);
            iv.setContentDescription(app.getString(R.string.detail_screenshot_n, i + 1));
            iv.setOnClickListener(v -> app.openUrl(u));
            ImageLoader.loadShot(iv, u, h);
            row.addView(iv, Ui.margins(Ui.lp(w, h), 0, 0, i == urls.size() - 1 ? 0 : 8, 0));
        }
        hs.addView(row);
        col.addView(hs, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
    }

    /** "What's new" card for the latest release; highlighted when it is an available update. */
    static void changelog(MainActivity app, LinearLayout col, String version, String text, boolean isUpdate) {
        if (text == null || text.isEmpty()) return;
        LinearLayout card = Ui.vbox(app);
        card.setBackground(Ui.round(isUpdate ? Ui.ACCENT_SOFT : Ui.SURFACE2, 12));
        card.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        String title = version == null || version.isEmpty() ? app.getString(R.string.detail_whats_new) : app.getString(R.string.detail_whats_new_in, shortVer(version));
        card.addView(Ui.oneLine(Ui.text(app, title, 13.5f, isUpdate ? Ui.ACCENT : Ui.TEXT, Ui.MEDIUM)), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 6));
        expandable(app, card, styled(Markdown.render(text)), Ui.TEXT2);
        col.addView(card, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 14));
    }

    /** Recent releases from the index; each row opens its GitHub release page. */
    static void releases(final MainActivity app, LinearLayout col, List<Model.Release> rels, String installed, final String repo) {
        boolean list = rels != null && rels.size() >= 2;
        if (!list && repo == null) return;
        col.addView(sectionTitle(app, app.getString(R.string.detail_versions)), Ui.margins(Ui.lp(-1, -2), 0, 16, 0, 4));
        if (list) for (final Model.Release r : rels) {
            LinearLayout row = linkRow(app, r.changelogUrl);
            TextView v = Ui.oneLine(Ui.text(app, shortVer(r.version), 13, Ui.TEXT, Ui.MEDIUM));
            row.addView(v, Ui.lpw(0, -2, 1));
            if (installed != null && AddonManager.sameVersion(r.version, installed)) {
                row.addView(Ui.pill(app, app.getString(R.string.pill_installed), Ui.GREEN, Ui.GREEN_SOFT), Ui.margins(Ui.lp(-2, -2), 8, 0, 0, 0));
            }
            if (r.prerelease) row.addView(Ui.pill(app, app.getString(R.string.pill_beta), Ui.ORANGE, Ui.ORANGE_SOFT), Ui.margins(Ui.lp(-2, -2), 8, 0, 0, 0));
            if (r.released > 0) row.addView(Ui.text(app, Ui.formatDate(r.released), 12, Ui.TEXT3, Ui.REGULAR), Ui.margins(Ui.lp(-2, -2), 10, 0, 0, 0));
            col.addView(row, Ui.lp(-1, -2));
        }
        if (repo != null) {
            LinearLayout all = linkRow(app, "https://github.com/" + repo + "/releases");
            all.addView(Ui.oneLine(Ui.text(app, app.getString(R.string.detail_all_releases), 13, Ui.ACCENT, Ui.MEDIUM)), Ui.lpw(0, -2, 1));
            all.addView(Ui.icon(app, R.drawable.ic_open, Ui.ACCENT, 16));
            col.addView(all, Ui.lp(-1, -2));
        }
        col.addView(Ui.space(app, 0, 10));
    }

    private static LinearLayout linkRow(final MainActivity app, final String url) {
        LinearLayout row = Ui.hbox(app);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(8), Ui.dp(9), Ui.dp(8), Ui.dp(9));
        row.setBackground(Ui.selectable(0x00000000, 10, 0));
        if (url != null) {
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnClickListener(v -> app.openUrl(url));
        }
        return row;
    }

    private static void expandable(MainActivity app, LinearLayout parent, final CharSequence text, int color) {
        final TextView t = Ui.text(app, "", 13, color, Ui.REGULAR);
        t.setText(text);
        parent.addView(t, Ui.lp(-1, -2));
        if (text.length() <= COLLAPSED_CHARS) return;
        final CharSequence shortText = cut(text);
        t.setText(shortText);
        final TextView more = Ui.text(app, app.getString(R.string.show_more), 13, Ui.ACCENT, Ui.MEDIUM);
        more.setPadding(0, Ui.dp(8), Ui.dp(8), Ui.dp(4));
        more.setBackground(Ui.selectable(0x00000000, 8, 0));
        more.setFocusable(true);
        more.setClickable(true);
        more.setOnClickListener(new View.OnClickListener() {
            boolean open;
            @Override public void onClick(View v) {
                open = !open;
                t.setText(open ? text : shortText);
                more.setText(app.getString(open ? R.string.show_less : R.string.show_more));
            }
        });
        parent.addView(more, Ui.lp(-2, -2));
    }

    private static CharSequence cut(CharSequence s) {
        String str = s.toString();
        int nl = str.lastIndexOf('\n', COLLAPSED_CHARS);
        int sp = str.lastIndexOf(' ', COLLAPSED_CHARS);
        int at = nl > COLLAPSED_CHARS / 2 ? nl : (sp > COLLAPSED_CHARS / 2 ? sp : COLLAPSED_CHARS);
        int end = at;
        while (end > 0 && Character.isWhitespace(str.charAt(end - 1))) end--;
        return new SpannableStringBuilder(s.subSequence(0, end)).append("…");
    }

    /** Release notes with their headings and bold text styled. */
    static CharSequence styled(Markdown.Doc d) {
        SpannableStringBuilder sb = new SpannableStringBuilder(d.text);
        for (int[] r : d.headings) {
            if (r[0] >= r[1]) continue;
            sb.setSpan(new StyleSpan(Typeface.BOLD), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(Ui.TEXT), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(1.08f), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int[] r : d.bold) {
            if (r[0] < r[1]) sb.setSpan(new StyleSpan(Typeface.BOLD), r[0], r[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    static TextView sectionTitle(MainActivity app, String s) {
        return Ui.oneLine(Ui.text(app, s, 14, Ui.TEXT, Ui.BOLD));
    }

    static String shortVer(String v) {
        if (v == null) return "";
        return v.length() > 22 ? v.substring(0, 22) + "…" : v;
    }

    private DetailExtras() {}
}
