package com.musicfusion.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Openverse Audio — WordPress基金会开源CC音频聚合
 * 聚合 Jamendo/Freesound/Wikimedia 等数十个CC音源, 免费无Key
 */
public class Openverse {

    /** 返回统一行: title\u0001creator(license)\u0001duration\u0001url */
    public static String[] search(String query, int page) throws Exception {
        String url = "https://api.openverse.org/v1/audio/?q="
            + URLEncoder.encode(query, "UTF-8")
            + "&page_size=20&page=" + page + "&license_type=all";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "MusicFusion/1.0");
        BufferedReader r = new BufferedReader(new InputStreamReader(
            c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        JSONObject resp = new JSONObject(sb.toString());
        JSONArray results = resp.getJSONArray("results");
        String[] out = new String[results.length()];
        for (int i = 0; i < results.length(); i++) {
            JSONObject t = results.getJSONObject(i);
            String title = t.optString("title", "?");
            String creator = t.optString("creator", "?");
            String license = t.optString("license", "").toUpperCase();
            int dur = t.optInt("duration", 0) / 1000;
            String play = t.optString("url", "");
            out[i] = title + "\u0001" + creator + " · " + license
                + "\u0001" + (dur / 60) + ":" + String.format("%02d", dur % 60)
                + "\u0001" + play;
        }
        return out;
    }
}
