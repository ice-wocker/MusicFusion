package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ThemeManager — 用户自定义主题
 *  - 内置: AMOLED / 高对比 / 护眼 / 材料3 动态色
 *  - 自定义色板 (主色/次色/背景/卡片/文字/强调)
 *  - 导出/导入 JSON
 *  - 即时预览 + 持久化 */
public final class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String PREF_KEY = "theme_v1";
    private static final String CUSTOM_PREFIX = "custom_";

    // 主题键
    public static final String THEME_DEFAULT = "default";       // 深色原版
    public static final String THEME_AMOLED = "amoled";         // 纯黑 #000000
    public static final String THEME_HIGH_CONTRAST = "high_contrast"; // 高对比
    public static final String THEME_EYE_CARE = "eye_care";     // 护眼暖色
    public static final String THEME_LIGHT = "light";           // 浅色
    public static final String THEME_DYNAMIC = "dynamic";       // Material You 动态色 (API 31+)
    public static final String THEME_CUSTOM = "custom";         // 用户自定义

    // 颜色键
    public static final String C_BG = "bg";         // 主背景
    public static final String C_CARD = "card";     // 卡片/面板背景
    public static final String C_LINE = "line";     // 分割线/边框
    public static final String C_TXT = "txt";       // 主文字
    public static final String C_DIM = "dim";       // 次要文字
    public static final String C_PRI = "pri";       // 主强调色 (品牌色)
    public static final String C_SEC = "sec";       // 次强调色
    public static final String C_GRN = "grn";       // 成功/播放
    public static final String C_ERR = "err";       // 错误
    public static final String C_WRN = "wrn";       // 警告
    public static final String C_INF = "inf";       // 信息背景

    // 内置主题定义
    private static final Map<String, Map<String, Integer>> BUILTIN = new HashMap<>();
    static {
        // default (v12 Nebula 深色)
        Map<String, Integer> d = new HashMap<>();
        d.put(C_BG, 0xFF0B0E14); d.put(C_CARD, 0xFF161C28); d.put(C_LINE, 0xFF232C3D);
        d.put(C_TXT, 0xFFE6EDF3); d.put(C_DIM, 0xFF8B949E); d.put(C_PRI, 0xFF1DB954);
        d.put(C_SEC, 0xFF58A6FF); d.put(C_GRN, 0xFF1DB954); d.put(C_ERR, 0xFFF85149);
        d.put(C_WRN, 0xFFD29922); d.put(C_INF, 0xFF1D3A5C);
        BUILTIN.put(THEME_DEFAULT, d);

        // amoled
        Map<String, Integer> a = new HashMap<>();
        a.put(C_BG, 0xFF000000); a.put(C_CARD, 0xFF111111); a.put(C_LINE, 0xFF222222);
        a.put(C_TXT, 0xFFFFFFFF); a.put(C_DIM, 0xFF888888); a.put(C_PRI, 0xFF1DB954);
        a.put(C_SEC, 0xFF58A6FF); a.put(C_GRN, 0xFF1DB954); a.put(C_ERR, 0xFFFF4444);
        a.put(C_WRN, 0xFFFFAA00); a.put(C_INF, 0xFF004488);
        BUILTIN.put(THEME_AMOLED, a);

        // high_contrast
        Map<String, Integer> h = new HashMap<>();
        h.put(C_BG, 0xFF000000); h.put(C_CARD, 0xFF000000); h.put(C_LINE, 0xFFFFFFFF);
        h.put(C_TXT, 0xFFFFFFFF); h.put(C_DIM, 0xFFCCCCCC); h.put(C_PRI, 0xFF00FF00);
        h.put(C_SEC, 0xFF00FFFF); h.put(C_GRN, 0xFF00FF00); h.put(C_ERR, 0xFFFF0000);
        h.put(C_WRN, 0xFFFFFF00); h.put(C_INF, 0xFF0000FF);
        BUILTIN.put(THEME_HIGH_CONTRAST, h);

        // eye_care (暖色护眼)
        Map<String, Integer> e = new HashMap<>();
        e.put(C_BG, 0xFF1A1610); e.put(C_CARD, 0xFF241E16); e.put(C_LINE, 0xFF3D3328);
        e.put(C_TXT, 0xFFF5E6D3); e.put(C_DIM, 0xFFA09078); e.put(C_PRI, 0xFFE8A838);
        e.put(C_SEC, 0xFFD48828); e.put(C_GRN, 0xFF88CC44); e.put(C_ERR, 0xFFCC4444);
        e.put(C_WRN, 0xFFFFAA33); e.put(C_INF, 0xFF335588);
        BUILTIN.put(THEME_EYE_CARE, e);

        // light
        Map<String, Integer> l = new HashMap<>();
        l.put(C_BG, 0xFFF6F8FA); l.put(C_CARD, 0xFFFFFFFF); l.put(C_LINE, 0xFFD0D7DE);
        l.put(C_TXT, 0xFF1F2328); l.put(C_DIM, 0xFF656D76); l.put(C_PRI, 0xFF1DB954);
        l.put(C_SEC, 0xFF0969DA); l.put(C_GRN, 0xFF1A7F37); l.put(C_ERR, 0xFFCF222E);
        l.put(C_WRN, 0xFF9A6700); l.put(C_INF, 0xFFDDF4FF);
        BUILTIN.put(THEME_LIGHT, l);
    }

    /** 获取当前主题名 */
    public static String getCurrentTheme(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        return sp.getString(PREF_KEY, THEME_DEFAULT);
    }

    /** 设置主题 */
    public static void setTheme(Context ctx, String themeName) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        sp.edit().putString(PREF_KEY, themeName).apply();
        // 通知 MainActivity 刷新
        if (MainActivity.inst != null) MainActivity.inst.recreate();
    }

    /** 获取主题色值 */
    public static int getColor(Context ctx, String colorKey) {
        String theme = getCurrentTheme(ctx);
        Map<String, Integer> colors = getThemeColors(ctx, theme);
        Integer c = colors.get(colorKey);
        return c != null ? c : BUILTIN.get(THEME_DEFAULT).get(colorKey);
    }

    /** 获取完整主题色表 */
    public static Map<String, Integer> getThemeColors(Context ctx, String themeName) {
        if (BUILTIN.containsKey(themeName)) return new HashMap<>(BUILTIN.get(themeName));
        if (THEME_CUSTOM.equals(themeName)) return loadCustomTheme(ctx);
        if (THEME_DYNAMIC.equals(themeName)) return getDynamicColors(ctx);
        return new HashMap<>(BUILTIN.get(THEME_DEFAULT));
    }

    /** Material You 动态色 (API 31+) */
    private static Map<String, Integer> getDynamicColors(Context ctx) {
        Map<String, Integer> m = new HashMap<>();
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                // Use reflection to access API 31+ classes
                Class<?> colorSchemeClass = Class.forName("android.content.res.ColorScheme");
                Object cs = ctx.getClass().getMethod("getColorScheme").invoke(ctx);
                int primary = (Integer) colorSchemeClass.getMethod("getPrimaryContainer").invoke(cs);
                int onPrimary = (Integer) colorSchemeClass.getMethod("getOnPrimaryContainer").invoke(cs);
                int surface = (Integer) colorSchemeClass.getMethod("getSurface").invoke(cs);
                int onSurface = (Integer) colorSchemeClass.getMethod("getOnSurface").invoke(cs);
                int outline = (Integer) colorSchemeClass.getMethod("getOutline").invoke(cs);
                m.put(C_BG, surface);
                m.put(C_CARD, blend(surface, primary, 0.1f));
                m.put(C_LINE, outline);
                m.put(C_TXT, onSurface);
                m.put(C_DIM, blend(onSurface, surface, 0.5f));
                m.put(C_PRI, primary);
                m.put(C_SEC, invokeInt(cs, "getSecondaryContainer"));
                m.put(C_GRN, primary);
                m.put(C_ERR, invokeInt(cs, "getErrorContainer"));
                m.put(C_WRN, invokeInt(cs, "getTertiaryContainer"));
                m.put(C_INF, invokeInt(cs, "getPrimaryContainer"));
                return m;
            } catch (Exception ignored) {}
        }
        // 回退
        return new HashMap<>(BUILTIN.get(THEME_DEFAULT));
    }

    private static int blend(int c1, int c2, float ratio) {
        int r = (int) (Color.red(c1) * (1 - ratio) + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * (1 - ratio) + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * (1 - ratio) + Color.blue(c2) * ratio);
        return Color.rgb(r, g, b);
    }

    private static int invokeInt(Object obj, String method) {
        try {
            return (Integer) obj.getClass().getMethod(method).invoke(obj);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 列出所有可用主题 (内置 + 自定义) */
    public static List<String> listThemes(Context ctx) {
        List<String> list = new ArrayList<>();
        list.add(THEME_DEFAULT);
        list.add(THEME_AMOLED);
        list.add(THEME_HIGH_CONTRAST);
        list.add(THEME_EYE_CARE);
        list.add(THEME_LIGHT);
        if (android.os.Build.VERSION.SDK_INT >= 31) list.add(THEME_DYNAMIC);
        // 自定义主题
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();
        for (String k : all.keySet()) {
            if (k.startsWith(CUSTOM_PREFIX)) {
                String name = k.substring(CUSTOM_PREFIX.length());
                if (!list.contains(name)) list.add(name);
            }
        }
        return list;
    }

    /** 保存自定义主题 */
    public static boolean saveCustomTheme(Context ctx, String name, Map<String, Integer> colors) {
        if (TextUtils.isEmpty(name) || colors == null) return false;
        // 校验必填键
        String[] required = {C_BG, C_CARD, C_LINE, C_TXT, C_DIM, C_PRI, C_SEC, C_GRN, C_ERR, C_WRN, C_INF};
        for (String k : required) if (!colors.containsKey(k)) return false;

        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Integer> e : colors.entrySet()) o.put(e.getKey(), e.getValue());
            sp.edit().putString(CUSTOM_PREFIX + name, o.toString()).apply();
            return true;
        } catch (Exception ex) { Log.w(TAG, "saveCustomTheme", ex); return false; }
    }

    /** 删除自定义主题 */
    public static void deleteCustomTheme(Context ctx, String name) {
        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit().remove(CUSTOM_PREFIX + name).apply();
    }

    /** 加载自定义主题 */
    private static Map<String, Integer> loadCustomTheme(Context ctx) {
        String themeName = getCurrentTheme(ctx);
        if (!THEME_CUSTOM.equals(themeName)) return new HashMap<>(BUILTIN.get(THEME_DEFAULT));
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        // 这里需要知道自定义主题的具体名字, 简化: 存一个当前自定义主题名
        String customName = sp.getString(PREF_KEY + "_custom_name", "");
        if (TextUtils.isEmpty(customName)) return new HashMap<>(BUILTIN.get(THEME_DEFAULT));
        String raw = sp.getString(CUSTOM_PREFIX + customName, "");
        Map<String, Integer> m = new HashMap<>();
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONObject o = new JSONObject(raw);
                for (String k : new String[]{C_BG, C_CARD, C_LINE, C_TXT, C_DIM, C_PRI, C_SEC, C_GRN, C_ERR, C_WRN, C_INF}) {
                    m.put(k, o.optInt(k, BUILTIN.get(THEME_DEFAULT).get(k)));
                }
            } catch (Exception ignored) {}
        }
        return m.isEmpty() ? new HashMap<>(BUILTIN.get(THEME_DEFAULT)) : m;
    }

    /** 设置当前自定义主题名 (配合 saveCustomTheme) */
    public static void setActiveCustomTheme(Context ctx, String name) {
        ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).edit()
            .putString(PREF_KEY, THEME_CUSTOM)
            .putString(PREF_KEY + "_custom_name", name)
            .apply();
    }

    /** 导出主题为 JSON 字符串 */
    public static String exportTheme(Context ctx, String themeName) {
        Map<String, Integer> colors = getThemeColors(ctx, themeName);
        try {
            JSONObject o = new JSONObject();
            o.put("name", themeName);
            o.put("colors", new JSONObject(colors));
            return o.toString(2);
        } catch (Exception e) { return null; }
    }

    /** 导入主题 */
    public static boolean importTheme(Context ctx, String json) {
        try {
            JSONObject o = new JSONObject(json);
            String name = o.optString("name", "imported_" + System.currentTimeMillis());
            JSONObject colors = o.optJSONObject("colors");
            Map<String, Integer> m = new HashMap<>();
            for (String k : new String[]{C_BG, C_CARD, C_LINE, C_TXT, C_DIM, C_PRI, C_SEC, C_GRN, C_ERR, C_WRN, C_INF}) {
                m.put(k, colors.optInt(k, BUILTIN.get(THEME_DEFAULT).get(k)));
            }
            return saveCustomTheme(ctx, name, m);
        } catch (Exception e) { return false; }
    }

    /** 检查是否为深色主题 */
    public static boolean isDarkTheme(Context ctx) {
        int bg = getColor(ctx, C_BG);
        // 简单亮度判断
        double lum = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg);
        return lum < 128;
    }

    /** 获取对比色 (用于文字在背景上可读) */
    public static int getContrastTextColor(Context ctx, String bgColorKey) {
        int bg = getColor(ctx, bgColorKey);
        double lum = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg);
        return lum > 128 ? 0xFF000000 : 0xFFFFFFFF;
    }

    // 为了编译
    static { try { android.text.TextUtils.class.getName(); } catch (Exception ignored) {} }
}