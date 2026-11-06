package app.wowforever.addons;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** The queue, opened from the progress card: what runs now, what waits (and can be cancelled), what just finished. */
final class QueueSheet extends Screen {
    /** The rows; null while the sheet is closed. */
    private LinearLayout list;
    private View clear;

    QueueSheet(MainActivity app) { super(app); }

    void open() {
        list = Ui.vbox(app);
        ScrollView sc = new ScrollView(app);
        sc.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sc.addView(list, new FrameLayout.LayoutParams(-1, -2));
        // The card stays on the screen however long the queue is: title and buttons take about 150dp.
        int screen = app.getResources().getDisplayMetrics().heightPixels;
        MaxHeight box = new MaxHeight(app, Math.max(Ui.dp(120), screen - Ui.dp(170)));
        box.addView(sc, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout btns = Ui.hbox(app);
        btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clear = Ui.button(app, s(R.string.queue_clear), 0, Ui.Kind.GHOST, v -> { app.queue.clearFinished(); app.manager.queueChanged(); });
        btns.addView(clear, Ui.lp(-2, Ui.dp(42)));
        final LinearLayout close = Ui.button(app, s(R.string.action_close), 0, Ui.Kind.PRIMARY, v -> close());
        btns.addView(close, Ui.margins(Ui.lp(-2, Ui.dp(42)), 8, 0, 0, 0));

        app.modal(s(R.string.queue_title), null, box, btns);
        fill();
        close.post(close::requestFocus);
    }

    void close() {
        list = null;
        app.closeModal();
    }

    /** The queue changed: rebuild the rows if the sheet is open. */
    void refresh() {
        if (list == null) return;
        if (!list.isAttachedToWindow()) { list = null; return; }
        fill();
    }

    void tick(Queue.Op op) {
        if (list != null) tick(list, op);
    }

    private void fill() {
        list.removeAllViews();
        List<Queue.Op> ops = app.queue.snapshot();
        clear.setVisibility(app.queue.finishedCount() > 0 ? View.VISIBLE : View.GONE);
        if (ops.isEmpty()) {
            LinearLayout empty = Ui.vbox(app);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(8), Ui.dp(18), Ui.dp(8), Ui.dp(22));
            empty.addView(Ui.icon(app, R.drawable.ic_check, Ui.GREEN, 28), Ui.lp(Ui.dp(28), Ui.dp(28)));
            TextView t = Ui.text(app, s(R.string.queue_empty), 15, Ui.TEXT, Ui.BOLD);
            t.setGravity(Gravity.CENTER);
            empty.addView(t, Ui.margins(Ui.lp(-2, -2), 0, 10, 0, 4));
            TextView m = Ui.text(app, s(R.string.queue_empty_text), 13, Ui.TEXT2, Ui.REGULAR);
            m.setGravity(Gravity.CENTER);
            empty.addView(m, Ui.lp(-2, -2));
            list.addView(empty, Ui.lp(-1, -2));
            return;
        }
        for (Queue.Op op : ops) list.addView(opRow(op), Ui.margins(Ui.lp(-1, -2), 0, 0, 0, 8));
    }

    private View opRow(final Queue.Op op) {
        boolean rm = op.kind == Queue.Kind.REMOVE;
        LinearLayout row = Ui.hbox(app);
        row.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        row.setMinimumHeight(Ui.dp(56));
        GradientDrawable bg = rm && op.active() ? Ui.round(Ui.RED_SOFT, 14, LibraryScreen.alpha(Ui.RED, 0x80), 1f) : Ui.round(Ui.SURFACE2, 14, Ui.STROKE, 1f);
        row.setBackground(bg);

        ImageView av = avatar(op.iconUrl, op.name, 36);
        if (op.finished()) av.setAlpha(0.6f);
        row.addView(av);

        LinearLayout mid = Ui.vbox(app);
        TextView title = Ui.oneLine(Ui.text(app, op.name, 14, op.finished() ? Ui.TEXT2 : rm ? Ui.RED : Ui.TEXT, Ui.MEDIUM));
        mid.addView(title, Ui.lp(-1, -2));
        if (op.active()) {
            queueLines(mid, op);
        } else {
            String line = kindName(op.kind) + (op.modules > 0 ? "  ·  " + q(R.plurals.modules_count, op.modules + 1) : "");
            TextView st = Ui.text(app, op.state == Queue.State.FAILED && op.error != null ? line + "  ·  " + op.error : line, 11.5f,
                    op.state == Queue.State.FAILED ? Ui.RED : Ui.TEXT3, Ui.REGULAR);
            st.setMaxLines(3);
            st.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(st, Ui.margins(Ui.lp(-1, -2), 0, 3, 0, 0));
        }
        row.addView(mid, Ui.margins(Ui.lpw(0, -2, 1), 12, 0, 8, 0));

        switch (op.state) {
            case WAITING: {
                row.addView(queuePill(op));
                String label = s(R.string.queue_cancel_one, op.name);
                LinearLayout x = Ui.button(app, null, R.drawable.ic_close, Ui.Kind.GHOST, v -> {
                    if (app.queue.cancel(op)) app.manager.queueChanged();
                });
                x.setPadding(0, 0, 0, 0);
                x.setMinimumHeight(0);
                x.setContentDescription(label);
                x.setTooltipText(label);
                row.addView(x, Ui.margins(Ui.lp(Ui.dp(36), Ui.dp(36)), 6, 0, 0, 0));
                Screen.pulse(row, 0.6f, 1f);
                break;
            }
            case RUNNING:
                row.addView(queuePill(op));
                break;
            case DONE:
                row.addView(LibraryScreen.iconPill(app, s(R.string.queue_done), R.drawable.ic_check, Ui.GREEN, Ui.GREEN_SOFT));
                break;
            case FAILED:
                row.addView(Ui.pill(app, s(R.string.queue_failed), Ui.RED, Ui.RED_SOFT));
                break;
            default:
                row.addView(Ui.pill(app, s(R.string.queue_cancelled), Ui.TEXT2, Ui.SURFACE3));
        }
        return row;
    }

    /** A frame that never grows past a height, so a long queue scrolls inside the card. */
    private static final class MaxHeight extends FrameLayout {
        private final int max;

        MaxHeight(Context c, int max) { super(c); this.max = max; }

        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }
}
