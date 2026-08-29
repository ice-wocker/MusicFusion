package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** PodcastEngine — RSS/OPML 解析, 播客订阅/播放/更新
 *  - 支持 RSS 2.0, Atom 1.0, iTunes 扩展
 *  - OPML 导入/导出订阅列表
 *  - 自动更新 (后台轮询)
 *  - 播放进度同步 (每集独立) */
public final class PodcastEngine {
    private static final String TAG = "PodcastEngine";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final String PREF_FEEDS = "podcast_feeds_v1";
    private static final String PREF_EPISODES = "podcast_eps_v1";
    private static final String PREF_PROGRESS = "podcast_prog_v1";

    public static class Feed {
        public String id;           // feed URL 的 hash
        public String url;          // RSS/Atom URL
        public String title;
        public String author;
        public String description;
        public String imageUrl;
        public String link;         // 网站链接
        public String language;
        public String copyright;
        public String itunesExplicit;
        public String itunesCategory;
        public long lastUpdated = 0;
        public long addedAt = 0;
        public boolean enabled = true;
        public int updateIntervalHours = 6; // 更新间隔
    }

    public static class Episode {
        public String id;           // GUID 或 enclosure URL hash
        public String feedId;
        public String title;
        public String description;
        public String audioUrl;     // enclosure URL
        public String audioType;    // mime type
        public long audioLength = 0;
        public long pubDate = 0;    // 发布时间戳
        public String imageUrl;     // 节目图 (iTunes:image)
        public String guid;
        public int durationSec = 0; // iTunes:duration
        public String season, episodeNum;
        public boolean explicit = false;
        public String podcastTitle; // 所属播客标题 (用于显示)
    }

    public static class PlaybackProgress {
        public String episodeId;
        public long positionMs = 0;
        public long durationMs = 0;
        public long lastPlayed = 0;
        public boolean completed = false;
    }

    public interface FeedCallback {
        void onFeedsLoaded(List<Feed> feeds);
        void onError(String error);
    }

    public interface EpisodeCallback {
        void onEpisodesLoaded(List<Episode> episodes);
        void onError(String error);
    }

    public interface ParseCallback {
        void onFeedParsed(Feed feed, List<Episode> episodes);
        void onError(String error);
    }

