package app.wowforever.addons;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Theme and view factories; everything is built in code. */
final class Ui {
    // Palette: the Runeloader icon. Deep teal surfaces, cream text like the rune strokes, gold
    // like its arrowhead for actions and updates, and a mint "rune light" for the installed
    // state and gamepad focus (so focus stays visible on gold buttons).
    static final int BG = 0xFF0A1513;
    static final int RAIL = 0xFF0D1B18;
    static final int SURFACE = 0xFF11221E;
    static final int SURFACE2 = 0xFF162B26;
    static final int SURFACE3 = 0xFF1D3730;
    static final int STROKE = 0xFF24423A;
    static final int TEXT = 0xFFF3EAD6;
    static final int TEXT2 = 0xFFAEBBB2;
    static final int TEXT3 = 0xFF6E847C;
    static final int ACCENT = 0xFFD9A441;
    static final int ACCENT_DARK = 0xFF1B1405;
    static final int ACCENT_SOFT = 0x29D9A441;
    static final int BLUE = 0xFF7DB8E8;
    static final int BLUE_SOFT = 0x247DB8E8;
    static final int GREEN = 0xFF6FE3C6;
    static final int GREEN_SOFT = 0x226FE3C6;
    static final int ORANGE = 0xFFE8944A;
    static final int ORANGE_SOFT = 0x24E8944A;
    static final int RED = 0xFFE86A5F;
    static final int RED_SOFT = 0x26E86A5F;
    static final int FOCUS = 0xFF6FE3C6;
    /** Soft inner halo under the focus ring. */
    static final int FOCUS_GLOW = 0x596FE3C6;
    /** Snackbar and toast background. */
    static final int SNACK = 0xFF223F38;
    /** Dim layer behind sheets and dialogs. */
    static final int SCRIM = 0x9E030807;

    // Type scale in sp: display (brand screens), screen title, compact title (low windows),
    // section heading, list item title, body, caption.
    static final float TYPE_DISPLAY = 24;
    static final float TYPE_TITLE = 21;
    static final float TYPE_TITLE_COMPACT = 19;
    static final float TYPE_HEADING = 17;
    static final float TYPE_ITEM = 15;
    static final float TYPE_BODY = 13.5f;
    static final float TYPE_CAPTION = 12.5f;

    /** Width to height of res/drawable/wordmark.xml (its viewport is 3034 x 553). */
    static final float WORDMARK_RATIO = 3034f / 553f;

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    static float density = 2f;

