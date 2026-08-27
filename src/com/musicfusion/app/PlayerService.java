package com.musicfusion.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import java.util.ArrayList;

/** PlayerService v4 — 队列连播/循环模式/倍速/均衡器/睡眠定时/进度 */
public class PlayerService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener {

    static MediaPlayer mp;
    static Equalizer eq;
    static String nowPlaying = "";
    static final ArrayList<String> queueUrl = new ArrayList<String>();
    static final ArrayList<String> queueTitle = new ArrayList<String>();
    static int queueIdx = 0;
    static int repeatMode = 0;          // 0=列表循环 1=单曲循环
    static boolean shuffle = false;
    static float speed = 1.0f;
    static long sleepAt = 0;            // 睡眠定时戳, 0=off
    static final Handler handler = new Handler();

    android.content.BroadcastReceiver noisy;
    android.media.session.MediaSession session;

    @Override public void onCreate() {
        super.onCreate();
        L10n.load(this);
        // 耳机拔出/蓝牙断开 → 自动暂停
        noisy = new android.content.BroadcastReceiver() {
            public void onReceive(Context c, Intent i) { toggle(); }
        };
        try {
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(noisy, new IntentFilter(
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    4 /* Context.RECEIVER_NOT_EXPORTED */);
            else registerReceiver(noisy, new IntentFilter(
                AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        } catch (Exception ignored) {}
        // 媒体按钮(耳机线控/蓝牙)
        try {
            session = new android.media.session.MediaSession(this, "MusicFusion");
            session.setCallback(new android.media.session.MediaSession.Callback() {
                public boolean onMediaButtonEvent(Intent i) {
                    String a = i.getAction();
                    if (Intent.ACTION_MEDIA_BUTTON.equals(a)) {
                        int kc = (android.view.KeyEvent) i.getExtras()
                            .get(Intent.EXTRA_KEY_EVENT) != null
                            ? ((android.view.KeyEvent) i.getExtras()
                                .get(Intent.EXTRA_KEY_EVENT)).getKeyCode() : 0;
                        if (kc == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) toggle();
                        else if (kc == android.view.KeyEvent.KEYCODE_MEDIA_NEXT) next();
                        else if (kc == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) prev();
                        return true;
                    }
                    return super.onMediaButtonEvent(i);
                }
            });
            session.setActive(true);
        } catch (Exception ignored) {}
        if (mp == null) {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setOnPreparedListener(this);
            mp.setOnErrorListener(this);
            mp.setOnCompletionListener(this);
        }
        handler.postDelayed(tick, 500);
    }

    // 500ms心跳: 睡眠定时检查 + 进度回传
    final Runnable tick = new Runnable() { public void run() {
        try {
            if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                sleepAt = 0;
                if (mp != null) mp.pause();
                MainActivity.onPlayState(nowPlaying, "🌙 睡眠定时已暂停");
            }
            if (mp != null && mp.isPlaying()) {
                MainActivity.onProgress(mp.getCurrentPosition(), mp.getDuration());
            }
        } catch (Exception ignored) {}
        handler.postDelayed(this, 500);
    }};

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) return START_STICKY;
        String act = i.getAction();
        if ("PAUSE".equals(act)) { toggle(); return START_STICKY; }
        if ("NEXT".equals(act)) { next(); return START_STICKY; }
        if ("PREV".equals(act)) { prev(); return START_STICKY; }
        if ("SHUFFLE".equals(act)) { shuffle = !shuffle;
            MainActivity.onPlayState(nowPlaying, shuffle ? L10n.s("shuffle_on") : L10n.s("order")); return START_STICKY; }
        if ("REPEAT".equals(act)) { repeatMode = (repeatMode + 1) % 2;
            MainActivity.onPlayState(nowPlaying, repeatMode == 1 ? L10n.s("repeat_one") : L10n.s("repeat")); return START_STICKY; }
        if ("SEEK".equals(act)) {
            try { if (mp != null && mp.getDuration() > 0)
                seek((int) (mp.getDuration() * i.getFloatExtra("frac", 0f)));
            } catch (Exception ignored) {}
            return START_STICKY;
        }
        if ("SPEED".equals(act)) { speed = i.getFloatExtra("v", 1f);
            applySpeed(); return START_STICKY; }
        if ("EQ_PRESET".equals(act)) { // v11
            int idx = i.getIntExtra("idx", -1);
            EqPresets.apply(new EqPresets.Real(), idx);
            MainActivity.onPlayState(nowPlaying, "EQ: " + EqPresets.name(idx));
            return START_STICKY; }
        if ("SLEEP".equals(act)) { long min = i.getLongExtra("min", 0);
            sleepAt = min == 0 ? 0 : System.currentTimeMillis() + min * 60000;
            MainActivity.onPlayState(nowPlaying, min == 0 ? "睡眠定时取消" : "睡眠定时 " + min + "分钟");
            return START_STICKY; }
        if ("CLEAR".equals(act)) { // v11: 清空队列
            queueUrl.clear(); queueTitle.clear(); queueIdx = 0;
            stopIcyTicker();
            return START_STICKY; }

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

    void applySpeed() {
        try {
            if (mp != null && Build.VERSION.SDK_INT >= 23)
                mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed));
            MainActivity.onPlayState(nowPlaying, "倍速 " + speed + "x");
        } catch (Exception ignored) {}
    }

    void seek(int ms) {
        try { if (mp != null) mp.seekTo(ms); } catch (Exception ignored) {}
    }

    /** 均衡器(供设置面板调用): 返回波段数与当前值, -1=不支持 */
    public static short[] eqBands() {
        try {
            if (mp == null) return null;
            if (eq == null) eq = new Equalizer(0, mp.getAudioSessionId());
            return eq.getBandLevelRange();   // [min, max]
        } catch (Exception e) { return null; }
    }
    public static short bandCount() {
        try { return eq == null ? 0 : eq.getNumberOfBands(); }
        catch (Exception e) { return 0; }
    }
    public static short bandLevel(short b) {
        try { return eq.getBandLevel(b); } catch (Exception e) { return 0; }
    }
    public static String bandFreq(short b) {
        try { return eq.getCenterFreq(b) / 1000 + "Hz"; } catch (Exception e) { return "?"; }
    }
    public static void setBand(short b, short level) {
        try { if (eq != null) eq.setBandLevel(b, level); } catch (Exception ignored) {}
    }
    public static void eqReset() {
        try { if (eq != null) eq.setEnabled(true); } catch (Exception ignored) {}
    }

    void playCurrent() {
        if (queueIdx < 0 || queueIdx >= queueUrl.size()) return;
        String url = queueUrl.get(queueIdx);
        nowPlaying = queueTitle.get(queueIdx);
        if (url == null || url.isEmpty()) { next(); return; }
        try {
            mp.reset();
            // v11: 本地文件路径直接传
            if (url.startsWith("/")) {
                java.io.FileInputStream fis = new java.io.FileInputStream(url);
                mp.setDataSource(fis.getFD());
                fis.close();
            } else {
                mp.setDataSource(url);
            }
            mp.prepareAsync();
            MainActivity.onPlayState(nowPlaying, L10n.s("buffering"));
            MainActivity.onProgress(0, 0);
            showNotif(nowPlaying, "缓冲中");
            // v11: 直播流/电台拉取ICY元数据
            if (url.startsWith("http")) startIcyTicker(url);
            else stopIcyTicker();
        } catch (Exception e) {
            MainActivity.onPlayState(nowPlaying, "✗ " + e.getMessage());
        }
    }

    // ── v11: ICY直播流元数据 ticker ──
    java.util.Timer icyTimer;
    void startIcyTicker(final String url) {
        stopIcyTicker();
        icyTimer = new java.util.Timer();
        icyTimer.schedule(new java.util.TimerTask() {
            public void run() {
                try {
                    final String[] m = IcyMetadata.fetch(url);
                    final String cur = m[2];
                    if (cur != null && !cur.isEmpty()) {
                        MainActivity.onStreamMeta(cur);
                    } else if (m[0] != null && !m[0].isEmpty()) {
                        MainActivity.onStreamMeta(m[0] + " · " + m[1]);
                    } else {
                        MainActivity.onStreamMeta(null);
                    }
                } catch (Exception ignored) {}
            }
        }, 1500, 15000); // 首次1.5s, 之后每15s
    }
    void stopIcyTicker() {
        if (icyTimer != null) { icyTimer.cancel(); icyTimer = null; }
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
                MainActivity.onPlayState(nowPlaying, L10n.s("paused")); }
            else { mp.start();
                MainActivity.onPlayState(nowPlaying, L10n.s("playing")); }
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
        if (repeatMode == 1) {
            try { p.seekTo(0); p.start();
                MainActivity.onPlayState(nowPlaying, L10n.s("repeat_one")); return; }
            catch (Exception ignored) {}
        }
        next();
    }

    @Override public void onPrepared(MediaPlayer p) {
        p.start();
        if (speed != 1.0f) applySpeed();
        MainActivity.onPlayState(nowPlaying, L10n.s("playing"));
        showNotif(nowPlaying, "播放中 " + (queueIdx + 1) + "/" + queueUrl.size());
    }
    @Override public boolean onError(MediaPlayer p, int what, int extra) {
        MainActivity.onPlayState(nowPlaying, L10n.s("error"));
        next();
        return true;
    }

    void showNotif(String title, String state) {
        MusicWidget.update(this, title, state);
        try {
            NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Notification.Builder nb = new Notification.Builder(this)
                .setContentTitle("MusicFusion")
                .setContentText(title + " · " + state + (shuffle ? " 随机" : "")
                    + (repeatMode == 1 ? " 单曲循环" : ""))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).setContentIntent(pi);
            if (Build.VERSION.SDK_INT >= 26) {
                if (nm.getNotificationChannel("play") == null) {
                    NotificationChannel ch = new NotificationChannel("play", "播放",
                        NotificationManager.IMPORTANCE_LOW);
                    ch.setSound(null, null);
                    nm.createNotificationChannel(ch);
                }
                nb.setChannelId("play");
                startForeground(2001, nb.build());
            } else nm.notify(2001, nb.build());
        } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        try { unregisterReceiver(noisy); } catch (Exception ignored) {}
        try { if (session != null) { session.release(); session = null; } } catch (Exception ignored) {}
        handler.removeCallbacks(tick);
        stopIcyTicker();
        try { if (eq != null) eq.release(); } catch (Exception ignored) {}
        if (mp != null) { mp.release(); mp = null; }
        super.onDestroy();
    }
}
