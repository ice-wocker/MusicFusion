package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** CrashReporter v2 — JSON结构化崩溃日志 + 启动上传/展示 */
public class CrashReporter {
    private static final String TAG = "CrashReporter";
    private static final String PREFS = "mf_crash";
    private static final String KEY_COUNT = "crash_count";
    private static final String KEY_LAST_UPLOAD = "last_upload";
    private static final int MAX_CRASH_FILES = 20;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    public static void install(Context ctx) {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            private final Thread.UncaughtExceptionHandler sys = Thread.getDefaultUncaughtExceptionHandler();
            @Override public void uncaughtException(Thread t, Throwable e) {
                try { writeCrash(ctx, t, e); } catch (Throwable ignored) {}
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                if (sys != null) sys.uncaughtException(t, e);
            }
        });
    }

    public static void writeCrash(Context ctx, Thread thread, Throwable e) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                File dir = new File(ctx.getFilesDir(), "crashes");
                if (!dir.exists()) dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null && old.length >= MAX_CRASH_FILES) {
                    java.util.Arrays.sort(old, new java.util.Comparator<File>() {
                        public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
                    });
                    for (int i = 0; i < old.length - MAX_CRASH_FILES + 1; i++) old[i].delete();
                }
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File f = new File(dir, "crash_" + ts + ".json");
                JSONObject json = buildCrashJson(ctx, thread, e);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(json.toString(2).getBytes("UTF-8"));
                fos.close();
                SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                sp.edit().putInt(KEY_COUNT, sp.getInt(KEY_COUNT, 0) + 1).apply();
            } catch (Throwable ignored) {}
        }});
    }

    private static JSONObject buildCrashJson(Context ctx, Thread thread, Throwable e) {
        JSONObject o = new JSONObject();
        try {
            o.put("timestamp", System.currentTimeMillis());
            o.put("iso_time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date()));
            o.put("thread", thread != null ? thread.getName() : "unknown");
            o.put("error_class", e.getClass().getName());
            o.put("error_message", e.getMessage() != null ? e.getMessage() : "");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            o.put("stack_trace", sw.toString());
            // 设备信息
            JSONObject dev = new JSONObject();
            dev.put("model", Build.MODEL);
            dev.put("brand", Build.BRAND);
            dev.put("device", Build.DEVICE);
            dev.put("android_version", Build.VERSION.RELEASE);
            dev.put("sdk_int", Build.VERSION.SDK_INT);
            dev.put("fingerprint", Build.FINGERPRINT);
            o.put("device", dev);
            // 应用信息
            JSONObject app = new JSONObject();
            try {
                app.put("version_code", ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode);
                app.put("version_name", ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName);
            } catch (Exception ignored) {}
            o.put("app", app);
            // 内存/存储
            JSONObject mem = new JSONObject();
            Runtime rt = Runtime.getRuntime();
            mem.put("free_mb", rt.freeMemory() / 1048576);
            mem.put("total_mb", rt.totalMemory() / 1048576);
            mem.put("max_mb", rt.maxMemory() / 1048576);
            File dataDir = Environment.getDataDirectory();
            if (dataDir != null) {
                mem.put("storage_free_mb", dataDir.getFreeSpace() / 1048576);
                mem.put("storage_total_mb", dataDir.getTotalSpace() / 1048576);
            }
            o.put("memory", mem);
        } catch (Exception ignored) {}
        return o;
    }

    /** 启动时检查未上报崩溃，返回JSON数组供UI展示 */
    public static JSONArray getUnreportedCrashes(Context ctx) {
        JSONArray arr = new JSONArray();
        File dir = new File(ctx.getFilesDir(), "crashes");
        if (!dir.exists()) return arr;
        File[] allFiles = dir.listFiles();
        if (allFiles == null) return arr;
        java.util.List<File> list = new java.util.ArrayList<File>();
        for (File f : allFiles) {
            String n = f.getName();
            if (n.startsWith("crash_") && n.endsWith(".json")) list.add(f);
        }
        File[] files = list.toArray(new File[0]);
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        for (File f : files) {
            try {
                java.nio.file.Path p = f.toPath();
                String content = new String(java.nio.file.Files.readAllBytes(p), "UTF-8");
                arr.put(new JSONObject(content));
            } catch (Exception ignored) {}
        }
        return arr;
    }

    public static void clearCrashes(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "crashes");
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COUNT, 0).apply();
    }
}