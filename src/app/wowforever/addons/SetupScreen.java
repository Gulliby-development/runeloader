package app.wowforever.addons;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** First run: storage permission, then the game folder, shown as steps next to the rune seal. */
final class SetupScreen extends Screen {

    SetupScreen(MainActivity app) { super(app); }

    void renderPermission() {
        onboarding(0, s(R.string.perm_title), s(R.string.perm_text),
                Ui.button(app, s(R.string.perm_grant), R.drawable.ic_check, Ui.Kind.PRIMARY, v -> app.requestStorage()));
    }

    void renderNoGame() {
        onboarding(1, s(R.string.game_where_title), s(R.string.game_where_text),
                Ui.button(app, s(R.string.game_auto_detect), R.drawable.ic_search, Ui.Kind.PRIMARY, v -> app.gameFolder.autoDetect(true)),
                Ui.button(app, s(R.string.game_pick_folder), R.drawable.ic_folder, Ui.Kind.SECONDARY, v -> app.gameFolder.openFolderPicker()));
    }

    private static final int[] STEPS = {R.string.setup_step_access, R.string.setup_step_folder};

    private void onboarding(int step, String title, String text, View... actions) {
        LayoutSpec ls = app.layout;
        boolean wide = ls.rail;

        LinearLayout page = Ui.vbox(app);
        page.setPadding(Ui.dp(wide ? 8 : 16), Ui.dp(12), Ui.dp(16), Ui.dp(wide ? 16 : 24));

        // The language can be changed here, before the user has to read anything else.
        LinearLayout top = Ui.hbox(app);
        top.setGravity(Gravity.CENTER_VERTICAL);
        // The wordmark replaces the header on these screens; it shrinks to leave room for the language button.
        int wm = Math.max(16, Math.min(28, Math.round((ls.listDp - 220) / Ui.WORDMARK_RATIO)));
        top.addView(Ui.wordmark(app, wm), Ui.margins(Ui.lp(Math.round(Ui.dp(wm) * Ui.WORDMARK_RATIO), Ui.dp(wm)), 8, 0, 0, 0));
        top.addView(new View(app), Ui.lpw(0, 1, 1f));
        top.addView(Ui.button(app, Lang.name(app, Lang.saved(app)), R.drawable.ic_language, Ui.Kind.GHOST, v -> languageDialog()), Ui.lp(-2, Ui.dp(40)));
        page.addView(top, Ui.lp(-1, -2));

        LinearLayout steps = Ui.vbox(app);
        steps.addView(stepRow(step, true), Ui.lp(-1, -2));
        TextView h = Ui.text(app, title, wide && ls.compactHeight ? 21 : 22, Ui.TEXT, Ui.BOLD);
        steps.addView(h, Ui.margins(Ui.lp(-1, -2), 0, 14, 0, 0));
        TextView p = Ui.text(app, text, 13.5f, Ui.TEXT2, Ui.REGULAR);
        p.setLineSpacing(0, 1.25f);
        steps.addView(p, Ui.margins(Ui.lp(-1, -2), 0, 10, 0, 0));
        if (wide) {
            Ui.FlowRow row = new Ui.FlowRow(app, 10, 10);
            for (View a : actions) {
                a.setMinimumHeight(Ui.dp(44));
                row.addView(a, new ViewGroup.LayoutParams(-2, Ui.dp(44)));
            }
            steps.addView(row, Ui.margins(Ui.lp(-1, -2), 0, 18, 0, 0));
        } else {
            for (int i = 0; i < actions.length; i++) {
                steps.addView(actions[i], Ui.margins(Ui.lp(-1, Ui.dp(46)), 0, i == 0 ? 20 : 10, 0, 0));
            }
        }
        if (step + 1 < STEPS.length) steps.addView(stepRow(step + 1, false), Ui.margins(Ui.lp(-1, -2), 0, 20, 0, 0));

        if (wide) {
            int artCol = Math.round(ls.listDp / 2.15f);
            int seal = Math.max(120, Math.min(Math.min(260, ls.heightDp / 2), artCol - 24));
            LinearLayout cols = Ui.hbox(app);
            cols.addView(seal(seal), Ui.lpw(0, Ui.dp(seal + 40), 1f));
            cols.addView(steps, Ui.margins(Ui.lpw(0, -2, 1.15f), 8, 0, 12, 0));
            LinearLayout center = Ui.vbox(app);
            center.setGravity(Gravity.CENTER_VERTICAL);
            center.addView(cols, Ui.lp(-1, -2));
            page.addView(center, Ui.lpw(-1, 0, 1f));
        } else {
            int seal = ls.heightDp >= 700 ? 150 : 120;
            page.addView(seal(seal), Ui.margins(Ui.lp(-1, Ui.dp(seal + 24)), 0, 4, 0, 8));
            steps.setPadding(Ui.dp(4), 0, Ui.dp(4), 0);
            page.addView(steps, Ui.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(app);
        sv.setFillViewport(true);
        sv.addView(page, new FrameLayout.LayoutParams(-1, -2));
        app.bodyBox.addView(sv, new FrameLayout.LayoutParams(-1, -1));
    }

    private View stepRow(int index, boolean current) {
        LinearLayout r = Ui.hbox(app);
        TextView n = Ui.text(app, String.valueOf(index + 1), 11.5f, current ? Ui.ACCENT_DARK : Ui.TEXT3, Ui.BOLD);
        n.setGravity(Gravity.CENTER);
        n.setBackground(current ? Ui.round(Ui.ACCENT, 12) : Ui.round(0, 12, Ui.STROKE, 1));
        r.addView(n, Ui.lp(Ui.dp(24), Ui.dp(24)));
        TextView t = Ui.text(app, s(STEPS[index]), 13, current ? Ui.TEXT : Ui.TEXT3, Ui.MEDIUM);
        r.addView(t, Ui.margins(Ui.lpw(0, -2, 1f), 10, 0, 0, 0));
        return r;
    }

    private View seal(int sizeDp) {
        ImageView iv = new ImageView(app);
        iv.setImageDrawable(new RuneSeal());
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout f = new FrameLayout(app);
        f.addView(iv, Ui.flp(Ui.dp(sizeDp), Ui.dp(sizeDp), Gravity.CENTER));
        return f;
    }

    /** Language list as a dialog; the settings tab offers the same choice as chips. */
    void languageDialog() {
        LinearLayout col = Ui.vbox(app);
        String current = Lang.saved(app);
        View focus = null;
        for (final String tag : Lang.TAGS) {
            boolean on = tag.equals(current);
            LinearLayout row = Ui.hbox(app);
            row.setBackground(Ui.selectable(on ? Ui.SURFACE3 : Ui.SURFACE2, 12, on ? Ui.ACCENT : Ui.STROKE));
            row.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
            row.setMinimumHeight(Ui.dp(46));
            row.setFocusable(true);
            row.setClickable(true);
            row.addView(Ui.oneLine(Ui.text(app, Lang.name(app, tag), 14, on ? Ui.TEXT : Ui.TEXT2, Ui.MEDIUM)), Ui.lpw(0, -2, 1f));
            if (on) row.addView(Ui.icon(app, R.drawable.ic_check, Ui.ACCENT, 18));
            row.setOnClickListener(v -> {
                app.closeModal();
                if (tag.equals(Lang.saved(app))) return;
                Lang.save(app, tag);
                app.recreate();
            });
            col.addView(row, Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 6));
            if (on) focus = row;
        }
        ScrollView sv = new ScrollView(app);
        sv.addView(col);
        app.modal(s(R.string.settings_language), null, sv, null);
        int maxH = app.getResources().getDisplayMetrics().heightPixels - Ui.dp(140);
        ViewGroup.LayoutParams lp = sv.getLayoutParams();
        lp.height = Math.min(Lang.TAGS.length * Ui.dp(52), maxH);
        sv.setLayoutParams(lp);
        final View f = focus != null ? focus : col.getChildAt(0);
        f.post(f::requestFocus);
    }

    /**
     * The Runeloader rune drawn in code, in the 100-unit space of the app icon: a cream stave with
     * two upward twigs and a gold arrowhead pointing down, in a thin gold ring over a soft mint glow.
     */
    static final class RuneSeal extends Drawable {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stave = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        RuneSeal() {
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(withAlpha(Ui.ACCENT, 0xC0));
            halo.setStyle(Paint.Style.STROKE);
            halo.setColor(withAlpha(Ui.GREEN, 0x30));
            stave.setStyle(Paint.Style.STROKE);
            stave.setColor(Ui.TEXT);
            stave.setStrokeCap(Paint.Cap.ROUND);
            arrow.setStyle(Paint.Style.STROKE);
            arrow.setColor(Ui.ACCENT);
            arrow.setStrokeCap(Paint.Cap.ROUND);
            arrow.setStrokeJoin(Paint.Join.ROUND);
        }

        private static int withAlpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

        @Override protected void onBoundsChange(Rect b) {
            float r = Math.min(b.width(), b.height()) / 2f;
            if (r <= 0) return;
            glow.setShader(new RadialGradient(b.exactCenterX(), b.exactCenterY(), r,
                    new int[]{withAlpha(Ui.GREEN, 0x38), withAlpha(Ui.GREEN, 0x12), 0},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height());
            if (size <= 0) return;
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            c.drawCircle(cx, cy, size / 2f, glow);
            // The rune takes the inner 78%; the glow fills the rest.
            float k = size * 0.78f / 100f;
            float ox = cx - 50 * k, oy = cy - 50 * k;
            halo.setStrokeWidth(Math.max(1f, 1.2f * k));
            c.drawCircle(cx, cy, 49 * k, halo);
            ring.setStrokeWidth(Math.max(1f, 1.6f * k));
            c.drawCircle(cx, cy, 44 * k, ring);
            stave.setStrokeWidth(5 * k);
            c.drawLine(ox + 50 * k, oy + 20 * k, ox + 50 * k, oy + 76 * k, stave);
            c.drawLine(ox + 50 * k, oy + 42 * k, ox + 37 * k, oy + 31 * k, stave);
            c.drawLine(ox + 50 * k, oy + 46 * k, ox + 63 * k, oy + 35 * k, stave);
            arrow.setStrokeWidth(5 * k);
            path.reset();
            path.moveTo(ox + 41 * k, oy + 68 * k);
            path.lineTo(ox + 50 * k, oy + 78 * k);
            path.lineTo(ox + 59 * k, oy + 68 * k);
            c.drawPath(path, arrow);
        }

        @Override public void setAlpha(int a) {}

        @Override public void setColorFilter(ColorFilter cf) {}

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
