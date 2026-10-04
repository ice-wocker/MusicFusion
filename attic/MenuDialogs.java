package com.musicfusion.app;

// MenuDialogs v13.0 - 所有 dialog/菜单方法 (从 MainActivity 提取)
// 每个方法 static, 第一个参数是 MainActivity (用作 Context + 访问字段)
// 字段引用 this.field -> activity.field

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
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

class MenuDialogs {
    private static final String TAG = "MenuDialogs";

    // 全部方法 static, 参数 activity 替代 this

    static void showLyricsSheet(final MainActivity activity) { MenuDialogs.showLyricsSheet(activity); }
    void showVisualizerDialog() { MenuDialogs.showVisualizerDialog(activity); }
    void noiseDialog() { MenuDialogs.noiseDialog(activity); }
    void eqPresetDialog() { MenuDialogs.eqPresetDialog(activity); }
    void sleepDialog() { MenuDialogs.sleepDialog(activity); }
    void speedDialog() { MenuDialogs.speedDialog(activity); }
    void eqDialog() { MenuDialogs.eqDialog(activity); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void showVisualizerDialog(final MainActivity activity) { MenuDialogs.showVisualizerDialog(activity); }
    void noiseDialog() { MenuDialogs.noiseDialog(activity); }
    void eqPresetDialog() { MenuDialogs.eqPresetDialog(activity); }
    void sleepDialog() { MenuDialogs.sleepDialog(activity); }
    void speedDialog() { MenuDialogs.speedDialog(activity); }
    void eqDialog() { MenuDialogs.eqDialog(activity); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void noiseDialog(final MainActivity activity) { MenuDialogs.noiseDialog(activity); }
    void eqPresetDialog() { MenuDialogs.eqPresetDialog(activity); }
    void sleepDialog() { MenuDialogs.sleepDialog(activity); }
    void speedDialog() { MenuDialogs.speedDialog(activity); }
    void eqDialog() { MenuDialogs.eqDialog(activity); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void eqPresetDialog(final MainActivity activity) { MenuDialogs.eqPresetDialog(activity); }
    void sleepDialog() { MenuDialogs.sleepDialog(activity); }
    void speedDialog() { MenuDialogs.speedDialog(activity); }
    void eqDialog() { MenuDialogs.eqDialog(activity); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void sleepDialog(final MainActivity activity) { MenuDialogs.sleepDialog(activity); }
    void speedDialog() { MenuDialogs.speedDialog(activity); }
    void eqDialog() { MenuDialogs.eqDialog(activity); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void speedDialog(final MainActivity activity) { MenuDialogs.speedDialog(activity); }
    void eqDialog() { MenuDialogs.eqDialog(activity); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void eqDialog(final MainActivity activity) { MenuDialogs.eqDialog(activity); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void replayGainDialog(final MainActivity activity) { MenuDialogs.replayGainDialog(activity); }
    void backupDialog() { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void backupDialog(final MainActivity activity) { MenuDialogs.backupDialog(activity); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void smartPlaylistDialog(final MainActivity activity) { MenuDialogs.smartPlaylistDialog(activity); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void crashReportDialog(final MainActivity activity) { MenuDialogs.crashReportDialog(activity); }
    void statsDialog() { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void statsDialog(final MainActivity activity) { MenuDialogs.statsDialog(activity); }
    void aboutDialog() { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void aboutDialog(final MainActivity activity) { MenuDialogs.aboutDialog(activity); }
    void langDialog() { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void langDialog(final MainActivity activity) { MenuDialogs.langDialog(activity); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(activity); }

}

    static void showMiniMenu(final MainActivity activity) { MenuDialogs.showMiniMenuV13(activity); }

}

}

// ===== v13 新增功能 =====

static void showMiniMenuV13(final MainActivity activity) {
    try {
        new AlertDialog.Builder(activity)
            .setTitle(activity.miniTitle.getText().toString())
            .setItems(new String[]{
                L10n.s("artist") + " (下钻)",
                L10n.s("lyrics_panel"),
                "暂停/继续",
                "可视化",
                "切换循环",
                "📥 离线下载",
                "🌐 翻译歌词",
                "📡 scrobble"
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        if (w == 0 && activity.curUserId != null) activity.drillArtist(activity.curUserId);
                        else if (w == 1) showLyricsSheet(activity);
                        else if (w == 2) activity.send("PAUSE");
                        else if (w == 3) showVisualizerDialog(activity);
                        else if (w == 4) activity.send("REPEAT");
                        else if (w == 5) downloadCurrent(activity);
                        else if (w == 6) translateCurrent(activity);
                        else if (w == 7) scrobbleCurrent(activity);
                    } catch (Throwable t) {}
                }
            }).show();
    } catch (Throwable t) {}
}

static void downloadCurrent(final MainActivity activity) {
    try {
        String url = currentUrl();
        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            toast(activity, "当前曲目无 URL");
            return;
        }
        long id = OfflineDownload.start(activity, url,
            activity.miniTitle.getText().toString(), currentArtist());
        toast(activity, id > 0 ? ("已加入下载 (id=" + id + ")") : "下载启动失败");
    } catch (Throwable t) { Log.e("MenuDialogs", "download: " + t.getMessage()); }
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
    } catch (Throwable t) { Log.e("MenuDialogs", "translate: " + t.getMessage()); }
}

static void scrobbleCurrent(final MainActivity activity) {
    try {
        Scrobbler sc = Scrobbler.load(activity);
        if (sc.mode == Scrobbler.Service.OFF) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    new AlertDialog.Builder(activity)
                        .setTitle("📡 Scrobble")
                        .setMessage("未配置 scrobble 服务\n\n" +
                            "支持:\n" +
                            "  • last.fm (需账号)\n" +
                            "  • ListenBrainz (开源, 需 token)\n" +
                            "设置 → scrobble 配置")
                        .setPositiveButton("知道了", null).show();
                }
            });
            return;
        }
        String title = activity.miniTitle.getText().toString();
        if (title.startsWith("选择")) { toast(activity, "无曲目"); return; }
        sc.scrobble(currentArtist(), title, "", System.currentTimeMillis() / 1000);
        toast(activity, "📡 已记录: " + title);
    } catch (Throwable t) { Log.e("MenuDialogs", "scrobble: " + t.getMessage()); }
}

// ===== 静态工具 =====
static void toast(final MainActivity activity, final String msg) {
    activity.runOnUiThread(new Runnable() {
        public void run() {
            android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show();
        }
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
