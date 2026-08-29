package com.musicfusion.app;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** MusicBrainz — 元数据补全 (MusicBrainz / OpenMusicBrainz / Cover Art Archive)
 *  - 录音/发行/艺术家/作品 查询
 *  - 封面图获取 (Cover Art Archive)
 *  - 标签/流派/年代补全
 *  - 无需 API Key (礼貌请求: User-Agent + 1req/s) */
public final class MusicBrainz {
    private static final String TAG = "MusicBrainz";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final String MB_ROOT = "https://musicbrainz.org/ws/2/";
    private static final String CAA_ROOT = "https://coverartarchive.org/";
    private static final String USER_AGENT = "MusicFusion/13.0 (https://github.com/ice-wocker/MusicFusion)";
    private static long lastRequestMs = 0;
    private static final long MIN_INTERVAL_MS = 1100; // 礼貌: ~1req/s

    public interface LookupCallback {
        void onResult(RecordingInfo info);
        void onError(String error);
    }

    public interface SearchCallback {
        void onResults(List<RecordingInfo> results);
        void onError(String error);
    }

    public interface CoverCallback {
        void onCoverUrl(String url);
        void onError(String error);
    }

    public static class RecordingInfo {
        public String mbid;           // Recording MBID
        public String title;
        public String artist;
        public String artistMbid;
        public String album;
        public String albumMbid;
        public String releaseMbid;    // 具体发行 MBID
        public int year = 0;
        public String date;           // 完整日期
        public String genre;
        public List<String> tags = new ArrayList<>();
        public int durationMs = 0;
        public String isrc;
        public String coverUrl;       // 封面图 URL (来自 CAA)
        public int trackNumber = 0;
        public int trackCount = 0;
        public String disambiguation;
    }

    public static class ReleaseInfo {
        public String mbid;
        public String title;
        public String artist;
        public String artistMbid;
        public int year = 0;
        public String date;
        public String country;
        public String status;         // Official / Promotion / Bootleg / ...
        public String packaging;      // Jewel Case / Digipak / ...
        public List<String> formats = new ArrayList<>(); // CD / Vinyl / Digital / ...
        public List<RecordingInfo> tracks = new ArrayList<>();
        public String coverUrl;
    }

    public static class ArtistInfo {
        public String mbid;
        public String name;
        public String sortName;
        public String disambiguation;
        public String type;           // Person / Group / Orchestra / ...
        public String gender;
        public String country;
        public List<String> tags = new ArrayList<>();
        public String beginDate;
        public String endDate;
        public String wikipediaUrl;
    }

    /** 通过录音 MBID 查询详情 (含发行、封面) */
    public static void lookupRecording(String mbid, final LookupCallback cb) {
        if (TextUtils.isEmpty(mbid)) { cb.onError("MBID 为空"); return; }
        EXEC.execute(new Runnable() { public void run() {
            try {
                rateLimit();
                String url = MB_ROOT + "recording/" + mbid + "?inc=releases+artists+tags+isrcs&fmt=json";
                String json = httpGet(url);
                RecordingInfo info = parseRecording(json);
                if (info != null) {
                    // 补全封面
                    if (!TextUtils.isEmpty(info.releaseMbid)) {
                        fetchCover(info.releaseMbid, new CoverCallback() {
                            public void onCoverUrl(String coverUrl) {
                                info.coverUrl = coverUrl;
                                cb.onResult(info);
                            }
                            public void onError(String error) { cb.onResult(info); }
                        });
                    } else {
                        cb.onResult(info);
                    }
                } else {
                    cb.onError("解析失败");
                }
            } catch (Exception e) { Log.w(TAG, "lookupRecording", e); cb.onError(e.getMessage()); }
        }});
    }

