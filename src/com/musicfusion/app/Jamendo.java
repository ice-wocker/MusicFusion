package com.musicfusion.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Jamendo — 全球最大CC授权音乐平台(独立艺术家, 合法免费)
 * 需免费client_id: dev.jamendo.com 注册
 */
public class Jamendo {

    public static String clientId(Context ctx) {
        return ctx.getSharedPreferences("mf", Context.MODE_PRIVATE)
            .getString("jamendo_id", "");
    }

    public static boolean ready(Context ctx) {
        return !clientId(ctx).isEmpty();
    }

    /** 搜索: 行格式 title\u0001artist\u0001duration\u0001audio url */
    public static String[] search(Context ctx, String query) throws Exception {
        String url = "https://api.jamendo.com/v3.0/tracks/?client_id=" + clientId(ctx)
            + "&format=json&limit=25&search=" + URLEncoder.encode(query, "UTF-8")
            + "&include=musicinfo&audioformat=mp32";
        return parse(httpGet(url));
    }

    public static String[] popular(Context ctx) throws Exception {
        String url = "https://api.jamendo.com/v3.0/tracks/?client_id=" + clientId(ctx)
            + "&format=json&limit=25&order=popularity_week&audioformat=mp32";
        return parse(httpGet(url));
    }

    public static String[] parse(String json) throws Exception {
        JSONArray results = new JSONObject(json).getJSONArray("results");
        String[] out = new String[results.length()];
        for (int i = 0; i < results.length(); i++) {
            JSONObject t = results.getJSONObject(i);
            int dur = t.optInt("duration", 0);
            out[i] = t.optString("name", "?") + "\u0001"
                + t.getJSONObject("artist").optString("name", "?")
                + "\u0001" + (dur / 60 + ":" + String.format("%02d", dur % 60))
                + "\u0001" + t.optString("audio", "");
        }
        return out;
    }

    static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "MusicFusion/1.0");
        BufferedReader r = new BufferedReader(new InputStreamReader(
            c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        return sb.toString();
    }
}
