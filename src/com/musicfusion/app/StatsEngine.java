package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** StatsEngine — 听歌统计图表
 *  - 周/月/年 播放时长/曲数/流派/时段/艺术家分布
 *  - 柱状图/饼图/折线图/热力图
 *  - 数据来源: SharedPreferences 播放历史 */
public final class StatsEngine {
    private static final String TAG = "StatsEngine";
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final String PREF_STATS = "stats_v1";

    public static class StatSnapshot {
        public long periodStart;    // 周/月/年 开始时间戳
        public long periodEnd;
        public int totalPlays = 0;
        public long totalDurationMs = 0;
        public Map<String, Integer> genreCounts = new HashMap<>();    // 流派 -> 次数
        public Map<String, Integer> artistCounts = new HashMap<>();   // 艺术家 -> 次数
        public Map<String, Integer> hourCounts = new HashMap<>();     // 小时(0-23) -> 次数
        public Map<String, Integer> dayCounts = new HashMap<>();      // 日期 -> 次数
        public Map<String, Integer> sourceCounts = new HashMap<>();   // 音源 -> 次数
        public List<PlayRecord> recent = new ArrayList<>();           // 最近 50 条
    }

    public static class PlayRecord {
        public String title, artist, source, genre;
        public long timestamp;
        public int durationMs;
    }

    public interface StatsCallback {
        void onStatsReady(StatSnapshot snapshot);
        void onError(String error);
    }

    /** 记录一次播放 (由 PlayerService 调用) */
    public static void recordPlay(Context ctx, String title, String artist, String source,
                                  String genre, int durationMs) {
        EXEC.execute(new Runnable() { public void run() {
            try {
                SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
                String raw = sp.getString(PREF_STATS, "");
                JSONArray arr = TextUtils.isEmpty(raw) ? new JSONArray() : new JSONArray(raw);

                JSONObject o = new JSONObject();
                o.put("title", title);
                o.put("artist", artist);
                o.put("source", source != null ? source : "unknown");
                o.put("genre", genre != null ? genre : "unknown");
                o.put("duration", durationMs);
                o.put("ts", System.currentTimeMillis());

                arr.put(o);

                // 限制保留最近 2000 条
                while (arr.length() > 2000) arr.remove(0);

                sp.edit().putString(PREF_STATS, arr.toString()).apply();

                // 更新总计数 (用于首页显示)
                int total = sp.getInt("total_plays", 0);
                sp.edit().putInt("total_plays", total + 1).apply();

            } catch (Exception e) { Log.w(TAG, "recordPlay", e); }
        }});
    }

    /** 获取统计快照 (周/月/年/全部) */
    public static void getStats(Context ctx, String period, final StatsCallback cb) {
        // period: "week" / "month" / "year" / "all"
        EXEC.execute(new Runnable() { public void run() {
            try {
                SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
                String raw = sp.getString(PREF_STATS, "");
                if (TextUtils.isEmpty(raw)) {
                    cb.onStatsReady(new StatSnapshot());
                    return;
                }

                JSONArray arr = new JSONArray(raw);
                long now = System.currentTimeMillis();
                Calendar cal = Calendar.getInstance(Locale.US);
                cal.setTimeInMillis(now);

                long start;
                if ("week".equals(period)) {
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                    start = cal.getTimeInMillis();
                } else if ("month".equals(period)) {
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                    start = cal.getTimeInMillis();
                } else if ("year".equals(period)) {
                    cal.set(Calendar.MONTH, Calendar.JANUARY);
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                    start = cal.getTimeInMillis();
                } else {
                    start = 0; // all
                }

                StatSnapshot snap = new StatSnapshot();
                snap.periodStart = start;
                snap.periodEnd = now;

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    long ts = o.optLong("ts", 0);
                    if (start > 0 && ts < start) continue;

                    snap.totalPlays++;
                    snap.totalDurationMs += o.optInt("duration", 0);

                    String genre = o.optString("genre", "unknown");
                    inc(snap.genreCounts, genre);

                    String artist = o.optString("artist", "unknown");
                    inc(snap.artistCounts, artist);

                    Calendar c = Calendar.getInstance(Locale.US);
                    c.setTimeInMillis(ts);
                    String hour = String.valueOf(c.get(Calendar.HOUR_OF_DAY));
                    inc(snap.hourCounts, hour);

                    String day = String.format(Locale.US, "%04d-%02d-%02d",
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
                    inc(snap.dayCounts, day);

                    String source = o.optString("source", "unknown");
                    inc(snap.sourceCounts, source);

                    if (snap.recent.size() < 50) {
                        PlayRecord r = new PlayRecord();
                        r.title = o.optString("title", "");
                        r.artist = o.optString("artist", "");
                        r.source = source;
                        r.genre = genre;
                        r.timestamp = ts;
                        r.durationMs = o.optInt("duration", 0);
                        snap.recent.add(r);
                    }
                }

                // 最近播放按时间倒序
                Collections.sort(snap.recent, new java.util.Comparator<PlayRecord>() {
                    public int compare(PlayRecord a, PlayRecord b) {
                        return Long.compare(b.timestamp, a.timestamp);
                    }
                });

                cb.onStatsReady(snap);

            } catch (Exception e) { Log.w(TAG, "getStats", e); cb.onError(e.getMessage()); }
        }});
    }

