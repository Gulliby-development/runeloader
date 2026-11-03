package app.wowforever.addons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny async image loader with memory + disk cache. */
final class ImageLoader {
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static File dir;

    /** An addon logo: a letter icon until the image arrives, or for good if it fails. */
    static void load(final ImageView iv, final String url, final String nameForLetters) {
        iv.setImageDrawable(new Ui.LetterDrawable(nameForLetters));
        fetch(iv, url, 128, true);
    }

    /** A screenshot, decoded to about maxPx on its short side and shown as is. */
    static void loadShot(final ImageView iv, final String url, int maxPx) {
        iv.setImageDrawable(null);
        fetch(iv, url, maxPx, false);
    }

    private static void fetch(final ImageView iv, final String url, final int minSide, final boolean round) {
        iv.setTag(url);
        final String memKey = url + "#" + minSide;
        Bitmap cached = url == null ? null : MEM.get(memKey);
        if (cached != null) {
            iv.setImageDrawable(round ? new Ui.RoundBitmap(cached) : new android.graphics.drawable.BitmapDrawable(iv.getResources(), cached));
            return;
        }
        if (url == null || url.isEmpty() || dir == null) return;
        POOL.execute(() -> {
            Bitmap b = null;
            try {
                File f = new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length() + ".img");
                if (!f.exists() || f.length() == 0) {
                    File tmp = new File(f.getPath() + ".part");
                    Http.download(url, null, tmp, null);
                    tmp.renameTo(f);
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= minSide && o.outHeight / (sample * 2) >= minSide) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                b = BitmapFactory.decodeFile(f.getPath(), o);
                if (b == null) f.delete();
            } catch (Exception ignored) {
            }
            final Bitmap bmp = b;
            if (bmp == null) return;
            MEM.put(memKey, bmp);
            MAIN.post(() -> {
                if (url.equals(iv.getTag())) iv.setImageDrawable(round ? new Ui.RoundBitmap(bmp) : new android.graphics.drawable.BitmapDrawable(iv.getResources(), bmp));
            });
        });
    }

    private ImageLoader() {}
}
