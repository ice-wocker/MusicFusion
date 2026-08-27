package com.musicfusion.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ImageCache — 内存 LRU + 磁盘缓存 (专辑封面/电台图标/歌手头像)
 * 内存: 8MB LruCache
 * 磁盘: 50MB filesDir/image_cache, LRU 清理 */
public final class ImageCache {
    private static final String TAG = "ImageCache";
    private static final long MAX_DISK_SIZE = 50 * 1024 * 1024L; // 50MB
    private static final long MAX_FILE_SIZE = 500 * 1024L;       // 500KB per file
    private static final int CONNECTION_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 15000;
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static ImageCache INSTANCE;
    private final LruCache<String, Bitmap> memoryCache;
    private final File cacheDir;

    public static synchronized ImageCache get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new ImageCache(ctx.getApplicationContext());
        return INSTANCE;
    }

    private ImageCache(Context ctx) {
        memoryCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        cacheDir = new File(ctx.getFilesDir(), "image_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        cleanupDisk();
    }

    /** 获取位图 (内存 → 磁盘 → 网络), callback 在主线程 */
    public void get(String url, Callback cb) {
        if (url == null || url.isEmpty()) { cb.onResult(null); return; }
        String key = hashKey(url);

        Bitmap bmp = memoryCache.get(key);
        if (bmp != null && !bmp.isRecycled()) { cb.onResult(bmp); return; }

        // 磁盘
        File f = new File(cacheDir, key);
        if (f.exists()) {
            Bitmap disk = decodeFile(f);
            if (disk != null) {
                memoryCache.put(key, disk);
                cb.onResult(disk);
                return;
            }
        }

        // 网络
        cb.onLoading();
        EXEC.execute(new Runnable() { public void run() {
            try {
                Bitmap net = download(url);
                if (net != null) {
                    memoryCache.put(key, net);
                    FileOutputStream fos = new FileOutputStream(f);
                    net.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                    fos.close();
                    cb.onResult(net);
                } else cb.onError("download failed");
            } catch (Exception e) {
                Log.w(TAG, "get " + url, e);
                cb.onError(e.getMessage());
            }
        }
        });
    }

    /** 同步获取 (用于通知/Widget 等) */
    public Bitmap getSync(String url) {
        if (url == null || url.isEmpty()) return null;
        String key = hashKey(url);
        Bitmap bmp = memoryCache.get(key);
        if (bmp != null && !bmp.isRecycled()) return bmp;
        File f = new File(cacheDir, key);
        if (f.exists()) {
            Bitmap disk = decodeFile(f);
            if (disk != null) { memoryCache.put(key, disk); return disk; }
        }
        return null;
    }

    private Bitmap decodeFile(File f) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            int sample = 1;
            while (opts.outWidth / sample > 512 || opts.outHeight / sample > 512) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Throwable t) { return null; }
    }

    private Bitmap download(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "MusicFusion/12.0");
        int code = conn.getResponseCode();
        if (code >= 400) return null;
        InputStream is = conn.getInputStream();
        // 限制下载大小
        File tmp = new File(cacheDir, "tmp_" + System.currentTimeMillis());
        OutputStream os = new FileOutputStream(tmp);
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = is.read(buf)) > 0) {
            total += n;
            if (total > MAX_FILE_SIZE) { os.close(); tmp.delete(); return null; }
            os.write(buf, 0, n);
        }
        os.close(); is.close();
        Bitmap bmp = decodeFile(tmp);
        tmp.delete();
        return bmp;
    }

    private void cleanupDisk() {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= MAX_DISK_SIZE) return;
        // 按 mtime 排序, 删除最旧直到 < 80%
        java.util.Arrays.sort(files, new java.util.Comparator<File>() { public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); } });
        long target = (long) (MAX_DISK_SIZE * 0.8);
        for (File f : files) {
            if (total <= target) break;
            total -= f.length();
            f.delete();
        }
    }

    public void clearMemory() { memoryCache.evictAll(); }

    public void clearAll() {
        clearMemory();
        File[] files = cacheDir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    public String stats() {
        long diskSize = 0;
        File[] files = cacheDir.listFiles();
        if (files != null) for (File f : files) diskSize += f.length();
        return String.format(Locale.US, "Memory: %d MB / Disk: %d KB / Files: %d",
            memoryCache.size() / 1048576, diskSize / 1024, files != null ? files.length : 0);
    }

    private String hashKey(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    public interface Callback {
        default void onLoading() {}
        void onResult(Bitmap bitmap);
        default void onError(String error) {}
    }
}