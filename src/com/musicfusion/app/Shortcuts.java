package com.musicfusion.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

/** Shortcuts — App Shortcuts (长按图标菜单)
 *  - 继续播放
 *  - 搜索音乐
 *  - 电台直播
 *  - 离线下载
 *  - 智能歌单
 *  - 需在 AndroidManifest.xml 的 MainActivity 中声明 <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/> */
public final class Shortcuts {
    private static final String ID_PLAY = "play";
    private static final String ID_SEARCH = "search";
    private static final String ID_RADIO = "radio";
    private static final String ID_DOWNLOAD = "download";
    private static final String ID_SMART = "smart";

    public static void setup(Context ctx) {
        if (Build.VERSION.SDK_INT < 25) return;
        ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
        if (sm == null) return;

        ShortcutInfo[] shortcuts = new ShortcutInfo[5];

        // 继续播放
        shortcuts[0] = new ShortcutInfo.Builder(ctx, ID_PLAY)
            .setShortLabel("继续播放")
            .setLongLabel("继续上次播放")
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_play))
            .setIntent(new Intent(ctx, MainActivity.class).setAction("SHORTCUT_PLAY").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .setRank(0)
            .build();

        // 搜索音乐
        shortcuts[1] = new ShortcutInfo.Builder(ctx, ID_SEARCH)
            .setShortLabel("搜索")
            .setLongLabel("搜索音乐")
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_search))
            .setIntent(new Intent(ctx, MainActivity.class).setAction("SHORTCUT_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .setRank(1)
            .build();

        // 电台直播
        shortcuts[2] = new ShortcutInfo.Builder(ctx, ID_RADIO)
            .setShortLabel("电台")
            .setLongLabel("打开电台直播")
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_radio))
            .setIntent(new Intent(ctx, MainActivity.class).setAction("SHORTCUT_RADIO").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .setRank(2)
            .build();

        // 离线下载
        shortcuts[3] = new ShortcutInfo.Builder(ctx, ID_DOWNLOAD)
            .setShortLabel("下载")
            .setLongLabel("管理离线下载")
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_download))
            .setIntent(new Intent(ctx, MainActivity.class).setAction("SHORTCUT_DOWNLOAD").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .setRank(3)
            .build();

        // 智能歌单
        shortcuts[4] = new ShortcutInfo.Builder(ctx, ID_SMART)
            .setShortLabel("智能歌单")
            .setLongLabel("生成智能歌单")
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_smart))
            .setIntent(new Intent(ctx, MainActivity.class).setAction("SHORTCUT_SMART").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .setRank(4)
            .build();

        sm.setDynamicShortcuts(java.util.Arrays.asList(shortcuts));
    }

    public static void updatePlayShortcut(Context ctx, String title, String artist) {
        if (Build.VERSION.SDK_INT < 25) return;
        ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
        if (sm == null) return;
        ShortcutInfo si = new ShortcutInfo.Builder(ctx, ID_PLAY)
            .setShortLabel("继续: " + title)
            .setLongLabel(title + " - " + artist)
            .setIcon(Icon.createWithResource(ctx, R.drawable.ic_play))
            .setIntent(new Intent(ctx, MainActivity.class).setAction("SHORTCUT_PLAY").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .setRank(0)
            .build();
        sm.updateShortcuts(java.util.Arrays.asList(si));
    }

    /** 处理 shortcut 启动 (在 MainActivity.onNewIntent 中调用) */
    public static void handleShortcut(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case "SHORTCUT_PLAY":
                // 发送播放广播
                ctx.startService(new Intent(ctx, PlayerService.class).setAction("PAUSE"));
                break;
            case "SHORTCUT_SEARCH":
                // 切到搜索标签并聚焦搜索框
                if (MainActivity.inst != null) {
                    MainActivity.inst.switchTab(1); // 搜索标签
                    MainActivity.inst.focusSearch();
                }
                break;
            case "SHORTCUT_RADIO":
                if (MainActivity.inst != null) MainActivity.inst.switchTab(2); // 电台标签
                break;
            case "SHORTCUT_DOWNLOAD":
                if (MainActivity.inst != null) MainActivity.inst.switchTab(5); // 下载标签 (新增)
                break;
            case "SHORTCUT_SMART":
                if (MainActivity.inst != null) MainActivity.inst.smartPlaylistDialog();
                break;
        }
    }
}