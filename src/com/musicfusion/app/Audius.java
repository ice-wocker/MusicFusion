package com.musicfusion.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/** Audius 去中心化音乐平台 (公开API, 创作者自主上传) */
public class Audius {
    static String host = "discoveryprovider.audius.co";
    static final String APP = "MusicFusion";

    // 备用host池(主host失败时轮换)
    static final String[] FALLBACKS = {
        "discoveryprovider2.audius.co", "discoveryprovider3.audius.co",
        "audius-discovery-1.altego.net", "discovery-au-02.audius.openplayer.org"
    };
    static int fbIdx = -1;

    public static String pickHost() throws Exception {
        if (fbIdx >= 0 && fbIdx < FALLBACKS.length) return FALLBACKS[fbIdx];
        return host;
    }
    public static void markFail() {
        fbIdx++;
        if (fbIdx >= FALLBACKS.length) fbIdx = -1;
    }

    /** 搜索曲目: 返回 JSON数组字符串(title|artist|streamUrl 精简结构由调用方解析原始json) */
    public static String search(String query) throws Exception {
        String h = pickHost();
        return httpGet("https://" + h + "/v1/tracks/search?query="
            + java.net.URLEncoder.encode(query, "UTF-8")
            + "&app_name=" + APP, null);
    }

    public static String trending() throws Exception {
        String h = pickHost();
        return httpGet("https://" + h + "/v1/tracks/trending?app_name=" + APP, null);
    }

    /** 解析Audius响应为统一行格式: title\u0001artist\u0001duration\u0001streamUrl */
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
