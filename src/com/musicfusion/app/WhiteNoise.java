package com.musicfusion.app;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * WhiteNoise v11 — 离线白噪声/粉噪声/棕噪声生成器
 * 算法: 标准Voss-McCartney (粉/棕) 或 Linear Congruential (白)
 * 输出: 16-bit PCM mono 22050Hz, 30秒循环, 写入cache, PlayerService以文件路径播放
 */
public class WhiteNoise {

    public static final int SAMPLE_RATE = 22050;
    public static final int DURATION_SEC = 30;
    public static final int TOTAL = SAMPLE_RATE * DURATION_SEC;

    /** 生成 noise.wav 三层混音版到 cacheDir, 返回文件路径 */
    public static String generate(java.io.File cacheDir, int rain, int fire, int brown) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File out = new File(cacheDir, "noise_" + rain + "_" + fire + "_" + brown + ".wav");
            if (out.exists() && out.length() > 100000) return out.getAbsolutePath();
            byte[] wav = buildWav(rain, fire, brown);
            FileOutputStream fo = new FileOutputStream(out);
            fo.write(wav);
            fo.close();
            return out.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    static byte[] buildWav(int rain, int fire, int brown) {
        int n = TOTAL;
        int[] mix = new int[n];
        if (rain > 0)   addRain(mix, rain);
        if (fire > 0)   addFire(mix, fire);
        if (brown > 0)  addBrown(mix, brown);
        // 16-bit PCM mono 22050Hz
        int dataBytes = n * 2;
        int totalBytes = 36 + dataBytes;
        byte[] h = new byte[44];
        h[0]='R'; h[1]='I'; h[2]='F'; h[3]='F';
        writeInt(h, 4, totalBytes);
        h[8]='W'; h[9]='A'; h[10]='V'; h[11]='E';
        h[12]='f'; h[13]='m'; h[14]='t'; h[15]=' ';
        writeInt(h, 16, 16);           // fmt chunk size
        writeShort(h, 20, 1);          // PCM
        writeShort(h, 22, 1);          // channels
        writeInt(h, 24, SAMPLE_RATE);  // sample rate
        writeInt(h, 28, SAMPLE_RATE * 2);  // byte rate
        writeShort(h, 32, 2);          // block align
        writeShort(h, 34, 16);         // bits per sample
        h[36]='d'; h[37]='a'; h[38]='t'; h[39]='a';
        writeInt(h, 40, dataBytes);
        byte[] pcm = new byte[dataBytes];
        for (int i = 0; i < n; i++) {
            int v = mix[i];
            if (v > 32767) v = 32767;
            if (v < -32768) v = -32768;
            pcm[i * 2]     = (byte) (v & 0xff);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        byte[] full = new byte[44 + dataBytes];
        System.arraycopy(h, 0, full, 0, 44);
        System.arraycopy(pcm, 0, full, 44, dataBytes);
        return full;
    }

    static void writeInt(byte[] b, int o, int v) {
        b[o]   = (byte) (v & 0xff);
        b[o+1] = (byte) ((v >> 8) & 0xff);
        b[o+2] = (byte) ((v >> 16) & 0xff);
        b[o+3] = (byte) ((v >> 24) & 0xff);
    }
    static void writeShort(byte[] b, int o, int v) {
        b[o]   = (byte) (v & 0xff);
        b[o+1] = (byte) ((v >> 8) & 0xff);
    }

    // ── 雨声: 模拟随机滴落 + 持续白噪声底 ──
    static void addRain(int[] m, int vol) {
        Random r = new Random(42);
        int n = m.length;
        for (int i = 0; i < n; i++) m[i] += (r.nextInt(2001) - 1000) * vol / 100 / 3;
        // 稀疏雨滴爆点
        for (int d = 0; d < n; d += 50 + r.nextInt(150)) {
            if (d >= n) break;
            int burst = (r.nextInt(4001) - 2000) * vol / 100;
            for (int k = 0; k < 40 && d + k < n; k++)
                m[d + k] += burst * (40 - k) / 40;
        }
    }

    // ── 柴火: 低频爆裂+噼啪声 ──
    static void addFire(int[] m, int vol) {
        Random r = new Random(99);
        int n = m.length;
        for (int i = 0; i < n; i += 2) {
            int burst = (r.nextInt(3001) - 1500) * vol / 100;
            m[i] += burst / 2;
            if (i + 1 < n) m[i + 1] += burst / 2;
        }
        for (int d = 0; d < n; d += 200 + r.nextInt(600)) {
            if (d >= n) break;
            int crack = (r.nextInt(8001) - 4000) * vol / 100;
            for (int k = 0; k < 20 && d + k < n; k++)
                m[d + k] += crack * (20 - k) / 20;
        }
    }

    // ── 棕噪声: 累积白噪声, 强低频压制 ──
    static void addBrown(int[] m, int vol) {
        Random r = new Random(7);
        double last = 0;
        for (int i = 0; i < m.length; i++) {
            double white = (r.nextDouble() - 0.5) * 2;
            last = (last + 0.02 * white) / 1.02;
            m[i] += (int) (last * 8000 * vol / 100);
        }
    }
}
