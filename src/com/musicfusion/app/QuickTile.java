package com.musicfusion.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** QuickTile — 系统快捷设置磁贴
 *  - 播放/暂停/上一曲/下一曲 循环切换
 *  - 显示当前曲目标题
 *  - 长按打开应用 */
public class QuickTile extends TileService {

    private static final String PREF_KEY = "quicktile_state";
    private static int tileState = 0; // 0=播放 1=暂停 2=下一曲 3=上一曲

    @Override public void onStartListening() {
        super.onStartListening();
        loadState();
        updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        Context ctx = getApplicationContext();
        switch (tileState) {
            case 0: send(ctx, "PAUSE"); tileState = 1; break;
            case 1: send(ctx, "PAUSE"); tileState = 0; break; // 切回播放
            case 2: send(ctx, "NEXT"); break;
            case 3: send(ctx, "PREV"); break;
        }
        saveState();
        updateTile();
    }

    @Override public void onTileAdded() {
        super.onTileAdded();
        tileState = 0;
        saveState();
        updateTile();
    }

    @Override public void onTileRemoved() {
        super.onTileRemoved();
        clearState();
    }

    private void send(Context ctx, String action) {
        Intent i = new Intent(ctx, PlayerService.class);
        i.setAction(action);
        ctx.startService(i);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        String title = sp.getString("widget_title", "MusicFusion");
        boolean playing = sp.getBoolean("widget_playing", false);

        // 循环状态: 播放中显示暂停, 暂停显示播放, 点击后切到下一曲/上一曲
        if (playing) {
            tile.setLabel("暂停");
            tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pause));
            tile.setState(Tile.STATE_ACTIVE);
            tileState = 1;
        } else {
            tile.setLabel("播放");
            tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_play));
            tile.setState(Tile.STATE_INACTIVE);
            tileState = 0;
        }
        tile.setSubtitle(title);
        tile.updateTile();
    }

    private void loadState() {
        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        tileState = sp.getInt(PREF_KEY, 0);
    }

    private void saveState() {
        getSharedPreferences("mf", MODE_PRIVATE).edit().putInt(PREF_KEY, tileState).apply();
    }

    private void clearState() {
        getSharedPreferences("mf", MODE_PRIVATE).edit().remove(PREF_KEY).apply();
    }

    /** 由 PlayerService 调用刷新磁贴 */
    public static void refreshFromService(Context ctx) {
        // TileService 无法直接从外部刷新, 需发广播或等待 onStartListening
        // 这里提供入口, 实际刷新在用户下拉通知栏时触发
    }
}