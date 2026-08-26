package com.musicfusion.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** 桌面小部件: 显示曲名+状态, 上一首/暂停/下一首 */
public class MusicWidget extends AppWidgetProvider {

    static void update(Context ctx, String title, String state) {
        try {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(),
                R.layout.music_widget);
            rv.setTextViewText(R.id.w_title, title);
            rv.setTextViewText(R.id.w_state, state);
            String[][] acts = {{"PREV"}, {"PAUSE"}, {"NEXT"}};
            int[] ids = {R.id.w_prev, R.id.w_pause, R.id.w_next};
            for (int i = 0; i < acts.length; i++) {
                Intent si = new Intent(ctx, PlayerService.class);
                si.setAction(acts[i][0]);
                PendingIntent pi = PendingIntent.getService(ctx, i,
                    si, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                rv.setOnClickPendingIntent(ids[i], pi);
            }
            AppWidgetManager.getInstance(ctx).updateAppWidget(
                new ComponentName(ctx, MusicWidget.class), rv);
        } catch (Exception ignored) {}
    }

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        update(ctx, "MusicFusion", "未在播放");
    }
}
