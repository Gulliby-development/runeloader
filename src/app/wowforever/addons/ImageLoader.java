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

    static void load(final ImageView iv, final String url, final String nameForLetters) {
        iv.setTag(url);
        Bitmap cached = url == null ? null : MEM.get(url);
        if (cached != null) {
            iv.setImageDrawable(new Ui.RoundBitmap(cached));
            return;
        }
        iv.setImageDrawable(new Ui.LetterDrawable(nameForLetters));
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
                while (o.outWidth / (sample * 2) >= 128 && o.outHeight / (sample * 2) >= 128) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                b = BitmapFactory.decodeFile(f.getPath(), o);
                if (b == null) f.delete();
            } catch (Exception ignored) {
            }
            final Bitmap bmp = b;
            if (bmp == null) return;
            MEM.put(url, bmp);
            MAIN.post(() -> {
                if (url.equals(iv.getTag())) iv.setImageDrawable(new Ui.RoundBitmap(bmp));
            });
        });
    }

    private ImageLoader() {}
}
