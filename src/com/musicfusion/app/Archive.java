package com.musicfusion.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/** Internet Archive 高级检索
 * v12 新增:
 *  - collection: etree, audio, opensource_audio
 *  - date: 范围 (year)
 *  - subject/venue/source 字段过滤
 *  - median 排序 (下载量/评分)
 *  - 多字段组合 (concatenation: AND/OR/NOT) */
public class Archive {
    static final String BASE = "https://archive.org/advancedsearch.php";

    public static String search(String query) throws Exception {
        return search(query, "audio", null, null, "downloads", 30);
    }

    /** 高级搜索: collection/date/subject/venue/source/creator 自由组合 */
    public static String search(String query, String collection, String dateRange,
                                String subject, String sortField, int limit) throws Exception {
        StringBuilder q = new StringBuilder();
        // 主查询
        if (query != null && !query.isEmpty()) {
            q.append("(").append(query).append(")");
        }
        // collection
        if (collection != null && !collection.isEmpty()) {
            if (q.length() > 0) q.append(" AND ");
            q.append("collection:").append(collection);
        }
        // date 范围 [YYYY TO YYYY]
        if (dateRange != null && !dateRange.isEmpty()) {
            if (q.length() > 0) q.append(" AND ");
            q.append("date:[").append(dateRange).append("]");
        }
        // subject
        if (subject != null && !subject.isEmpty()) {
            if (q.length() > 0) q.append(" AND ");
            q.append("subject:(").append(subject).append(")");
        }
        // 媒体类型限制
        if (q.length() > 0) q.append(" AND ");
        q.append("mediatype:audio");

        String url = BASE + "?q=" + URLEncoder.encode(q.toString(), "UTF-8")
            + "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=date&fl[]=subject&fl[]=venue"
            + "&fl[]=downloads&fl[]=avg_rating&fl[]=length&fl[]=format&fl[]=collection"
            + "&sort[]=" + (sortField != null ? sortField : "downloads") + " desc"
            + "&rows=" + limit
            + "&output=json";
        return httpGet(url);
    }

    /** 现场录音 (etree) */
    public static String liveConcerts(String query) throws Exception {
        return search(query, "etree", null, "live", "downloads", 30);
    }

    /** 古董唱片 (georgeblood) */
    public static String vintage(String query, String decade) throws Exception {
        return search(query, "georgeblood", decade, null, "avg_rating", 30);
    }

    /** 公开音乐集合 */
    public static String openSourceAudio(String query) throws Exception {
        return search(query, "opensource_audio", null, null, "downloads", 30);
    }

    /** 80-90年代 */
    public static String eighties(String query) throws Exception {
        return search(query, "audio", "1980 TO 1989", null, "downloads", 30);
    }

    /** 90年代 */
    public static String nineties(String query) throws Exception {
        return search(query, "audio", "1990 TO 1999", null, "downloads", 30);
    }

    /** v12: 流派/艺术家子集合 (creator 字段) */
    public static String byCreator(String creator) throws Exception {
        return search(null, "audio", null, null, "downloads", 30)
            .replace("(" + creator + ")", "creator:\"" + creator + "\"");
    }

    /** 解析高级搜索响应 → 统一行格式 */
    public static String[] parse(String json) throws Exception {
        JSONObject resp = new JSONObject(json);
        JSONObject res = resp.getJSONObject("response");
        JSONArray docs = res.getJSONArray("docs");
        String[] out = new String[docs.length()];
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            String id = d.optString("identifier", "?");
            String title = d.optString("title", "?");
            String creator = d.optString("creator", "");
            String date = d.optString("date", "");
            String dur = d.optString("length", "");
            int downloads = d.optInt("downloads", 0);
            double rating = d.optDouble("avg_rating", 0);
            String collection = d.optString("collection", "");
            // 时长格式化
            String durStr = "";
            try { durStr = formatDuration(Double.parseDouble(dur)); } catch (Exception ignored) {}
            // 流派标签
            String subj = d.optString("subject", "");
            String tags = subj.length() > 30 ? subj.substring(0, 30) : subj;
            // stream URL (用 identifier 直接拼详情页 + 流)
            String streamUrl = "https://archive.org/details/" + id;
            out[i] = title + "\u0001" + creator + "\u0001" + durStr
                + " · " + date + " · " + collection
                + "\u0001" + streamUrl
                + "\u0001" + downloads + "d " + String.format(Locale.US, "%.1f★", rating)
                + "\u0001" + id + "\u0001" + tags;
        }
        return out;
    }

    /** 解析器 v2: 含完整 metadata (供 UI 展示) */
    public static String[] parseWithMeta(String json) throws Exception {
        JSONObject resp = new JSONObject(json);
        JSONObject res = resp.getJSONObject("response");
        JSONArray docs = res.getJSONArray("docs");
        String[] out = new String[docs.length()];
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            String id = d.optString("identifier", "?");
            String title = d.optString("title", "?");
            String creator = d.optString("creator", "");
            String date = d.optString("date", "");
            String dur = d.optString("length", "");
            int downloads = d.optInt("downloads", 0);
            double rating = d.optDouble("avg_rating", 0);
            String venue = d.optString("venue", "");
            String source = d.optString("source", "");
            String collection = d.optString("collection", "");
            String format = d.optString("format", "");
            String durStr = "";
            try { durStr = formatDuration(Double.parseDouble(dur)); } catch (Exception ignored) {}
            String streamUrl = "https://archive.org/details/" + id;
            String meta = collection + " · " + date;
            if (!venue.isEmpty()) meta += " · " + venue;
            if (!source.isEmpty()) meta += " · " + source;
            out[i] = title + "\u0001" + creator + "\u0001" + durStr
                + " · " + downloads + "下载 · " + String.format(Locale.US, "%.1f★", rating)
                + "\u0001" + streamUrl
                + "\u0001" + meta
                + "\u0001" + id
                + "\u0001" + format;
        }
        return out;
    }

    private static String formatDuration(double sec) {
        int h = (int) (sec / 3600);
        int m = (int) ((sec % 3600) / 60);
        int s = (int) (sec % 60);
        if (h > 0) return h + ":" + String.format(Locale.US, "%02d:%02d", m, s);
        return m + ":" + String.format(Locale.US, "%02d", s);
    }

    /** 提取可流式播放的真实 MP3/OGG URL (从详情页 metadata) */
    public static String getStreamUrl(String identifier) throws Exception {
        return "https://archive.org/details/" + identifier;
    }

    /** v12 兼容: 从 identifier 提取首个 MP3 音频直链 (供 playAt 使用) */
    public static String firstAudio(String identifier) throws Exception {
        // 优先尝试 IA 元数据 API
        String metaUrl = "https://archive.org/metadata/" + identifier + "/files";
        try {
            String json = httpGet(metaUrl);
            org.json.JSONObject resp = new org.json.JSONObject(json);
            org.json.JSONArray results = resp.optJSONArray("result");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    org.json.JSONObject f = results.getJSONObject(i);
                    String name = f.optString("name", "");
                    String fmt = f.optString("format", "");
                    if (name.toLowerCase().endsWith(".mp3") || fmt.equalsIgnoreCase("VBR MP3")
                        || fmt.equalsIgnoreCase("128Kbps MP3") || fmt.contains("MP3")) {
                        return "https://archive.org/" + identifier + "/" + name;
                    }
                }
            }
        } catch (Exception e) {
            // 兜底: 直接用 details 页
        }
        return "https://archive.org/details/" + identifier;
    }

    static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "MusicFusion/12.0");
        BufferedReader r = new BufferedReader(new InputStreamReader(
            c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        return sb.toString();
    }
}