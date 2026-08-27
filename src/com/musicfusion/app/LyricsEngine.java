package com.musicfusion.app;

import android.content.Context;
import android.os.AsyncTask;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LyricsEngine — 多源歌词聚合 + 时间轴同步 */
public final class LyricsEngine {
    private static final String TAG = "LyricsEngine";
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Map<String, LyricsResult> CACHE = new ConcurrentHashMap<String, LyricsResult>(32);
    private static final int CACHE_MAX = 100;

    public static class LyricsResult {
        public String title, artist, album, by, source;
        public long offsetMs = 0;
        public List<Line> lines = new ArrayList<Line>();
        public boolean synced = false;
        public String raw;

        public static class Line implements Comparable<Line> {
            public long timeMs;
            public String text;
            public Line(long t, String tx) { timeMs = t; text = tx; }
            public int compareTo(Line o) { return Long.compare(timeMs, o.timeMs); }
        }

        public int getCurrentLineIndex(long positionMs) {
            if (lines.isEmpty()) return -1;
            int lo = 0, hi = lines.size() - 1, ans = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (lines.get(mid).timeMs <= positionMs) { ans = mid; lo = mid + 1; }
                else hi = mid - 1;
            }
            return ans;
        }

        public List<Line> getVisibleLines(int centerIdx, int radius) {
            List<Line> out = new ArrayList<Line>();
            int start = Math.max(0, centerIdx - radius);
            int end = Math.min(lines.size() - 1, centerIdx + radius);
            for (int i = start; i <= end; i++) out.add(lines.get(i));
            return out;
        }
    }

    public interface Callback {
        void onResult(LyricsResult result);
        void onError(String error);
    }

    public static void search(final Context ctx, final String title, final String artist, final long durationMs, final Callback cb) {
        String key = makeKey(title, artist);
        LyricsResult cached = CACHE.get(key);
        if (cached != null) { cb.onResult(cached); return; }

        EXEC.execute(new Runnable() { public void run() {
            doSearch(title, artist, durationMs, cb);
        }});
    }

    private static void doSearch(String title, String artist, long durationMs, Callback cb) {
        String key = makeKey(title, artist);
        LyricsResult result = null;
        String[] errors = new String[4];

        try { result = fetchLrclib(title, artist, durationMs); } catch (Exception e) { errors[0] = e.getMessage(); }
        if (result != null && result.synced) { putCache(key, result); cb.onResult(result); return; }

        try { result = fetchNetease(title, artist); } catch (Exception e) { errors[1] = e.getMessage(); }
        if (result != null && result.synced) { putCache(key, result); cb.onResult(result); return; }

        try { result = fetchQQMusic(title, artist); } catch (Exception e) { errors[2] = e.getMessage(); }
        if (result != null && result.synced) { putCache(key, result); cb.onResult(result); return; }

        if (result != null) {
            putCache(key, result);
            cb.onResult(result);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < errors.length; i++) if (errors[i] != null) sb.append("源").append(i+1).append(": ").append(errors[i]).append("; ");
            cb.onError(sb.length() > 0 ? sb.toString() : "未找到歌词");
        }
    }

    public static LyricsResult parseLrc(String lrcText, long offsetMs) {
        LyricsResult r = new LyricsResult();
        r.raw = lrcText;
        r.offsetMs = offsetMs;
        String[] rawLines = lrcText.split("\\r?\\n");
        Pattern p = Pattern.compile("\\[(\\d{1,2}):(\\d{2})[.:](\\d{1,3})\\]");
        for (String raw : rawLines) {
            raw = raw.trim();
            if (raw.isEmpty()) continue;
            if (raw.startsWith("[ti:")) r.title = extractTag(raw);
            else if (raw.startsWith("[ar:")) r.artist = extractTag(raw);
            else if (raw.startsWith("[al:")) r.album = extractTag(raw);
            else if (raw.startsWith("[by:")) r.by = extractTag(raw);
            else if (raw.startsWith("[offset:")) {
                try { r.offsetMs = Long.parseLong(extractTag(raw)); } catch (Exception ignored) {}
            } else if (raw.startsWith("[re:") || raw.startsWith("[ve:")) {
                // ignore
            } else {
                Matcher m = p.matcher(raw);
                List<Long> times = new ArrayList<Long>();
                String text = raw;
                while (m.find()) {
                    int min = Integer.parseInt(m.group(1));
                    int sec = Integer.parseInt(m.group(2));
                    String msStr = m.group(3);
                    int ms = msStr.length() == 3 ? Integer.parseInt(msStr) : Integer.parseInt(msStr) * 10;
                    times.add((long) min * 60000 + sec * 1000 + ms);
                    text = text.replaceFirst("\\[\\d{1,2}:\\d{2}[.:]\\d{1,3}\\]", "");
                }
                text = text.trim();
                if (!text.isEmpty()) {
                    for (long t : times) r.lines.add(new LyricsResult.Line(t + r.offsetMs, text));
                } else if (!times.isEmpty()) {
                    for (long t : times) r.lines.add(new LyricsResult.Line(t + r.offsetMs, ""));
                }
            }
        }
        Collections.sort(r.lines);
        r.synced = !r.lines.isEmpty() && r.lines.get(0).timeMs >= 0;
        r.source = "local";
        return r;
    }

    private static String extractTag(String line) {
        int start = line.indexOf(':');
        int end = line.lastIndexOf(']');
        if (start > 0 && end > start) return line.substring(start + 1, end).trim();
        return "";
    }

    private static String makeKey(String title, String artist) {
        return (title + "|" + artist).toLowerCase(Locale.US).replaceAll("[^a-z0-9\\u4e00-\\u9fff|]", "_");
    }

    private static void putCache(String key, LyricsResult r) {
        if (CACHE.size() >= CACHE_MAX) {
            String first = CACHE.keySet().iterator().next();
            CACHE.remove(first);
        }
        CACHE.put(key, r);
    }

    private static LyricsResult fetchLrclib(String title, String artist, long durationMs) throws Exception {
        String url = "https://lrclib.net/api/search?q=" + URLEncoder.encode(title + " " + artist, "UTF-8");
        String json = httpGet(url);
        JSONArray arr = new JSONArray(json);
        if (arr.length() == 0) return null;
        JSONObject best = null;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            long dur = o.optLong("duration", 0);
            long diff = Math.abs(dur - durationMs);
            if (diff < bestDiff) { bestDiff = diff; best = o; }
        }
        if (best == null) return null;
        String synced = best.optString("syncedLyrics", "");
        String plain = best.optString("plainLyrics", "");
        String useText = !TextUtils.isEmpty(synced) ? synced : plain;
        if (TextUtils.isEmpty(useText)) return null;
        LyricsResult r = parseLrc(useText, 0);
        r.title = best.optString("trackName", title);
        r.artist = best.optString("artistName", artist);
        r.album = best.optString("albumName", "");
        r.source = "lrclib";
        return r;
    }

    private static LyricsResult fetchNetease(String title, String artist) throws Exception {
        return null;
    }

    private static LyricsResult fetchQQMusic(String title, String artist) throws Exception {
        return null;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "MusicFusion/12.0");
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
            code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    public static void clearCache() { CACHE.clear(); }
}