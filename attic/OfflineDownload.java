package com.musicfusion.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** OfflineDownload v13.0 — 离线下载 + 管理
 * 用 Android DownloadManager (系统服务) 下载流媒体到本地
 * - 进度回调 (DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR / TOTAL)
 * - 状态跟踪 (pending / running / paused / completed / failed)
 * - 列表查询 + 删除
 * - 持久化记录 (SharedPreferences 存已下载列表)
 *
 * 存储路径: /storage/emulated/0/Download/MusicFusion/{id}.mp3
 * 或      : /storage/emulated/0/Music/MusicFusion/{id}.mp3 (Android 10+ 优先)
 *
 * 接入 MainActivity:
 *   1. UI 加 "下载" 按钮 (context menu 加)
 *   2. onComplete 后扫描 MediaStore 让系统音乐 app 也能播放
 *   3. 播放在线/本地: PlayerService 检测 URI scheme 走 MediaPlayer.setDataSource
 */
public class OfflineDownload {
    private static final String TAG = "OfflineDownload";
    private static final String PREF = "mf_downloads";
    private static final String DL_SUBDIR = "MusicFusion";

    /** 开始下载一首
     * @param ctx Context
     * @param url 流地址 (http/https)
     * @param title 显示用标题
     * @param artist 作者 (用于显示)
     * @return long downloadId (DownloadManager 唯一 ID) */
    public static long start(Context ctx, String url, String title, String artist) {
        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            Log.w(TAG, "skip non-http url: " + url);
            return -1;
        }
        try {
            // 保存到 Download/MusicFusion/<title>.mp3
            File dlDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), DL_SUBDIR);
            if (!dlDir.exists()) dlDir.mkdirs();
            String safe = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safe.length() > 50) safe = safe.substring(0, 50);
            String filename = safe + ".mp3";

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url))
                .setTitle(title)
                .setDescription("MusicFusion · " + (artist == null ? "" : artist))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    DL_SUBDIR + "/" + filename);
            long id = downloadManager(ctx).enqueue(req);
            save(ctx, id, title, artist, url, filename);
            Log.d(TAG, "enqueued " + id + " " + filename);
            return id;
        } catch (Exception e) {
            Log.e(TAG, "start failed: " + e.getMessage());
            return -1;
        }
    }

    /** 已下载列表 */
    public static class Downloaded {
        public long id;
        public String title, artist, url, file;
        public int status;     // 0=pending 1=running 2=paused 3=complete 4=failed
        public long total, sofar;
    }

    public static List<Downloaded> list(Context ctx) {
        List<Downloaded> out = new ArrayList<Downloaded>();
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        for (String key : sp.getAll().keySet()) {
            if (!key.startsWith("dl_")) continue;
            String v = sp.getString(key, "");
            try {
                // dl_id -> title|artist|url|file
                String[] p = v.split("\\|", -1);
                if (p.length < 4) continue;
                long did = Long.parseLong(key.substring(3));
                Downloaded d = new Downloaded();
                d.id = did;
                d.title = p[0];
                d.artist = p[1];
                d.url = p[2];
                d.file = p[3];
                out.add(d);
            } catch (Exception e) {}
        }
        return out;
    }

    public static void remove(Context ctx, long id) {
        try { downloadManager(ctx).remove(id); } catch (Exception e) {}
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .remove("dl_" + id).apply();
    }

    /** 查询真实下载状态 (从 DownloadManager 查) */
    public static int queryStatus(Context ctx, long id) {
        DownloadManager dm = downloadManager(ctx);
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (idx >= 0) return c.getInt(idx);
            }
        } catch (Exception e) {}
        return -1;
    }

    /** 本地文件路径 (已下载的) - 返回绝对路径 或 null */
    public static String localPath(Context ctx, long id) {
        DownloadManager dm = downloadManager(ctx);
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                if (idx >= 0) {
                    String uri = c.getString(idx);
                    if (uri != null && uri.startsWith("file://")) {
                        return Uri.parse(uri).getPath();
                    }
                    if (uri != null && uri.startsWith("content://")) {
                        return uri;  // MediaStore URI
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }

    static void save(Context ctx, long id, String title, String artist, String url, String filename) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("dl_" + id, title + "|" + artist + "|" + url + "|" + filename)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "save: " + e.getMessage());
        }
    }

    static DownloadManager downloadManager(Context ctx) {
        return (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
    }
}
