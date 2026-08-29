package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** GraphicEqualizer — 10段图形均衡器 UI
 *  - 可拖拽调整各频段增益 (-12dB ~ +12dB)
 *  - 预设曲线加载/保存/删除
 *  - 实时频谱叠加显示
 *  - 与 PlayerService.EqPresets 联动 */
public class GraphicEqualizer extends View {

    private static final int BAND_COUNT = 10;
    private static final float MIN_DB = -12f;
    private static final float MAX_DB = 12f;
    private static final int[] CENTER_FREQS = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000}; // Hz

    private Paint bgPaint, gridPaint, barPaint, curvePaint, textPaint, handlePaint, spectrumPaint;
    private float[] gains = new float[BAND_COUNT]; // 当前增益 dB
    private float[] spectrum = new float[BAND_COUNT]; // 实时频谱 0-1
    private int draggingBand = -1;
    private float lastTouchY;
    private OnGainChangeListener listener;
    private boolean enabled = true;
    private int colorPrimary = 0xFF1DB954;
    private int colorBg = 0xFF0B0E14;
    private int colorGrid = 0xFF232C3D;
    private int colorText = 0xFF8B949E;

    public interface OnGainChangeListener {
        void onGainChange(int bandIndex, float gainDb);
        void onGainChangeFinish(int[] bands, float[] gains);
    }

    public GraphicEqualizer(Context ctx) { this(ctx, null); }
    public GraphicEqualizer(Context ctx, AttributeSet attrs) { this(ctx, attrs, 0); }
    public GraphicEqualizer(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        init();
        loadBuiltinPreset(ctx, 0); // Pop
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);

        bgPaint = new Paint();
        bgPaint.setColor(colorBg);
        bgPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint();
        gridPaint.setColor(colorGrid);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setAntiAlias(true);

        barPaint = new Paint();
        barPaint.setColor(colorPrimary);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setAntiAlias(true);

        curvePaint = new Paint();
        curvePaint.setColor(0xFF58A6FF);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(3f);
        curvePaint.setAntiAlias(true);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);

        spectrumPaint = new Paint();
        spectrumPaint.setColor(0x44FFFFFF);
        spectrumPaint.setStyle(Paint.Style.FILL);
        spectrumPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(colorText);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        handlePaint = new Paint();
        handlePaint.setColor(0xFFFFFFFF);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAntiAlias(true);
        handlePaint.setShadowLayer(4f, 0, 2, 0x88000000);

        for (int i = 0; i < BAND_COUNT; i++) gains[i] = 0f;
    }

    public void setOnGainChangeListener(OnGainChangeListener l) { listener = l; }
    public void setEnabled(boolean e) { enabled = e; invalidate(); }
    public void setColors(int primary, int bg, int grid, int text) {
        colorPrimary = primary; colorBg = bg; colorGrid = grid; colorText = text;
        barPaint.setColor(colorPrimary);
        gridPaint.setColor(colorGrid);
        textPaint.setColor(colorText);
        bgPaint.setColor(colorBg);
        invalidate();
    }

    /** 设置频谱数据 (0-1, 来自 Visualizer FFT) */
    public void setSpectrum(float[] spec) {
        if (spec != null && spec.length >= BAND_COUNT) {
            System.arraycopy(spec, 0, spectrum, 0, BAND_COUNT);
            postInvalidate();
        }
    }

    /** 获取当前增益数组 */
    public float[] getGains() { return gains.clone(); }

    /** 设置增益 (外部调用, 如加载预设) */
    public void setGains(float[] g) {
        if (g != null && g.length == BAND_COUNT) {
            System.arraycopy(g, 0, gains, 0, BAND_COUNT);
            applyToPlayer();
            invalidate();
        }
    }

    /** 重置为平坦 */
    public void reset() {
        for (int i = 0; i < BAND_COUNT; i++) gains[i] = 0f;
        applyToPlayer();
        invalidate();
    }

    /** 应用到 PlayerService */
    private void applyToPlayer() {
        try {
            for (int i = 0; i < BAND_COUNT; i++) {
                short level = dbToLevel(gains[i]);
                PlayerService.setBand((short) i, level);
            }
            PlayerService.eqReset();
        } catch (Exception ignored) {}
    }

    private short dbToLevel(float db) {
        // Android Equalizer: 单位是毫贝 (mB), 范围通常 -12000 到 +12000
        return (short) Math.max(-12000, Math.min(12000, Math.round(db * 1000)));
    }

    private float levelToDb(short level) { return level / 1000f; }

    /** 加载预设 */
    public void loadPreset(Context ctx, String name) {
        int[] ipreset = EqPresets.getPreset(name);
        if (ipreset != null) {
            float[] preset = new float[ipreset.length];
            for (int i = 0; i < ipreset.length; i++) preset[i] = ipreset[i];
            setGains(preset);
        }
    }

    /** 根据索引加载内置预设 */
    public void loadBuiltinPreset(Context ctx, int index) {
        int[] preset = EqPresets.getPreset(index);
        if (preset != null) {
            float[] g = new float[BAND_COUNT];
            for (int i = 0; i < BAND_COUNT; i++) g[i] = preset[i];
            setGains(g);
        }
    }

    /** 保存用户预设 */
    public void savePreset(Context ctx, String name) {
        SharedPreferences sp = ctx.getSharedPreferences("mf_eq_presets", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (float g : gains) arr.put(g);
            ed.putString(name, arr.toString());
            ed.apply();
        } catch (Exception ignored) {}
    }

    /** 删除用户预设 */
    public void deletePreset(Context ctx, String name) {
        SharedPreferences sp = ctx.getSharedPreferences("mf_eq_presets", Context.MODE_PRIVATE);
        sp.edit().remove(name).apply();
    }

    /** 列出所有预设 (内置 + 用户) */
    public static String[] listPresets(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf_eq_presets", Context.MODE_PRIVATE);
        java.util.Set<String> keys = sp.getAll().keySet();
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        // 内置预设
        list.add("flat"); list.add("pop"); list.add("rock"); list.add("classical");
        list.add("jazz"); list.add("electronic"); list.add("bass"); list.add("vocal");
        // 用户预设
        for (String k : keys) if (!list.contains(k)) list.add(k);
        return list.toArray(new String[0]);
    }

    /** 获取预设增益数组 (内置 + 用户) */
    public static float[] getPreset(Context ctx, String name) {
        // 先查用户预设
        SharedPreferences sp = ctx.getSharedPreferences("mf_eq_presets", Context.MODE_PRIVATE);
        String raw = sp.getString(name, null);
        if (raw != null) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                float[] g = new float[BAND_COUNT];
                for (int i = 0; i < BAND_COUNT; i++) g[i] = (float) arr.optDouble(i, 0);
                return g;
            } catch (Exception ignored) {}
        }
        // 内置预设
        int[] ipreset = EqPresets.getPreset(name);
        if (ipreset != null) {
            float[] g = new float[BAND_COUNT];
            for (int i = 0; i < BAND_COUNT; i++) g[i] = ipreset[i];
            return g;
        }
        return null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawRect(0, 0, w, h, bgPaint);

        // 绘制网格线 (+-12dB, 0dB 中线)
        float topMargin = 40f;
        float bottomMargin = 60f;
        float drawH = h - topMargin - bottomMargin;
        float zeroY = topMargin + drawH * 0.5f;

        // 水平网格 (+-3, +-6, +-9, +-12)
        for (int i = -4; i <= 4; i++) {
            float y = zeroY - i * (drawH / 8f);
            canvas.drawLine(0, y, w, y, gridPaint);
            // dB 标签
            String label = (i * 3) + "dB";
            canvas.drawText(label, 30, y + 8, textPaint);
        }

        // 垂直网格 (频段分隔)
        float bandW = w / (float) BAND_COUNT;
        for (int i = 0; i <= BAND_COUNT; i++) {
            float x = i * bandW;
            canvas.drawLine(x, topMargin, x, h - bottomMargin, gridPaint);
        }

        // 频率标签
        textPaint.setTextSize(20f);
        for (int i = 0; i < BAND_COUNT; i++) {
            float cx = (i + 0.5f) * bandW;
            String freq = CENTER_FREQS[i] >= 1000 ? (CENTER_FREQS[i] / 1000) + "k" : String.valueOf(CENTER_FREQS[i]);
            canvas.drawText(freq, cx, h - 10, textPaint);
        }

        // 绘制频谱背景 (半透明填充)
        if (enabled) {
            Path specPath = new Path();
            for (int i = 0; i < BAND_COUNT; i++) {
                float cx = (i + 0.5f) * bandW;
                float specH = spectrum[i] * drawH * 0.4f;
                float x1 = cx - bandW * 0.4f;
                float x2 = cx + bandW * 0.4f;
                float yBase = zeroY;
                if (i == 0) specPath.moveTo(x1, yBase);
                specPath.lineTo(x1, yBase - specH);
                specPath.lineTo(x2, yBase - specH);
                specPath.lineTo(x2, yBase);
                if (i == BAND_COUNT - 1) specPath.lineTo(x2, yBase);
            }
            specPath.close();
            canvas.drawPath(specPath, spectrumPaint);
        }

        // 绘制均衡器曲线 (平滑贝塞尔)
        if (enabled) {
            Path curvePath = new Path();
            for (int i = 0; i < BAND_COUNT; i++) {
                float cx = (i + 0.5f) * bandW;
                float gainRatio = (gains[i] - MIN_DB) / (MAX_DB - MIN_DB); // 0-1
                float cy = zeroY - (gainRatio - 0.5f) * drawH;
                if (i == 0) curvePath.moveTo(cx, cy);
                else {
                    float prevCx = (i - 0.5f) * bandW;
                    float ctrlX = (prevCx + cx) * 0.5f;
                    curvePath.cubicTo(ctrlX, zeroY + drawH * 0.3f, ctrlX, cy, cx, cy);
                }
            }
            canvas.drawPath(curvePath, curvePaint);
        }

        // 绘制增益柱 + 手柄
        for (int i = 0; i < BAND_COUNT; i++) {
            float cx = (i + 0.5f) * bandW;
            float gainRatio = (gains[i] - MIN_DB) / (MAX_DB - MIN_DB);
            float cy = zeroY - (gainRatio - 0.5f) * drawH;
            float barW = bandW * 0.6f;
            if (gains[i] > 0) {
                canvas.drawRect(cx - barW/2, cy, cx + barW/2, zeroY, barPaint);
            } else if (gains[i] < 0) {
                canvas.drawRect(cx - barW/2, zeroY, cx + barW/2, cy, barPaint);
            } else {
                // 0dB 显示细线
                canvas.drawLine(cx - barW/2, zeroY, cx + barW/2, zeroY, barPaint);
            }

            // 手柄
            if (enabled) {
                canvas.drawCircle(cx, cy, 18f, handlePaint);
                // dB 数值
                textPaint.setTextSize(22f);
                textPaint.setColor(0xFFFFFFFF);
                canvas.drawText(String.format(Locale.US, "%.1f", gains[i]), cx, cy + 8, textPaint);
            }
        }

        // 标题
        textPaint.setTextSize(28f);
        textPaint.setColor(colorText);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Graphic EQ (10-band)", 20, 30, textPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        int w = getWidth();
        int h = getHeight();
        float topMargin = 40f;
        float bottomMargin = 60f;
        float drawH = h - topMargin - bottomMargin;
        float zeroY = topMargin + drawH * 0.5f;
        float bandW = w / (float) BAND_COUNT;

        float x = event.getX();
        float y = event.getY();
        int band = (int) (x / bandW);
        if (band < 0) band = 0;
        if (band >= BAND_COUNT) band = BAND_COUNT - 1;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 检查是否点在手柄附近
                float cx = (band + 0.5f) * bandW;
                float gainRatio = (gains[band] - MIN_DB) / (MAX_DB - MIN_DB);
                float cy = zeroY - (gainRatio - 0.5f) * drawH;
                if (Math.abs(x - cx) < 40 && Math.abs(y - cy) < 60) {
                    draggingBand = band;
                    lastTouchY = y;
                    return true;
                }
                // 点击空白处 -> 该频段归零
                gains[band] = 0f;
                applyToPlayer();
                if (listener != null) listener.onGainChange(band, 0f);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (draggingBand >= 0) {
                    float dy = lastTouchY - y; // 向上拖动 = 增益增加
                    float dbPerPixel = (MAX_DB - MIN_DB) / drawH;
                    float newGain = gains[draggingBand] + dy * dbPerPixel;
                    newGain = Math.max(MIN_DB, Math.min(MAX_DB, newGain));
                    gains[draggingBand] = newGain;
                    applyToPlayer();
                    if (listener != null) listener.onGainChange(draggingBand, newGain);
                    lastTouchY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (draggingBand >= 0) {
                    if (listener != null) listener.onGainChangeFinish(new int[]{draggingBand}, new float[]{gains[draggingBand]});
                    draggingBand = -1;
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    // 为了编译通过
    static { try { java.util.Locale.class.getName(); } catch (Exception ignored) {} }
}