    /** 加载所有订阅源 */
    public static void loadFeeds(Context ctx, FeedCallback cb) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_FEEDS, "");
        List<Feed> feeds = new ArrayList<>();
        if (!TextUtils.isEmpty(raw)) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    Feed f = jsonToFeed(arr.getJSONObject(i));
                    if (f != null) feeds.add(f);
                }
            } catch (Exception e) { Log.w(TAG, "loadFeeds parse error", e); }
        }
        // 按添加时间倒序
        Collections.sort(feeds, new java.util.Comparator<Feed>() {
            public int compare(Feed a, Feed b) {
                return Long.compare(b.addedAt, a.addedAt);
            }
        });
        cb.onFeedsLoaded(feeds);
    }

    /** 同步获取所有订阅源 (用于 UI 列表) */
    public static Feed[] getSubscriptions(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_FEEDS, "");
        List<Feed> feeds = new ArrayList<>();
        if (!TextUtils.isEmpty(raw)) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    Feed f = jsonToFeed(arr.getJSONObject(i));
                    if (f != null) feeds.add(f);
                }
            } catch (Exception e) { Log.w(TAG, "getSubscriptions parse", e); }
        }
        // 按添加时间倒序
        Collections.sort(feeds, new java.util.Comparator<Feed>() {
            public int compare(Feed a, Feed b) {
                return Long.compare(b.addedAt, a.addedAt);
            }
        });
        return feeds.toArray(new Feed[0]);
    }

    /** 添加订阅源 (解析验证) */
    public static void addFeed(Context ctx, String url, ParseCallback cb) {
        if (TextUtils.isEmpty(url)) { cb.onError("URL 为空"); return; }
        EXEC.execute(new Runnable() { public void run() {
            try {
                ParseResult res = parseFeed(url);
                if (res == null || res.feed == null) { cb.onError("解析失败: 无效的 RSS/Atom"); return; }
                // 保存 Feed
                SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
                String raw = sp.getString(PREF_FEEDS, "");
                org.json.JSONArray arr = TextUtils.isEmpty(raw) ? new org.json.JSONArray() : new org.json.JSONArray(raw);
                // 去重 (同 URL)
                for (int i = 0; i < arr.length(); i++) {
                    if (url.equals(arr.getJSONObject(i).optString("url"))) {
                        cb.onError("已订阅该源"); return;
                    }
                }
                arr.put(feedToJson(res.feed));
                sp.edit().putString(PREF_FEEDS, arr.toString()).apply();
                // 保存 Episodes
                saveEpisodes(ctx, res.feed.id, res.episodes);
                cb.onFeedParsed(res.feed, res.episodes);
            } catch (Exception e) {
                Log.w(TAG, "addFeed failed", e);
                cb.onError("添加失败: " + e.getMessage());
            }
        }});
    }

    /** 删除订阅源 */
    public static void removeFeed(Context ctx, String feedId) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_FEEDS, "");
        if (TextUtils.isEmpty(raw)) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            org.json.JSONArray narr = new org.json.JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (!feedId.equals(arr.getJSONObject(i).optString("id"))) narr.put(arr.getJSONObject(i));
            }
            sp.edit().putString(PREF_FEEDS, narr.toString()).apply();
            // 删除该源的 episodes
            sp.edit().remove(PREF_EPISODES + "_" + feedId).apply();
            // 删除进度
            removeProgress(ctx, feedId);
        } catch (Exception e) { Log.w(TAG, "removeFeed", e); }
    }

    /** 刷新单个源 */
    public static void refreshFeed(Context ctx, String feedId, ParseCallback cb) {
        loadFeeds(ctx, new FeedCallback() {
            public void onFeedsLoaded(List<Feed> feeds) {
                final Feed target;
                Feed t = null;
                for (Feed f : feeds) if (feedId.equals(f.id)) { t = f; break; }
                target = t;
                if (target == null) { cb.onError("源不存在"); return; }
                EXEC.execute(new Runnable() { public void run() {
                    try {
                        ParseResult res = parseFeed(target.url);
                        if (res == null) { cb.onError("刷新解析失败"); return; }
                        // 保留原有 ID
                        res.feed.id = target.id;
                        res.feed.addedAt = target.addedAt;
                        res.feed.lastUpdated = System.currentTimeMillis();
                        // 更新 feed
                        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
                        String raw = sp.getString(PREF_FEEDS, "");
                        org.json.JSONArray arr = new org.json.JSONArray(raw);
                        for (int i = 0; i < arr.length(); i++) {
                            if (feedId.equals(arr.getJSONObject(i).optString("id"))) {
                                arr.put(i, feedToJson(res.feed));
                                break;
                            }
                        }
                        sp.edit().putString(PREF_FEEDS, arr.toString()).apply();
                        // 合并 episodes (去重 by GUID)
                        List<Episode> existing = loadEpisodes(ctx, feedId);
                        Map<String, Episode> epMap = new HashMap<>();
                        for (Episode e : existing) epMap.put(e.guid, e);
                        for (Episode e : res.episodes) epMap.put(e.guid, e);
                        List<Episode> merged = new ArrayList<>(epMap.values());
                        Collections.sort(merged, new java.util.Comparator<Episode>() {
                        public int compare(Episode a, Episode b) {
                            return Long.compare(b.pubDate, a.pubDate);
                        }
                    });
                        saveEpisodes(ctx, feedId, merged);
                        cb.onFeedParsed(res.feed, merged);
                    } catch (Exception e) {
                        cb.onError("刷新失败: " + e.getMessage());
                    }
                }});
            }
            public void onError(String error) { cb.onError(error); }
        });
    }

    /** 刷新所有启用的源 */
    public static void refreshAll(Context ctx, final Runnable onDone) {
        final Runnable onDoneFinal = onDone;
        loadFeeds(ctx, new FeedCallback() {
            public void onFeedsLoaded(List<Feed> feeds) {
                int t = 0;
                for (Feed f : feeds) if (f.enabled) t++;
                final int total = t;
                if (total == 0) { if (onDoneFinal != null) onDoneFinal.run(); return; }
                final int[] done = {0};
                for (Feed f : feeds) {
                    if (!f.enabled) continue;
                    refreshFeed(ctx, f.id, new ParseCallback() {
                        public void onFeedParsed(Feed feed, List<Episode> episodes) {
                            if (++done[0] >= total && onDoneFinal != null) onDoneFinal.run();
                        }
                        public void onError(String error) {
                            if (++done[0] >= total && onDoneFinal != null) onDoneFinal.run();
                        }
                    });
                }
            }
            public void onError(String error) { if (onDoneFinal != null) onDoneFinal.run(); }
        });
    }

    /** 加载某源的剧集 */
    public static List<Episode> loadEpisodes(Context ctx, String feedId) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_EPISODES + "_" + feedId, "");
        List<Episode> list = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return list;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                Episode e = jsonToEpisode(arr.getJSONObject(i));
                if (e != null) list.add(e);
            }
        } catch (Exception e) { Log.w(TAG, "loadEpisodes parse", e); }
        Collections.sort(list, new java.util.Comparator<Episode>() {
            public int compare(Episode a, Episode b) {
                return Long.compare(b.pubDate, a.pubDate);
            }
        });
        return list;
    }

    /** 获取最新一集 (同步加载缓存) */
    public static void getLatestEpisode(Context ctx, String feedUrl, EpisodeCallback cb) {
        String feedId = "feed_" + Math.abs(feedUrl.hashCode());
        new Thread(new Runnable() {
            public void run() {
                List<Episode> eps = loadEpisodes(ctx, feedId);
                Episode latest = eps.isEmpty() ? null : eps.get(0);
                final Episode f = latest;
                if (ctx instanceof android.app.Activity) {
                    ((android.app.Activity) ctx).runOnUiThread(new Runnable() {
                        public void run() {
                            if (f != null) cb.onEpisodesLoaded(java.util.Collections.singletonList(f));
                            else cb.onError("无缓存剧集");
                        }
                    });
                }
            }
        }).start();
    }

    /** 获取播放进度 */
    public static PlaybackProgress getProgress(Context ctx, String episodeId) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_PROGRESS + "_" + episodeId, "");
        if (TextUtils.isEmpty(raw)) return new PlaybackProgress();
        try {
            org.json.JSONObject o = new org.json.JSONObject(raw);
            PlaybackProgress p = new PlaybackProgress();
            p.episodeId = episodeId;
            p.positionMs = o.optLong("pos", 0);
            p.durationMs = o.optLong("dur", 0);
            p.lastPlayed = o.optLong("last", 0);
            p.completed = o.optBoolean("comp", false);
            return p;
        } catch (Exception e) { return new PlaybackProgress(); }
    }

    /** 保存播放进度 */
    public static void saveProgress(Context ctx, PlaybackProgress p) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("pos", p.positionMs);
            o.put("dur", p.durationMs);
            o.put("last", p.lastPlayed);
            o.put("comp", p.completed);
            sp.edit().putString(PREF_PROGRESS + "_" + p.episodeId, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** 导出 OPML */
    public static String exportOpml(Context ctx) {
        loadFeeds(ctx, new FeedCallback() {
            public void onFeedsLoaded(List<Feed> feeds) {}
            public void onError(String error) {}
        });
        // 简化: 同步读取
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_FEEDS, "");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<opml version=\"2.0\">\n<head><title>MusicFusion Podcasts</title></head>\n<body>\n");
        if (!TextUtils.isEmpty(raw)) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    String title = o.optString("title", "Untitled");
                    String url = o.optString("url", "");
                    sb.append(String.format("  <outline type=\"rss\" text=\"%s\" title=\"%s\" xmlUrl=\"%s\" htmlUrl=\"%s\"/>\n",
                        escapeXml(title), escapeXml(title), escapeXml(url), escapeXml(o.optString("link", ""))));
                }
            } catch (Exception ignored) {}
        }
        sb.append("</body>\n</opml>");
        return sb.toString();
    }

    /** 导入 OPML (简化版, 仅提取 xmlUrl) */
    public static void importOpml(Context ctx, String opmlText, final Runnable onDone) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new java.io.StringReader(opmlText));
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && "outline".equals(parser.getName())) {
                        String type = parser.getAttributeValue(null, "type");
                        String xmlUrl = parser.getAttributeValue(null, "xmlUrl");
                        if ("rss".equals(type) && !TextUtils.isEmpty(xmlUrl)) {
                            // 异步添加, 忽略重复错误
                            addFeed(ctx, xmlUrl, new ParseCallback() {
                                public void onFeedParsed(Feed feed, List<Episode> episodes) {}
                                public void onError(String error) {}
                            });
                        }
                    }
                    eventType = parser.next();
                }
            } catch (Exception e) { Log.w(TAG, "importOpml", e); }
            if (onDone != null) onDone.run();
        }});
    }

    // ===== 内部解析 =====
    private static class ParseResult {
        Feed feed;
        List<Episode> episodes = new ArrayList<>();
    }

    private static ParseResult parseFeed(String feedUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(feedUrl).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "MusicFusion/13.0 Podcast");
        int code = conn.getResponseCode();
        if (code >= 400) throw new Exception("HTTP " + code);
        InputStream is = conn.getInputStream();
        String xml = readStream(is);
        is.close();
        conn.disconnect();

        return parseXml(xml, feedUrl);
    }

    private static ParseResult parseXml(String xml, String feedUrl) throws XmlPullParserException, java.io.IOException {
        ParseResult res = new ParseResult();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(xml));
        int eventType = parser.getEventType();

        Feed feed = new Feed();
        feed.url = feedUrl;
        feed.id = "feed_" + Math.abs(feedUrl.hashCode());
        feed.addedAt = System.currentTimeMillis();

        Episode currentEp = null;
        boolean inItem = false;
        String currentTag = "";

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = name;
                if ("item".equals(name) || "entry".equals(name)) {
                    inItem = true;
                    currentEp = new Episode();
                    currentEp.feedId = feed.id;
                } else if (inItem) {
                    parseEpisodeField(currentEp, name, parser, "");
                } else {
                    parseFeedField(feed, name, parser);
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (("item".equals(name) || "entry".equals(name)) && currentEp != null) {
                    if (TextUtils.isEmpty(currentEp.guid)) currentEp.guid = currentEp.audioUrl;
                    if (TextUtils.isEmpty(currentEp.id)) currentEp.id = "ep_" + Math.abs(currentEp.guid.hashCode());
                    res.episodes.add(currentEp);
                    currentEp = null;
                    inItem = false;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                String text = parser.getText().trim();
                if (!TextUtils.isEmpty(text)) {
                    if (inItem && currentEp != null) {
                        parseEpisodeField(currentEp, currentTag, parser, text);
                        appendEpisodeField(currentEp, currentTag, text);
                    } else {
                        appendFeedField(feed, currentTag, text);
                    }
                }
            }
            eventType = parser.next();
        }

        res.feed = feed;
        return res;
    }

    private static void parseFeedField(Feed f, String tag, XmlPullParser parser) {
        // 属性优先 (如 itunes:image href)
        String href = parser.getAttributeValue(null, "href");
        String url = parser.getAttributeValue(null, "url");
        if ("image".equals(tag) || "itunes:image".equals(tag) || "logo".equals(tag) || "icon".equals(tag)) {
            if (!TextUtils.isEmpty(href)) f.imageUrl = href;
            else if (!TextUtils.isEmpty(url)) f.imageUrl = url;
        } else if ("link".equals(tag)) {
            if (!TextUtils.isEmpty(href)) f.link = href;
        } else if ("itunes:explicit".equals(tag)) {
            f.itunesExplicit = parser.getAttributeValue(null, "text");
        } else if ("itunes:category".equals(tag)) {
            f.itunesCategory = parser.getAttributeValue(null, "text");
        }
    }

    private static void appendFeedField(Feed f, String tag, String text) {
        if ("title".equals(tag)) f.title = text;
        else if ("description".equals(tag) || "subtitle".equals(tag) || "itunes:summary".equals(tag)) {
            if (TextUtils.isEmpty(f.description)) f.description = text;
        } else if ("author".equals(tag) || "itunes:author".equals(tag) || "managingEditor".equals(tag)) {
            f.author = text;
        } else if ("language".equals(tag)) f.language = text;
        else if ("copyright".equals(tag) || "rights".equals(tag)) f.copyright = text;
        else if ("lastBuildDate".equals(tag) || "pubDate".equals(tag) || "updated".equals(tag)) {
            f.lastUpdated = parseDate(text);
        }
    }

    private static void parseEpisodeField(Episode e, String tag, XmlPullParser parser, String text) {
        String href = parser.getAttributeValue(null, "href");
        String url = parser.getAttributeValue(null, "url");
        String type = parser.getAttributeValue(null, "type");
        String length = parser.getAttributeValue(null, "length");
        if ("enclosure".equals(tag) && !TextUtils.isEmpty(href)) {
            e.audioUrl = href;
            e.audioType = type;
            try { if (!TextUtils.isEmpty(length)) e.audioLength = Long.parseLong(length); } catch (Exception ignored) {}
        } else if ("itunes:image".equals(tag) && !TextUtils.isEmpty(href)) {
            e.imageUrl = href;
        } else if ("guid".equals(tag)) {
            String isPerma = parser.getAttributeValue(null, "isPermaLink");
            e.guid = text; // 将在 TEXT 事件中获取
        } else if ("itunes:duration".equals(tag)) {
            e.durationSec = parseDuration(text);
        } else if ("itunes:explicit".equals(tag)) {
            e.explicit = "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
        } else if ("itunes:season".equals(tag)) e.season = text;
        else if ("itunes:episode".equals(tag)) e.episodeNum = text;
    }

    private static void appendEpisodeField(Episode e, String tag, String text) {
        if ("title".equals(tag)) e.title = text;
        else if ("description".equals(tag) || "summary".equals(tag) || "itunes:summary".equals(tag) || "content:encoded".equals(tag)) {
            if (TextUtils.isEmpty(e.description)) e.description = text;
        } else if ("pubDate".equals(tag) || "published".equals(tag) || "updated".equals(tag) || "dc:date".equals(tag)) {
            e.pubDate = parseDate(text);
        } else if ("guid".equals(tag) && TextUtils.isEmpty(e.guid)) {
            e.guid = text;
        } else if ("link".equals(tag) && TextUtils.isEmpty(e.audioUrl)) {
            // 某些源用 link 作为音频链接
            if (text.toLowerCase().matches(".*\\.(mp3|m4a|ogg|flac|wav|aac|opus)(\\?.*)?$")) {
                e.audioUrl = text;
            }
        }
    }

    private static long parseDate(String text) {
        String[] formats = {
            "EEE, dd MMM yyyy HH:mm:ss Z",   // RFC 822
            "EEE, dd MMM yyyy HH:mm:ss z",   // RFC 822 无时区偏移
            "yyyy-MM-dd'T'HH:mm:ss'Z'",      // ISO 8601 UTC
            "yyyy-MM-dd'T'HH:mm:ssZ",        // ISO 8601
            "yyyy-MM-dd'T'HH:mm:ss",         // ISO 无时区
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        };
        for (String fmt : formats) {
            try {
                return new java.text.SimpleDateFormat(fmt, java.util.Locale.US).parse(text).getTime();
            } catch (Exception ignored) {}
        }
        return System.currentTimeMillis();
    }

    private static int parseDuration(String text) {
        // iTunes duration: "HH:MM:SS" 或 "MM:SS" 或 秒数
        if (TextUtils.isEmpty(text)) return 0;
        text = text.trim();
        if (text.matches("\\d+")) return Integer.parseInt(text); // 纯秒数
        String[] parts = text.split(":");
        try {
            if (parts.length == 3) return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
            if (parts.length == 2) return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        return 0;
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&").replace("<", "<").replace(">", ">")
                .replace("\"", "\"").replace("'", "&apos;");
    }

    // JSON 序列化
    private static org.json.JSONObject feedToJson(Feed f) throws Exception {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id", f.id); o.put("url", f.url); o.put("title", f.title);
        o.put("author", f.author); o.put("description", f.description);
        o.put("imageUrl", f.imageUrl); o.put("link", f.link);
        o.put("language", f.language); o.put("copyright", f.copyright);
        o.put("itunesExplicit", f.itunesExplicit); o.put("itunesCategory", f.itunesCategory);
        o.put("lastUpdated", f.lastUpdated); o.put("addedAt", f.addedAt);
        o.put("enabled", f.enabled); o.put("updateIntervalHours", f.updateIntervalHours);
        return o;
    }

    private static Feed jsonToFeed(org.json.JSONObject o) {
        Feed f = new Feed();
        f.id = o.optString("id"); f.url = o.optString("url"); f.title = o.optString("title");
        f.author = o.optString("author"); f.description = o.optString("description");
        f.imageUrl = o.optString("imageUrl"); f.link = o.optString("link");
        f.language = o.optString("language"); f.copyright = o.optString("copyright");
        f.itunesExplicit = o.optString("itunesExplicit"); f.itunesCategory = o.optString("itunesCategory");
        f.lastUpdated = o.optLong("lastUpdated", 0); f.addedAt = o.optLong("addedAt", 0);
        f.enabled = o.optBoolean("enabled", true); f.updateIntervalHours = o.optInt("updateIntervalHours", 6);
        return f;
    }

    private static org.json.JSONObject episodeToJson(Episode e) throws Exception {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id", e.id); o.put("feedId", e.feedId); o.put("title", e.title);
        o.put("description", e.description); o.put("audioUrl", e.audioUrl);
        o.put("audioType", e.audioType); o.put("audioLength", e.audioLength);
        o.put("pubDate", e.pubDate); o.put("imageUrl", e.imageUrl);
        o.put("guid", e.guid); o.put("durationSec", e.durationSec);
        o.put("season", e.season); o.put("episodeNum", e.episodeNum);
        o.put("explicit", e.explicit); o.put("podcastTitle", e.podcastTitle);
        return o;
    }

    private static Episode jsonToEpisode(org.json.JSONObject o) {
        Episode e = new Episode();
        e.id = o.optString("id"); e.feedId = o.optString("feedId"); e.title = o.optString("title");
        e.description = o.optString("description"); e.audioUrl = o.optString("audioUrl");
        e.audioType = o.optString("audioType"); e.audioLength = o.optLong("audioLength", 0);
        e.pubDate = o.optLong("pubDate", 0); e.imageUrl = o.optString("imageUrl");
        e.guid = o.optString("guid"); e.durationSec = o.optInt("durationSec", 0);
        e.season = o.optString("season"); e.episodeNum = o.optString("episodeNum");
        e.explicit = o.optBoolean("explicit", false); e.podcastTitle = o.optString("podcastTitle", "");
        return e;
    }

    private static void saveEpisodes(Context ctx, String feedId, List<Episode> episodes) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Episode e : episodes) arr.put(episodeToJson(e));
            sp.edit().putString(PREF_EPISODES + "_" + feedId, arr.toString()).apply();
        } catch (Exception e) { Log.w(TAG, "saveEpisodes", e); }
    }

    private static void removeProgress(Context ctx, String feedId) {
        // 简化: 不清理单集进度, 体积很小
    }

    /** 清除所有播放进度 */
    public static void clearPlayed(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith(PREF_PROGRESS + "_")) {
                sp.edit().remove(key).apply();
            }
        }
    }
}