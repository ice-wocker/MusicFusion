package com.musicfusion.app;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

/** MaterialColor v1 — Material You 动态色提取 + 色调生成 (API 31+)，降级策略兼容 API 24+ */
public final class MaterialColor {
    private static final String TAG = "MaterialColor";
    private static final float[] TEMP_HSL = new float[3];

    /** 色调方案 */
    public static class Scheme {
        public int primary, onPrimary, primaryContainer, onPrimaryContainer;
        public int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
        public int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
        public int error, onError, errorContainer, onErrorContainer;
        public int background, onBackground, surface, onSurface;
        public int surfaceVariant, onSurfaceVariant, outline, outlineVariant;
        public int shadow, scrim, inverseSurface, inverseOnSurface, inversePrimary;

        @Override public String toString() {
            return "Scheme{p=" + Integer.toHexString(primary) + " s=" + Integer.toHexString(secondary) + " t=" + Integer.toHexString(tertiary) + "}";
        }
    }

    /** 从壁纸提取主色调 (API 31+) */
     public static Integer extractPrimaryColor( Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return null;
        try {
            android.graphics.drawable.Drawable wallpaper = android.app.WallpaperManager.getInstance(ctx).getDrawable();
            if (wallpaper == null) return null;
            android.graphics.Bitmap bitmap = drawableToBitmap(wallpaper);
            if (bitmap == null) return null;
            // v12: 简单像素采样取主色调 (避免 androidx Palette 依赖)
            Integer c = extractDominantColor(bitmap);
            if (c != null) return c;
            return c;
        } catch (Exception e) {
            Log.w(TAG, "extractPrimaryColor failed", e);
            return null;
        }
    }

