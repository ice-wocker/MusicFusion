package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

/** VisualizerView — 波形/频谱柱/圆环 可视化 (API 24+ Visualizer API) */
public class VisualizerView extends View {
    private static final String TAG = "VisualizerView";
    private static final int MODE_WAVEFORM = 0;
    private static final int MODE_BARS = 1;
    private static final int MODE_CIRCLE = 2;
    private static final int MODE_PARTICLES = 3;

    private Visualizer visualizer;
    private byte[] fftBytes;
    private byte[] waveBytes;
    private int mode = MODE_BARS;
    private boolean enabled = false;
    private int sessionId = -1;

    // 绘制参数
    private Paint barPaint, wavePaint, circlePaint, particlePaint, glowPaint;
    private int barCount = 64;
    private float[] barHeights;
    private float[] particleX, particleY, particleVx, particleVy, particleLife;
    private int particleCount = 80;
    private long lastFrameTime = 0;
    private int colorPrimary = 0xFF1DB954;
    private int colorSecondary = 0xFF58A6FF;
    private int colorBg = 0xFF0B0E14;
    private boolean useGradient = true;
    private float sensitivity = 1.0f;
    private boolean mirror = false;
    private int fpsTarget = 30;
    private long frameInterval = 1000 / 30;

    public VisualizerView(Context ctx) { this(ctx, null); }
    public VisualizerView(Context ctx, AttributeSet attrs) { this(ctx, attrs, 0); }
    public VisualizerView(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        init();
        loadPrefs(ctx);
    }

    private void init() {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        barPaint = new Paint();
        barPaint.setAntiAlias(true);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setStrokeCap(Paint.Cap.ROUND);

        wavePaint = new Paint();
        wavePaint.setAntiAlias(true);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(2f);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeJoin(Paint.Join.ROUND);

        circlePaint = new Paint();
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(3f);
        circlePaint.setStrokeCap(Paint.Cap.ROUND);

        particlePaint = new Paint();
        particlePaint.setAntiAlias(true);
        particlePaint.setStyle(Paint.Style.FILL);

        glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAlpha(80);

        barHeights = new float[barCount];
        particleX = new float[particleCount];
        particleY = new float[particleCount];
        particleVx = new float[particleCount];
        particleVy = new float[particleCount];
        particleLife = new float[particleCount];
        for (int i = 0; i < particleCount; i++) resetParticle(i);
    }

    private void loadPrefs(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf_vis", Context.MODE_PRIVATE);
        mode = sp.getInt("vis_mode", MODE_BARS);
        enabled = sp.getBoolean("vis_enabled", true);
        barCount = sp.getInt("vis_bars", 64);
        sensitivity = sp.getFloat("vis_sens", 1.0f);
        mirror = sp.getBoolean("vis_mirror", false);
        fpsTarget = sp.getInt("vis_fps", 30);
        frameInterval = 1000 / fpsTarget;
        colorPrimary = sp.getInt("vis_color_pri", 0xFF1DB954);
        colorSecondary = sp.getInt("vis_color_sec", 0xFF58A6FF);
        useGradient = sp.getBoolean("vis_grad", true);
        barHeights = new float[barCount];
    }

    public void savePrefs(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("mf_vis", Context.MODE_PRIVATE);
        sp.edit()
            .putInt("vis_mode", mode)
            .putBoolean("vis_enabled", enabled)
            .putInt("vis_bars", barCount)
            .putFloat("vis_sens", sensitivity)
            .putBoolean("vis_mirror", mirror)
            .putInt("vis_fps", fpsTarget)
            .putInt("vis_color_pri", colorPrimary)
            .putInt("vis_color_sec", colorSecondary)
            .putBoolean("vis_grad", useGradient)
            .apply();
    }

