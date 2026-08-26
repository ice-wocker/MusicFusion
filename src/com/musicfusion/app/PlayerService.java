package com.musicfusion.app;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;

/** 前景播放服务: 流媒体引擎 */
public class PlayerService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    static MediaPlayer mp;
    static String nowPlaying = "";

    @Override public void onCreate() {
        super.onCreate();
        if (mp == null) {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setOnPreparedListener(this);
            mp.setOnErrorListener(this);
        }
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String url = i != null ? i.getStringExtra("url") : null;
        String title = i != null ? i.getStringExtra("title") : "";
        if (url != null) play(url, title);
        else if (mp != null && i != null && "PAUSE".equals(i.getAction())) {
            if (mp.isPlaying()) mp.pause();
            else mp.start();
        }
        return START_STICKY;
    }

    void play(String url, String title) {
        try {
            nowPlaying = title;
            mp.reset();
            mp.setDataSource(url);
            mp.prepareAsync();   // 异步缓冲
            MainActivity.onPlayState(title, "缓冲中…");
        } catch (Exception e) {
            MainActivity.onPlayState(title, "播放失败: " + e.getMessage());
        }
    }

    @Override public void onPrepared(MediaPlayer p) {
        p.start();
        MainActivity.onPlayState(nowPlaying, "▶ 播放中");
    }
    @Override public boolean onError(MediaPlayer p, int what, int extra) {
        MainActivity.onPlayState(nowPlaying, "✗ 播放器错误(" + what + ")");
        return true;
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (mp != null) { mp.release(); mp = null; }
        super.onDestroy();
    }
}
