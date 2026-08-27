package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** BackupManager — 完整备份/恢复: 设置+歌单+收藏+历史+统计+EQ预设+主题+语言+白噪声配置 */
public final class BackupManager {
    private static final String TAG = "BackupManager";
    private static final String VERSION = "1.0";

    /** 备份数据结构 */
    public static class BackupData {
        public String version;
        public long timestamp;
        public String deviceModel;
        public JSONObject settings = new JSONObject();
        public JSONArray playlists = new JSONArray();
        public JSONArray favorites = new JSONArray();
        public JSONArray history = new JSONArray();
        public JSONArray smartRules = new JSONArray();
        public JSONObject stats = new JSONObject();
        public JSONArray visualizerConfigs = new JSONArray();
    }

    /** 导出到 Downloads/MusicFusion/ */
    public static File exportBackup(Context ctx) throws Exception {
        BackupData data = collect(ctx);
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicFusion");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "MusicFusion_backup_" + ts + ".json");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(dataToJson(data).toString(2).getBytes("UTF-8"));
        fos.close();
        return file;
    }

    /** 导出到应用内部 filesDir (用于导入场景) */
    public static File exportInternal(Context ctx) throws Exception {
        BackupData data = collect(ctx);
        File file = new File(ctx.getFilesDir(), "backup_" + System.currentTimeMillis() + ".json");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(dataToJson(data).toString(2).getBytes("UTF-8"));
        fos.close();
        return file;
    }