    /** 绑定音频会话 (MediaPlayer.getAudioSessionId()) */
    public void link(int audioSessionId) {
        if (audioSessionId <= 0) return;
        if (sessionId == audioSessionId && visualizer != null && visualizer.getEnabled()) return;
        release();
        sessionId = audioSessionId;
        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setEnabled(false);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {
                    if (!enabled) return;
                    waveBytes = waveform;
                    postInvalidateOnAnimation();
                }
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    if (!enabled) return;
                    fftBytes = fft;
                    processFft(fft);
                    postInvalidateOnAnimation();
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);
            visualizer.setEnabled(true);
        } catch (Exception e) {
            android.util.Log.w(TAG, "Visualizer link failed", e);
            release();
        }
    }

    private void processFft(byte[] fft) {
        if (fft == null) return;
        int n = Math.min(barCount, fft.length / 2);
        for (int i = 0; i < n; i++) {
            // FFT 幅度: 0-255, 取模
            int re = fft[i * 2] & 0xFF;
            int im = fft[i * 2 + 1] & 0xFF;
            float mag = (float) Math.sqrt(re * re + im * im) / 128f;
            // 对数缩放 + 平滑
            float target = (float) (Math.log1p(mag * sensitivity * 10f) / Math.log(11f)) * sensitivity;
            barHeights[i] = barHeights[i] * 0.6f + target * 0.4f;
        }
    }

    public void release() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {}
            visualizer = null;
        }
        sessionId = -1;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (now - lastFrameTime < frameInterval) {
            postInvalidateDelayed(frameInterval - (now - lastFrameTime));
            return;
        }
        lastFrameTime = now;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawColor(colorBg);

        // 渐变着色器
        Shader shader = null;
        if (useGradient) {
            shader = new LinearGradient(0, h, 0, 0, colorPrimary, colorSecondary, Shader.TileMode.CLAMP);
            barPaint.setShader(shader);
            wavePaint.setShader(shader);
            circlePaint.setShader(shader);
        } else {
            barPaint.setShader(null);
            wavePaint.setColor(colorPrimary);
            circlePaint.setColor(colorPrimary);
        }

        switch (mode) {
            case MODE_WAVEFORM: drawWaveform(canvas, w, h); break;
            case MODE_BARS: drawBars(canvas, w, h); break;
            case MODE_CIRCLE: drawCircle(canvas, w, h); break;
            case MODE_PARTICLES: drawParticles(canvas, w, h); break;
        }

        if (shader != null) {
            barPaint.setShader(null);
            wavePaint.setShader(null);
            circlePaint.setShader(null);
        }
    }

    private void drawBars(Canvas canvas, int w, int h) {
        int n = Math.min(barCount, barHeights.length);
        float barW = (float) w / (mirror ? n : n * 2);
        float cx = w / 2f;
        for (int i = 0; i < n; i++) {
            float bh = barHeights[i] * h * 0.45f;
            float x, x2;
            if (mirror) {
                x = i * barW;
                canvas.drawRect(x, h/2f - bh, x + barW * 0.8f, h/2f, barPaint);
                canvas.drawRect(x, h/2f, x + barW * 0.8f, h/2f + bh, barPaint);
            } else {
                x = cx - (n - i) * barW * 2;
                x2 = cx + (i - 1) * barW * 2;
                canvas.drawRect(x, h - bh, x + barW * 1.5f, h, barPaint);
                canvas.drawRect(x2, h - bh, x2 + barW * 1.5f, h, barPaint);
            }
        }
    }

    private void drawWaveform(Canvas canvas, int w, int h) {
        if (waveBytes == null) return;
        float cx = w / 2f;
        float cy = h / 2f;
        float scale = h / 512f * sensitivity;
        float step = (float) w / waveBytes.length;
        wavePaint.setColor(colorPrimary);
        float px = 0, py = cy;
        for (int i = 0; i < waveBytes.length; i++) {
            float x = i * step;
            float y = cy - (waveBytes[i] - 128) * scale;
            if (i > 0) canvas.drawLine(px, py, x, y, wavePaint);
            px = x; py = y;
        }
    }

    private void drawCircle(Canvas canvas, int w, int h) {
        if (fftBytes == null) return;
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(w, h) * 0.3f;
        int n = Math.min(barCount, fftBytes.length / 2);
        float angleStep = 360f / n;
        for (int i = 0; i < n; i++) {
            int re = fftBytes[i * 2] & 0xFF;
            int im = fftBytes[i * 2 + 1] & 0xFF;
            float mag = (float) Math.sqrt(re * re + im * im) / 128f;
            float r = radius + mag * radius * 0.5f * sensitivity;
            float angle = i * angleStep - 90f;
            float x1 = cx + radius * (float) Math.cos(Math.toRadians(angle));
            float y1 = cy + radius * (float) Math.sin(Math.toRadians(angle));
            float x2 = cx + r * (float) Math.cos(Math.toRadians(angle));
            float y2 = cy + r * (float) Math.sin(Math.toRadians(angle));
            circlePaint.setColor(interpolateColor(colorPrimary, colorSecondary, i / (float) n));
            canvas.drawLine(x1, y1, x2, y2, circlePaint);
        }
        // 中心光环
        glowPaint.setColor(colorPrimary);
        glowPaint.setAlpha(60);
        canvas.drawCircle(cx, cy, radius * 0.5f, glowPaint);
    }

    private void drawParticles(Canvas canvas, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        float avgLevel = 0f;
        if (fftBytes != null) {
            for (int i = 0; i < Math.min(32, fftBytes.length / 2); i++) {
                int re = fftBytes[i * 2] & 0xFF;
                int im = fftBytes[i * 2 + 1] & 0xFF;
                avgLevel += Math.sqrt(re * re + im * im);
            }
            avgLevel /= 32f * 128f;
        }
        float force = avgLevel * sensitivity * 200f;

        for (int i = 0; i < particleCount; i++) {
            particleLife[i] -= 1f / 60f;
            if (particleLife[i] <= 0) resetParticle(i);
            particleX[i] += particleVx[i];
            particleY[i] += particleVy[i];
            particleVy[i] += 0.5f; // 重力

            float alpha = Math.max(0, Math.min(1, particleLife[i] * 2f));
            int c = interpolateColor(colorPrimary, colorSecondary, alpha);
            particlePaint.setColor(c);
            particlePaint.setAlpha((int) (alpha * 255));
            float size = 2f + alpha * 6f;
            canvas.drawCircle(particleX[i], particleY[i], size, particlePaint);
        }

        // 根据音频能量在中心生成新粒子
        if (force > 10f) {
            for (int i = 0; i < 3; i++) {
                int idx = (int) (Math.random() * particleCount);
                particleX[idx] = cx + (float) (Math.random() - 0.5) * 20;
                particleY[idx] = cy + (float) (Math.random() - 0.5) * 20;
                double angle = Math.random() * 2 * Math.PI;
                float speed = (float) (force * 0.01 * (0.5 + Math.random()));
                particleVx[idx] = (float) (Math.cos(angle) * speed);
                particleVy[idx] = (float) (Math.sin(angle) * speed) - force * 0.02f;
                particleLife[idx] = 1f + (float) Math.random() * 2f;
            }
        }
    }

    private void resetParticle(int i) {
        particleLife[i] = 0;
        particleX[i] = particleY[i] = particleVx[i] = particleVy[i] = 0;
    }

    private int interpolateColor(int c1, int c2, float t) {
        int r = (int) (Color.red(c1) * (1 - t) + Color.red(c2) * t);
        int g = (int) (Color.green(c1) * (1 - t) + Color.green(c2) * t);
        int b = (int) (Color.blue(c1) * (1 - t) + Color.blue(c2) * t);
        return Color.rgb(r, g, b);
    }

    // ===== 公共配置 API =====
    public void setMode(int m) { if (m >= 0 && m <= 3) { mode = m; invalidate(); } }
    public int getMode() { return mode; }
    public void setEnabled(boolean e) { enabled = e; if (!e) invalidate(); }
    public boolean isEnabled() { return enabled; }
    public void setBarCount(int c) { barCount = Math.max(16, Math.min(256, c)); barHeights = new float[barCount]; }
    public void setSensitivity(float s) { sensitivity = Math.max(0.1f, Math.min(5f, s)); }
    public void setMirror(boolean m) { mirror = m; }
    public void setFps(int fps) { fpsTarget = Math.max(15, Math.min(60, fps)); frameInterval = 1000 / fpsTarget; }
    public void setColors(int primary, int secondary) { colorPrimary = primary; colorSecondary = secondary; }
    public void setUseGradient(boolean g) { useGradient = g; }
}