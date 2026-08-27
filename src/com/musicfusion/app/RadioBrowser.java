package com.musicfusion.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * RadioBrowser v12 — 标签/语言/编解码器/国家/州/语言 高级筛选
 * 新增:
 *  - byTag: 流派/类型/语言
 *  - byLanguage: 56 种语言
 *  - byCountry/State
 *  - byCodec: MP3/AAC/OGG/OPUS
 *  - advanced: 多条件组合
 *  - 点击/投票/比特率排序
 */
public class RadioBrowser {
    static final String BASE = "https://de1.api.radio-browser.info/json";
    static String currentServer = "de1.api.radio-browser.info";
    static final String[] SERVERS = {
        "de1.api.radio-browser.info", "de2.api.radio-browser.info",
        "at1.api.radio-browser.info", "nl1.api.radio-browser.info"
    };
    static int serverIdx = 0;

    static String getBase() {
        return "https://" + currentServer + "/json";
    }

    public static String search(String query) throws Exception {
        String url = getBase() + "/stations/search?name="
            + URLEncoder.encode(query, "UTF-8") + "&limit=30&hidebroken=true";
        return httpGet(url);
    }

    public static String popular() throws Exception {
        return httpGet(getBase() + "/stations/search?limit=30&order=clickcount&reverse=true&hidebroken=true");
    }

    public static String topByVotes(int n) throws Exception {
        return httpGet(getBase() + "/stations/search?limit=" + n
            + "&order=votes&reverse=true&hidebroken=true&has_geo_info=true");
    }

    public static String listCountries() throws Exception {
        return httpGet(BASE + "/countries?limit=300");
    }

    /** v12: 按标签搜索 (jazz, classical, news, talk, sports...) */
    public static String byTag(String tag, int limit) throws Exception {
        return httpGet(getBase() + "/stations/search?tag=" + URLEncoder.encode(tag, "UTF-8")
            + "&limit=" + limit + "&order=votes&reverse=true&hidebroken=true");
    }

    /** v12: 按语言 (chinese, english, japanese...) */
    public static String byLanguage(String lang, int limit) throws Exception {
        return httpGet(getBase() + "/stations/search?language=" + URLEncoder.encode(lang, "UTF-8")
            + "&limit=" + limit + "&order=votes&reverse=true&hidebroken=true");
    }

    /** v12: 按国家代码 (CN, US, JP, DE, FR...) */
    public static String byCountry(String country, int limit) throws Exception {
        return httpGet(getBase() + "/stations/search?country=" + URLEncoder.encode(country, "UTF-8")
            + "&limit=" + limit + "&order=votes&reverse=true&hidebroken=true");
    }

    /** v12: 按编解码器 (MP3, AAC, OGG, OPUS) */
    public static String byCodec(String codec, int limit) throws Exception {
        return httpGet(getBase() + "/stations/search?codec=" + URLEncoder.encode(codec, "UTF-8")
            + "&limit=" + limit + "&order=votes&reverse=true&hidebroken=true");
    }

    /** v12: 高级组合 (tag + language + codec + bitrate 范围) */
    public static String advanced(String tag, String language, String codec,
                                  int bitrateMin, int bitrateMax, int limit) throws Exception {
        StringBuilder url = new StringBuilder(getBase()).append("/stations/search?");
        if (tag != null && !tag.isEmpty()) url.append("tag=").append(URLEncoder.encode(tag, "UTF-8")).append("&");
        if (language != null && !language.isEmpty()) url.append("language=").append(URLEncoder.encode(language, "UTF-8")).append("&");
        if (codec != null && !codec.isEmpty()) url.append("codec=").append(URLEncoder.encode(codec, "UTF-8")).append("&");
        if (bitrateMin > 0) url.append("bitrateMin=").append(bitrateMin).append("&");
        if (bitrateMax > 0) url.append("bitrateMax=").append(bitrateMax).append("&");
        url.append("limit=").append(limit).append("&order=votes&reverse=true&hidebroken=true");
        return httpGet(url.toString());
    }

    /** v12: 点击/评分/比特率多维度排序 */
    public static String sortBy(String orderField, boolean reverse, int limit) throws Exception {
        return httpGet(getBase() + "/stations/search?limit=" + limit
            + "&order=" + orderField + (reverse ? "&reverse=true" : "") + "&hidebroken=true");
    }

    /** 解析: name\u0001meta\u0001bitrate\u0001url + 扩展元数据 */
    public static String[] parse(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        String[] out = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            out[i] = s.optString("name", "?").trim() + "\u0001"
                + s.optString("country", "") + " · "
                + s.optString("tags", "").split(",")[0]
                + "\u0001" + s.optInt("bitrate", 0) + "kbps"
                + "\u0001" + s.optString("url_resolved", "")
                + "\u0001" + s.optString("language", "")
                + "\u0001" + s.optString("codec", "")
                + "\u0001" + s.optInt("votes", 0) + "♥"
                + "\u0001" + s.optInt("clickcount", 0) + "▶"
                + "\u0001" + s.optString("homepage", "");
        }
        return out;
    }

    /** v12: 完整元数据解析 (含 ID/UUID) */
    public static String[] parseFull(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        String[] out = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            out[i] = s.optString("name", "?").trim() + "\u0001"
                + s.optString("country", "") + " · "
                + s.optString("tags", "").split(",")[0]
                + "\u0001" + s.optInt("bitrate", 0) + "kbps"
                + "\u0001" + s.optString("url_resolved", "")
                + "\u0001" + s.optString("stationuuid", "")
                + "\u0001" + s.optString("language", "")
                + "\u0001" + s.optString("codec", "")
                + "\u0001" + s.optString("homepage", "");
        }
        return out;
    }

    /** v12: 国家列表 (供 UI 选择) */
    public static String[] parseCountries(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        String[] out = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            out[i] = c.optString("name", "?") + " (" + c.optString("iso_3166_1", "") + ")";
        }
        return out;
    }

    /** v12: 流派标签热门 (默认 25 个) */
    public static String topTags() {
        return "jazz,classical,rock,pop,electronic,ambient,chill,lounge,"
            + "news,talk,sports,oldies,80s,90s,2000s,2010s,country,folk,"
            + "hip-hop,indie,metal,reggae,world,spiritual,christmas";
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