    private static void inc(Map<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    /** 获取 Top N 艺术家/流派/音源 */
    public static List<Map.Entry<String, Integer>> getTop(Map<String, Integer> map, int n) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, new java.util.Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        if (list.size() > n) list = list.subList(0, n);
        return list;
    }

    /** 生成完整统计报告 (同步, 用于 UI 展示) */
    public static StatsReport generateReport(Context ctx) {
        final StatsReport[] result = new StatsReport[1];
        final Object lock = new Object();
        getStats(ctx, "all", new StatsCallback() {
            public void onStatsReady(StatSnapshot snap) {
                result[0] = buildReport(snap);
                synchronized (lock) { lock.notify(); }
            }
            public void onError(String error) {
                result[0] = new StatsReport();
                synchronized (lock) { lock.notify(); }
            }
        });
        synchronized (lock) {
            try { lock.wait(5000); } catch (InterruptedException ignored) {}
        }
        return result[0] != null ? result[0] : new StatsReport();
    }

    /** 构建报告对象 */
    private static StatsReport buildReport(StatSnapshot snap) {
        StatsReport r = new StatsReport();
        r.totalPlays = snap.totalPlays;
        r.totalMs = snap.totalDurationMs;
        r.uniqueTracks = snap.recent.size();

        // 周统计
        getStats(null, "week", new StatsCallback() {
            public void onStatsReady(StatSnapshot s) {
                r.weekStats = toDayStats(s);
            }
            public void onError(String error) {}
        });

        // 月统计
        getStats(null, "month", new StatsCallback() {
            public void onStatsReady(StatSnapshot s) {
                r.monthStats = toDayStats(s);
            }
            public void onError(String error) {}
        });

        // 年统计
        getStats(null, "year", new StatsCallback() {
            public void onStatsReady(StatSnapshot s) {
                r.yearStats = toDayStats(s);
            }
            public void onError(String error) {}
        });

        // 流派 Top
        r.genreTop = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : getTop(snap.genreCounts, 10)) {
            r.genreTop.put(e.getKey(), e.getValue());
        }