    /** 通过 ISRC 查询 */
    public static void lookupIsrc(String isrc, final LookupCallback cb) {
        if (TextUtils.isEmpty(isrc)) { cb.onError("ISRC 为空"); return; }
        EXEC.execute(new Runnable() { public void run() {
            try {
                rateLimit();
                String url = MB_ROOT + "isrc/" + isrc + "?inc=recordings&fmt=json";
                String json = httpGet(url);
                JSONObject o = new JSONObject(json);
                JSONArray recordings = o.optJSONArray("recordings");
                if (recordings != null && recordings.length() > 0) {
                    String recMbid = recordings.getJSONObject(0).optString("id");
                    lookupRecording(recMbid, cb);
                } else {
                    cb.onError("ISRC 未找到录音");
                }
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }});
    }

    /** 搜索录音 (按标题+艺术家) */
    public static void searchRecording(String title, String artist, int limit, final SearchCallback cb) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                rateLimit();
                StringBuilder q = new StringBuilder();
                if (!TextUtils.isEmpty(title)) q.append("recording:").append(escapeQuery(title));
                if (!TextUtils.isEmpty(artist)) {
                    if (q.length() > 0) q.append(" AND ");
                    q.append("artist:").append(escapeQuery(artist));
                }
                String url = MB_ROOT + "recording/?query=" + URLEncoder.encode(q.toString(), "UTF-8")
                        + "&limit=" + Math.max(1, Math.min(limit, 100)) + "&fmt=json";
                String json = httpGet(url);
                List<RecordingInfo> results = parseRecordingList(json);
                cb.onResults(results);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }});
    }

    /** 搜索发行 */
    public static void searchRelease(String title, String artist, int limit, final SearchCallback cb) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                rateLimit();
                StringBuilder q = new StringBuilder();
                if (!TextUtils.isEmpty(title)) q.append("release:").append(escapeQuery(title));
                if (!TextUtils.isEmpty(artist)) {
                    if (q.length() > 0) q.append(" AND ");
                    q.append("artist:").append(escapeQuery(artist));
                }
                String url = MB_ROOT + "release/?query=" + URLEncoder.encode(q.toString(), "UTF-8")
                        + "&limit=" + Math.max(1, Math.min(limit, 100)) + "&fmt=json";
                String json = httpGet(url);
                List<RecordingInfo> results = parseReleaseList(json);
                cb.onResults(results);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }});
    }

    /** 搜索艺术家 */
    public static void searchArtist(String name, int limit, final SearchCallback cb) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                rateLimit();
                String url = MB_ROOT + "artist/?query=artist:" + URLEncoder.encode(escapeQuery(name), "UTF-8")
                        + "&limit=" + Math.max(1, Math.min(limit, 100)) + "&fmt=json";
                String json = httpGet(url);
                List<RecordingInfo> results = parseArtistList(json);
                cb.onResults(results);
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }});
    }

    /** 获取封面图 (Cover Art Archive) */
    public static void fetchCover(String releaseMbid, final CoverCallback cb) {
        if (TextUtils.isEmpty(releaseMbid)) { cb.onError("Release MBID 为空"); return; }
        EXEC.execute(new Runnable() { public void run() {
            try {
                rateLimit();
                String url = CAA_ROOT + "release/" + releaseMbid + "/front-500";
                // HEAD 请求检查是否存在
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200) {
                    cb.onCoverUrl(url);
                } else {
                    // 尝试获取图片列表, 取第一张
                    fetchCoverList(releaseMbid, cb);
                }
            } catch (Exception e) { cb.onError(e.getMessage()); }
        }});
    }

    private static void fetchCoverList(String releaseMbid, final CoverCallback cb) {
        try {
            rateLimit();
            String url = CAA_ROOT + "release/" + releaseMbid;
            String json = httpGet(url);
            JSONObject o = new JSONObject(json);
            JSONArray images = o.optJSONArray("images");
            if (images != null && images.length() > 0) {
                // 找最大的 front 图
                String best = null;
                int bestSize = 0;
                for (int i = 0; i < images.length(); i++) {
                    JSONObject img = images.getJSONObject(i);
                    if ("Front".equalsIgnoreCase(img.optString("types", ""))) {
                        // CAA 不直接给尺寸, 尝试 thumbnail-500 / thumbnail-1200
                        String thumb = img.optString("thumbnails", "");
                        // 简化: 直接用 image URL
                        best = img.optString("image", "");
                        break;
                    }
                }
                if (TextUtils.isEmpty(best) && images.length() > 0) {
                    best = images.getJSONObject(0).optString("image", "");
                }
                if (!TextUtils.isEmpty(best)) cb.onCoverUrl(best);
                else cb.onError("无封面");
            } else {
                cb.onError("无封面");
            }
        } catch (Exception e) { cb.onError(e.getMessage()); }
    }

    /** 补全元数据 (高层接口: 传入已有信息, 返回补全后的信息) */
    public static void completeMetadata(Context ctx, String title, String artist, String album,
                                        final LookupCallback cb) {
        // 1. 先搜索录音
        searchRecording(title, artist, 5, new SearchCallback() {
            public void onResults(List<RecordingInfo> results) {
                if (results.isEmpty()) { cb.onError("未找到匹配录音"); return; }
                // 优先匹配专辑名
                RecordingInfo best = results.get(0);
                for (RecordingInfo r : results) {
                    if (!TextUtils.isEmpty(album) && !TextUtils.isEmpty(r.album)
                            && r.album.toLowerCase().contains(album.toLowerCase())) {
                        best = r; break;
                    }
                }
                // 2. 查详情补全
                lookupRecording(best.mbid, cb);
            }
            public void onError(String error) { cb.onError(error); }
        });
    }

    // ===== 解析 =====

    private static RecordingInfo parseRecording(String json) {
        try {
            JSONObject o = new JSONObject(json);
            RecordingInfo r = new RecordingInfo();
            r.mbid = o.optString("id");
            r.title = o.optString("title");
            r.durationMs = o.optInt("length", 0);
            r.disambiguation = o.optString("disambiguation", "");

            // ISRC
            JSONArray isrcs = o.optJSONArray("isrcs");
            if (isrcs != null && isrcs.length() > 0) r.isrc = isrcs.optString(0);

            // 艺术家
            JSONArray artistCredits = o.optJSONArray("artist-credit");
            if (artistCredits != null && artistCredits.length() > 0) {
                JSONObject ac = artistCredits.getJSONObject(0);
                JSONObject artist = ac.optJSONObject("artist");
                if (artist != null) {
                    r.artist = artist.optString("name");
                    r.artistMbid = artist.optString("id");
                }
            }

            // 标签/流派
            JSONArray tags = o.optJSONArray("tags");
            if (tags != null) {
                for (int i = 0; i < tags.length(); i++) {
                    String tag = tags.getJSONObject(i).optString("name");
                    r.tags.add(tag);
                    if (TextUtils.isEmpty(r.genre) && isGenreTag(tag)) r.genre = tag;
                }
            }

            // 发行信息 (取第一个发行)
            JSONArray releases = o.optJSONArray("releases");
            if (releases != null && releases.length() > 0) {
                JSONObject rel = releases.getJSONObject(0);
                r.releaseMbid = rel.optString("id");
                r.album = rel.optString("title");
                r.date = rel.optString("date");
                if (!TextUtils.isEmpty(r.date) && r.date.length() >= 4) {
                    try { r.year = Integer.parseInt(r.date.substring(0, 4)); } catch (Exception ignored) {}
                }
                // 发行的 track 号
                JSONArray media = rel.optJSONArray("media");
                if (media != null) {
                    int trackIdx = 0;
                    for (int m = 0; m < media.length(); m++) {
                        JSONArray tracks = media.getJSONObject(m).optJSONArray("tracks");
                        if (tracks != null) {
                            for (int t = 0; t < tracks.length(); t++) {
                                trackIdx++;
                                JSONObject tr = tracks.getJSONObject(t);
                                if (r.mbid.equals(tr.optString("id"))) {
                                    r.trackNumber = trackIdx;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            return r;
        } catch (Exception e) { Log.w(TAG, "parseRecording", e); return null; }
    }

    private static List<RecordingInfo> parseRecordingList(String json) {
        List<RecordingInfo> list = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("recordings");
            if (arr == null) return list;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rec = arr.getJSONObject(i);
                RecordingInfo r = new RecordingInfo();
                r.mbid = rec.optString("id");
                r.title = rec.optString("title");
                r.durationMs = rec.optInt("length", 0);
                // 艺术家
                JSONArray ac = rec.optJSONArray("artist-credit");
                if (ac != null && ac.length() > 0) {
                    JSONObject a = ac.getJSONObject(0).optJSONObject("artist");
                    if (a != null) { r.artist = a.optString("name"); r.artistMbid = a.optString("id"); }
                }
                // 发行 (取第一个)
                JSONArray rels = rec.optJSONArray("releases");
                if (rels != null && rels.length() > 0) {
                    JSONObject rel = rels.getJSONObject(0);
                    r.album = rel.optString("title");
                    r.releaseMbid = rel.optString("id");
                    r.date = rel.optString("date");
                }
                // 评分
                r.disambiguation = rec.optString("disambiguation", "");
                list.add(r);
            }
        } catch (Exception e) { Log.w(TAG, "parseRecordingList", e); }
        return list;
    }

    private static List<RecordingInfo> parseReleaseList(String json) {
        List<RecordingInfo> list = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("releases");
            if (arr == null) return list;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject rel = arr.getJSONObject(i);
                RecordingInfo r = new RecordingInfo();
                r.mbid = rel.optString("id");
                r.album = rel.optString("title");
                r.releaseMbid = rel.optString("id");
                r.date = rel.optString("date");
                // 艺术家
                JSONArray ac = rel.optJSONArray("artist-credit");
                if (ac != null && ac.length() > 0) {
                    JSONObject a = ac.getJSONObject(0).optJSONObject("artist");
                    if (a != null) { r.artist = a.optString("name"); r.artistMbid = a.optString("id"); }
                }
                // 国家/状态/格式
                r.disambiguation = rel.optString("country", "") + " | "
                    + rel.optString("status", "") + " | "
                    + rel.optString("packaging", "");
                list.add(r);
            }
        } catch (Exception e) { Log.w(TAG, "parseReleaseList", e); }
        return list;
    }

    private static List<RecordingInfo> parseArtistList(String json) {
        List<RecordingInfo> list = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray arr = o.optJSONArray("artists");
            if (arr == null) return list;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject art = arr.getJSONObject(i);
                RecordingInfo r = new RecordingInfo(); // 复用结构
                r.mbid = art.optString("id");
                r.artist = art.optString("name");
                r.artistMbid = art.optString("id");
                r.album = art.optString("sort-name", "");
                r.disambiguation = art.optString("type", "") + " | " + art.optString("country", "") + " | " + art.optString("disambiguation", "");
                list.add(r);
            }
        } catch (Exception e) { Log.w(TAG, "parseArtistList", e); }
        return list;
    }

    private static boolean isGenreTag(String tag) {
        String[] genres = {"rock", "pop", "jazz", "classical", "electronic", "hip hop", "folk", "country",
            "metal", "ambient", "blues", "reggae", "funk", "soul", "r&b", "indie", "alternative",
            "punk", "disco", "house", "techno", "trance", "drum and bass", "dubstep"};
        String t = tag.toLowerCase();
        for (String g : genres) if (t.contains(g)) return true;
        return false;
    }

    private static String escapeQuery(String s) {
        return s.replaceAll("[\\[\\]{}:\\\\\\+\\-!()\"~*?]", " ").replaceAll("\\s+", " ").trim();
    }

    private static void rateLimit() {
        long now = System.currentTimeMillis();
        long wait = MIN_INTERVAL_MS - (now - lastRequestMs);
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
        }
        lastRequestMs = System.currentTimeMillis();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
            code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }
}