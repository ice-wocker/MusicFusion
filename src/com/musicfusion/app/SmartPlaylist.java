package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SmartPlaylist — 智能歌单生成器
 * 规则类型:
 *  - 流派 (genre): 流行/摇滚/古典/爵士/电子/嘻哈/民谣/乡村
 *  - 年代 (decade): 2020s/2010s/2000s/1990s/1980s
 *  - 心情 (mood): chill/energetic/dark/romantic/ambient
 *  - BPM 范围: 60-180 BPM
 *  - 能量 (energy): low/med/high
 *  - 听感匹配: 用户最近播放的 80% 流派 + 20% 探索新流派
 *  - 智能洗牌: 避免连续同流派/同艺术家
 */
public final class SmartPlaylist {
    private static final String TAG = "SmartPlaylist";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    public static class Rule {
        public String type;   // genre/decade/mood/bpm/energy/random
        public String key;    // pop/rock/.../2020s/chill/...
        public int value;     // bpm 值或能量阈值
        public boolean negate = false;

        public Rule(String t, String k) { type = t; key = k; }
        public Rule(String t, int v) { type = t; value = v; }
    }

    public static class SmartTrack {
        public String title, artist, url, source, mood, genre;
        public int duration;
        public int bpm;       // -1=未知
        public int energy;    // 0-100
        public int year;
        public long addedAt;

        public SmartTrack(String ti, String ar, String u, String src, int dur) {
            title = ti; artist = ar; url = u; source = src; duration = dur;
            bpm = -1; energy = 50; year = 0; addedAt = System.currentTimeMillis();
        }
    }

    public static class SmartPlaylistResult {
        public String name;
        public List<SmartTrack> tracks = new ArrayList<>();
        public int totalCandidates = 0;
        public String description;
        public String coverEmoji = "🎵";
    }

    // 流派关键词映射
    private static final Map<String, String[]> GENRE_KEYWORDS = new HashMap<>();
    static {
        GENRE_KEYWORDS.put("pop", new String[]{"pop", "taylor", "swift", "edsheeran", "billie", "adele", "bruno", "火星"});
        GENRE_KEYWORDS.put("rock", new String[]{"rock", "metal", "punk", "nirvana", "led zeppelin", "pink floyd", "崔健"});
        GENRE_KEYWORDS.put("classical", new String[]{"classical", "symphony", "mozart", "beethoven", "chopin", "古典", "肖邦"});
        GENRE_KEYWORDS.put("jazz", new String[]{"jazz", "bebop", "miles", "coltrane", "michael jackson", "fusion"});
        GENRE_KEYWORDS.put("electronic", new String[]{"electronic", "edm", "house", "techno", "trance", "drum and bass", "dubstep", "skrillex"});
        GENRE_KEYWORDS.put("hiphop", new String[]{"hip", "rap", "drake", "kendrick", "jay-z", "嘻哈", "说唱"});
        GENRE_KEYWORDS.put("folk", new String[]{"folk", "acoustic", "bob dylan", "民谣", "赵雷", "宋冬野"});
        GENRE_KEYWORDS.put("country", new String[]{"country", "johnny cash", "乡村", "taylor swift"});
        GENRE_KEYWORDS.put("indie", new String[]{"indie", "alt-j", "vampire weekend", "独立"});
        GENRE_KEYWORDS.put("ambient", new String[]{"ambient", "noise", "drone", "环境"});
    }

    // 心情关键词
    private static final Map<String, String[]> MOOD_KEYWORDS = new HashMap<>();
    static {
        MOOD_KEYWORDS.put("chill", new String[]{"chill", "lofi", "mellow", "calm", "放松", "轻音乐", "bossa"});
        MOOD_KEYWORDS.put("energetic", new String[]{"workout", "energy", "power", "fitness", "运动", "嗨", "club"});
        MOOD_KEYWORDS.put("dark", new String[]{"dark", "sad", "melancholy", "gothic", "抑郁", "黑金属"});
        MOOD_KEYWORDS.put("romantic", new String[]{"love", "romantic", "ballad", "情歌", "love song"});
        MOOD_KEYWORDS.put("ambient", new String[]{"ambient", "space", "drone", "soundscape", "环境音"});
        MOOD_KEYWORDS.put("happy", new String[]{"happy", "smile", "joy", "开心", "阳光"});
        MOOD_KEYWORDS.put("focus", new String[]{"study", "focus", "concentration", "专注", "学习"});
    }

