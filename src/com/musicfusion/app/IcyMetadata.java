package com.musicfusion.app;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** IcyMetadata v12 — 直播流 ICY 元数据解析 */
public class IcyMetadata {
    static final Pattern P_TITLE = Pattern.compile("StreamTitle='([^']*)'");

    public static String[] fetch(String streamUrl) {
        String[] r = new String[]{"", "", ""};
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(streamUrl).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "MusicFusion/12.0");
            c.setRequestProperty("Icy-MetaData", "1");
            c.connect();
            r[0] = c.getHeaderField("icy-name") != null ? c.getHeaderField("icy-name") : "";
            r[1] = c.getHeaderField("icy-genre") != null ? c.getHeaderField("icy-genre") : "";
            String metaInt = c.getHeaderField("icy-metaint");
            if (metaInt == null) return r;
            int interval = Integer.parseInt(metaInt);
            InputStream in = c.getInputStream();
            long skip = 0;
            byte[] buf = new byte[4096];
            while (skip < interval) {
                int need = (int) Math.min(buf.length, interval - skip);
                int n = in.read(buf, 0, need);
                if (n < 0) break;
                skip += n;
            }
            int len = in.read();
            if (len < 0 || len == 0) return r;
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
        } finally {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
        }
        return r;
    }
}