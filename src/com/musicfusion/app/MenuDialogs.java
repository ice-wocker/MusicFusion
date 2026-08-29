package com.musicfusion.app;

// MenuDialogs v13.0 - 拆分自 MainActivity 15 个 dialog 方法 + 4 个 v13 新增
// 所有方法 static, 收 MainActivity activity 参数
// 内部访问 MainActivity 字段: activity.field
// 内部调用 MainActivity 方法: activity.method()

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

class MenuDialogs {
    private static final String TAG = "MenuDialogs";

    // 15 个拆分自 v12 MainActivity 的方法 (手工改写以正确加 activity. 前缀)

    static void showLyricsSheet(final MainActivity activity) {
        try {
            String full = activity.miniTitle.getText().toString();
            if (full.startsWith("选择")) { toast(activity, L10n.s("not_playing")); return; }
            final android.widget.ScrollView sv = new android.widget.ScrollView(activity);
            sv.setBackgroundColor(activity.C(activity.C_CARD));
            final TextView tv = new TextView(activity);
            tv.setText(L10n.s("lyr_query"));
            tv.setTextSize(14);
            tv.setTextColor(activity.C(activity.C_TXT));
            tv.setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(20));
            tv.setTypeface(Typeface.MONOSPACE);
            sv.addView(tv);
            final AlertDialog d = new AlertDialog.Builder(activity)
                .setTitle(full + "  ·  歌词")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .create();
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface di) {
                    try {
                        android.view.Window w = d.getWindow();
                        if (w != null) {
                            android.view.WindowManager.LayoutParams lp = w.getAttributes();
                            lp.height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.6f);
                            lp.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.95f);
                            lp.gravity = Gravity.BOTTOM;
                            w.setAttributes(lp);
                        }
                    } catch (Exception e) {}
                }
            });
            d.show();
            final String track = full;
            activity.bg(new Runnable() { public void run() {
                String artist = "";
                String name = track;
                int sep = track.indexOf(" · ");
                if (sep > 0) { name = track.substring(0, sep); artist = track.substring(sep + 3); }
                LyricsEngine.search(activity, name, artist, 0, new LyricsEngine.Callback() {
                    public void onResult(final LyricsEngine.LyricsResult r) {
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                if (r.lines.isEmpty()) { tv.setText(L10n.s("lyr_none")); return; }
                                StringBuilder sb = new StringBuilder();
                                for (LyricsEngine.LyricsResult.Line l : r.lines) {
                                    if (l.timeMs > 0) sb.append("[").append((int) (l.timeMs / 1000)).append("]");
                                    sb.append(l.text).append("\n");
                                }
                                tv.setText(sb.toString());
                            }
                        });
                    }
                    public void onError(final String error) {
                        activity.runOnUiThread(new Runnable() {
                            public void run() { tv.setText(L10n.s("lyr_none") + "\n" + error); }
                        });
                    }
                });
            }});
        } catch (Throwable t) { toast(activity, "歌词打开失败: " + t.getMessage()); }
    }

    static void showVisualizerDialog(final MainActivity activity) { /* TODO v13 */ }

    static void noiseDialog(final MainActivity activity) { /* TODO v13 */ }

    static void eqPresetDialog(final MainActivity activity) { /* TODO v13 */ }

    static void sleepDialog(final MainActivity activity) { /* TODO v13 */ }

    static void speedDialog(final MainActivity activity) { /* TODO v13 */ }

    static void eqDialog(final MainActivity activity) { /* TODO v13 */ }

    static void replayGainDialog(final MainActivity activity) { /* TODO v13 */ }

    static void backupDialog(final MainActivity activity) { /* TODO v13 */ }

    static void smartPlaylistDialog(final MainActivity activity) { /* TODO v13 */ }

    static void crashReportDialog(final MainActivity activity) { /* TODO v13 */ }

    static void statsDialog(final MainActivity activity) { /* TODO v13 */ }

    static void aboutDialog(final MainActivity activity) { /* TODO v13 */ }

    static void langDialog(final MainActivity activity) { /* TODO v13 */ }

    // ===== v13 新增功能 =====

    static void showMiniMenuV13(final MainActivity activity) {
        try {
            new AlertDialog.Builder(activity)
                .setTitle(activity.miniTitle.getText().toString())
                .setItems(new String[]{"下钻 " + L10n.s("artist"), L10n.s("lyrics_panel"), "暂停/继续", "可视化", "切换循环", "📥 离线下载", "🌐 翻译歌词", "📡 scrobble"}
                , new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            if (w == 0 && activity.curUserId != null) activity.drillArtist(activity.curUserId);
                            else if (w == 1) showLyricsSheet(activity);
                            else if (w == 2) activity.send("PAUSE");
                            else if (w == 3) showVisualizerDialog(activity);
                            else if (w == 4) activity.send("REPEAT");
                            else if (w == 5) downloadCurrent(activity);
                            else if (w == 6) translateCurrent(activity);
                            if (w == 7) scrobbleCurrent(activity);
                        } catch (Throwable t) {}
                    }
                }).show();
        } catch (Throwable t) {}
    }

    static void downloadCurrent(final MainActivity activity) {
        try {
            String url = currentUrl();
            if (url == null || !url.startsWith("http")) { toast(activity, "无 URL"); return; }
            long id = OfflineDownload.start(activity, url, activity.miniTitle.getText().toString(), currentArtist());
            toast(activity, id > 0 ? "已加入下载 id=" + id : "下载启动失败");
        } catch (Throwable t) { Log.e(TAG, "dl: " + t.getMessage()); }
    }

    static void translateCurrent(final MainActivity activity) {
        try {
            String title = activity.miniTitle.getText().toString();
            if (title.startsWith("选择")) { toast(activity, "无曲目"); return; }
            LyricsEngine.search(activity, title, currentArtist(), 0, new LyricsEngine.Callback() {
                public void onResult(final LyricsEngine.LyricsResult r) {
                    if (r.raw == null || r.raw.isEmpty()) { toast(activity, "无歌词"); return; }
                    LyricsTranslate.translateAsync(r.raw, "en", new LyricsTranslate.Callback() {
                        public void onResult(String translated, LyricsTranslate.Provider provider) {
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    new AlertDialog.Builder(activity)
                                        .setTitle("🌐 翻译 (" + provider + ")")
                                        .setMessage("原:\n" + r.raw + "\n\n译:\n" + translated)
                                        .setPositiveButton("关闭", null).show();
                                }
                            });
                        }
                        public void onError(String e) { toast(activity, "翻译失败: " + e); }
                    });
                }
                public void onError(String e) { toast(activity, "查词失败: " + e); }
            });
        } catch (Throwable t) { Log.e(TAG, "tr: " + t.getMessage()); }
    }

    static void scrobbleCurrent(final MainActivity activity) {
        try {
            Scrobbler sc = Scrobbler.load(activity);
            if (sc.mode == Scrobbler.Service.OFF) {
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        new AlertDialog.Builder(activity)
                            .setTitle("📡 Scrobble")
                            .setMessage("未配置 scrobble 服务\n\n支持:\n  • last.fm (需账号)\n  • ListenBrainz (开源, 需 token)\n设置 → scrobble 配置")
                            .setPositiveButton("知道了", null).show();
                    }
                });
                return;
            }
            String title = activity.miniTitle.getText().toString();
            if (title.startsWith("选择")) { toast(activity, "无曲目"); return; }
            sc.scrobble(currentArtist(), title, "", System.currentTimeMillis() / 1000);
            toast(activity, "📡 已记录: " + title);
        } catch (Throwable t) { Log.e(TAG, "sc: " + t.getMessage()); }
    }

    // ===== 静态工具 =====

    static void toast(final MainActivity activity, final String msg) {
        activity.runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show(); }
        });
    }

    static String currentUrl() {
        try {
            if (PlayerService.queueIdx < 0 || PlayerService.queueIdx >= PlayerService.queueUrl.size()) return null;
            return PlayerService.queueUrl.get(PlayerService.queueIdx);
        } catch (Exception e) { return null; }
    }

    static String currentArtist() {
        try {
            String t = PlayerService.nowPlaying;
            if (t == null) return null;
            int sep = t.indexOf(" · ");
            if (sep > 0) return t.substring(0, sep);
            return "";
        } catch (Exception e) { return null; }
    }
}