    private static android.graphics.Bitmap drawableToBitmap(android.graphics.drawable.Drawable d) {
        if (d instanceof android.graphics.drawable.BitmapDrawable) {
            return ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
        }
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return null;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);
        return bmp;
    }

    /** v12: 简单主色调提取 (采样像素, 统计饱和度+亮度加权) */
    private static Integer extractDominantColor(android.graphics.Bitmap bitmap) {
        try {
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int sampleSize = Math.max(1, Math.min(w, h) / 50);
            long totalR = 0, totalG = 0, totalB = 0, count = 0;
            int bestR = 0, bestG = 0, bestB = 0;
            float bestScore = -1;
            for (int y = 0; y < h; y += sampleSize) {
                for (int x = 0; x < w; x += sampleSize) {
                    int c = bitmap.getPixel(x, y);
                    int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                    float[] hsl = rgbToHsl(c);
                    // 偏好高饱和度+中等亮度
                    float score = hsl[1] * (1f - Math.abs(hsl[2] - 0.5f));
                    if (score > bestScore) {
                        bestScore = score;
                        bestR = r; bestG = g; bestB = b;
                    }
                    totalR += r; totalG += g; totalB += b; count++;
                }
            }
            // 优先用饱和度最高的颜色, 回退到平均值
            if (bestScore > 0.1f) return Color.rgb(bestR, bestG, bestB);
            if (count > 0) return Color.rgb((int) (totalR / count), (int) (totalG / count), (int) (totalB / count));
        } catch (Throwable t) { /* 静默 */ }
        return null;
    }

    /** 从种子色生成完整色调方案 (Material 3 算法简化版) */
     public static Scheme fromSeed( Context ctx, int seedColor, boolean dark) {
        Scheme s = new Scheme();
        float[] hsl = rgbToHsl(seedColor);
        float h = hsl[0], sat = hsl[1], lum = hsl[2];

        // Primary: 种子色 ± 色调调整
        s.primary = hslToRgb(h, clamp(sat * 0.8f, 0.1f, 1f), dark ? 0.4f : 0.6f);
        s.onPrimary = hslToRgb(h, 0f, dark ? 1f : 0f);
        s.primaryContainer = hslToRgb(h, clamp(sat * 0.6f, 0f, 1f), dark ? 0.3f : 0.9f);
        s.onPrimaryContainer = hslToRgb(h, sat, dark ? 0.9f : 0.1f);

        // Secondary: 色相偏移 30°
        float h2 = (h + 30f) % 360f;
        s.secondary = hslToRgb(h2, clamp(sat * 0.5f, 0.05f, 0.8f), dark ? 0.5f : 0.5f);
        s.onSecondary = hslToRgb(h2, 0f, dark ? 1f : 0f);
        s.secondaryContainer = hslToRgb(h2, clamp(sat * 0.4f, 0f, 0.6f), dark ? 0.2f : 0.95f);
        s.onSecondaryContainer = hslToRgb(h2, sat * 0.5f, dark ? 0.9f : 0.1f);

        // Tertiary: 色相偏移 60°
        float h3 = (h + 60f) % 360f;
        s.tertiary = hslToRgb(h3, clamp(sat * 0.6f, 0.1f, 0.8f), dark ? 0.5f : 0.5f);
        s.onTertiary = hslToRgb(h3, 0f, dark ? 1f : 0f);
        s.tertiaryContainer = hslToRgb(h3, clamp(sat * 0.4f, 0f, 0.6f), dark ? 0.2f : 0.95f);
        s.onTertiaryContainer = hslToRgb(h3, sat * 0.5f, dark ? 0.9f : 0.1f);

        // Error (固定红色系)
        s.error = hslToRgb(27f, 0.9f, dark ? 0.5f : 0.5f);
        s.onError = hslToRgb(27f, 0f, dark ? 1f : 0f);
        s.errorContainer = hslToRgb(27f, 0.6f, dark ? 0.3f : 0.9f);
        s.onErrorContainer = hslToRgb(27f, 0.8f, dark ? 0.9f : 0.1f);

        // Neutral (背景/表面)
        float nSat = 0.02f;
        s.background = hslToRgb(h, nSat, dark ? 0.05f : 0.98f);
        s.onBackground = hslToRgb(h, nSat, dark ? 0.95f : 0.05f);
        s.surface = hslToRgb(h, nSat, dark ? 0.08f : 0.96f);
        s.onSurface = hslToRgb(h, nSat, dark ? 0.9f : 0.1f);
        s.surfaceVariant = hslToRgb(h, nSat * 2f, dark ? 0.15f : 0.9f);
        s.onSurfaceVariant = hslToRgb(h, nSat, dark ? 0.7f : 0.3f);
        s.outline = hslToRgb(h, nSat, dark ? 0.5f : 0.5f);
        s.outlineVariant = hslToRgb(h, nSat, dark ? 0.35f : 0.65f);

        // Shadow/Scrim/Inverse
        s.shadow = Color.BLACK;
        s.scrim = Color.BLACK;
        s.inverseSurface = hslToRgb(h, nSat, dark ? 0.9f : 0.1f);
        s.inverseOnSurface = hslToRgb(h, nSat, dark ? 0.1f : 0.9f);
        s.inversePrimary = hslToRgb(h, sat, dark ? 0.8f : 0.2f);

        return s;
    }

    /** 回退：从现有 C() 色值映射生成方案 */
     public static Scheme fromLegacy(int bg, int card, int accent, boolean dark) {
        Scheme s = fromSeed(null, accent, dark);
        s.background = bg;
        s.surface = card;
        s.primary = accent;
        return s;
    }

    // HSL <-> RGB 转换
    private static float[] rgbToHsl(int rgb) {
        float r = Color.red(rgb) / 255f;
        float g = Color.green(rgb) / 255f;
        float b = Color.blue(rgb) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h = 0, s = 0, l = (max + min) / 2f;
        float d = max - min;
        if (d > 0) {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6f : 0f);
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
        }
        TEMP_HSL[0] = h; TEMP_HSL[1] = s; TEMP_HSL[2] = l;
        return TEMP_HSL;
    }

    private static int hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            float hk = h / 360f;
            r = hueToRgb(p, q, hk + 1f/3f);
            g = hueToRgb(p, q, hk);
            b = hueToRgb(p, q, hk - 1f/3f);
        }
        return Color.rgb((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 0.5f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 计算对比度是否达标 (WCAG AA 4.5:1) */
    public static boolean meetsContrast(int fg, int bg) {
        return calculateContrastRatio(fg, bg) >= 4.5f;
    }

    /** 内联对比度计算 (替代 androidx ColorUtils) */
    private static double calculateContrastRatio(int fg, int bg) {
        double lumFg = relativeLuminance(fg);
        double lumBg = relativeLuminance(bg);
        double lighter = Math.max(lumFg, lumBg);
        double darker = Math.min(lumFg, lumBg);
        return (lighter + 0.05) / (darker + 0.05);
    }
    private static double relativeLuminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /** 自动修正前景色以满足对比度 */
    public static int fixContrast(int fg, int bg) {
        if (meetsContrast(fg, bg)) return fg;
        // 尝试黑/白
        if (meetsContrast(Color.WHITE, bg)) return Color.WHITE;
        if (meetsContrast(Color.BLACK, bg)) return Color.BLACK;
        // 调整亮度
        float[] hsl = rgbToHsl(fg);
        for (float l = 0f; l <= 1f; l += 0.05f) {
            int c = hslToRgb(hsl[0], hsl[1], l);
            if (meetsContrast(c, bg)) return c;
        }
        return fg; // 放弃
    }
}