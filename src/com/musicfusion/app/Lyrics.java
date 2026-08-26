package com.musicfusion.app;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Lyrics v7 — LRCLIB 开源歌词库(社区维护, 免费无Key) */
public class Lyrics {

    /** 精确查询 → 失败转搜索; 返回纯文本歌词或null */
    public static String get(String artist, String title) throws Exception {
        String u = "https://lrclib.net/api/get?artist_name="
            + URLEncoder.encode(artist, "UTF-8")
            + "&track_name=" + URLEncoder.encode(title, "UTF-8");
        String json = http(u);
        if (json.contains("\"plainLyrics\"")) {
            String p = field(json, "plainLyrics");
            if (p != null && !p.isEmpty()) return p;
        }
        // 搜索兜底
        String s = http("https://lrclib.net/api/search?q="
            + URLEncoder.encode(title, "UTF-8"));
        if (s.startsWith("[") && s.contains("plainLyrics")) {
            int i = s.indexOf("\"plainLyrics\":\"");
            if (i >= 0) {
                int st = i + 15;
                StringBuilder sb = new StringBuilder();
                for (int j = st; j < s.length(); j++) {
                    char ch = s.charAt(j);
                    if (ch == '"' && s.charAt(j - 1) != '\\') break;
                    sb.append(ch);
                }
                String out = sb.toString()
                    .replace("\\n", "\n").replace("\\\"", "\"");
                if (!out.isEmpty()) return out;
            }
        }
        return null;
    }

    static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\":\"");
        if (i < 0) return null;
        int st = i + key.length() + 4;
        StringBuilder sb = new StringBuilder();
        for (int j = st; j < json.length(); j++) {
            char ch = json.charAt(j);
            if (ch == '"' && json.charAt(j - 1) != '\\') break;
            sb.append(ch);
        }
        return sb.toString().replace("\\n", "\n").replace("\\\"", "\"");
    }

    static String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
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
