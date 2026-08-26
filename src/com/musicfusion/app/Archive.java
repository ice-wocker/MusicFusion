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
        return searchCollection(query, null);
    }

    /** 指定IA专集搜索: etree(现场演出) / georgeblood(78rpm古董唱片) / audio_music */
    public static String searchCollection(String query, String collection) throws Exception {
        String q = "(" + query + ") AND mediatype:(audio)";
        if (collection != null && !collection.isEmpty())
            q += " AND collection:(" + collection + ")";
        String url = "https://archive.org/advancedsearch.php?q="
            + URLEncoder.encode(q, "UTF-8")
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

    /** 按收藏浏览: etree现场/georgeblood 78转/musopen古典/librivoxaudio有声书 */
    public static String searchByCollection(String collection, String extra) throws Exception {
        String q = "collection:(" + collection + ") AND mediatype:(audio)";
        if (extra != null && !extra.isEmpty())
            q = "(" + URLEncoder.encode(extra, "UTF-8") + ") AND " + q;
        String url = "https://archive.org/advancedsearch.php?q=" + q
            + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator"
            + "&rows=25&page=1&output=json&sort%5B%5D=downloads+desc";
        return httpGet(url);
    }

    /** 乐库分类定义 {显示名, collection, 关键词} */
    public static final String[][] CATALOGS = {
        {"现场演出 Live", "etree", ""},
        {"78转老唱片", "georgeblood", ""},
        {"古典音乐", "musopen", ""},
        {"有声书", "librivoxaudio", ""},
        {"爵士现场", "etree", "jazz"},
        {"摇滚现场", "etree", "rock"},
        {"民谣现场", "etree", "folk"},
        {"电子音乐", "audio_music", "electronic"},
    };

    /** 解析搜索结果: title\u0001creator\u0001identifier\u0001IA */
    public static String[] parse(String json) throws Exception {
        JSONObject resp = new JSONObject(json);
        JSONArray docs = resp.getJSONObject("response").getJSONArray("docs");
        String[] out = new String[docs.length()];
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            out[i] = field(d, "title") + "\u0001" + field(d, "creator")
                + "\u0001IA:" + d.getString("identifier") + "\u0001IA";
        }
        return out;
    }
    static String field(JSONObject d, String key) {
        try { return d.getJSONArray(key).getString(0); }
        catch (Exception e) { return d.optString(key, "?"); }
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
