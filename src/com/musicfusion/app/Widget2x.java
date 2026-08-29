package com.musicfusion.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

/** Widget2x — 2x1/4x1/4x2 小组件
 *  - 专辑图 + 进度条 + 歌词行 + 播放控制
 *  - 三种尺寸: 2x1(紧凑) / 4x1(标准) / 4x2(大卡)
 *  - 点击专辑图打开应用, 点击控制按钮发送广播 */
public class Widget2x extends AppWidgetProvider {

    private static final String ACTION_PLAY = "com.musicfusion.app.WIDGET_PLAY";
    private static final String ACTION_PAUSE = "com.musicfusion.app.WIDGET_PAUSE";
    private static final String ACTION_NEXT = "com.musicfusion.app.WIDGET_NEXT";
    private static final String ACTION_PREV = "com.musicfusion.app.WIDGET_PREV";
    private static final String ACTION_OPEN = "com.musicfusion.app.WIDGET_OPEN";

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case ACTION_PLAY:
            case ACTION_PAUSE:
                sendToService(ctx, "PAUSE");
                break;
            case ACTION_NEXT:
                sendToService(ctx, "NEXT");
                break;
            case ACTION_PREV:
                sendToService(ctx, "PREV");
                break;
            case ACTION_OPEN:
                Intent open = new Intent(ctx, MainActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(open);
                break;
        }
        // 刷新所有组件
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, Widget2x.class));
        for (int id : ids) updateWidget(ctx, mgr, id);
    }

    private static void sendToService(Context ctx, String action) {
        Intent i = new Intent(ctx, PlayerService.class);
        i.setAction(action);
        ctx.startService(i);
    }

    private static void updateWidget(Context ctx, AppWidgetManager mgr, int id) {
        RemoteViews rv = buildView(ctx, id);
        mgr.updateAppWidget(id, rv);
    }

    private static RemoteViews buildView(Context ctx, int widgetId) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        String title = sp.getString("widget_title", "MusicFusion");
        String artist = sp.getString("widget_artist", "未播放");
        int progress = sp.getInt("widget_progress", 0);
        int duration = sp.getInt("widget_duration", 0);
        boolean playing = sp.getBoolean("widget_playing", false);
        String coverPath = sp.getString("widget_cover", "");
        String lyricLine = sp.getString("widget_lyric", "");

        // 根据尺寸选择布局 (通过 widgetId 无法直接知道尺寸, 这里用 3 种布局轮询, 实际应用中需在配置 Activity 选)
        // 简化: 根据宽度判断 (AppWidgetManager.getAppWidgetOptions)
        // 这里提供三个布局, 系统会按尺寸选最合适的
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_4x1); // 默认标准
        // 实际会在 onUpdate 中根据尺寸选布局, 这里仅演示绑定数据
        bindData(rv, ctx, title, artist, progress, duration, playing, coverPath, lyricLine);
        return rv;
    }

    private static RemoteViews[] buildViews(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews[] views = new RemoteViews[ids.length];
        for (int i = 0; i < ids.length; i++) {
            // 获取组件尺寸
            android.os.Bundle opts = mgr.getAppWidgetOptions(ids[i]);
            int minWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int minHeight = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);

            int layoutRes;
            // 粗略判断: 2x1 ≈ 180x110, 4x1 ≈ 360x110, 4x2 ≈ 360x220
            if (minWidth < 300) layoutRes = R.layout.widget_2x1;
            else if (minHeight < 150) layoutRes = R.layout.widget_4x1;
            else layoutRes = R.layout.widget_4x2;

            RemoteViews rv = new RemoteViews(ctx.getPackageName(), layoutRes);
            bindData(rv, ctx,
                ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).getString("widget_title", "MusicFusion"),
                ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).getString("widget_artist", "未播放"),
                ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).getInt("widget_progress", 0),
                ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).getInt("widget_duration", 0),
                ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).getBoolean("widget_playing", false),
                ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).getString("widget_cover", ""),
                ctx.getSharedPreferences("mf", Context.MODE_PRIVATE).getString("widget_lyric", "")
            );
            views[i] = rv;
        }
        return views;
    }

    private static void bindData(RemoteViews rv, Context ctx, String title, String artist,
                                 int progress, int duration, boolean playing,
                                 String coverPath, String lyricLine) {
        rv.setTextViewText(R.id.widget_title, title);
        rv.setTextViewText(R.id.widget_artist, artist);
        rv.setTextViewText(R.id.widget_lyric, TextUtils.isEmpty(lyricLine) ? "" : lyricLine);

        // 进度条
        if (duration > 0) {
            rv.setProgressBar(R.id.widget_progress, 1000, progress * 1000 / duration, false);
            rv.setViewVisibility(R.id.widget_progress, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.widget_progress, View.GONE);
        }

        // 播放/暂停按钮
        int playIcon = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        rv.setImageViewResource(R.id.widget_play, playIcon);

        // 点击事件
        rv.setOnClickPendingIntent(R.id.widget_play, PendingIntent.getBroadcast(ctx, 0,
            new Intent(ctx, Widget2x.class).setAction(playing ? ACTION_PAUSE : ACTION_PLAY),
            Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        rv.setOnClickPendingIntent(R.id.widget_next, PendingIntent.getBroadcast(ctx, 1,
            new Intent(ctx, Widget2x.class).setAction(ACTION_NEXT),
            Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        rv.setOnClickPendingIntent(R.id.widget_prev, PendingIntent.getBroadcast(ctx, 2,
            new Intent(ctx, Widget2x.class).setAction(ACTION_PREV),
            Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(ctx, 3,
            new Intent(ctx, Widget2x.class).setAction(ACTION_OPEN),
            Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        // 专辑图
        if (!TextUtils.isEmpty(coverPath)) {
            try {
                Bitmap bmp;
                if (coverPath.startsWith("content://") || coverPath.startsWith("file://")) {
                    Uri uri = Uri.parse(coverPath);
                    bmp = BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(uri));
                } else {
                    bmp = BitmapFactory.decodeFile(coverPath);
                }
                if (bmp != null) rv.setImageViewBitmap(R.id.widget_cover, bmp);
                else rv.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher);
            } catch (Exception e) {
                rv.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher);
            }
        } else {
            rv.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher);
        }
    }

    /** 由 PlayerService 调用更新组件数据 */
    public static void updateFromService(Context ctx, String title, String artist,
                                         int progress, int duration, boolean playing,
                                         String coverPath, String lyricLine) {
        SharedPreferences sp = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE);
        sp.edit()
            .putString("widget_title", title)
            .putString("widget_artist", artist)
            .putInt("widget_progress", progress)
            .putInt("widget_duration", duration)
            .putBoolean("widget_playing", playing)
            .putString("widget_cover", coverPath)
            .putString("widget_lyric", lyricLine)
            .apply();

        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName cn = new ComponentName(ctx, Widget2x.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids.length > 0) {
            RemoteViews[] views = buildViews(ctx, mgr, ids);
            for (int i = 0; i < ids.length; i++) mgr.updateAppWidget(ids[i], views[i]);
        }
    }

    static { try { android.text.TextUtils.class.getName(); } catch (Exception ignored) {} }
}