package com.musicfusion.app;

import android.media.audiofx.Equalizer;

/** EqPresets v12 — 5种音乐风格预设 (流行/摇滚/古典/爵士/电子) */
public class EqPresets {
    static final int[][] PRESETS = {
        // {60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz}
        { 200,  -100,  0,    200,  300},   // Pop
        { 400,  200,   -200, 100,  400},   // Rock
        { 300,  100,   0,    200,  200},   // Classical
        { 200,  100,   -100, 0,    200},   // Jazz
        { 500,  0,     -300, 200,  500},   // Electronic
    };
    static final String[] NAMES = {"Pop", "Rock", "Classical", "Jazz", "Electronic"};

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
    public static int[] getPreset(int i) { return (i >= 0 && i < PRESETS.length) ? PRESETS[i] : null; }
    public static int[] getPreset(String n) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(n)) return PRESETS[i];
        return null;
    }
    public static float[] getPresetFloat(int i) { 
        int[] p = getPreset(i); 
        if (p == null) return null; 
        float[] f = new float[p.length]; 
        for (int j = 0; j < p.length; j++) f[j] = p[j]; 
        return f; 
    }
    public static float[] getPresetFloat(String n) { 
        int[] p = getPreset(n); 
        if (p == null) return null; 
        float[] f = new float[p.length]; 
        for (int j = 0; j < p.length; j++) f[j] = p[j]; 
        return f; 
    }

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