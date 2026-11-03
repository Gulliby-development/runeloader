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
    // Palette
    static final int BG = 0xFF0B0E13;
    static final int RAIL = 0xFF0E1218;
    static final int SURFACE = 0xFF141922;
    static final int SURFACE2 = 0xFF1A202B;
    static final int SURFACE3 = 0xFF222A37;
    static final int STROKE = 0xFF262F3D;
    static final int TEXT = 0xFFE8ECF2;
    static final int TEXT2 = 0xFF9AA4B2;
    static final int TEXT3 = 0xFF6B7585;
    static final int ACCENT = 0xFFF2B640;
    static final int ACCENT_DARK = 0xFF1A1405;
    static final int ACCENT_SOFT = 0x26F2B640;
    static final int BLUE = 0xFF5AA9FF;
    static final int BLUE_SOFT = 0x225AA9FF;
    static final int GREEN = 0xFF3DDC97;
    static final int GREEN_SOFT = 0x223DDC97;
    static final int ORANGE = 0xFFFF9F43;
    static final int ORANGE_SOFT = 0x26FF9F43;
    static final int RED = 0xFFFF5C6C;
    static final int RED_SOFT = 0x22FF5C6C;
    static final int FOCUS = 0xFFF2B640;

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

    /** Background with ripple + a gold ring when focused by a gamepad / keyboard. */
    static Drawable selectable(int color, float radiusDp, int strokeColor) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, round(color, radiusDp, FOCUS, 2f));
        s.addState(new int[]{android.R.attr.state_selected}, round(blend(color, 0xFFFFFFFF, 0.06f), radiusDp, strokeColor == 0 ? STROKE : strokeColor, 1f));
        s.addState(new int[]{}, strokeColor == 0 ? round(color, radiusDp) : round(color, radiusDp, strokeColor, 1f));
        GradientDrawable mask = round(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), s, mask);
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

    /** A pill button with optional icon. */
    static LinearLayout button(Context c, String label, int iconRes, Kind kind, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(c);
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
        b.setOnClickListener(l);
        b.setTag(kind);
        return b;
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

    static final int[] AVATAR_COLORS = {0xFF3B4D7A, 0xFF5B3B7A, 0xFF7A3B55, 0xFF7A5A3B, 0xFF3B7A6A, 0xFF3B6A7A, 0xFF6A7A3B, 0xFF4A3B7A};

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
            fg.setColor(0xFFF4F6FA);
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

    static String formatCount(long n) {
        if (n < 0) return "";
        if (n >= 1_000_000) return String.format(java.util.Locale.US, "%.1f млн", n / 1_000_000.0).replace('.', ',');
        if (n >= 1_000) return String.format(java.util.Locale.US, "%.1f тыс.", n / 1_000.0).replace('.', ',').replace(",0 ", " ");
        return String.valueOf(n);
    }

    static String formatBytes(long b) {
        if (b < 1024) return b + " Б";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f КБ", b / 1024.0);
        return String.format(java.util.Locale.US, "%.1f МБ", b / (1024.0 * 1024)).replace('.', ',');
    }

    static String formatDate(long ms) {
        if (ms <= 0) return "";
        long days = (System.currentTimeMillis() - ms) / (24L * 3600 * 1000);
        if (days < 1) return "сегодня";
        if (days == 1) return "вчера";
        if (days < 30) return days + " " + plural(days, "день", "дня", "дней") + " назад";
        return new java.text.SimpleDateFormat("d MMM yyyy", new java.util.Locale("ru")).format(new java.util.Date(ms));
    }

    static String plural(long n, String one, String few, String many) {
        long m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    static void setMatchParent(View v) {
        v.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
    }

    private Ui() {}
}