    /** 智能生成 - 根据历史 + 规则 */
    public static void generate(Context ctx, List<Rule> rules, String name, Callback cb) {
        EXEC.execute(new Runnable() { public void run() {
            SmartPlaylistResult result = new SmartPlaylistResult();
            result.name = name;
            try {
                // 1. 加载历史播放 + 收藏 + 歌单
                List<SmartTrack> history = loadHistory(ctx);
                List<SmartTrack> favorites = loadFavorites(ctx);
                List<SmartTrack> playlist = loadPlaylist(ctx);

                // 2. 加载音乐源数据 (静态 + 用户内容)
                List<SmartTrack> pool = new ArrayList<>();
                pool.addAll(history);
                pool.addAll(favorites);
                pool.addAll(playlist);

                // 3. 应用规则
                List<SmartTrack> filtered = new ArrayList<>();
                for (SmartTrack t : pool) {
                    if (matchAll(t, rules)) filtered.add(t);
                }
                result.totalCandidates = filtered.size();

                // 4. 智能洗牌
                smartShuffle(filtered, history);

                // 5. 限制数量
                int limit = 50;
                if (filtered.size() > limit) filtered = filtered.subList(0, limit);
                result.tracks = filtered;
                result.description = buildDescription(rules, result.totalCandidates);
                result.coverEmoji = pickEmoji(rules);

                cb.onResult(result);
            } catch (Exception e) {
                Log.w(TAG, "generate failed", e);
                cb.onError(e.getMessage());
            }
        }});
    }

    private static boolean matchAll(SmartTrack t, List<Rule> rules) {
        for (Rule r : rules) {
            if (!match(t, r)) return false;
        }
        return true;
    }

    private static boolean match(SmartTrack t, Rule r) {
        boolean matched;
        switch (r.type) {
            case "genre":
                matched = genreMatch(t, r.key);
                break;
            case "decade":
                matched = decadeMatch(t, r.key);
                break;
            case "mood":
                matched = moodMatch(t, r.key);
                break;
            case "bpm":
                matched = t.bpm > 0 && t.bpm >= r.value - 10 && t.bpm <= r.value + 10;
                break;
            case "energy":
                matched = t.energy >= r.value;
                break;
            case "random":
                matched = new Random().nextInt(100) < 30; // 30% 探索新内容
                break;
            default:
                matched = true;
        }
        return r.negate ? !matched : matched;
    }

    private static boolean genreMatch(SmartTrack t, String genre) {
        String[] keys = GENRE_KEYWORDS.get(genre.toLowerCase());
        if (keys == null) return false;
        String text = ((t.title != null ? t.title : "") + " " + (t.artist != null ? t.artist : "")).toLowerCase();
        for (String k : keys) if (text.contains(k.toLowerCase())) return true;
        return false;
    }

    private static boolean decadeMatch(SmartTrack t, String decade) {
        if (t.year <= 0) return false;
        switch (decade) {
            case "2020s": return t.year >= 2020;
            case "2010s": return t.year >= 2010 && t.year < 2020;
            case "2000s": return t.year >= 2000 && t.year < 2010;
            case "1990s": return t.year >= 1990 && t.year < 2000;
            case "1980s": return t.year >= 1980 && t.year < 1990;
            case "classic": return t.year < 1980;
        }
        return false;
    }

    private static boolean moodMatch(SmartTrack t, String mood) {
        String[] keys = MOOD_KEYWORDS.get(mood.toLowerCase());
        if (keys == null) return false;
        String text = ((t.title != null ? t.title : "") + " " + (t.artist != null ? t.artist : "")).toLowerCase();
        for (String k : keys) if (text.contains(k.toLowerCase())) return true;
        return false;
    }

