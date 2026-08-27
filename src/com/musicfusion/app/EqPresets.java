package com.musicfusion.app;

import android.media.audiofx.Equalizer;

/**
 * EqPresets v11 — 5种音乐风格预设(流行/摇滚/古典/爵士/电子)
 * 单位: mB (millibel = 0.1dB), 0=平线
 * 顺序: 60Hz/230Hz/910Hz/3.6kHz/14kHz (5段近似)
 */
public class EqPresets {

    static final int[][] PRESETS = {
        // {60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz}
        { 200,  -100,  0,    200,  300},   // Pop 流行 — 低音下潜+人声提亮+空气感
        { 400,  200,   -200, 100,  400},   // Rock 摇滚 — 重低音+失真感提亮
        { 300,  100,   0,    200,  200},   // Classical 古典 — 全频平衡微调
        { 200,  100,   -100, 0,    200},   // Jazz 爵士 — 中频下凹+高频柔和
        { 500,  0,     -300, 200,  500},   // Electronic 电子 — 重低+切中频+亮高频
    };
    static final String[] NAMES = {"Pop", "Rock", "Classical", "Jazz", "Electronic"};

    /** 应用预设; bandCount < 5 时按比例压缩/分配 */
    public static void apply(EqPresetsApply a, int idx) {
        if (idx < 0 || idx >= PRESETS.length) return;
        int[] p = PRESETS[idx];
        short bands = a.bandCount();
        if (bands <= 0) return;
        short[] range = a.eqRange();
        if (range == null) return;
        for (short i = 0; i < bands; i++) {
            int srcIdx = (int) Math.round((double) i * (p.length - 1) / Math.max(1, bands - 1));
            int v = p[srcIdx];
            if (v < range[0]) v = range[0];
            if (v > range[1]) v = range[1];
            a.setBand(i, (short) v);
        }
    }

    public static int count() { return NAMES.length; }
    public static String name(int i) { return NAMES[i]; }

    /** 抽象层 — 让本类不直接依赖PlayerService的static, 方便测试 */
    public interface EqPresetsApply {
        short bandCount();
        short[] eqRange();
        void setBand(short band, short level);
    }

    /** 真实适配器 — 桥接 PlayerService */
    public static class Real implements EqPresetsApply {
        public short bandCount() { return PlayerService.bandCount(); }
        public short[] eqRange() { return PlayerService.eqBands(); }
        public void setBand(short b, short v) { PlayerService.setBand(b, v); }
    }
}
