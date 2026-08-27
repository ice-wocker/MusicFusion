package com.musicfusion.app;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IcyMetadata v11 — 直播流 ICY 元数据解析 (StreamTitle)
 * Shoutcast/Icecast 协议: HTTP响应头 icy-metaint 间隔插入元数据
 * 用法: 短连接HEAD/GET 10秒, 解析 icy-name/icy-genre/StreamTitle
 */
public class IcyMetadata {

    static final Pattern P_TITLE = Pattern.compile("StreamTitle='([^']*)'");

    /** 抓取一次元数据快照, 返回 {station, genre, current} 任一为空字符串 */
    public static String[] fetch(String streamUrl) {
        String[] r = new String[]{"", "", ""};
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(streamUrl).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "MusicFusion/1.0");
            c.setRequestProperty("Icy-MetaData", "1");
            c.connect();
            r[0] = c.getHeaderField("icy-name") != null ? c.getHeaderField("icy-name") : "";
            r[1] = c.getHeaderField("icy-genre") != null ? c.getHeaderField("icy-genre") : "";
            String metaInt = c.getHeaderField("icy-metaint");
            if (metaInt == null) return r;
            int interval = Integer.parseInt(metaInt);
            InputStream in = c.getInputStream();
            // 跳过音频数据
            long skip = 0;
            byte[] buf = new byte[4096];
            while (skip < interval) {
                int need = (int) Math.min(buf.length, interval - skip);
                int n = in.read(buf, 0, need);
                if (n < 0) break;
                skip += n;
            }
            // 读元数据长度字节
            int len = in.read();
            if (len < 0 || len == 0) return r;
            // 长度×16 = 元数据字节
            int metaLen = len * 16;
            byte[] meta = new byte[metaLen];
            int got = 0;
            while (got < metaLen) {
                int n = in.read(meta, got, metaLen - got);
                if (n < 0) break;
                got += n;
            }
            String s = new String(meta, 0, got, "UTF-8").trim();
            Matcher m = P_TITLE.matcher(s);
            if (m.find()) r[2] = m.group(1);
            in.close();
        } catch (Exception ignored) {
            // 网络超时/无元数据/格式异常都视为空, 不抛
        } finally {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }
        return r;
    }
}