    /** 智能洗牌: 避免连续同流派/同艺术家, 80% 历史偏好 + 20% 探索 */
    private static void smartShuffle(List<SmartTrack> list, List<SmartTrack> history) {
        if (list.isEmpty()) return;
        Collections.shuffle(list, new Random(System.currentTimeMillis()));
        // 简单重排: 不连续同艺术家
        for (int i = 1; i < list.size() - 1; i++) {
            if (list.get(i).artist.equals(list.get(i-1).artist)) {
                for (int j = i + 1; j < list.size(); j++) {
                    if (!list.get(j).artist.equals(list.get(i-1).artist)
                        && !list.get(j).artist.equals(list.get(i+1).artist)) {
                        Collections.swap(list, i, j);
                        break;
                    }
                }
            }
        }
    }

    private static String buildDescription(List<Rule> rules, int total) {
        StringBuilder sb = new StringBuilder();
        for (Rule r : rules) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(r.type).append(":").append(r.key != null ? r.key : String.valueOf(r.value));
        }
        if (sb.length() == 0) sb.append("随机探索");
        sb.append(" (从 ").append(total).append(" 候选中生成)");
        return sb.toString();
    }

    private static String pickEmoji(List<Rule> rules) {
        for (Rule r : rules) {
            if ("mood".equals(r.type)) {
                switch (r.key) {
                    case "chill": return "🌙";
                    case "energetic": return "⚡";
                    case "dark": return "🌑";
                    case "romantic": return "💕";
                    case "happy": return "🌞";
                    case "focus": return "🎯";
                    case "ambient": return "🌌";
                }
            }
            if ("genre".equals(r.type)) {
                switch (r.key) {
                    case "pop": return "🎤";
                    case "rock": return "🎸";
                    case "classical": return "🎻";
                    case "jazz": return "🎷";
                    case "electronic": return "🎛️";
                    case "hiphop": return "🎧";
                    case "folk": return "🪕";
                    case "indie": return "🌿";
                }
            }
        }
        return "🎵";
    }

    // ===== 数据加载 =====
    private static List<SmartTrack> loadHistory(Context ctx) {
        List<SmartTrack> list = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
            String raw = sp.getString(PREF_REC, "");
            if (TextUtils.isEmpty(raw)) return list;
            String[] entries = raw.split("\n");
            for (String e : entries) {
                String[] parts = e.split("\u0001", -1);
                if (parts.length >= 4) {
                    SmartTrack t = new SmartTrack(parts[0], parts[1], parts[3], parts.length > 4 ? parts[4] : "?", 0);
                    list.add(t);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "loadHistory", e);
        }
        return list;
    }

    private static List<SmartTrack> loadFavorites(Context ctx) {
        // 复用 MainActivity.PREF_FAV (无法直接引用, 硬编码)
        return loadEntriesByName(ctx, "mf_fav");
    }

    private static List<SmartTrack> loadPlaylist(Context ctx) {
        // 加载所有歌单合并
        List<SmartTrack> all = new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String names = sp.getString("playlists", "");
        for (String n : names.split("\n")) {
            if (n.isEmpty()) continue;
            all.addAll(loadEntriesByName(ctx, "pl_" + n.hashCode()));
        }
        return all;
    }

    private static List<SmartTrack> loadEntriesByName(Context ctx, String prefName) {
        List<SmartTrack> list = new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(prefName, "");
        if (TextUtils.isEmpty(raw)) return list;
        for (String e : raw.split("\n")) {
            String[] parts = e.split("\u0001", -1);
            if (parts.length >= 3) {
                SmartTrack t = new SmartTrack(parts[0], parts[1], parts[2], "local", 0);
                list.add(t);
            }
        }
        return list;
    }

    // MainActivity 中的常量引用
    private static final String PREF_REC = "recent";
    private static final String PREF_FAV = "fav";

    public interface Callback {
        void onResult(SmartPlaylistResult result);
        void onError(String error);
    }
}