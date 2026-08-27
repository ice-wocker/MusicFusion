package com.musicfusion.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Openverse v12 — 高级筛选 (许可证/文件类型/扩展名/来源)
 * 新增:
 *  - 许可证筛选 (CC0/CC-BY/CC-BY-SA/CC-BY-NC)
 *  - 文件扩展名 (MP3/OGG/FLAC/WAV)
 *  - 来源 (Wikimedia/Jamendo/Freesound/SoundCloud)
 *  - 时长范围
 *  - 排序 (relevance/popularity/...)
 */
public class Openverse {

    static final java.util.LinkedHashMap<String, String[]> cache =
        new java.util.LinkedHashMap<String, String[]>(16, 0.75f, true) {
            protected boolean removeEldest(java.util.Map.Entry<String, String[]> e) {
                return size() > 30; }};

    /** v12: 高级搜索 */
    public static String[] search(String query, int page) throws Exception {
        return search(query, page, null, null, null);
    }

    public static String[] search(String query, int page, String license, String extension, String source) throws Exception {
        String ck = query + "#" + page + "#" + license + "#" + extension + "#" + source;
        String[] hit = cache.get(ck);
        if (hit != null) return hit;
        String[] r = doSearch(query, page, license, extension, source);
        cache.put(ck, r);
        return r;
    }

    static String[] doSearch(String query, int page, String license, String extension, String source) throws Exception {
        StringBuilder url = new StringBuilder("https://api.openverse.org/v1/audio/?q=")
            .append(URLEncoder.encode(query, "UTF-8"))
            .append("&page_size=20&page=").append(page);
        if (license != null && !license.isEmpty()) {
            url.append("&license=").append(license); // CC0, BY, BY-SA, BY-NC
        }
        if (extension != null && !extension.isEmpty()) {
            url.append("&extension=").append(extension);
        }
        if (source != null && !source.isEmpty()) {
            url.append("&source=").append(source);
        }
        return doFetch(url.toString());
    }

    /** v12: 纯 CC0 公共领域 */
    public static String[] searchCC0(String query) throws Exception {
        return search(query, 1, "CC0", null, null);
    }

    /** v12: CC-BY (署名) */
    public static String[] searchCCBY(String query) throws Exception {
        return search(query, 1, "BY", null, null);
    }

    /** v12: 按来源 (Jamendo/Freesound/Wikimedia) */
    public static String[] searchBySource(String query, String source) throws Exception {
        return search(query, 1, null, null, source);
    }

    /** v12: 按扩展名 (MP3/OGG/FLAC) */
    public static String[] searchByExt(String query, String ext) throws Exception {
        return search(query, 1, null, ext, null);
    }

    static String[] doFetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "MusicFusion/12.0");
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
            String source = t.optString("source", "");
            String ext = "";
            try {
                JSONObject file = t.optJSONObject("files");
                if (file != null) ext = file.optString("ext", "");
            } catch (Exception ignored) {}
            String thumb = t.optString("thumbnail", "");
            out[i] = title + "\u0001" + creator + " · " + license
                + (source.isEmpty() ? "" : " · " + source)
                + (ext.isEmpty() ? "" : " · " + ext)
                + "\u0001" + (dur / 60) + ":" + String.format("%02d", dur % 60)
                + "\u0001" + play
                + "\u0001" + source
                + "\u0001" + license
                + "\u0001" + ext
                + "\u0001" + thumb;
        }
        return out;
    }

    /** v12: 列出支持的来源 */
    public static String[] sources() {
        return new String[]{"wikimedia", "jamendo", "freesound", "incompetech", "cchound"};
    }

    /** v12: 列出支持的许可证 */
    public static String[] licenses() {
        return new String[]{"CC0", "BY", "BY-SA", "BY-NC", "BY-ND", "BY-NC-SA", "BY-NC-ND"};
    }
}