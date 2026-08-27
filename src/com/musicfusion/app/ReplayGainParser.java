package com.musicfusion.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** ReplayGainParser — 解析 MP3(ID3v2)/FLAC(VorbisComment)/OGG(VorbisComment)/OPUS 的 ReplayGain 标签
 * 支持: TXXX:REPLAYGAIN_TRACK_GAIN, TXXX:REPLAYGAIN_ALBUM_GAIN, R128_TRACK_GAIN, R128_ALBUM_GAIN
 * 单位: dB, 目标响度 -16 LUFS (EBU R128) / 89 dB (ReplayGain) */
public final class ReplayGainParser {
    private static final String TAG = "ReplayGainParser";

    public static class GainInfo {
        public float trackGain = 0f;    // dB
        public float albumGain = 0f;    // dB
        public float trackPeak = 1f;    // 0-1
        public float albumPeak = 1f;    // 0-1
        public boolean hasTrack = false;
        public boolean hasAlbum = false;
        public String source = "";      // "id3" / "vorbis" / "opus"

        public float getEffectiveGain(boolean useAlbum) {
            if (useAlbum && hasAlbum) return albumGain;
            if (hasTrack) return trackGain;
            return 0f;
        }
        public float getEffectivePeak(boolean useAlbum) {
            if (useAlbum && hasAlbum) return albumPeak;
            if (hasTrack) return trackPeak;
            return 1f;
        }
    }

