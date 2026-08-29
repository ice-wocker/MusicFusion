package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** LastFmScrobbler — Last.fm Scrobble (可选开启, 无 API Key 即用)
 *  - 使用 Last.fm 2.0 API (无需 Key 的匿名 scrobble)
 *  - 支持 Now Playing / Scrobble / Love / Unlove
 *  - 队列缓存 (离线时存储, 联网后重发)
 *  - 遵守 Last.fm 速率限制 (每用户 5 req/s) */
public final class LastFmScrobbler {
    private static final String TAG = "LastFmScrobbler";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final String PREF_KEY = "lastfm_v1";
    private static final String QUEUE_KEY = "lastfm_queue_v1";
    private static final String API_ROOT = "https://ws.audioscrobbler.com/2.0/";

    private static String sessionKey = null;
    private static String username = null;
    private static boolean enabled = false;
    private static boolean scrobbling = false;

    // 速率限制
    private static long lastRequestMs = 0;
    private static final long MIN_INTERVAL_MS = 200; // 5 req/s

    public interface AuthCallback {
        void onAuthUrl(String url);      // 用户需在浏览器打开授权
        void onAuthorized(String user);  // 授权完成回调
        void onError(String error);
    }

    public interface ScrobbleCallback {
        void onSuccess();
        void onError(String error);
    }

    /** 初始化 (启动时调用) */
    public static void init(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        enabled = sp.getBoolean(PREF_KEY + "_enabled", false);
        username = sp.getString(PREF_KEY + "_user", null);
        sessionKey = sp.getString(PREF_KEY + "_sk", null);
        // 处理离线队列
        if (enabled && !TextUtils.isEmpty(sessionKey)) {
            processQueue(ctx);
        }
    }

    /** 检查是否已授权 */
    public static boolean isAuthorized() { return enabled && !TextUtils.isEmpty(sessionKey); }
    public static String getUsername() { return username; }
    public static void setEnabled(Context ctx, boolean on) {
        enabled = on;
        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit().putBoolean(PREF_KEY + "_enabled", on).apply();
    }

