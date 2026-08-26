package com.musicfusion.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** Internet Archive 音乐档案 (公有领域/CC音乐, 公开API) */
public class Archive {

    /** collection:audio_music + etree(Live Music Archive) */
    public static String search(String query) throws Exception {
        String q = URLEncoder.encode(
            "(" + query + ") AND mediatype:(audio)", "UTF-8");
        String url = "https://archive.org/advancedsearch.php?q=" + q
            + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator"
            + "&rows=20&page=1&output=json";
        return httpGet(url);
    }

    /** 获取条目内第一个MP3播放地址 */
    public static String firstAudio(String identifier) throws Exception {
        String json = httpGet("https://archive.org/metadata/" + identifier);
        JSONObject meta = new JSONObject(json);
        JSONArray files = meta.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            String name = f.optString("name", "");
            String fmt = f.optString("format", "");
            if ((fmt.contains("MP3") || name.endsWith(".mp3")) && !name.contains("_sample"))
                return "https://archive.org/download/" + identifier + "/" + name;
        }
        return null;
    }

    /** 解析搜索结果: title\u0001creator\u0001identifier\u0001PENDING */
    public static String[] parse(String json) throws Exception {
        JSONObject resp = new JSONObject(json);
        JSONArray docs = resp.getJSONObject("response").getJSONArray("docs");
        String[] out = new String[docs.length()];
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            String title = d.has("title") ? (d.getJSONArray("title").getString(0)) : "?";
            String creator = d.has("creator")
                ? d.getJSONArray("creator").getString(0) : "Internet Archive";
            out[i] = title + "\u0001" + creator + "\u0001IA:"
                + d.getString("identifier") + "\u0001IA";
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