    static int dp(float v) { return Math.round(v * density); }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    static GradientDrawable round(int color, float radiusDp, int stroke, float strokeDp) {
        GradientDrawable g = round(color, radiusDp);
        g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    /** Background with ripple + a mint ring and a soft halo when focused by a gamepad / keyboard. */
    static Drawable selectable(int color, float radiusDp, int strokeColor) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focusRing(color, radiusDp));
        s.addState(new int[]{android.R.attr.state_selected}, round(blend(color, 0xFFFFFFFF, 0.06f), radiusDp, strokeColor == 0 ? STROKE : strokeColor, 1f));
        s.addState(new int[]{}, strokeColor == 0 ? round(color, radiusDp) : round(color, radiusDp, strokeColor, 1f));
        GradientDrawable mask = round(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), s, mask);
    }

    /** A 2dp ring with a translucent halo inside it, so the view keeps its size when focused. */
    static Drawable focusRing(int color, float radiusDp) {
        GradientDrawable halo = round(0, Math.max(0, radiusDp - 2), FOCUS_GLOW, 2f);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{round(color, radiusDp, FOCUS, 2f), halo});
        int in = dp(2);
        ld.setLayerInset(1, in, in, in, in);
        return ld;
    }

    /** The Runeloader wordmark at the given height, with its own aspect ratio. */
    static ImageView wordmark(Context c, int heightDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(R.drawable.wordmark);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setContentDescription(c.getString(R.string.app_name));
        iv.setLayoutParams(new LinearLayout.LayoutParams(Math.round(dp(heightDp) * WORDMARK_RATIO), dp(heightDp)));
        return iv;
    }

    static int blend(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.argb(Color.alpha(a), (int) (ar + (br - ar) * t), (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    enum Kind { PRIMARY, SECONDARY, GHOST, DANGER, SUCCESS }

    /**
     * A pill button with optional icon. When the parent gives it less width than its label needs,
     * a button with an icon drops the label (keeping it as content description and tooltip)
     * instead of cutting the text.
     */
    static LinearLayout button(Context c, String label, int iconRes, Kind kind, View.OnClickListener l) {
        LinearLayout b = new CollapsingButton(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        int bg, fg, stroke = 0;
        switch (kind) {
            case PRIMARY: bg = ACCENT; fg = ACCENT_DARK; break;
            case DANGER: bg = RED_SOFT; fg = RED; break;
            case SUCCESS: bg = GREEN_SOFT; fg = GREEN; break;
            case GHOST: bg = 0x00000000; fg = TEXT2; stroke = STROKE; break;
            default: bg = SURFACE3; fg = TEXT; break;
        }
        b.setBackground(selectable(bg, 12, stroke));
        b.setPadding(dp(label == null ? 10 : 14), dp(9), dp(label == null ? 10 : 16), dp(9));
        b.setMinimumHeight(dp(40));
        b.setFocusable(true);
        b.setClickable(true);
        if (iconRes != 0) {
            ImageView iv = icon(c, iconRes, fg, 18);
            b.addView(iv);
        }
        if (label != null) {
            TextView t = text(c, label, 14, fg, MEDIUM);
            t.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (iconRes != 0) lp.leftMargin = dp(8);
            b.addView(t, lp);
        }
        if (label != null) {
            b.setContentDescription(label);
            if (iconRes != 0) b.setTooltipText(label);
        }
        b.setOnClickListener(l);
        b.setTag(kind);
        return b;
    }

    /** See {@link #button}: hides its label when it does not fit, so text is never cut. */
    static final class CollapsingButton extends LinearLayout {
        CollapsingButton(Context c) { super(c); }

        @Override protected void onMeasure(int w, int h) {
            TextView label = null;
            boolean hasIcon = false;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v instanceof TextView) label = (TextView) v;
                else if (v instanceof ImageView) hasIcon = true;
            }
            if (label != null && hasIcon) label.setVisibility(VISIBLE);
            super.onMeasure(w, h);
            if (label == null || !hasIcon || MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED) return;
            label.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            int need = getPaddingLeft() + getPaddingRight() + dp(18 + 8) + label.getMeasuredWidth();
            if (need > MeasureSpec.getSize(w)) {
                label.setVisibility(GONE);
                super.onMeasure(w, h);
            }
        }
    }

    /** Lays children out left to right and wraps to a new line when the width runs out. */
    static final class FlowRow extends ViewGroup {
        private final int hGap, vGap;

        FlowRow(Context c, int hGapDp, int vGapDp) {
            super(c);
            hGap = dp(hGapDp);
            vGap = dp(vGapDp);
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int max = MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                    : MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, lineH = 0, widest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                v.measure(MeasureSpec.makeMeasureSpec(max == Integer.MAX_VALUE ? 0 : max, max == Integer.MAX_VALUE ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cw = v.getMeasuredWidth(), ch = v.getMeasuredHeight();
                if (x > 0 && x + cw > max) { y += lineH + vGap; x = 0; lineH = 0; }
                x += cw + hGap;
                lineH = Math.max(lineH, ch);
                widest = Math.max(widest, x - hGap);
            }
            int width = MeasureSpec.getMode(wSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(wSpec) : widest + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(width, resolveSize(y + lineH + getPaddingTop() + getPaddingBottom(), hSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int max = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, lineH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                int cw = v.getMeasuredWidth(), ch = v.getMeasuredHeight();
                if (x > 0 && x + cw > max) { y += lineH + vGap; x = 0; lineH = 0; }
                v.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + cw, getPaddingTop() + y + ch);
                x += cw + hGap;
                lineH = Math.max(lineH, ch);
            }
        }
    }

    static void setButtonLabel(LinearLayout b, String label) {
        for (int i = 0; i < b.getChildCount(); i++) {
            if (b.getChildAt(i) instanceof TextView) ((TextView) b.getChildAt(i)).setText(label);
        }
    }

    static TextView pill(Context c, String s, int fg, int bg) {
        TextView t = text(c, s, 11.5f, fg, MEDIUM);
        t.setBackground(round(bg, 20));
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        t.setSingleLine(true);
        return t;
    }

    static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
        return v;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(STROKE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        return v;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpw(int w, int h, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.weight = weight;
        return p;
    }

    static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    static FrameLayout.LayoutParams flp(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static final int[] AVATAR_COLORS = {0xFF2F5A6E, 0xFF5B3B6E, 0xFF7A3B4D, 0xFF7A5A2F, 0xFF2F6E5E, 0xFF3B5A7A, 0xFF5E6E2F, 0xFF4A3B7A};

    /** Rounded square with the first letters of a name; used until a logo loads. */
    static final class LetterDrawable extends Drawable {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String letters;
        private final RectF r = new RectF();

        LetterDrawable(String name) {
            String n = name == null ? "?" : name.replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
            String[] parts = n.split("\\s+");
            String l = parts.length > 1 && !parts[1].isEmpty()
                    ? parts[0].substring(0, 1) + parts[1].substring(0, 1)
                    : (n.length() >= 2 ? n.substring(0, 2) : (n.isEmpty() ? "?" : n));
            letters = l.toUpperCase();
            int color = AVATAR_COLORS[Math.abs((name == null ? 0 : name.hashCode()) % AVATAR_COLORS.length)];
            bg.setColor(color);
            fg.setColor(TEXT);
            fg.setTypeface(BOLD);
            fg.setTextAlign(Paint.Align.CENTER);
        }

        @Override public void draw(Canvas c) {
            r.set(getBounds());
            float rad = r.width() * 0.26f;
            c.drawRoundRect(r, rad, rad, bg);
            fg.setTextSize(r.height() * 0.38f);
            Paint.FontMetrics fm = fg.getFontMetrics();
            c.drawText(letters, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, fg);
        }

        @Override public void setAlpha(int a) { bg.setAlpha(a); fg.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { fg.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A bitmap clipped to a rounded square on a dark tile (logos have mixed backgrounds). */
    static final class RoundBitmap extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Bitmap bmp;
        private final RectF r = new RectF();
        private final android.graphics.Matrix m = new android.graphics.Matrix();

        RoundBitmap(android.graphics.Bitmap b) {
            bmp = b;
            paint.setShader(new android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
            tile.setColor(SURFACE3);
        }

        @Override public void draw(Canvas c) {
            r.set(getBounds());
            float rad = r.width() * 0.26f;
            c.drawRoundRect(r, rad, rad, tile);
            float scale = Math.max(r.width() / bmp.getWidth(), r.height() / bmp.getHeight());
            m.reset();
            m.setScale(scale, scale);
            m.postTranslate(r.left + (r.width() - bmp.getWidth() * scale) / 2f, r.top + (r.height() - bmp.getHeight() * scale) / 2f);
            paint.getShader().setLocalMatrix(m);
            c.drawRoundRect(r, rad, rad, paint);
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** One decimal at most, with the UI language's decimal separator: 1.2 / 1,2. */
    private static String decimal(double v, int maxFraction) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(Lang.locale());
        f.setMaximumFractionDigits(maxFraction);
        f.setGroupingUsed(false);
        return f.format(v);
    }

    static String formatCount(long n) {
        if (n < 0) return "";
        if (n >= 1_000_000) return Lang.s(R.string.fmt_millions, decimal(n / 1_000_000.0, 1));
        if (n >= 1_000) return Lang.s(R.string.fmt_thousands, decimal(n / 1_000.0, 1));
        return String.valueOf(n);
    }

    static String formatBytes(long b) {
        if (b < 1024) return Lang.s(R.string.fmt_bytes, (int) b);
        if (b < 1024 * 1024) return Lang.s(R.string.fmt_kb, decimal(b / 1024.0, 0));
        return Lang.s(R.string.fmt_mb, decimal(b / (1024.0 * 1024), 1));
    }

    static String formatDate(long ms) {
        if (ms <= 0) return "";
        long days = (System.currentTimeMillis() - ms) / (24L * 3600 * 1000);
        if (days < 1) return Lang.s(R.string.today);
        if (days == 1) return Lang.s(R.string.yesterday);
        if (days < 30) return Lang.q(R.plurals.days_ago, (int) days);
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Lang.locale()).format(new java.util.Date(ms));
    }

    /** Fades a chip row at the edge that has more chips, so a cut chip reads as "scroll for more". */
    static void scrollHint(android.widget.HorizontalScrollView v) {
        v.setHorizontalFadingEdgeEnabled(true);
        v.setFadingEdgeLength(dp(28));
    }

    static void setMatchParent(View v) {
        v.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
    }

    private Ui() {}
}
