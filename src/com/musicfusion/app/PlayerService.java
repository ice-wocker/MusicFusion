package com.musicfusion.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;

import java.util.ArrayList;

/** PlayerService v3 — 播放队列 + 自动连播 + 音频焦点 + 媒体通知 */
public class PlayerService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener {

    static MediaPlayer mp;
    static String nowPlaying = "";
    static ArrayList<String> queueUrl = new ArrayList<String>();
    static ArrayList<String> queueTitle = new ArrayList<String>();
    static int queueIdx = 0;
    static boolean shuffle = false;

    @Override public void onCreate() {
        super.onCreate();
        if (mp == null) {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setOnPreparedListener(this);
            mp.setOnErrorListener(this);
            mp.setOnCompletionListener(this);
        }
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) return START_STICKY;
        String act = i.getAction();
        if ("PAUSE".equals(act)) { toggle(); return START_STICKY; }
        if ("NEXT".equals(act)) { next(); return START_STICKY; }
        if ("PREV".equals(act)) { prev(); return START_STICKY; }
        if ("SHUFFLE".equals(act)) { shuffle = !shuffle; notifyState(shuffle ? "🔀 随机开" : "▶ 顺序播"); return START_STICKY; }

        String[] urls = i.getStringArrayExtra("urls");
        String[] titles = i.getStringArrayExtra("titles");
        int idx = i.getIntExtra("index", 0);
        if (urls != null && titles != null && idx < urls.length) {
            queueUrl.clear(); queueTitle.clear();
            for (String u : urls) queueUrl.add(u);
            for (String t : titles) queueTitle.add(t);
            queueIdx = idx;
            requestFocus();
            playCurrent();
        }
        return START_STICKY;
    }

    void playCurrent() {
        if (queueIdx < 0 || queueIdx >= queueUrl.size()) return;
        String url = queueUrl.get(queueIdx);
        nowPlaying = queueTitle.get(queueIdx);
        if (url == null || url.isEmpty()) {
            MainActivity.onPlayState(nowPlaying, "✗ 无播放地址");
            next();
            return;
        }
        try {
            mp.reset();
            mp.setDataSource(url);
            mp.prepareAsync();
            MainActivity.onPlayState(nowPlaying, "⏳ 缓冲");
            showNotif(nowPlaying, "缓冲中…");
        } catch (Exception e) {
            MainActivity.onPlayState(nowPlaying, "✗ " + e.getMessage());
        }
    }

    void next() {
        if (queueUrl.isEmpty()) return;
        if (shuffle) queueIdx = (int) (Math.random() * queueUrl.size());
        else queueIdx = (queueIdx + 1) % queueUrl.size();
        playCurrent();
    }
    void prev() {
        if (queueUrl.isEmpty()) return;
        queueIdx = (queueIdx - 1 + queueUrl.size()) % queueUrl.size();
        playCurrent();
    }

    void toggle() {
        if (mp == null) return;
        try {
            if (mp.isPlaying()) { mp.pause();
                MainActivity.onPlayState(nowPlaying, "⏸ 已暂停"); }
            else { mp.start();
                MainActivity.onPlayState(nowPlaying, "▶ 播放中"); }
        } catch (Exception ignored) {}
    }

    void requestFocus() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) am.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignored) {}
    }

    @Override public void onAudioFocusChange(int f) { if (f <= 0) toggle(); }

    @Override public void onCompletion(MediaPlayer p) {
        MainActivity.onPlayState(nowPlaying, "⏭ 下一首");
        next();   // 自动连播
    }

    @Override public void onPrepared(MediaPlayer p) {
        p.start();
        MainActivity.onPlayState(nowPlaying, "▶ 播放中");
        showNotif(nowPlaying, "▶ 播放中 " + (queueIdx + 1) + "/" + queueUrl.size());
    }
    @Override public boolean onError(MediaPlayer p, int what, int extra) {
        MainActivity.onPlayState(nowPlaying, "✗ 错误(" + what + "), 跳下一首");
        next();
        return true;
    }

    void notifyState(String s) { MainActivity.onPlayState(nowPlaying, s); }

    void showNotif(String title, String state) {
        try {
            NotificationManagerHolder nm = new NotificationManagerHolder(this);
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Notification.Builder nb = new Notification.Builder(this)
                .setContentTitle("🎵 " + title)
                .setContentText(state + (shuffle ? " 🔀" : ""))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).setContentIntent(pi);
            if (Build.VERSION.SDK_INT >= 26) {
                if (nm.nm.getNotificationChannel("play") == null) {
                    NotificationChannel ch = new NotificationChannel("play", "播放",
                        NotificationManager.IMPORTANCE_LOW);
                    ch.setSound(null, null);
                    nm.nm.createNotificationChannel(ch);
                }
                nb.setChannelId("play");
                startForeground(2001, nb.build());
            } else nm.nm.notify(2001, nb.build());
        } catch (Exception ignored) {}
    }

    static class NotificationManagerHolder {
        android.app.NotificationManager nm;
        NotificationManagerHolder(Service s) {
            nm = (android.app.NotificationManager) s.getSystemService(NOTIFICATION_SERVICE);
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (mp != null) { mp.release(); mp = null; }
        super.onDestroy();
    }
}
