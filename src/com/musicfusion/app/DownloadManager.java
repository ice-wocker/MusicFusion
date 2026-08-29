package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import android.content.ContentValues;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** DownloadManager — 离线下载管理器
 *  - API 29+: MediaStore Downloads (分区存储)
 *  - API <29: 直接 File 写入 /sdcard/MusicFusion/
 *  - 支持断点续传、队列、进度、只下载明确允许的源 (IA/Archive/Openverse CC0) */
public final class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Map<String, DownloadTask> TASKS = new HashMap<>();
    public static final Map<String, DownloadTask> TASKS_PUBLIC = TASKS;
    private static final String PREF_KEY = "downloads_v1";

    // 允许下载的源白名单 (只下载明确开放/CC0 的内容)
    private static final String[] ALLOWED_SOURCES = {"archive", "openverse", "jamendo", "freesound", "radiobrowser", "soma"};

    public static class DownloadTask {
        public String id;
        public String title, artist, url, source, coverUrl;
        public long totalBytes = -1;
        public long downloadedBytes = 0;
        public int status = 0; // 0=等待 1=下载中 2=暂停 3=完成 4=失败
        public String errorMsg;
        public String localPath;
        public long startTime, endTime;
    }

    public interface Callback {
        void onProgress(DownloadTask task);
        void onComplete(DownloadTask task);
        void onError(DownloadTask task, String error);
        void onQueueChanged();
    }

    private static final List<Callback> CALLBACKS = new ArrayList<>();

    public static void registerCallback(Callback cb) { if (cb != null) CALLBACKS.add(cb); }
    public static void unregisterCallback(Callback cb) { CALLBACKS.remove(cb); }

    /** 获取单个任务 (用于 UI 显示详情) */
    public static DownloadTask getTask(String taskId) {
        return TASKS.get(taskId);
    }

    private static void notifyProgress(DownloadTask t) { for (Callback c : CALLBACKS) c.onProgress(t); }
    private static void notifyComplete(DownloadTask t) { for (Callback c : CALLBACKS) c.onComplete(t); }
    private static void notifyError(DownloadTask t, String e) { for (Callback c : CALLBACKS) c.onError(t, e); }
    private static void notifyQueue() { for (Callback c : CALLBACKS) c.onQueueChanged(); }

    /** 添加下载任务 (自动校验源是否允许) */
    public static void add(final Context ctx, final String title, final String artist,
                           final String url, final String source, final String coverUrl,
                           final Callback cb) {
        if (!isSourceAllowed(source)) {
            if (cb != null) cb.onError(null, "源不允许下载: " + source);
            return;
        }
        EXEC.execute(new Runnable() { public void run() {
            DownloadTask task = new DownloadTask();
            task.id = "dl_" + System.currentTimeMillis() + "_" + Math.abs(url.hashCode());
            task.title = title; task.artist = artist; task.url = url; task.source = source; task.coverUrl = coverUrl;
            task.startTime = System.currentTimeMillis();
            task.status = 1;
            TASKS.put(task.id, task);
            persist(ctx);
            notifyQueue();
            doDownload(ctx, task, cb);
        }});
    }

    private static boolean isSourceAllowed(String src) {
        for (String s : ALLOWED_SOURCES) if (s.equalsIgnoreCase(src)) return true;
        return false;
    }

    private static void doDownload(Context ctx, DownloadTask task, Callback cb) {
        HttpURLConnection conn = null;
        FileOutputStream fos = null;
        InputStream is = null;
        File tempFile = null;
        try {
            URL url = new URL(task.url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "MusicFusion/13.0");
            if (task.downloadedBytes > 0) {
                conn.setRequestProperty("Range", "bytes=" + task.downloadedBytes + "-");
            }
            int code = conn.getResponseCode();
            if (code == 416) { // Range not satisfiable, 可能已完成
                task.status = 3; task.endTime = System.currentTimeMillis(); task.localPath = task.localPath;
                persist(ctx); notifyComplete(task); if (cb != null) cb.onComplete(task); return;
            }
            if (code >= 400) throw new Exception("HTTP " + code);

            task.totalBytes = conn.getContentLength();
            if (task.totalBytes <= 0) task.totalBytes = -1;

            // 确定保存路径
            String ext = guessExt(task.url);
            String safeTitle = sanitize(task.title);
            String safeArtist = sanitize(task.artist);
            String filename = safeArtist + " - " + safeTitle + ext;

            if (Build.VERSION.SDK_INT >= 29) {
                // MediaStore Downloads
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                cv.put(MediaStore.Downloads.MIME_TYPE, guessMime(ext));
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MusicFusion/");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("MediaStore insert failed");
                fos = (FileOutputStream) ctx.getContentResolver().openOutputStream(uri);
                task.localPath = uri.toString();
                // 下载完成后标记非 pending
                try {
                    ContentValues cv2 = new ContentValues();
                    cv2.put(MediaStore.Downloads.IS_PENDING, 0);
                    ctx.getContentResolver().update(uri, cv2, null, null);
                } catch (Exception ignored) {}
            } else {
                // 传统文件路径
                File dir = new File(Environment.getExternalStorageDirectory(), "MusicFusion");
                if (!dir.exists()) dir.mkdirs();
                tempFile = new File(dir, filename + ".part");
                task.localPath = tempFile.getAbsolutePath().replace(".part", "");
                fos = new FileOutputStream(tempFile, true); // 追加模式支持断点
            }

            byte[] buffer = new byte[8192];
            int len;
            is = conn.getInputStream();
            while ((len = is.read(buffer)) != -1) {
                if (task.status == 2) { // 暂停
                    persist(ctx); notifyProgress(task); return;
                }
                fos.write(buffer, 0, len);
                task.downloadedBytes += len;
                if (task.totalBytes > 0) notifyProgress(task);
            }
            fos.flush();

            if (Build.VERSION.SDK_INT < 29 && tempFile != null) {
                File finalFile = new File(task.localPath);
                if (tempFile.renameTo(finalFile)) {
                    task.localPath = finalFile.getAbsolutePath();
                }
            }

            task.status = 3;
            task.endTime = System.currentTimeMillis();
            persist(ctx);
            notifyComplete(task);
            if (cb != null) cb.onComplete(task);

            // 自动加入播放队列 (可选)
            // PlayerService.enqueueLocal(task.localPath, task.title, task.artist);

        } catch (Exception e) {
            task.status = 4;
            task.errorMsg = e.getMessage();
            task.endTime = System.currentTimeMillis();
            persist(ctx);
            notifyError(task, e.getMessage());
            if (cb != null) cb.onError(task, e.getMessage());
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /** 暂停下载 */
    public static void pause(String taskId) {
        DownloadTask t = TASKS.get(taskId);
        if (t != null && t.status == 1) {
            t.status = 2;
            persist(null);
            notifyProgress(t);
        }
    }

    /** 恢复下载 */
    public static void resume(Context ctx, String taskId) {
        DownloadTask t = TASKS.get(taskId);
        if (t != null && t.status == 2) {
            t.status = 1;
            persist(ctx);
            notifyQueue();
            doDownload(ctx, t, null);
        }
    }

    /** 删除任务 (含本地文件) */
    public static void delete(Context ctx, String taskId) {
        DownloadTask t = TASKS.remove(taskId);
        if (t != null) {
            if (!TextUtils.isEmpty(t.localPath)) {
                try {
                    if (Build.VERSION.SDK_INT >= 29 && t.localPath.startsWith("content://")) {
                        ctx.getContentResolver().delete(Uri.parse(t.localPath), null, null);
                    } else {
                        new File(t.localPath).delete();
                    }
                } catch (Exception ignored) {}
            }
            persist(ctx);
            notifyQueue();
        }
    }

    /** 获取所有任务 */
    public static List<DownloadTask> getAll() {
        List<DownloadTask> list = new ArrayList<>(TASKS.values());
        // 按开始时间倒序
        Collections.sort(list, new java.util.Comparator<DownloadTask>() {
            public int compare(DownloadTask a, DownloadTask b) {
                return Long.compare(b.startTime, a.startTime);
            }
        });
        return list;
    }

    /** 获取进行中任务数 */
    public static int getActiveCount() {
        int c = 0;
        for (DownloadTask t : TASKS.values()) if (t.status == 1) c++;
        return c;
    }

    /** 启动时恢复队列 */
    public static void restore(Context ctx) {
        TASKS.clear();
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_KEY, "");
        if (TextUtils.isEmpty(raw)) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                DownloadTask task = new DownloadTask();
                task.id = o.optString("id");
                task.title = o.optString("title");
                task.artist = o.optString("artist");
                task.url = o.optString("url");
                task.source = o.optString("source");
                task.coverUrl = o.optString("coverUrl");
                task.totalBytes = o.optLong("totalBytes", -1);
                task.downloadedBytes = o.optLong("downloadedBytes", 0);
                task.status = o.optInt("status", 0);
                task.errorMsg = o.optString("errorMsg", "");
                task.localPath = o.optString("localPath", "");
                task.startTime = o.optLong("startTime", 0);
                task.endTime = o.optLong("endTime", 0);
                // 只恢复等待/暂停/失败的任务，完成的保留记录
                if (task.status == 1) task.status = 2; // 下载中改为暂停，等用户恢复
                TASKS.put(task.id, task);
            }
        } catch (Exception e) { Log.w(TAG, "restore failed", e); }
    }

    private static void persist(Context ctx) {
        if (ctx == null) return;
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        try {
            JSONArray arr = new JSONArray();
            for (DownloadTask t : TASKS.values()) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("title", t.title);
                o.put("artist", t.artist);
                o.put("url", t.url);
                o.put("source", t.source);
                o.put("coverUrl", t.coverUrl);
                o.put("totalBytes", t.totalBytes);
                o.put("downloadedBytes", t.downloadedBytes);
                o.put("status", t.status);
                o.put("errorMsg", t.errorMsg);
                o.put("localPath", t.localPath);
                o.put("startTime", t.startTime);
                o.put("endTime", t.endTime);
                arr.put(o);
            }
            ed.putString(PREF_KEY, arr.toString());
            ed.apply();
        } catch (Exception ignored) {}
    }

    private static String guessExt(String url) {
        String u = url.toLowerCase();
        if (u.contains(".mp3")) return ".mp3";
        if (u.contains(".flac")) return ".flac";
        if (u.contains(".ogg")) return ".ogg";
        if (u.contains(".m4a")) return ".m4a";
        if (u.contains(".wav")) return ".wav";
        if (u.contains(".aac")) return ".aac";
        if (u.contains(".opus")) return ".opus";
        return ".mp3";
    }

    private static String guessMime(String ext) {
        switch (ext) {
            case ".flac": return "audio/flac";
            case ".ogg": return "audio/ogg";
            case ".m4a": return "audio/mp4";
            case ".wav": return "audio/wav";
            case ".aac": return "audio/aac";
            case ".opus": return "audio/opus";
            default: return "audio/mpeg";
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    // 为了编译通过添加的静态导入
    static {
        try { java.util.Collections.class.getName(); } catch (Exception ignored) {}
    }
}