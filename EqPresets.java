package com.musicfusion.app;

import android.media.audiofx.Equalizer;

/** EqPresets v13.0 — 10 种音乐风格预设 (5 原 + 5 新增)
 * 原: Pop, Rock, Classical, Jazz, Electronic
 * 新: Bass, Vocal, Acoustic, HipHop, Flat
 * 频率中心 (Hz): 60, 230, 910, 3.6k, 14k
 * 增益 (mB, -1500..+1500): 流行中等, 重低音多, 古典平, 爵士凸中音, 电子切中
 *
 * 真实 EQ 系统支持任意段数 (通过 EqPresetsApply 抽象).
 * apply() 用线性插值分配 5 段预设到设备实际段数.
 */
public class EqPresets {
    // {60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz}  增益单位 mB (-1500..+1500)
    static final int[][] PRESETS = {
        // 原有 5 个
        {  200, -100,    0,  200,  300},   // Pop        流行 - 低音下潜 + 人声提亮
        {  400,  200, -200,  100,  400},   // Rock       摇滚 - 重低音 + 失真感提亮
        {  300,  100,    0,  200,  200},   // Classical  古典 - 全频平衡微调
        {  200,  100, -100,    0,  200},   // Jazz       爵士 - 凸中音人声
        {  500,    0, -300,  200,  500},   // Electronic 电子 - 重低 + 切中 + 空气感
        // v13 新增 5 个
        {  600,  200,    0,  100,  200},   // Bass       重低音 - 加重 60Hz+230Hz
        {  100,  300,  500,  300,  100},   // Vocal      人声 - 凸 910Hz 提亮人声
        {  200,    0,  100,  300,  200},   // Acoustic   原声 - 凸 3.6kHz 突出指弹
        {  500,  100, -200,    0,  200},   // HipHop     嘻哈 - 重低 + 切中
        {    0,    0,    0,    0,    0},   // Flat       平线 - 旁路 EQ
    };
    static final String[] NAMES = {
        "Pop", "Rock", "Classical", "Jazz", "Electronic",
        "Bass", "Vocal", "Acoustic", "HipHop", "Flat"
    };

    /** 应用预设到设备 EQ (设备段数自动适配) */
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

    public interface EqPresetsApply {
        short bandCount();
        short[] eqRange();
        void setBand(short band, short level);
    }

    public static class Real implements EqPresetsApply {
        public short bandCount() { return PlayerService.bandCount(); }
        public short[] eqRange() { return PlayerService.eqBands(); }
        public void setBand(short b, short v) { PlayerService.setBand(b, v); }
    }
}