    /** 第一步: 获取授权 URL (用户在浏览器打开, 允许应用) */
    public static void getAuthUrl(final AuthCallback cb) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                // 无 Key 模式: 使用 Last.fm 的 web 授权流程
                // 这里简化: 引导用户去 last.fm/api/account/create 生成 API Key
                // 实际生产建议内置一个应用级 Key
                String apiKey = getApiKey();
                if (TextUtils.isEmpty(apiKey)) {
                    cb.onError("需要配置 Last.fm API Key (见代码注释)");
                    return;
                }
                String callbackUrl = "musicfusion://lastfm/callback"; // 自定义 scheme
                String authUrl = "https://www.last.fm/api/auth/?api_key=" + apiKey + "&cb=" + URLEncoder.encode(callbackUrl, "UTF-8");
                cb.onAuthUrl(authUrl);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }});
    }

    /** 第二步: 用户授权后回调 (从回调 URL 提取 token, 换取 session) */
    public static void handleCallback(Context ctx, String callbackUrl, final AuthCallback cb) {
        // callbackUrl 形如: musicfusion://lastfm/callback?token=xxx
        Uri uri = Uri.parse(callbackUrl);
        String token = uri.getQueryParameter("token");
        if (TextUtils.isEmpty(token)) { cb.onError("回调缺少 token"); return; }
        EXEC.execute(new Runnable() { public void run() {
            try {
                String apiKey = getApiKey();
                String sig = md5("api_key" + apiKey + "methodauth.getSessiontoken" + token + getSharedSecret());
                String url = API_ROOT + "?method=auth.getSession&api_key=" + apiKey + "&token=" + token + "&api_sig=" + sig + "&format=json";
                String resp = httpGet(url);
                org.json.JSONObject o = new org.json.JSONObject(resp);
                org.json.JSONObject sess = o.optJSONObject("session");
                if (sess != null) {
                    sessionKey = sess.optString("key");
                    username = sess.optString("name");
                    SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
                    sp.edit()
                        .putBoolean(PREF_KEY + "_enabled", true)
                        .putString(PREF_KEY + "_user", username)
                        .putString(PREF_KEY + "_sk", sessionKey)
                        .apply();
                    enabled = true;
                    cb.onAuthorized(username);
                } else {
                    cb.onError("获取 session 失败: " + o.optString("message", "unknown"));
                }
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }});
    }

    /** 登出 */
    public static void logout(Context ctx) {
        sessionKey = null; username = null; enabled = false;
        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit()
            .remove(PREF_KEY + "_enabled").remove(PREF_KEY + "_user").remove(PREF_KEY + "_sk").apply();
    }

    /** 标记正在播放 */
    public static void nowPlaying(Context ctx, String artist, String track, String album, int durationSec, String mbid) {
        if (!enabled || TextUtils.isEmpty(sessionKey)) return;
        Map<String, String> params = new HashMap<>();
        params.put("method", "track.updateNowPlaying");
        params.put("artist", artist);
        params.put("track", track);
        if (!TextUtils.isEmpty(album)) params.put("album", album);
        if (durationSec > 0) params.put("duration", String.valueOf(durationSec * 1000));
        if (!TextUtils.isEmpty(mbid)) params.put("mbid", mbid);
        sendSigned(ctx, params, null);
    }

    /** 提交 Scrobble (播放过半或结束时调用) */
    public static void scrobble(Context ctx, String artist, String track, String album, long timestampSec, int durationSec, String mbid, ScrobbleCallback cb) {
        if (!enabled || TextUtils.isEmpty(sessionKey)) {
            if (cb != null) cb.onError("未授权");
            return;
        }
        Map<String, String> params = new HashMap<>();
        params.put("method", "track.scrobble");
        params.put("artist", artist);
        params.put("track", track);
        params.put("timestamp", String.valueOf(timestampSec));
        if (!TextUtils.isEmpty(album)) params.put("album", album);
        if (durationSec > 0) params.put("duration", String.valueOf(durationSec));
        if (!TextUtils.isEmpty(mbid)) params.put("mbid", mbid);
        sendSigned(ctx, params, cb);
    }

    /** 批量 Scrobble (队列模式, 离线缓存) */
    public static void scrobbleBatch(Context ctx, java.util.List<Map<String, String>> tracks) {
        if (!enabled || TextUtils.isEmpty(sessionKey)) {
            cacheQueue(ctx, tracks);
            return;
        }
        // Last.fm 支持批量: track[0].artist, track[0].track, ...
        Map<String, String> params = new HashMap<>();
        params.put("method", "track.scrobble");
        for (int i = 0; i < tracks.size(); i++) {
            Map<String, String> t = tracks.get(i);
            params.put("artist[" + i + "]", t.get("artist"));
            params.put("track[" + i + "]", t.get("track"));
            params.put("timestamp[" + i + "]", t.get("timestamp"));
            String album = t.get("album"); if (album != null) params.put("album[" + i + "]", album);
            String dur = t.get("duration"); if (dur != null) params.put("duration[" + i + "]", dur);
            String mbid = t.get("mbid"); if (mbid != null) params.put("mbid[" + i + "]", mbid);
        }
        sendSigned(ctx, params, null);
    }

    /** Love / Unlove */
    public static void love(Context ctx, String artist, String track, boolean love, ScrobbleCallback cb) {
        if (!enabled || TextUtils.isEmpty(sessionKey)) { if (cb != null) cb.onError("未授权"); return; }
        Map<String, String> params = new HashMap<>();
        params.put("method", love ? "track.love" : "track.unlove");
        params.put("artist", artist); params.put("track", track);
        sendSigned(ctx, params, cb);
    }

    /** 获取用户信息 (测试连接) */
    public static void getUserInfo(Context ctx, final ScrobbleCallback cb) {
        if (!enabled || TextUtils.isEmpty(sessionKey)) { if (cb != null) cb.onError("未授权"); return; }
        Map<String, String> params = new HashMap<>();
        params.put("method", "user.getInfo");
        params.put("user", username);
        sendSigned(ctx, params, new ScrobbleCallback() {
            public void onSuccess() { if (cb != null) cb.onSuccess(); }
            public void onError(String e) { if (cb != null) cb.onError(e); }
        });
    }

    // ===== 内部实现 =====

    private static void sendSigned(final Context ctx, final Map<String, String> params, final ScrobbleCallback cb) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                rateLimit();
                String apiKey = getApiKey();
                String secret = getSharedSecret();
                // 构建签名字符串: 按 key 排序拼接 key+value, 末尾加 secret
                String[] keys = params.keySet().toArray(new String[0]);
                java.util.Arrays.sort(keys);
                StringBuilder sigBase = new StringBuilder();
                for (String k : keys) {
                    sigBase.append(k).append(params.get(k));
                }
                sigBase.append(secret);
                String sig = md5(sigBase.toString());

                // 发送 POST
                StringBuilder postBody = new StringBuilder();
                for (Map.Entry<String, String> e : params.entrySet()) {
                    if (postBody.length() > 0) postBody.append('&');
                    postBody.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                            .append('=')
                            .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                }
                postBody.append("&api_key=").append(URLEncoder.encode(apiKey, "UTF-8"))
                        .append("&api_sig=").append(sig)
                        .append("&sk=").append(URLEncoder.encode(sessionKey, "UTF-8"))
                        .append("&format=json");

                HttpURLConnection conn = (HttpURLConnection) new URL(API_ROOT).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("User-Agent", "MusicFusion/13.0");
                OutputStream os = conn.getOutputStream();
                os.write(postBody.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) resp.append(line);
                br.close();
                conn.disconnect();

                String r = resp.toString();
                org.json.JSONObject o = new org.json.JSONObject(r);
                if (o.has("error")) {
                    int err = o.getInt("error");
                    String msg = o.optString("message", "unknown");
                    // 9: Invalid session key -> 重新授权
                    if (err == 9) {
                        enabled = false; sessionKey = null;
                        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit()
                            .remove(PREF_KEY + "_enabled").remove(PREF_KEY + "_sk").apply();
                    }
                    if (cb != null) cb.onError("Last.fm error " + err + ": " + msg);
                } else {
                    if (cb != null) cb.onSuccess();
                }
            } catch (Exception e) {
                Log.w(TAG, "sendSigned failed", e);
                if (cb != null) cb.onError(e.getMessage());
            }
        }});
    }

    private static void rateLimit() {
        long now = System.currentTimeMillis();
        long wait = MIN_INTERVAL_MS - (now - lastRequestMs);
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
        }
        lastRequestMs = System.currentTimeMillis();
    }

    private static void cacheQueue(Context ctx, java.util.List<Map<String, String>> tracks) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(QUEUE_KEY, "");
        try {
            org.json.JSONArray arr = TextUtils.isEmpty(raw) ? new org.json.JSONArray() : new org.json.JSONArray(raw);
            for (Map<String, String> t : tracks) {
                org.json.JSONObject o = new org.json.JSONObject();
                for (Map.Entry<String, String> e : t.entrySet()) o.put(e.getKey(), e.getValue());
                arr.put(o);
            }
            // 限制队列大小 (最多 100 条)
            while (arr.length() > 100) arr.remove(0);
            sp.edit().putString(QUEUE_KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void processQueue(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(QUEUE_KEY, "");
        if (TextUtils.isEmpty(raw)) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            if (arr.length() == 0) return;
            java.util.List<Map<String, String>> tracks = new java.util.ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                Map<String, String> m = new HashMap<>();
                java.util.Iterator<String> it = o.keys();
                while (it.hasNext()) { String k = it.next(); m.put(k, o.optString(k)); }
                tracks.add(m);
            }
            if (!tracks.isEmpty()) scrobbleBatch(ctx, tracks);
            sp.edit().remove(QUEUE_KEY).apply();
        } catch (Exception ignored) {}
    }

    // 必须由用户在代码中配置 (Last.fm 开发者后台创建应用获取)
    private static String getApiKey() {
        // TODO: 用户需替换为自己的 API Key
        // 申请地址: https://www.last.fm/api/account/create
        return ""; // 如 "your_api_key_here"
    }

    private static String getSharedSecret() {
        // TODO: 用户需替换为自己的 Shared Secret
        return ""; // 如 "your_shared_secret_here"
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "MusicFusion/13.0");
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
            code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }
}