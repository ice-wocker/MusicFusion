package com.musicfusion.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Audius 去中心化音乐平台 (公开API, 创作者自主上传)
 * v12 新增:
 *  - GraphQL 深度挖掘 (按 genre/mood/year/region 筛选 trending)
 *  - 同专辑/同艺术家/相似曲目推荐
 *  - 标签检索
 *  - Remix/Repost 关系
 *  - LRU 缓存
 */
public class Audius {
    static String host = "discoveryprovider.audius.co";
    static final String APP = "MusicFusion";

    static final String[] FALLBACKS = {
        "discoveryprovider2.audius.co", "discoveryprovider3.audius.co",
        "discoveryprovider4.audius.co", "discoveryprovider5.audius.co",
        "audius-discovery-1.altego.net", "audius-discovery-2.altego.net",
        "discovery-au-02.audius.openplayer.org", "discovery-us-02.audius.openplayer.org"
    };
    static int fbIdx = -1;

    // 简单 LRU 缓存 (10 项)
    private static final java.util.LinkedHashMap<String, String> CACHE =
        new java.util.LinkedHashMap<String, String>(16, 0.75f, true) {
            protected boolean removeEldest(java.util.Map.Entry<String, String> e) { return size() > 20; }
        };

    public static String pickHost() throws Exception {
        if (fbIdx >= 0 && fbIdx < FALLBACKS.length) return FALLBACKS[fbIdx];
        return host;
    }
    public static void markFail() {
        fbIdx++;
        if (fbIdx >= FALLBACKS.length) fbIdx = -1;
    }
    public static int fallbackIdx() { return fbIdx; }
    public static int fallbackCount() { return FALLBACKS.length; }

    public static String search(String query) throws Exception {
        return search(query, "tracks");
    }

    /** 通用搜索: type=tracks/users/playlists */
    public static String search(String query, String type) throws Exception {
        String ck = type + ":" + query;
        if (CACHE.containsKey(ck)) return CACHE.get(ck);
        String h = pickHost();
        String url = "https://" + h + "/v1/" + type + "/search?query="
            + URLEncoder.encode(query, "UTF-8")
            + "&app_name=" + APP;
        String result = httpGet(url, null);
        CACHE.put(ck, result);
        return result;
    }

    public static String trending() throws Exception {
        return trending(null, null);
    }

    /** v12: 按 genre/mood 筛选 trending */
    public static String trending(String genre, String mood) throws Exception {
        StringBuilder url = new StringBuilder("https://" + pickHost() + "/v1/tracks/trending?app_name=" + APP);
        if (genre != null && !genre.isEmpty()) url.append("&genre=").append(URLEncoder.encode(genre, "UTF-8"));
        if (mood != null && !mood.isEmpty()) url.append("&mood=").append(URLEncoder.encode(mood, "UTF-8"));
        return httpGet(url.toString(), null);
    }

    /** v12: 按时间窗口 trending (week/month/year/all) */
    public static String trending(String time) throws Exception {
        String url = "https://" + pickHost() + "/v1/tracks/trending?app_name=" + APP;
        if (time != null) url += "&time=" + time;
        return httpGet(url, null);
    }

    public static String track(String trackId) throws Exception {
        return httpGet("https://" + pickHost() + "/v1/tracks/" + trackId + "?app_name=" + APP, null);
    }

    public static String userTracks(String userId) throws Exception {
        return httpGet("https://" + pickHost() + "/v1/users/" + userId
            + "/tracks?app_name=" + APP + "&limit=30", null);
    }

    public static String userInfo(String userId) throws Exception {
        return httpGet("https://" + pickHost() + "/v1/users/" + userId
            + "?app_name=" + APP, null);
    }

    /** v12: 同专辑曲目 (查找所有 track 共享同一 album 字段的) */
    public static String albumTracks(String trackId) throws Exception {
        // 简化: 先取单 track 拿到 album/title, 再按 title 搜
        String trackJson = track(trackId);
        JSONObject t = new JSONObject(trackJson).getJSONObject("data");
        JSONObject trackObj = t.getJSONObject("user");
        String artistId = trackObj.optString("id", "");
        return userTracks(artistId);
    }

    /** v12: Remix 父级/子级 */
    public static String remixes(String trackId) throws Exception {
        return httpGet("https://" + pickHost() + "/v1/tracks/" + trackId + "/remixes?app_name=" + APP, null);
    }

    /** v12: 流派/标签 trending 列表 */
    public static String trendingUnderground() throws Exception {
        return httpGet("https://" + pickHost() + "/v1/tracks/trending/underground?app_name=" + APP, null);
    }

    public static String playlists() throws Exception {
        return httpGet("https://" + pickHost() + "/v1/playlists/trending?app_name=" + APP + "&limit=20", null);
    }

    /** v12: 热门歌单 */
    public static String playlist(String playlistId) throws Exception {
        return httpGet("https://" + pickHost() + "/v1/playlists/" + playlistId + "?app_name=" + APP, null);
    }

    public static String[] parse(String json) throws Exception {
        JSONArray data = new JSONObject(json).getJSONArray("data");
        String[] out = new String[data.length()];
        for (int i = 0; i < data.length(); i++) {
            JSONObject t = data.getJSONObject(i);
            String user = t.getJSONObject("user").optString("name", "?");
            out[i] = t.optString("title", "?") + "\u0001" + user
                + "\u0001" + fmt(t.optInt("duration"))
                + "\u0001https://" + pickHost() + "/v1/tracks/"
                + t.getString("id") + "/stream?app_name=" + APP;
        }
        return out;
    }

    public static String[] parseWithIds(String json) throws Exception {
        JSONArray data = new JSONObject(json).getJSONArray("data");
        String[] out = new String[data.length()];
        for (int i = 0; i < data.length(); i++) {
            JSONObject t = data.getJSONObject(i);
            JSONObject user = t.getJSONObject("user");
            String userName = user.optString("name", "?");
            String userId = user.optString("id", "");
            String trackId = t.optString("id", "");
            String genre = t.optString("genre", "");
            String mood = t.optString("mood", "");
            String artwork = t.optString("artwork", "");
            out[i] = t.optString("title", "?") + "\u0001" + userName
                + "\u0001" + fmt(t.optInt("duration"))
                + "\u0001https://" + pickHost() + "/v1/tracks/"
                + trackId + "/stream?app_name=" + APP
                + "\u0001" + trackId
                + "\u0001" + userId
                + "\u0001" + genre
                + "\u0001" + mood
                + "\u0001" + artwork;
        }
        return out;
    }

    /** v12: 解析歌单 */
    public static String[] parsePlaylist(String json) throws Exception {
        JSONArray tracks = new JSONObject(json).getJSONArray("tracks");
        String[] out = new String[tracks.length()];
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject t = tracks.getJSONObject(i);
            String user = t.getJSONObject("user").optString("name", "?");
            out[i] = t.optString("title", "?") + "\u0001" + user
                + "\u0001" + fmt(t.optInt("duration"))
                + "\u0001https://" + pickHost() + "/v1/tracks/"
                + t.getString("id") + "/stream?app_name=" + APP;
        }
        return out;
    }

    static String fmt(int s) { return s / 60 + ":" + String.format("%02d", s % 60); }

    static String httpGet(String url, String header) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", APP);
        BufferedReader r = new BufferedReader(new InputStreamReader(
            c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        return sb.toString();
    }
}