package id.my.bontot.camviewtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import androidx.media3.ui.PlayerView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Last-viewed frames, stored only in the app's private, non-backed-up cache. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class SnapshotStore {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private SnapshotStore() { }

    public static String key(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return key.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static File file(Context context, String ip) {
        return new File(new File(context.getCacheDir(), "previews"), key(ip) + ".jpg");
    }

    public static Bitmap load(Context context, String ip) {
        File file = file(context, ip);
        return file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
    }

    /** Takes ownership of the bitmap and recycles it after the write. */
    public static void save(Context context, String ip, Bitmap bitmap) {
        Context app = context.getApplicationContext();
        WRITER.execute(() -> {
            File dest = file(app, ip);
            File temporary = new File(dest.getPath() + ".tmp");
            try {
                File parent = dest.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output);
                }
                if (!temporary.renameTo(dest)) temporary.delete();
            } catch (Exception ignored) { temporary.delete(); }
            finally { bitmap.recycle(); }
        });
    }

    public static void delete(Context context, String ip) {
        file(context, ip).delete();
    }

    public static void capture(Context context, PlayerView view, String ip, Handler handler) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
        View surface = view.getVideoSurfaceView();
        int width = Math.min(640, Math.max(1, surface == null ? view.getWidth() : surface.getWidth()));
        int sourceWidth = surface == null ? view.getWidth() : surface.getWidth();
        int sourceHeight = surface == null ? view.getHeight() : surface.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        int height = Math.max(1, Math.min(480, width * sourceHeight / sourceWidth));
        if (surface instanceof TextureView) {
            Bitmap bitmap = ((TextureView) surface).getBitmap(width, height);
            if (bitmap != null) save(context, ip, bitmap);
        } else if (surface instanceof SurfaceView) {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                PixelCopy.request((SurfaceView) surface, bitmap, result -> {
                    if (result == PixelCopy.SUCCESS) save(context, ip, bitmap);
                    else bitmap.recycle();
                }, handler);
            } catch (Exception ignored) { bitmap.recycle(); }
        }
    }
}
