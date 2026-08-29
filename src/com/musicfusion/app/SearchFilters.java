package com.musicfusion.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/** SearchFilters — 搜索筛选面板
 *  - 源筛选 (Audius/IA/RadioBrowser/SomaFM/Openverse)
 *  - 画质筛选 (任意/128k+/256k+/无损)
 *  - 时长筛选 (任意/短<3min/中3-6min/长>6min)
 *  - 年份筛选 (任意/2020s/2010s/2000s/90s/80s/更早)
 *  - 流派筛选 (多选)
 *  - 结果排序 (相关度/最新/最热/时长/标题) */
public final class SearchFilters {
    private static final String PREF_KEY = "search_filters_v1";

    public static class FilterState {
        public boolean[] sources = new boolean[6];     // 0=Audius,1=IA,2=RadioBrowser,3=SomaFM,4=Openverse,5=Jamendo
        public int quality = 0;                         // 0=任意 1=128k+ 2=256k+ 3=无损
        public int duration = 0;                        // 0=任意 1=短 2=中 3=长
        public int year = 0;                            // 0=任意 1=2020s 2=2010s 3=2000s 4=90s 5=80s 6=更早
        public boolean[] genres = new boolean[20];      // 流派多选
        public int sort = 0;                            // 0=相关度 1=最新 2=最热 3=时长升 4=时长降 5=标题
    }

    public interface FilterCallback {
        void onFiltersApplied(FilterState state);
        void onFiltersCleared();
    }

    private static final String[] SOURCE_NAMES = {"Audius", "Internet Archive", "RadioBrowser", "SomaFM", "Openverse", "Jamendo"};
    private static final String[] QUALITY_NAMES = {"任意画质", "128kbps 以上", "256kbps 以上", "无损"};
    private static final String[] DURATION_NAMES = {"任意时长", "短 (<3分)", "中 (3-6分)", "长 (>6分)"};
    private static final String[] YEAR_NAMES = {"任意年份", "2020s", "2010s", "2000s", "1990s", "1980s", "更早"};
    private static final String[] GENRE_NAMES = {"Pop", "Rock", "Classical", "Jazz", "Electronic", "Hip Hop", "Folk", "Country",
        "R&B", "Reggae", "Metal", "Ambient", "Blues", "Funk", "Soul", "Indie", "Punk", "Disco", "House", "Techno"};
    private static final String[] SORT_NAMES = {"相关度", "最新", "最热", "时长 ↑", "时长 ↓", "标题"};

