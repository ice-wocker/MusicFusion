package com.musicfusion.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** RadioBrowser 全球电台目录 (社区维护, 公开API, 5万+电台) */
public class RadioBrowser {
    static final String BASE = "https://de1.api.radio-browser.info/json";

    public static String search(String query) throws Exception {
        String url = BASE + "/stations/search?name="
            + URLEncoder.encode(query, "UTF-8") + "&limit=30&hidebroken=true";
        return httpGet(url);
    }

    public static String popular() throws Exception {
        return httpGet(BASE + "/stations/search?limit=30&order=clickcount&reverse=true&hidebroken=true");
    }

    /** v11: 拉取全球投票数前100的活跃电台, 用于刷新本地离线目录 */
    public static String topByVotes(int n) throws Exception {
        return httpGet(BASE + "/stations/search?limit=" + n
            + "&order=votes&reverse=true&hidebroken=true&has_geo_info=true");
    }

    /** v11: 列出RadioBrowser国家列表(将来用) */
    public static String listCountries() throws Exception {
        return httpGet(BASE + "/countries?limit=300");
    }

    /** 解析: name\u0001country/tags\u0001bitrate\u0001url_resolved */
    public static String[] parse(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        String[] out = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            out[i] = s.optString("name", "?").trim() + "\u0001"
                + s.optString("country", "") + " · "
                + s.optString("tags", "").split(",")[0]
                + "\u0001" + s.optInt("bitrate", 0) + "kbps"
                + "\u0001" + s.optString("url_resolved", "");
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
