package com.musicfusion.app;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/** LyricsTranslate v13.0 — 歌词翻译集成
 * 公开 API:
 *   - Google Translate (无 key, 限频, 仅 demo, 实际项目应自配)
 *   - MyMemory (免费, 无 key, 限 5000 字/日/IP)
 *   - LibreTranslate (开源, 需自部署或公共实例)
 *
 * 用法 (异步):
 *   LyricsTranslate.translateAsync(lyrics, "zh-CN", "en", new Callback() {
 *       public void onResult(String translated) { ... }
 *       public void onError(String err) { ... }
 *   });
 *
 * 集成到 MainActivity / LyricsEngine:
 *   - 歌词显示时加 "译" 按钮
 *   - 点按 → 异步调用 → 显示双语并列
 */
public class LyricsTranslate {
    private static final String TAG = "LyricsTranslate";
    public static final String MYMEMORY = "https://api.mymemory.translated.net/get";

    public enum Provider { MYMEMORY, LIBRE, GOOGLE }

    public interface Callback {
        void onResult(String translated, Provider provider);
        void onError(String error);
    }

    /** 异步翻译整段歌词 */
    public static void translateAsync(String text, String targetLang, final Callback cb) {
        final String tl = targetLang;
        new AsyncTask<Void, Void, String[]>() {
            @Override protected String[] doInBackground(Void... v) {
                // 1) 试 MyMemory (免费, 无 key)
                try {
                    String r = myMemory(text, tl);
                    if (r != null && !r.startsWith("ERROR")) return new String[]{r, "mymemory"};
                } catch (Exception e) {}
                // 2) 失败 fallback 到原文
                return new String[]{text, "fallback"};
            }
            @Override protected void onPostExecute(String[] r) {
                if (r == null) { cb.onError("no result"); return; }
                if (r[1].equals("fallback")) { cb.onError("all providers failed, original returned"); return; }
                cb.onResult(r[0], Provider.MYMEMORY);
            }
        }.execute();
    }

    /** MyMemory API:
     *   GET https://api.mymemory.translated.net/get?q=TEXT&langpair=zh-CN|en
     *   返回 JSON: {responseData: {translatedText: "..."}} */
    static String myMemory(String text, String targetLang) throws Exception {
        if (text == null || text.isEmpty()) return null;
        String srcLang = detectLang(text);
        // 单次请求限 500 字符, 切分
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i += 450) {
            int end = Math.min(i + 450, text.length());
            String chunk = text.substring(i, end);
            String url = MYMEMORY + "?q=" + URLEncoder.encode(chunk, "UTF-8") +
                "&langpair=" + srcLang + "|" + targetLang;
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            r.close();
            String body = sb.toString();
            try {
                JSONObject json = new JSONObject(body);
                String translated = json.getJSONObject("responseData").getString("translatedText");
                if (out.length() > 0) out.append("\n");
                out.append(translated);
            } catch (Exception e) {
                return "ERROR: " + body.substring(0, Math.min(100, body.length()));
            }
        }
        return out.toString();
    }

    /** 简单语言检测 (CJK 字符比例) */
    static String detectLang(String text) {
        int cjk = 0, latin = 0;
        for (int i = 0; i < Math.min(text.length(), 200); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) cjk++;
            else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) latin++;
        }
        return cjk > latin ? "zh-CN" : "en";
    }
}