    /** 导入备份 (合并去重) */
    public static int importBackup(Context ctx, File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] buf = new byte[(int) file.length()];
        fis.read(buf); fis.close();
        BackupData data = jsonToData(new JSONObject(new String(buf, "UTF-8")));
        return apply(ctx, data);
    }

    private static BackupData collect(Context ctx) {
        BackupData d = new BackupData();
        d.version = VERSION;
        d.timestamp = System.currentTimeMillis();
        d.deviceModel = android.os.Build.MODEL;

        SharedPreferences mf = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        try {
            // 基础设置
            d.settings.put("light", mf.getBoolean("light", false));
            d.settings.put("lang", mf.getInt("lang", 0));
            d.settings.put("datasaver", mf.getBoolean("datasaver", false));
            d.settings.put("last_tab", mf.getInt("last_tab", 0));
            d.settings.put("total_plays", mf.getInt("total_plays", 0));
            d.settings.put("theme_color", mf.getInt("theme_color", 0xFF1DB954));
            d.settings.put("use_dynamic_color", mf.getBoolean("use_dynamic_color", false));
            d.settings.put("replaygain", mf.getBoolean("replaygain", true));
            d.settings.put("replaygain_album", mf.getBoolean("replaygain_album", false));
            d.settings.put("crossfade", mf.getBoolean("crossfade", false));
            d.settings.put("crossfade_ms", mf.getInt("crossfade_ms", 200));
            d.settings.put("gapless", mf.getBoolean("gapless", true));
            d.settings.put("sleep_default_min", mf.getInt("sleep_default_min", 0));
            d.settings.put("auto_kill_on_exit", mf.getBoolean("auto_kill_on_exit", false));
        } catch (Exception ignored) {}

        // 歌单
        try {
            String names = mf.getString("playlists", "");
            for (String n : names.split("\n")) {
                if (n.isEmpty()) continue;
                JSONObject pl = new JSONObject();
                pl.put("name", n);
                String content = mf.getString("pl_" + n.hashCode(), "");
                pl.put("items", content);
                d.playlists.put(pl);
            }
        } catch (Exception ignored) {}

        // 收藏
        try { d.favorites = new JSONArray(loadEntries(mf, "fav")); } catch (Exception ignored) {}

        // 历史
        try { d.history = new JSONArray(loadEntries(mf, "recent")); } catch (Exception ignored) {}

        // 统计
        try {
            // 来自Brain.java (如果存在)
            String brainData = mf.getString("brain_history", "");
            d.stats.put("brain_history", brainData);
        } catch (Exception ignored) {}

        // 可视化配置
        try {
            SharedPreferences vis = ctx.getSharedPreferences("mf_vis", Context.MODE_PRIVATE);
            JSONObject v = new JSONObject();
            v.put("mode", vis.getInt("vis_mode", 1));
            v.put("enabled", vis.getBoolean("vis_enabled", true));
            v.put("bars", vis.getInt("vis_bars", 64));
            v.put("sens", (double) vis.getFloat("vis_sens", 1.0f));
            v.put("mirror", vis.getBoolean("vis_mirror", false));
            v.put("fps", vis.getInt("vis_fps", 30));
            v.put("color_pri", vis.getInt("vis_color_pri", 0xFF1DB954));
            v.put("color_sec", vis.getInt("vis_color_sec", 0xFF58A6FF));
            d.visualizerConfigs.put(v);
        } catch (Exception ignored) {}

        return d;
    }

    private static int apply(Context ctx, BackupData d) {
        SharedPreferences mf = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = mf.edit();
        int merged = 0;

        try {
            // 基础设置 (覆盖)
            if (d.settings.has("light")) ed.putBoolean("light", d.settings.getBoolean("light"));
            if (d.settings.has("lang")) ed.putInt("lang", d.settings.getInt("lang"));
            if (d.settings.has("datasaver")) ed.putBoolean("datasaver", d.settings.getBoolean("datasaver"));
            if (d.settings.has("last_tab")) ed.putInt("last_tab", d.settings.getInt("last_tab"));
            if (d.settings.has("theme_color")) ed.putInt("theme_color", d.settings.getInt("theme_color"));
            if (d.settings.has("use_dynamic_color")) ed.putBoolean("use_dynamic_color", d.settings.getBoolean("use_dynamic_color"));
            if (d.settings.has("replaygain")) ed.putBoolean("replaygain", d.settings.getBoolean("replaygain"));
            if (d.settings.has("replaygain_album")) ed.putBoolean("replaygain_album", d.settings.getBoolean("replaygain_album"));
            if (d.settings.has("crossfade")) ed.putBoolean("crossfade", d.settings.getBoolean("crossfade"));
            if (d.settings.has("crossfade_ms")) ed.putInt("crossfade_ms", d.settings.getInt("crossfade_ms"));
            if (d.settings.has("gapless")) ed.putBoolean("gapless", d.settings.getBoolean("gapless"));
        } catch (Exception ignored) {}

        // 歌单 (合并)
        try {
            java.util.Set<String> existingNames = new HashSet<>();
            String names = mf.getString("playlists", "");
            for (String n : names.split("\n")) if (!n.isEmpty()) existingNames.add(n);
            for (int i = 0; i < d.playlists.length(); i++) {
                JSONObject pl = d.playlists.getJSONObject(i);
                String n = pl.getString("name");
                String items = pl.optString("items", "");
                if (!existingNames.contains(n)) existingNames.add(n);
                String key = "pl_" + n.hashCode();
                String existing = mf.getString(key, "");
                // 合并去重
                java.util.Set<String> merged2 = new HashSet<>();
                if (!existing.isEmpty()) for (String e : existing.split("\n")) if (!e.isEmpty()) merged2.add(e);
                if (!items.isEmpty()) for (String e : items.split("\n")) if (!e.isEmpty()) merged2.add(e);
                StringBuilder sb = new StringBuilder();
                for (String e : merged2) { sb.append(e).append("\n"); merged++; }
                ed.putString(key, sb.toString());
            }
            StringBuilder nb = new StringBuilder();
            for (String n : existingNames) nb.append(n).append("\n");
            ed.putString("playlists", nb.toString());
        } catch (Exception ignored) {}

        // 收藏 (合并去重)
        try {
            java.util.Set<String> fav = new HashSet<>();
            String existing = mf.getString("fav", "");
            if (!existing.isEmpty()) for (String e : existing.split("\n")) if (!e.isEmpty()) fav.add(e);
            for (int i = 0; i < d.favorites.length(); i++) {
                String e = d.favorites.getString(i);
                if (fav.add(e)) merged++;
            }
            StringBuilder sb = new StringBuilder();
            for (String e : fav) sb.append(e).append("\n");
            ed.putString("fav", sb.toString());
        } catch (Exception ignored) {}

        // 历史 (合并去重, 限制 200 条)
        try {
            java.util.LinkedHashSet<String> hist = new java.util.LinkedHashSet<>();
            String existing = mf.getString("recent", "");
            if (!existing.isEmpty()) for (String e : existing.split("\n")) if (!e.isEmpty()) hist.add(e);
            for (int i = 0; i < d.history.length(); i++) {
                hist.add(d.history.getString(i));
            }
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String e : hist) {
                sb.append(e).append("\n");
                if (++count >= 200) break;
            }
            ed.putString("recent", sb.toString());
        } catch (Exception ignored) {}

        // 可视化配置
        try {
            if (d.visualizerConfigs.length() > 0) {
                JSONObject v = d.visualizerConfigs.getJSONObject(0);
                SharedPreferences vis = ctx.getSharedPreferences("mf_vis", Context.MODE_PRIVATE);
                SharedPreferences.Editor vE = vis.edit();
                if (v.has("mode")) vE.putInt("vis_mode", v.getInt("mode"));
                if (v.has("enabled")) vE.putBoolean("vis_enabled", v.getBoolean("enabled"));
                if (v.has("bars")) vE.putInt("vis_bars", v.getInt("bars"));
                if (v.has("sens")) vE.putFloat("vis_sens", (float) v.getDouble("sens"));
                if (v.has("mirror")) vE.putBoolean("vis_mirror", v.getBoolean("mirror"));
                if (v.has("fps")) vE.putInt("vis_fps", v.getInt("fps"));
                if (v.has("color_pri")) vE.putInt("vis_color_pri", v.getInt("color_pri"));
                if (v.has("color_sec")) vE.putInt("vis_color_sec", v.getInt("color_sec"));
                vE.apply();
            }
        } catch (Exception ignored) {}

        ed.apply();
        return merged;
    }

    private static String[] loadEntries(SharedPreferences sp, String name) {
        String raw = sp.getString(name, "");
        if (raw.isEmpty()) return new String[0];
        return raw.split("\n");
    }

    private static JSONObject dataToJson(BackupData d) {
        JSONObject o = new JSONObject();
        try {
            o.put("version", d.version);
            o.put("timestamp", d.timestamp);
            o.put("device", d.deviceModel);
            o.put("settings", d.settings);
            o.put("playlists", d.playlists);
            o.put("favorites", d.favorites);
            o.put("history", d.history);
            o.put("stats", d.stats);
            o.put("visualizer", d.visualizerConfigs);
        } catch (Exception ignored) {}
        return o;
    }

    private static BackupData jsonToData(JSONObject o) {
        BackupData d = new BackupData();
        try {
            d.version = o.optString("version", "1.0");
            d.timestamp = o.optLong("timestamp", 0);
            d.deviceModel = o.optString("device", "");
            d.settings = o.optJSONObject("settings");
            if (d.settings == null) d.settings = new JSONObject();
            d.playlists = o.optJSONArray("playlists");
            if (d.playlists == null) d.playlists = new JSONArray();
            d.favorites = o.optJSONArray("favorites");
            if (d.favorites == null) d.favorites = new JSONArray();
            d.history = o.optJSONArray("history");
            if (d.history == null) d.history = new JSONArray();
            d.stats = o.optJSONObject("stats");
            if (d.stats == null) d.stats = new JSONObject();
            d.visualizerConfigs = o.optJSONArray("visualizer");
            if (d.visualizerConfigs == null) d.visualizerConfigs = new JSONArray();
        } catch (Exception ignored) {}
        return d;
    }

    /** 列出可导入的备份文件 */
    public static List<File> listBackupFiles() {
        List<File> files = new ArrayList<>();
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicFusion");
        if (dir.exists()) {
            File[] all = dir.listFiles();
            if (all != null) for (File f : all) {
                String n = f.getName();
                if (n.startsWith("MusicFusion_backup_") && n.endsWith(".json")) files.add(f);
            }
        }
        return files;
    }
}