    /** 统一入口：自动识别格式并解析 */
    public static GainInfo parse(File file) {
        if (file == null || !file.exists() || !file.canRead()) return new GainInfo();
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".mp3")) return parseMp3(file);
            if (name.endsWith(".flac")) return parseFlac(file);
            if (name.endsWith(".ogg") || name.endsWith(".oga")) return parseOgg(file);
            if (name.endsWith(".opus")) return parseOpus(file);
        } catch (Exception e) {
            android.util.Log.w(TAG, "parse failed: " + file.getName(), e);
        }
        return new GainInfo();
    }

    // ========= MP3 ID3v2 =========
    private static GainInfo parseMp3(File file) throws IOException {
        GainInfo info = new GainInfo();
        info.source = "id3";
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[10];
            if (fis.read(header) != 10) return info;
            if (!matches(header, 0, "ID3")) return info;
            int version = header[3] & 0xFF;
            int flags = header[5] & 0xFF;
            int tagSize = syncSafeInt(header, 6);
            boolean footerPresent = (flags & 0x10) != 0;
            long tagEnd = 10 + tagSize + (footerPresent ? 10 : 0);
            long pos = 10;
            while (pos + 10 <= tagEnd) {
                fis.getChannel().position(pos);
                byte[] frameHeader = new byte[10];
                int read = fis.read(frameHeader);
                if (read != 10) break;
                String frameId = new String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1).trim();
                int frameSize = syncSafeInt(frameHeader, 4);
                if (frameId.isEmpty() || frameSize <= 0 || frameSize > 1024 * 1024) break;
                pos += 10;
                if (frameId.equals("TXXX")) {
                    byte[] frameData = new byte[frameSize];
                    fis.read(frameData);
                    String txt = new String(frameData, StandardCharsets.UTF_8);
                    // TXXX 结构: encoding(1) + description\0 + value
                    if (frameData.length > 1) {
                        int descEnd = -1;
                        for (int i = 1; i < frameData.length; i++) {
                            if (frameData[i] == 0) { descEnd = i; break; }
                        }
                        if (descEnd > 1) {
                            String desc = new String(frameData, 1, descEnd - 1, StandardCharsets.UTF_8);
                            String val = new String(frameData, descEnd + 1, frameData.length - descEnd - 1, StandardCharsets.UTF_8);
                            parseReplayGainText(desc, val, info);
                        }
                    }
                } else {
                    fis.skip(frameSize);
                }
                pos += frameSize;
            }
        }
        return info;
    }

    private static void parseReplayGainText(String desc, String val, GainInfo info) {
        String d = desc.toUpperCase();
        try {
            float v = Float.parseFloat(val.replace(" dB", "").trim());
            if (d.contains("TRACK_GAIN") || d.contains("R128_TRACK")) {
                info.trackGain = v; info.hasTrack = true;
            } else if (d.contains("ALBUM_GAIN") || d.contains("R128_ALBUM")) {
                info.albumGain = v; info.hasAlbum = true;
            } else if (d.contains("TRACK_PEAK")) {
                info.trackPeak = v; info.hasTrack = true;
            } else if (d.contains("ALBUM_PEAK")) {
                info.albumPeak = v; info.hasAlbum = true;
            }
        } catch (NumberFormatException ignored) {}
    }

    // ========= FLAC (VorbisComment) =========
    private static GainInfo parseFlac(File file) throws IOException {
        GainInfo info = new GainInfo();
        info.source = "vorbis";
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            fis.read(magic);
            if (!matches(magic, 0, "fLaC")) return info;
            while (true) {
                byte[] blockHeader = new byte[4];
                if (fis.read(blockHeader) != 4) break;
                boolean last = (blockHeader[0] & 0x80) != 0;
                int type = blockHeader[0] & 0x7F;
                int length = ((blockHeader[1] & 0xFF) << 16) | ((blockHeader[2] & 0xFF) << 8) | (blockHeader[3] & 0xFF);
                if (type == 4) { // VORBIS_COMMENT
                    byte[] data = new byte[length];
                    fis.read(data);
                    parseVorbisComment(data, info);
                } else {
                    fis.skip(length);
                }
                if (last) break;
            }
        }
        return info;
    }

    // ========= OGG (VorbisComment in 2nd page) =========
    private static GainInfo parseOgg(File file) throws IOException {
        GainInfo info = new GainInfo();
        info.source = "vorbis";
        try (FileInputStream fis = new FileInputStream(file)) {
            // 简单扫描：找到 "vorbis" 标识的页面
            byte[] buf = new byte[4096];
            while (fis.read(buf) > 0) {
                for (int i = 0; i < buf.length - 6; i++) {
                    if (matches(buf, i, "vorbis")) {
                        // 找到 vorbis comment 包，尝试解析
                        int pageStart = i - 27; // 粗略回退到页面头
                        if (pageStart < 0) pageStart = 0;
                        fis.getChannel().position(pageStart);
                        // 这里简化：直接读取剩余并找注释
                        byte[] rest = new byte[(int) Math.min(64*1024, file.length() - pageStart)];
                        fis.read(rest);
                        parseVorbisComment(rest, info);
                        return info;
                    }
                }
            }
        }
        return info;
    }

    // ========= OPUS (VorbisComment in OpusHead) =========
    private static GainInfo parseOpus(File file) throws IOException {
        GainInfo info = new GainInfo();
        info.source = "opus";
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[64*1024];
            int read = fis.read(buf);
            for (int i = 0; i < read - 8; i++) {
                if (matches(buf, i, "OpusHead")) {
                    // OpusHead 后跟着 VorbisComment 页面
                    // 简化：从这里找 vorbis
                    for (int j = i; j < read - 6; j++) {
                        if (matches(buf, j, "vorbis")) {
                            parseVorbisComment(java.util.Arrays.copyOfRange(buf, j, read), info);
                            return info;
                        }
                    }
                }
            }
        }
        return info;
    }

    // ========= VorbisComment 通用解析 =========
    private static void parseVorbisComment(byte[] data, GainInfo info) {
        try {
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            // 跳过 vendor string
            if (bb.remaining() < 4) return;
            int vendorLen = bb.getInt();
            if (vendorLen < 0 || vendorLen > bb.remaining()) return;
            bb.position(bb.position() + vendorLen);
            if (bb.remaining() < 4) return;
            int comments = bb.getInt();
            for (int i = 0; i < comments && bb.remaining() > 4; i++) {
                int len = bb.getInt();
                if (len < 0 || len > bb.remaining()) break;
                byte[] commentBytes = new byte[len];
                bb.get(commentBytes);
                String comment = new String(commentBytes, StandardCharsets.UTF_8);
                int eq = comment.indexOf('=');
                if (eq > 0) {
                    String key = comment.substring(0, eq).toUpperCase();
                    String val = comment.substring(eq + 1);
                    parseReplayGainText(key, val, info);
                }
            }
        } catch (Exception ignored) {}
    }

    // ========= 工具方法 =========
    private static boolean matches(byte[] data, int offset, String str) {
        if (offset + str.length() > data.length) return false;
        for (int i = 0; i < str.length(); i++) {
            if (data[offset + i] != (byte) str.charAt(i)) return false;
        }
        return true;
    }

    private static int syncSafeInt(byte[] b, int offset) {
        return ((b[offset] & 0x7F) << 21) | ((b[offset+1] & 0x7F) << 14) |
               ((b[offset+2] & 0x7F) << 7) | (b[offset+3] & 0x7F);
    }

    /** 将 dB 增益转换为 MediaPlayer 可用的线性音量因子 (0.0-1.0) */
    public static float gainToVolume(float gainDb) {
        // ReplayGain 参考电平 89 dB, 目标响度 -16 LUFS ≈ 89 dB SPL
        // gainDb 为负表示降低音量, 为正表示升高
        // 线性因子 = 10^(gainDb/20)
        float factor = (float) Math.pow(10.0, gainDb / 20.0);
        // 限制在合理范围
        return Math.max(0.1f, Math.min(2.0f, factor));
    }
}