package com.musicfusion.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;

/** PlayerService v2 — 流媒体引擎 + 音频焦点 + 媒体通知 */
public class PlayerService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

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
        if (i == null) return START_STICKY;
        String url = i.getStringExtra("url");
        String title = i.getStringExtra("title");
        if ("PAUSE".equals(i.getAction())) {
            toggle();
        } else if (url != null) {
            requestFocus();
            play(url, title);
        }
        return START_STICKY;
    }

    void toggle() {
        if (mp == null) return;
        try {
            if (mp.isPlaying()) {
                mp.pause();
                MainActivity.onPlayState(nowPlaying, "⏸ 已暂停");
            } else {
                mp.start();
                MainActivity.onPlayState(nowPlaying, "▶ 播放中");
            }
        } catch (Exception ignored) {}
    }

    void requestFocus() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null)
                am.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignored) {}
    }

    @Override public void onAudioFocusChange(int f) {
        if (f <= 0) toggle();   // 失焦自动暂停
    }

    void play(String url, String title) {
        try {
            nowPlaying = title;
            mp.reset();
            mp.setDataSource(url);
            mp.prepareAsync();
            MainActivity.onPlayState(title, "⏳ 缓冲");
            showNotif(title, "缓冲中…");
        } catch (Exception e) {
            MainActivity.onPlayState(title, "✗ 播放失败: " + e.getMessage());
        }
    }

    @Override public void onPrepared(MediaPlayer p) {
        p.start();
        MainActivity.onPlayState(nowPlaying, "▶ 播放中");
        showNotif(nowPlaying, "▶ 播放中");
    }
    @Override public boolean onError(MediaPlayer p, int what, int extra) {
        MainActivity.onPlayState(nowPlaying, "✗ 播放器错误(" + what + ")");
        return true;
    }

    void showNotif(String title, String state) {
        try {
            NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null
                    && nm.getNotificationChannel("play") == null) {
                NotificationChannel ch = new NotificationChannel("play", "播放",
                    NotificationManager.IMPORTANCE_LOW);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Notification.Builder nb = new Notification.Builder(this)
                .setContentTitle("🎵 " + title)
                .setContentText(state)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pi);
            if (Build.VERSION.SDK_INT >= 26) nb.setChannelId("play");
            if (Build.VERSION.SDK_INT >= 26)
                startForeground(2001, nb.build());
            else
                nm.notify(2001, nb.build());
        } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (mp != null) { mp.release(); mp = null; }
        super.onDestroy();
    }
}
