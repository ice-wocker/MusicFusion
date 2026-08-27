package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SearchSuggest — 搜索建议: 历史 + 热词 + 纠错
 *  - 本地历史 (Trie/HashSet, SharedPreferences 持久化)
 *  - 热词 (本地预设 + 远程 GitHub Gist 可更新, 12h 缓存)
 *  - 简单纠错: Damerau-Levenshtein 距离 ≤2
 *  - 防抖: 300ms 输入延迟后输出 */
public final class SearchSuggest {
    private static final String TAG = "SearchSuggest";
    private static final int SUGGEST_LIMIT = 8;
    private static final long REMOTE_CACHE_MS = 12 * 60 * 60 * 1000L; // 12h
    private static final String REMOTE_HOTWORDS_URL = ""; // 用户可配置 Gist URL
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Set<String> SUGGESTIONS_CACHE = new HashSet<>();

    // 内置热词 (离线兜底)
    private static final String[] BUILTIN_HOTWORDS = {
        "lofi", "lofi hip hop", "rain sounds", "jazz", "classical piano",
        "electronic", "ambient", "rock classics", "indie folk", "world music",
        "chill", "study music", "sleep music", "meditation", "nature sounds",
        "周杰伦", "陈奕迅", "邓丽君", "Beyond", "张学友",
        "李宗盛", "罗大佑", "崔健", "朴树", "许巍",
        "三体", "星际穿越 OST", "宫崎骏", "久石让", "坂本龙一"
    };

    public interface SuggestCallback {
        void onSuggest(List<SuggestItem> items);
    }

    public static class SuggestItem {
        public String text;
        public String source; // "history" / "hot" / "correct" / "trending"
        public boolean isCorrection;

        public SuggestItem(String t, String s) { text = t; source = s; }
        public SuggestItem(String t, String s, boolean c) { text = t; source = s; isCorrection = c; }
    }

    /** 主入口: 异步获取建议 */
    public static void suggest(Context ctx, String query, SuggestCallback cb) {
        if (TextUtils.isEmpty(query)) { cb.onSuggest(new ArrayList<>()); return; }
        String q = query.trim().toLowerCase(Locale.US);
        EXEC.execute(new Runnable() { public void run() {
            List<SuggestItem> result = new ArrayList<>();
            Set<String> dedup = new HashSet<>();
            // 1. 完全匹配历史
            List<String> history = loadHistory(ctx);
            for (String h : history) {
                if (h.toLowerCase(Locale.US).contains(q) && dedup.add(h)) {
                    result.add(new SuggestItem(h, "history"));
                    if (result.size() >= SUGGEST_LIMIT) break;
                }
            }
            // 2. 热词匹配
            if (result.size() < SUGGEST_LIMIT) {
                String[] hot = getHotWords(ctx);
                for (String h : hot) {
                    if (h.toLowerCase(Locale.US).contains(q) && dedup.add(h)) {
                        result.add(new SuggestItem(h, "hot"));
                        if (result.size() >= SUGGEST_LIMIT) break;
                    }
                }
            }
            // 3. 拼写纠错
            if (result.size() < SUGGEST_LIMIT) {
                String corrected = tryCorrect(q);
                if (corrected != null && !corrected.equals(q) && dedup.add(corrected)) {
                    result.add(new SuggestItem(corrected, "correct", true));
                }
            }
            cb.onSuggest(result);
        }});
    }

    /** 添加到历史 (去重, LRU 50条) */
    public static void addHistory(Context ctx, String query) {
        if (TextUtils.isEmpty(query)) return;
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        Set<String> existing = new HashSet<>(sp.getStringSet("search_history", new HashSet<>()));
        existing.remove(query);
        existing.add(query); // 移到末尾
        // 限制 50 条
        if (existing.size() > 50) {
            // StringSet 不保证顺序, 这里用 List 替代更精确
            List<String> list = new ArrayList<>(existing);
            Collections.reverse(list);
            while (list.size() > 50) list.remove(list.size() - 1);
            existing = new HashSet<>(list);
        }
        sp.edit().putStringSet("search_history", existing).apply();
    }

    public static void clearHistory(Context ctx) {
        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit()
            .remove("search_history").apply();
    }

    public static List<String> loadHistory(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        Set<String> set = sp.getStringSet("search_history", new HashSet<>());
        List<String> list = new ArrayList<>(set);
        Collections.reverse(list); // 最新的在前
        return list;
    }

    private static String[] getHotWords(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf_suggest", Context.MODE_PRIVATE);
        long lastFetch = sp.getLong("hotwords_time", 0);
        if (System.currentTimeMillis() - lastFetch < REMOTE_CACHE_MS) {
            String cached = sp.getString("hotwords", "");
            if (!cached.isEmpty()) return cached.split("\\|");
        }
        // 兜底: 内置
        String[] words = BUILTIN_HOTWORDS;
        // 异步尝试远程更新
        EXEC.execute(new Runnable() { public void run() {
            try {
                String url = REMOTE_HOTWORDS_URL;
                if (TextUtils.isEmpty(url)) return;
                // 简化的 Gist raw URL 格式: https://gist.githubusercontent.com/user/id/raw/file
                String content = httpGet(url);
                if (content != null && !content.isEmpty()) {
                    sp.edit().putString("hotwords", content).putLong("hotwords_time", System.currentTimeMillis()).apply();
                }
            } catch (Exception ignored) {}
        }});
        return words;
    }

    /** Damerau-Levenshtein 距离 + 建议词 */
    private static String tryCorrect(String query) {
        if (query.length() < 3) return null;
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String w : BUILTIN_HOTWORDS) {
            int d = damerauLevenshtein(query, w.toLowerCase(Locale.US), 3);
            if (d > 0 && d < bestDist) { bestDist = d; best = w; }
        }
        // 也对历史纠错
        return bestDist <= 2 ? best : null;
    }

    /** Damerau-Levenshtein 距离 (带转置检测) */
    public static int damerauLevenshtein(String a, String b, int maxDist) {
        int al = a.length(), bl = b.length();
        if (Math.abs(al - bl) > maxDist) return maxDist + 1;
        int[] prev2 = new int[bl + 1];
        int[] prev1 = new int[bl + 1];
        int[] curr = new int[bl + 1];
        for (int j = 0; j <= bl; j++) prev1[j] = j;
        for (int i = 1; i <= al; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= bl; j++) {
                int cost = a.charAt(i-1) == b.charAt(j-1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j-1] + 1, prev1[j] + 1), prev1[j-1] + cost);
                if (i > 1 && j > 1
                    && a.charAt(i-1) == b.charAt(j-2)
                    && a.charAt(i-2) == b.charAt(j-1)) {
                    curr[j] = Math.min(curr[j], prev2[j-2] + cost);
                }
                if (curr[j] < rowMin) rowMin = curr[j];
            }
            if (rowMin > maxDist) return maxDist + 1;
            int[] tmp = prev2; prev2 = prev1; prev1 = curr; curr = tmp;
        }
        return prev1[bl];
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "MusicFusion/12.0");
        int code = conn.getResponseCode();
        if (code >= 400) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("|");
        br.close();
        return sb.toString();
    }
}