    /** 显示筛选对话框 */
    public static void showFilterDialog(Context ctx, FilterCallback cb) {
        FilterState current = load(ctx);
        View dialogView = buildDialogView(ctx, current);
        new AlertDialog.Builder(ctx)
            .setTitle("搜索筛选")
            .setView(dialogView)
            .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    FilterState newState = readFromView(dialogView);
                    save(ctx, newState);
                    if (cb != null) cb.onFiltersApplied(newState);
                }
            })
            .setNegativeButton("重置", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    FilterState def = new FilterState();
                    // 默认全选源
                    for (int i = 0; i < def.sources.length; i++) def.sources[i] = true;
                    save(ctx, def);
                    if (cb != null) cb.onFiltersApplied(def);
                }
            })
            .setNeutralButton("清空", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    clear(ctx);
                    if (cb != null) cb.onFiltersCleared();
                }
            })
            .show();
    }

    private static View buildDialogView(Context ctx, FilterState state) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 10, 20, 10);

        // 源筛选
        addSectionHeader(root, ctx, "音乐源");
        for (int i = 0; i < SOURCE_NAMES.length; i++) {
            CheckBox cb = new CheckBox(ctx);
            cb.setText(SOURCE_NAMES[i]);
            cb.setChecked(state.sources[i]);
            cb.setTag("src_" + i);
            root.addView(cb);
        }

        // 画质
        addSectionHeader(root, ctx, "最低画质");
        for (int i = 0; i < QUALITY_NAMES.length; i++) {
            final int idx = i;
            CheckBox cb = new CheckBox(ctx);
            cb.setText(QUALITY_NAMES[idx]);
            cb.setChecked(state.quality == idx);
            cb.setTag("qual_" + idx);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // 单选逻辑
                    for (int j = 0; j < QUALITY_NAMES.length; j++) {
                        View other = root.findViewWithTag("qual_" + j);
                        if (other instanceof CheckBox) ((CheckBox) other).setChecked(j == idx);
                    }
                }
            });
            root.addView(cb);
        }

        // 时长
        addSectionHeader(root, ctx, "时长范围");
        for (int i = 0; i < DURATION_NAMES.length; i++) {
            final int idx = i;
            CheckBox cb = new CheckBox(ctx);
            cb.setText(DURATION_NAMES[idx]);
            cb.setChecked(state.duration == idx);
            cb.setTag("dur_" + idx);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (int j = 0; j < DURATION_NAMES.length; j++) {
                        View other = root.findViewWithTag("dur_" + j);
                        if (other instanceof CheckBox) ((CheckBox) other).setChecked(j == idx);
                    }
                }
            });
            root.addView(cb);
        }

        // 年份
        addSectionHeader(root, ctx, "发行年代");
        for (int i = 0; i < YEAR_NAMES.length; i++) {
            final int idx = i;
            CheckBox cb = new CheckBox(ctx);
            cb.setText(YEAR_NAMES[idx]);
            cb.setChecked(state.year == idx);
            cb.setTag("year_" + idx);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (int j = 0; j < YEAR_NAMES.length; j++) {
                        View other = root.findViewWithTag("year_" + j);
                        if (other instanceof CheckBox) ((CheckBox) other).setChecked(j == idx);
                    }
                }
            });
            root.addView(cb);
        }

        // 流派
        addSectionHeader(root, ctx, "流派 (多选)");
        LinearLayout genreRow = new LinearLayout(ctx);
        genreRow.setOrientation(LinearLayout.HORIZONTAL);
        genreRow.setPadding(0, 5, 0, 5);
        root.addView(genreRow);
        for (int i = 0; i < GENRE_NAMES.length; i++) {
            CheckBox cb = new CheckBox(ctx);
            cb.setText(GENRE_NAMES[i]);
            cb.setChecked(state.genres[i]);
            cb.setTag("gen_" + i);
            cb.setPadding(10, 5, 10, 5);
            genreRow.addView(cb);
        }

        // 排序
        addSectionHeader(root, ctx, "排序方式");
        for (int i = 0; i < SORT_NAMES.length; i++) {
            final int idx = i;
            CheckBox cb = new CheckBox(ctx);
            cb.setText(SORT_NAMES[idx]);
            cb.setChecked(state.sort == idx);
            cb.setTag("sort_" + idx);
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    for (int j = 0; j < SORT_NAMES.length; j++) {
                        View other = root.findViewWithTag("sort_" + j);
                        if (other instanceof CheckBox) ((CheckBox) other).setChecked(j == idx);
                    }
                }
            });
            root.addView(cb);
        }

        return root;
    }

    private static void addSectionHeader(LinearLayout root, Context ctx, String title) {
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTextColor(0xFF8B949E);
        tv.setPadding(0, 15, 0, 5);
        root.addView(tv);
    }

    private static FilterState readFromView(View view) {
        FilterState fs = new FilterState();
        if (!(view instanceof LinearLayout)) return fs;
        LinearLayout root = (LinearLayout) view;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof CheckBox) {
                CheckBox cb = (CheckBox) child;
                String tag = (String) cb.getTag();
                if (tag == null) continue;
                if (tag.startsWith("src_")) {
                    int idx = Integer.parseInt(tag.substring(4));
                    if (idx >= 0 && idx < fs.sources.length) fs.sources[idx] = cb.isChecked();
                } else if (tag.startsWith("qual_") && cb.isChecked()) {
                    fs.quality = Integer.parseInt(tag.substring(5));
                } else if (tag.startsWith("dur_") && cb.isChecked()) {
                    fs.duration = Integer.parseInt(tag.substring(4));
                } else if (tag.startsWith("year_") && cb.isChecked()) {
                    fs.year = Integer.parseInt(tag.substring(5));
                } else if (tag.startsWith("gen_")) {
                    int idx = Integer.parseInt(tag.substring(4));
                    if (idx >= 0 && idx < fs.genres.length) fs.genres[idx] = cb.isChecked();
                } else if (tag.startsWith("sort_") && cb.isChecked()) {
                    fs.sort = Integer.parseInt(tag.substring(5));
                }
            } else if (child instanceof LinearLayout) {
                // 流派行
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View gc = row.getChildAt(j);
                    if (gc instanceof CheckBox) {
                        CheckBox cb = (CheckBox) gc;
                        String tag = (String) cb.getTag();
                        if (tag != null && tag.startsWith("gen_")) {
                            int idx = Integer.parseInt(tag.substring(4));
                            if (idx >= 0 && idx < fs.genres.length) fs.genres[idx] = cb.isChecked();
                        }
                    }
                }
            }
        }
        return fs;
    }

    /** 保存筛选状态 */
    public static void save(Context ctx, FilterState state) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            org.json.JSONArray src = new org.json.JSONArray();
            for (boolean b : state.sources) src.put(b);
            o.put("sources", src);
            o.put("quality", state.quality);
            o.put("duration", state.duration);
            o.put("year", state.year);
            org.json.JSONArray gen = new org.json.JSONArray();
            for (boolean b : state.genres) gen.put(b);
            o.put("genres", gen);
            o.put("sort", state.sort);
            sp.edit().putString(PREF_KEY, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** 加载筛选状态 */
    public static FilterState load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_KEY, "");
        FilterState fs = new FilterState();
        // 默认全选源
        for (int i = 0; i < fs.sources.length; i++) fs.sources[i] = true;
        if (TextUtils.isEmpty(raw)) return fs;
        try {
            org.json.JSONObject o = new org.json.JSONObject(raw);
            org.json.JSONArray src = o.optJSONArray("sources");
            if (src != null) for (int i = 0; i < src.length() && i < fs.sources.length; i++) fs.sources[i] = src.optBoolean(i, true);
            fs.quality = o.optInt("quality", 0);
            fs.duration = o.optInt("duration", 0);
            fs.year = o.optInt("year", 0);
            org.json.JSONArray gen = o.optJSONArray("genres");
            if (gen != null) for (int i = 0; i < gen.length() && i < fs.genres.length; i++) fs.genres[i] = gen.optBoolean(i, false);
            fs.sort = o.optInt("sort", 0);
        } catch (Exception ignored) {}
        return fs;
    }

    /** 清空筛选 */
    public static void clear(Context ctx) {
        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit().remove(PREF_KEY).apply();
    }

    /** 获取当前筛选状态 (别名: load) */
    public static FilterState getCurrentFilters(Context ctx) {
        return load(ctx);
    }

    /** 重置筛选状态 */
    public static void resetFilters(Context ctx) {
        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit().remove(PREF_KEY).apply();
    }

    /** 检查是否有活动筛选 */
    public static boolean hasActiveFilters(Context ctx) {
        FilterState fs = load(ctx);
        boolean anySourceOff = false;
        for (boolean b : fs.sources) if (!b) { anySourceOff = true; break; }
        boolean anyGenre = false;
        for (boolean b : fs.genres) if (b) { anyGenre = true; break; }
        return anySourceOff || fs.quality != 0 || fs.duration != 0 || fs.year != 0 || fs.sort != 0 ||
               anyGenre;
    }

    /** 应用筛选到搜索参数 (生成查询字符串片段) */
    public static String buildQuerySuffix(FilterState fs) {
        StringBuilder sb = new StringBuilder();
        // 源筛选在调用层处理 (决定调用哪个源的搜索)
        if (fs.quality > 0) sb.append(" quality:>=").append(fs.quality == 1 ? 128 : fs.quality == 2 ? 256 : 900);
        if (fs.duration > 0) {
            if (fs.duration == 1) sb.append(" duration:<180");
            else if (fs.duration == 2) sb.append(" duration:180-360");
            else sb.append(" duration:>360");
        }
        if (fs.year > 0) {
            int startYear = fs.year == 1 ? 2020 : fs.year == 2 ? 2010 : fs.year == 3 ? 2000 : fs.year == 4 ? 1990 : fs.year == 5 ? 1980 : 1900;
            int endYear = fs.year == 1 ? 2029 : fs.year == 2 ? 2019 : fs.year == 3 ? 2009 : fs.year == 4 ? 1999 : fs.year == 5 ? 1989 : 1979;
            sb.append(" year:").append(startYear).append("-").append(endYear);
        }
        if (fs.sort > 0) {
            String[] sortKeys = {"", "date", "popularity", "duration", "duration_desc", "title"};
            if (fs.sort < sortKeys.length) sb.append(" sort:").append(sortKeys[fs.sort]);
        }
        return sb.toString();
    }

    /** 判断结果是否匹配筛选 */
    public static boolean matches(FilterState fs, Object[] row) {
        // row: [title, subtitle, url, sourceTag, ...]
        if (row.length < 4) return true;
        String sourceTag = (String) row[3];
        int srcIdx = sourceTagToIndex(sourceTag);
        if (srcIdx >= 0 && srcIdx < fs.sources.length && !fs.sources[srcIdx]) return false;
        // 其他筛选需元数据支持, 这里简化
        return true;
    }

    private static int sourceTagToIndex(String tag) {
        if ("AUD".equals(tag)) return 0;
        if ("IA".equals(tag)) return 1;
        if ("RB".equals(tag)) return 2;
        if ("SFM".equals(tag)) return 3;
        if ("OV".equals(tag)) return 4;
        if ("JAM".equals(tag)) return 5;
        return -1;
    }

    static { try { android.text.TextUtils.class.getName(); } catch (Exception ignored) {} }
}