        // 艺术家 Top
        r.artistTop = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : getTop(snap.artistCounts, 10)) {
            r.artistTop.put(e.getKey(), e.getValue());
        }

        // 时段分布
        r.hourly = new int[24];
        for (Map.Entry<String, Integer> e : snap.hourCounts.entrySet()) {
            try {
                int h = Integer.parseInt(e.getKey());
                if (h >= 0 && h < 24) r.hourly[h] = e.getValue();
            } catch (Exception ignored) {}
        }

        return r;
    }

    private static List<DayStat> toDayStats(StatSnapshot s) {
        List<DayStat> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : s.dayCounts.entrySet()) {
            out.add(new DayStat(e.getKey(), e.getValue(), 0)); // duration 简化
        }
        Collections.sort(out, new java.util.Comparator<DayStat>() {
            public int compare(DayStat a, DayStat b) {
                return a.date.compareTo(b.date);
            }
        });
        return out;
    }

    /** 导出完整报告为 JSON */
    public static String exportReport(Context ctx) {
        StatsReport report = generateReport(ctx);
        try {
            JSONObject o = new JSONObject();
            o.put("totalPlays", report.totalPlays);
            o.put("totalMs", report.totalMs);
            o.put("uniqueTracks", report.uniqueTracks);
            o.put("exportedAt", System.currentTimeMillis());

            JSONArray weekArr = new JSONArray();
            for (DayStat ds : report.weekStats) {
                JSONObject w = new JSONObject();
                w.put("date", ds.date);
                w.put("plays", ds.plays);
                w.put("ms", ds.ms);
                weekArr.put(w);
            }
            o.put("weekStats", weekArr);

            JSONArray monthArr = new JSONArray();
            for (DayStat ds : report.monthStats) {
                JSONObject w = new JSONObject();
                w.put("date", ds.date);
                w.put("plays", ds.plays);
                w.put("ms", ds.ms);
                monthArr.put(w);
            }
            o.put("monthStats", monthArr);

            JSONArray yearArr = new JSONArray();
            for (DayStat ds : report.yearStats) {
                JSONObject w = new JSONObject();
                w.put("date", ds.date);
                w.put("plays", ds.plays);
                w.put("ms", ds.ms);
                yearArr.put(w);
            }
            o.put("yearStats", yearArr);

            JSONObject genreObj = new JSONObject();
            for (Map.Entry<String, Integer> e : report.genreTop.entrySet()) {
                genreObj.put(e.getKey(), e.getValue());
            }
            o.put("genreTop", genreObj);

            JSONArray hourlyArr = new JSONArray();
            for (int i = 0; i < 24; i++) hourlyArr.put(report.hourly[i]);
            o.put("hourly", hourlyArr);

            JSONObject artistObj = new JSONObject();
            for (Map.Entry<String, Integer> e : report.artistTop.entrySet()) {
                artistObj.put(e.getKey(), e.getValue());
            }
            o.put("artistTop", artistObj);

            return o.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static class StatsReport {
        public int totalPlays = 0;
        public long totalMs = 0;
        public int uniqueTracks = 0;
        public List<DayStat> weekStats = new ArrayList<>();
        public List<DayStat> monthStats = new ArrayList<>();
        public List<DayStat> yearStats = new ArrayList<>();
        public Map<String, Integer> genreTop = new LinkedHashMap<>();
        public Map<String, Integer> artistTop = new LinkedHashMap<>();
        public int[] hourly = new int[24];
    }

    public static class DayStat {
        public String date;
        public int plays = 0;
        public long ms = 0;
        public DayStat() {}
        public DayStat(String d, int p, long m) { date = d; plays = p; ms = m; }
    }

    /** 格式化时长 */

    /** 格式化时长 */
    public static String formatDuration(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        s = s % 60;
        if (h > 0) return String.format(Locale.US, "%dh %dm", h, m);
        return String.format(Locale.US, "%dm %ds", m, s);
    }

    /** 格式化播放次数 */
    public static String formatCount(int count) {
        if (count >= 10000) return String.format(Locale.US, "%.1fw", count / 10000.0);
        if (count >= 1000) return String.format(Locale.US, "%.1fk", count / 1000.0);
        return String.valueOf(count);
    }

    // ===== 图表 View 组件 =====

    /** 柱状图 View */
    public static class BarChartView extends View {
        private Paint barPaint, textPaint, axisPaint, gridPaint;
        private List<BarItem> items = new ArrayList<>();
        private int colorPrimary = 0xFF1DB954;
        private int colorBg = 0xFF0B0E14;
        private int colorText = 0xFF8B949E;
        private int maxValue = 1;
        private String unit = "";

        public BarChartView(Context ctx) { this(ctx, null); }
        public BarChartView(Context ctx, AttributeSet attrs) { this(ctx, attrs, 0); }
        public BarChartView(Context ctx, AttributeSet attrs, int defStyle) {
            super(ctx, attrs, defStyle);
            init();
        }

        private void init() {
            barPaint = new Paint();
            barPaint.setColor(colorPrimary);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAntiAlias(true);

            textPaint = new Paint();
            textPaint.setColor(colorText);
            textPaint.setTextSize(24f);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);

            axisPaint = new Paint();
            axisPaint.setColor(0xFF232C3D);
            axisPaint.setStrokeWidth(1f);
            axisPaint.setAntiAlias(true);

            gridPaint = new Paint();
            gridPaint.setColor(0xFF1A1F2B);
            gridPaint.setStrokeWidth(1f);
            gridPaint.setAntiAlias(true);
            gridPaint.setStyle(Paint.Style.STROKE);
        }

        public void setData(List<BarItem> data, String unit) {
            this.items = data != null ? data : new ArrayList<>();
            this.unit = unit != null ? unit : "";
            maxValue = 1;
            for (BarItem it : items) if (it.value > maxValue) maxValue = it.value;
            invalidate();
        }

        public void setColors(int primary, int bg, int text) {
            colorPrimary = primary; colorBg = bg; colorText = text;
            barPaint.setColor(colorPrimary);
            textPaint.setColor(colorText);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || items.isEmpty()) return;

            canvas.drawColor(colorBg);

            float left = 60f, right = 20f, top = 20f, bottom = 60f;
            float drawW = w - left - right;
            float drawH = h - top - bottom;

            // Y 轴网格线 (5 条)
            for (int i = 0; i <= 4; i++) {
                float y = top + i * drawH / 4f;
                canvas.drawLine(left, y, w - right, y, gridPaint);
                String val = String.format(Locale.US, "%.0f%s", maxValue * (1f - i / 4f), unit);
                canvas.drawText(val, left - 10, y + 8, textPaint);
            }

            // X 轴
            canvas.drawLine(left, h - bottom, w - right, h - bottom, axisPaint);

            // 柱子
            int n = items.size();
            float barW = drawW / n * 0.7f;
            float gap = drawW / n * 0.3f;
            for (int i = 0; i < n; i++) {
                BarItem it = items.get(i);
                float x = left + i * (barW + gap) + gap / 2f;
                float barH = (it.value / (float) maxValue) * drawH;
                float y = h - bottom - barH;
                canvas.drawRect(x, y, x + barW, h - bottom, barPaint);
                // 标签
                canvas.drawText(it.label, x + barW / 2f, h - bottom + 30, textPaint);
                // 数值
                canvas.drawText(it.value + unit, x + barW / 2f, y - 5, textPaint);
            }
        }

        public static class BarItem {
            public String label;
            public int value;
            public BarItem(String l, int v) { label = l; value = v; }
        }
    }

    /** 饼图 View */
    public static class PieChartView extends View {
        private Paint slicePaint, textPaint, legendPaint;
        private List<PieSlice> slices = new ArrayList<>();
        private int[] palette = {0xFF1DB954, 0xFF58A6FF, 0xFFF85149, 0xFFD29922, 0xFF8B949E,
            0xFF132A1C, 0xFF7D2D2D, 0xFF1D3A5C, 0xFF6A7A00, 0xFF9E6A00};
        private int colorBg = 0xFF0B0E14;
        private int colorText = 0xFF8B949E;

        public PieChartView(Context ctx) { this(ctx, null); }
        public PieChartView(Context ctx, AttributeSet attrs) { this(ctx, attrs, 0); }
        public PieChartView(Context ctx, AttributeSet attrs, int defStyle) {
            super(ctx, attrs, defStyle);
            init();
        }

        private void init() {
            slicePaint = new Paint();
            slicePaint.setStyle(Paint.Style.FILL);
            slicePaint.setAntiAlias(true);

            textPaint = new Paint();
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(20f);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);

            legendPaint = new Paint();
            legendPaint.setColor(colorText);
            legendPaint.setTextSize(22f);
            legendPaint.setAntiAlias(true);
            legendPaint.setTextAlign(Paint.Align.LEFT);
        }

        public void setData(List<PieSlice> data) {
            this.slices = data != null ? data : new ArrayList<>();
            invalidate();
        }

        public void setColors(int bg, int text) {
            colorBg = bg; colorText = text;
            legendPaint.setColor(colorText);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (slices.isEmpty()) return;

            canvas.drawColor(colorBg);

            float cx = w * 0.5f;
            float cy = h * 0.45f;
            float radius = Math.min(w, h) * 0.35f;

            int total = 0;
            for (PieSlice s : slices) total += s.value;
            if (total == 0) return;

            float startAngle = -90f;
            for (int i = 0; i < slices.size(); i++) {
                PieSlice s = slices.get(i);
                float sweep = (s.value / (float) total) * 360f;
                slicePaint.setColor(palette[i % palette.length]);
                canvas.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                    startAngle, sweep, true, slicePaint);
                // 百分比文字 (大扇区才画)
                if (sweep > 20) {
                    float mid = startAngle + sweep / 2f;
                    float tx = cx + (float) Math.cos(Math.toRadians(mid)) * radius * 0.6f;
                    float ty = cy + (float) Math.sin(Math.toRadians(mid)) * radius * 0.6f + 7;
                    canvas.drawText(String.format(Locale.US, "%.0f%%", sweep / 360f * 100), tx, ty, textPaint);
                }
                startAngle += sweep;
            }

            // 图例 (底部)
            float legendY = cy + radius + 30f;
            float legendX = 20f;
            for (int i = 0; i < slices.size(); i++) {
                PieSlice s = slices.get(i);
                slicePaint.setColor(palette[i % palette.length]);
                canvas.drawCircle(legendX + 10, legendY + i * 30 + 10, 8, slicePaint);
                String label = String.format(Locale.US, "%s (%d, %.0f%%)", s.label, s.value, s.value * 100f / total);
                canvas.drawText(label, legendX + 25, legendY + i * 30 + 15, legendPaint);
            }
        }

        public static class PieSlice {
            public String label;
            public int value;
            public PieSlice(String l, int v) { label = l; value = v; }
        }
    }

    /** 热力图 View (时段 x 星期) */
    public static class HeatmapView extends View {
        private Paint cellPaint, textPaint, axisPaint;
        private int[][] data = new int[7][24]; // [weekday][hour]
        private int maxVal = 1;
        private int colorLow = 0xFF1A1F2B, colorHigh = 0xFF1DB954;
        private int colorBg = 0xFF0B0E14;
        private int colorText = 0xFF8B949E;

        public HeatmapView(Context ctx) { this(ctx, null); }
        public HeatmapView(Context ctx, AttributeSet attrs) { this(ctx, attrs, 0); }
        public HeatmapView(Context ctx, AttributeSet attrs, int defStyle) {
            super(ctx, attrs, defStyle);
            init();
        }

        private void init() {
            cellPaint = new Paint();
            cellPaint.setStyle(Paint.Style.FILL);
            cellPaint.setAntiAlias(true);

            textPaint = new Paint();
            textPaint.setColor(colorText);
            textPaint.setTextSize(18f);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);

            axisPaint = new Paint();
            axisPaint.setColor(colorText);
            axisPaint.setTextSize(18f);
            axisPaint.setAntiAlias(true);
            axisPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void setData(int[][] matrix) {
            this.data = matrix != null ? matrix : new int[7][24];
            maxVal = 1;
            for (int d = 0; d < 7; d++) for (int h = 0; h < 24; h++) if (data[d][h] > maxVal) maxVal = data[d][h];
            invalidate();
        }

        public void setColors(int low, int high, int bg, int text) {
            colorLow = low; colorHigh = high; colorBg = bg; colorText = text;
            textPaint.setColor(colorText);
            axisPaint.setColor(colorText);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            canvas.drawColor(colorBg);

            float left = 50f, top = 30f;
            float cellW = (w - left - 20f) / 24f;
            float cellH = (h - top - 50f) / 7f;
            String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

            for (int d = 0; d < 7; d++) {
                float y = top + d * cellH;
                // 星期标签
                canvas.drawText(days[d], left - 30, y + cellH / 2f + 6, axisPaint);
                for (int hr = 0; hr < 24; hr++) {
                    float x = left + hr * cellW;
                    int val = data[d][hr];
                    float ratio = maxVal > 0 ? val / (float) maxVal : 0f;
                    int r = (int) (Color.red(colorLow) * (1 - ratio) + Color.red(colorHigh) * ratio);
                    int g = (int) (Color.green(colorLow) * (1 - ratio) + Color.green(colorHigh) * ratio);
                    int b = (int) (Color.blue(colorLow) * (1 - ratio) + Color.blue(colorHigh) * ratio);
                    cellPaint.setColor(Color.rgb(r, g, b));
                    canvas.drawRect(x, y, x + cellW, y + cellH, cellPaint);
                    // 小时标签 (顶部)
                    if (d == 0) canvas.drawText(String.valueOf(hr), x + cellW / 2f, top - 5, axisPaint);
                }
            }
        }
    }
}