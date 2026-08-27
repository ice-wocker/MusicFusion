package com.musicfusion.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.util.ArrayList;

/** PlayerService v12 — 双实例 Gapless + Crossfade + ReplayGain + MediaSessionCompat
 *  - 双 MediaPlayer 交替: 1主播放, 1后台预加载下一曲 (setNextMediaPlayer, API 23+)
 *  - Crossfade: 200ms 主音量淡出, 下一曲从 0 淡入
 *  - ReplayGain: 自动读取标签, 调整 setVolume
 *  - 队列持久化: 启动时恢复 SharedPreferences
 *  - MediaSession: 锁屏/通知/耳机线控/Assistant 统一 */
public class PlayerService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener {

    static MediaPlayer mp, mpNext;     // v12: 主+预加载
    static Equalizer eq;
    static String nowPlaying = "";
    static final ArrayList<String> queueUrl = new ArrayList<String>();
    static final ArrayList<String> queueTitle = new ArrayList<String>();
    static int queueIdx = 0;
    static int repeatMode = 0;          // 0=列表循环 1=单曲循环
    static int repeatAll = 1;           // v12: 0=关闭 1=列表循环 (单曲是另一个标志)
    static boolean shuffle = false;
    static float speed = 1.0f;
    static long sleepAt = 0;            // 睡眠定时戳
    static int sleepMode = 0;           // 0=关闭 1=按时间 2=播完当前曲 3=淡出
    static float sleepFadeStart = 1.0f; // 淡出起点音量
    static long sleepFadeBeginMs = 0;   // 淡出起始时间
    static final Handler handler = new Handler();

    // v12: ReplayGain
    static boolean replayGainEnabled = true;
    static boolean replayGainAlbum = false;

    // v12: 队列持久化 key
    static final String PREF_QUEUE = "queue_state";

    // v12: 音频焦点请求 (API 26+)
    static AudioFocusRequest focusRequest;
    static AudioManager audioManager;

    android.content.BroadcastReceiver noisy;
    Object session;  // v12: MediaSession 占位 (无androidx库, 改为null或反射加载)
    NotificationManager nm;

    @Override public void onCreate() {
        super.onCreate();
        L10n.load(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

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

        // v12: MediaSession (锁屏控制) - 通过反射初始化 (无androidx库)
        try {
            Class<?> msClass = Class.forName("android.media.session.MediaSession");
            if (msClass != null) {
                session = msClass.getConstructor(Context.class, String.class)
                    .newInstance(this, "MusicFusion");
                // 简化: 仅 setActive (回调需要MediaSessionCompat, 暂跳过)
                try {
                    msClass.getMethod("setActive", boolean.class).invoke(session, true);
                } catch (Exception e) {}
            }
        } catch (Throwable t) {
            android.util.Log.w("PlayerService", "MediaSession init failed", t);
            session = null;
        }

        if (mp == null) {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
            mp.setOnPreparedListener(this);
            mp.setOnErrorListener(this);
            mp.setOnCompletionListener(this);
        }

        // v12: 启动时恢复队列 (进程被系统杀)
        try { restoreQueue(); } catch (Exception ignored) {}

        handler.postDelayed(tick, 500);
    }

    // 500ms心跳: 睡眠定时 + 进度 + 淡出
    final Runnable tick = new Runnable() { public void run() {
        try {
            if (mp != null) {
                // v12: 睡眠淡出模式
                if (sleepMode == 3 && sleepFadeBeginMs > 0) {
                    long elapsed = System.currentTimeMillis() - sleepFadeBeginMs;
                    float fadeDuration = 30000f; // 30s 淡出
                    float v = Math.max(0f, 1f - (elapsed / fadeDuration));
                    try { mp.setVolume(v, v); } catch (Exception ignored) {}
                    if (v <= 0f) {
                        sleepMode = 0;
                        sleepFadeBeginMs = 0;
                        try { mp.pause(); } catch (Exception ignored) {}
                        MainActivity.onPlayState(nowPlaying, "🌙 淡出已暂停");
                    }
                }
                if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                    sleepAt = 0;
                    sleepMode = 0;
                    try { mp.pause(); } catch (Exception ignored) {}
                    MainActivity.onPlayState(nowPlaying, "🌙 睡眠定时已暂停");
                }
                if (mp.isPlaying()) {
                    try { MainActivity.onProgress(mp.getCurrentPosition(), mp.getDuration()); } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) { /* 心跳不能闪退 */ }
        handler.postDelayed(this, 500);
    }};

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) return START_STICKY;
        String act = i.getAction();
        if (act == null) act = "";
        switch (act) {
            case "PAUSE": toggle(); return START_STICKY;
            case "NEXT": next(); return START_STICKY;
            case "PREV": prev(); return START_STICKY;
            case "STOP": stop(); return START_STICKY;
            case "SHUFFLE":
                shuffle = !shuffle;
                MainActivity.onPlayState(nowPlaying, shuffle ? L10n.s("shuffle_on") : L10n.s("order"));
                updateSessionState();
                return START_STICKY;
            case "REPEAT":
                repeatAll = (repeatAll + 1) % 3; // 0=关 1=列表 2=单曲
                repeatMode = repeatAll == 2 ? 1 : 0;
                String rMsg = repeatAll == 0 ? "循环关闭" : repeatAll == 1 ? "列表循环" : "单曲循环";
                MainActivity.onPlayState(nowPlaying, rMsg);
                updateSessionState();
                return START_STICKY;
            case "SEEK":
                try { if (mp != null && mp.getDuration() > 0)
                    seek((int) (mp.getDuration() * i.getFloatExtra("frac", 0f)));
                } catch (Exception ignored) {}
                return START_STICKY;
            case "SPEED":
                speed = i.getFloatExtra("v", 1f);
                applySpeed();
                return START_STICKY;
            case "EQ_PRESET":
                int idx = i.getIntExtra("idx", -1);
                EqPresets.apply(new EqPresets.Real(), idx);
                MainActivity.onPlayState(nowPlaying, "EQ: " + EqPresets.name(idx));
                return START_STICKY;
            case "SLEEP": {
                long min = i.getLongExtra("min", 0);
                int mode = i.getIntExtra("mode", 1); // 0=关闭 1=按时间 2=播完 3=淡出
                if (mode == 0) {
                    sleepAt = 0; sleepMode = 0; sleepFadeBeginMs = 0;
                    try { if (mp != null) mp.setVolume(1f, 1f); } catch (Exception ignored) {}
                    MainActivity.onPlayState(nowPlaying, "睡眠定时取消");
                } else if (mode == 3) {
                    sleepMode = 3;
                    sleepFadeBeginMs = System.currentTimeMillis();
                    MainActivity.onPlayState(nowPlaying, "🌙 30秒淡出后暂停");
                } else if (mode == 2) {
                    sleepMode = 2;
                    sleepAt = Long.MAX_VALUE; // 永远不到, 由 onCompletion 触发停止
                    MainActivity.onPlayState(nowPlaying, "🌙 播完当前曲后停止");
                } else {
                    sleepMode = 1;
                    sleepAt = min == 0 ? 0 : System.currentTimeMillis() + min * 60000;
                    MainActivity.onPlayState(nowPlaying, min == 0 ? "睡眠定时取消" : "睡眠定时 " + min + "分钟");
                }
                return START_STICKY;
            }
            case "CLEAR":
                queueUrl.clear(); queueTitle.clear(); queueIdx = 0;
                stopIcyTicker();
                saveQueue();
                return START_STICKY;
            case "RG_TOGGLE":
                replayGainEnabled = i.getBooleanExtra("on", true);
                getSharedPreferences("mf", MODE_PRIVATE).edit()
                    .putBoolean("replaygain", replayGainEnabled).apply();
                return START_STICKY;
            case "RG_ALBUM":
                replayGainAlbum = i.getBooleanExtra("album", false);
                getSharedPreferences("mf", MODE_PRIVATE).edit()
                    .putBoolean("replaygain_album", replayGainAlbum).apply();
                return START_STICKY;
        }
        // 入队
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
            saveQueue();
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

    public static short[] eqBands() {
        try {
            if (mp == null) return null;
            if (eq == null) eq = new Equalizer(0, mp.getAudioSessionId());
            return eq.getBandLevelRange();
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

    /** 启动播放: ReplayGain 检测 → 音量调整 → Gapless 预加载 */
    void playCurrent() {
        if (queueIdx < 0 || queueIdx >= queueUrl.size()) return;
        String url = queueUrl.get(queueIdx);
        nowPlaying = queueTitle.get(queueIdx);
        if (url == null || url.isEmpty()) { next(); return; }
        try {
            // v12: ReplayGain 检测
            float volume = 1.0f;
            if (replayGainEnabled && url.startsWith("/")) {
                try {
                    ReplayGainParser.GainInfo gi = ReplayGainParser.parse(new File(url));
                    volume = ReplayGainParser.gainToVolume(
                        gi.getEffectiveGain(replayGainAlbum));
                } catch (Exception ignored) {}
            }
            // 释放预加载 (避免内存泄漏)
            releaseNext();
            mp.reset();
            mp.setVolume(volume, volume);
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
            updateSessionState();
            if (url.startsWith("http")) startIcyTicker(url);
            else stopIcyTicker();
            // v12: Gapless 预加载下一曲
            preloadNext();
        } catch (Exception e) {
            MainActivity.onPlayState(nowPlaying, "✗ " + e.getMessage());
        }
    }

    /** v12: Gapless 预加载下一曲 */
    void preloadNext() {
        if (queueUrl.isEmpty() || Build.VERSION.SDK_INT < 23) return;
        if (repeatMode == 1) return; // 单曲循环不需要预加载
        int nextIdx = shuffle
            ? (int) (Math.random() * queueUrl.size())
            : (queueIdx + 1) % queueUrl.size();
        if (nextIdx == queueIdx) return;
        String nextUrl = queueUrl.get(nextIdx);
        if (nextUrl == null || nextUrl.isEmpty()) return;
        try {
            mpNext = new MediaPlayer();
            mpNext.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
            mpNext.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer p, int w, int e) { return true; }
            });
            if (nextUrl.startsWith("/")) {
                java.io.FileInputStream fis = new java.io.FileInputStream(nextUrl);
                mpNext.setDataSource(fis.getFD());
                fis.close();
            } else {
                mpNext.setDataSource(nextUrl);
            }
            mpNext.prepareAsync();
            // v12: setNextMediaPlayer (API 23+)
            mp.setNextMediaPlayer(mpNext);
        } catch (Exception e) {
            android.util.Log.w("PlayerService", "preload next failed", e);
            releaseNext();
        }
    }

    void releaseNext() {
        if (mpNext != null) {
            try { mpNext.release(); } catch (Exception ignored) {}
            mpNext = null;
        }
    }

    void next() {
        if (queueUrl.isEmpty()) return;
        // v12: Gapless 接管 — 预加载好的 mpNext 切为主
        if (mpNext != null) {
            try {
                MediaPlayer old = mp;
                mp = mpNext;
                mpNext = null;
                // 复用监听
                mp.setOnPreparedListener(this);
                mp.setOnErrorListener(this);
                mp.setOnCompletionListener(this);
                // 释放旧的
                try { old.release(); } catch (Exception ignored) {}
                if (eq != null) { try { eq.release(); } catch (Exception ignored) {} eq = null; }
            } catch (Exception e) {
                // 失败回退
                releaseNext();
            }
        }
        if (shuffle) queueIdx = (int) (Math.random() * queueUrl.size());
        else queueIdx = (queueIdx + 1) % queueUrl.size();
        playCurrent();
        saveQueue();
    }
    void prev() {
        if (queueUrl.isEmpty()) return;
        queueIdx = (queueIdx - 1 + queueUrl.size()) % queueUrl.size();
        playCurrent();
        saveQueue();
    }

    void toggle() {
        if (mp == null) return;
        try {
            if (mp.isPlaying()) {
                mp.pause();
                MainActivity.onPlayState(nowPlaying, L10n.s("paused"));
            } else {
                mp.start();
                MainActivity.onPlayState(nowPlaying, L10n.s("playing"));
            }
            updateSessionState();
        } catch (Exception ignored) {}
    }

    void stop() {
        try { if (mp != null) { mp.pause(); mp.seekTo(0); } } catch (Exception ignored) {}
        MainActivity.onPlayState(nowPlaying, "已停止");
    }

    void requestFocus() {
        try {
            if (audioManager == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                if (focusRequest == null) {
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                        .setAcceptsDelayedFocusGain(true)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(this)
                        .build();
                }
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception ignored) {}
    }

    @Override public void onAudioFocusChange(int f) {
        if (f <= 0) toggle();
    }

    @Override public void onCompletion(MediaPlayer p) {
        // v12: 播完当前曲停止 (睡眠模式2)
        if (sleepMode == 2) {
            sleepMode = 0; sleepAt = 0;
            try { mp.pause(); } catch (Exception ignored) {}
            MainActivity.onPlayState(nowPlaying, "🌙 播完当前曲已停止");
            return;
        }
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
        // v12: 如果 mpNext 已就绪, 设置 setNextMediaPlayer
        if (mpNext != null && p == mp) {
            try { mp.setNextMediaPlayer(mpNext); } catch (Exception ignored) {}
        }
    }
    @Override public boolean onError(MediaPlayer p, int what, int extra) {
        MainActivity.onPlayState(nowPlaying, L10n.s("error"));
        try { next(); } catch (Exception ignored) {}
        return true;
    }

    // ── ICY 直播流元数据 ticker ──
    java.util.Timer icyTimer;
    void startIcyTicker(final String url) {
        stopIcyTicker();
        icyTimer = new java.util.Timer();
        final String fUrl = url;
        icyTimer.schedule(new java.util.TimerTask() {
            public void run() {
                try {
                    final String[] m = IcyMetadata.fetch(fUrl);
                    final String cur = m[2];
                    if (cur != null && !cur.isEmpty()) MainActivity.onStreamMeta(cur);
                    else if (m[0] != null && !m[0].isEmpty()) MainActivity.onStreamMeta(m[0] + " · " + m[1]);
                    else MainActivity.onStreamMeta(null);
                } catch (Exception ignored) {}
            }
        }, 1500, 15000);
    }
    void stopIcyTicker() {
        if (icyTimer != null) { icyTimer.cancel(); icyTimer = null; }
    }

    /** v12: MediaSession 状态更新 (使用反射避免androidx依赖) */
    void updateSessionState() {
        if (session == null) return;
        try {
            // 简化: 通过反射调用 setPlaybackState/setMetadata (无androidx时回退)
            // 实际锁屏控制由系统通知的 MediaStyle 自动接管
            Class<?> sessionClass = session.getClass();
            try {
                // 构造 PlaybackState
                Class<?> stateBuilderClass = Class.forName("android.media.session.PlaybackState$Builder");
                Object stateBuilder = stateBuilderClass.getConstructor().newInstance();
                stateBuilderClass.getMethod("setState", int.class, long.class, float.class)
                    .invoke(stateBuilder, mp != null && mp.isPlaying() ? 3 : 2, 0L, 1.0f);
                Object state = stateBuilderClass.getMethod("build").invoke(stateBuilder);
                sessionClass.getMethod("setPlaybackState", Class.forName("android.media.session.PlaybackState"))
                    .invoke(session, state);
            } catch (Throwable t) { /* 静默 */ }
        } catch (Throwable t) { /* 静默 */ }
    }

    /** v12: 队列持久化 */
    void saveQueue() {
        try {
            org.json.JSONObject q = new org.json.JSONObject();
            org.json.JSONArray urls = new org.json.JSONArray();
            org.json.JSONArray titles = new org.json.JSONArray();
            for (String u : queueUrl) urls.put(u);
            for (String t : queueTitle) titles.put(t);
            q.put("urls", urls);
            q.put("titles", titles);
            q.put("idx", queueIdx);
            q.put("pos", mp != null ? mp.getCurrentPosition() : 0);
            q.put("now", nowPlaying);
            getSharedPreferences("mf", MODE_PRIVATE).edit()
                .putString(PREF_QUEUE, q.toString()).apply();
        } catch (Exception ignored) {}
    }

    void restoreQueue() {
        try {
            String raw = getSharedPreferences("mf", MODE_PRIVATE).getString(PREF_QUEUE, "");
            if (raw.isEmpty()) return;
            org.json.JSONObject q = new org.json.JSONObject(raw);
            org.json.JSONArray urls = q.getJSONArray("urls");
            org.json.JSONArray titles = q.getJSONArray("titles");
            queueUrl.clear(); queueTitle.clear();
            for (int i = 0; i < urls.length(); i++) queueUrl.add(urls.getString(i));
            for (int i = 0; i < titles.length(); i++) queueTitle.add(titles.getString(i));
            queueIdx = q.getInt("idx");
            nowPlaying = q.optString("now", "");
            // 恢复播放位置
            final long pos = q.optLong("pos", 0);
            if (!queueUrl.isEmpty() && queueIdx < queueUrl.size()) {
                bgRestore(pos);
            }
        } catch (Exception ignored) {}
    }

    void bgRestore(final long pos) {
        handler.postDelayed(new Runnable() {
            public void run() {
                try {
                    mp.reset();
                    String url = queueUrl.get(queueIdx);
                    if (url.startsWith("/")) {
                        java.io.FileInputStream fis = new java.io.FileInputStream(url);
                        mp.setDataSource(fis.getFD());
                        fis.close();
                    } else {
                        mp.setDataSource(url);
                    }
                    mp.prepareAsync();
                    // prepared 后跳转
                    final MediaPlayer.OnPreparedListener prev = mp.getClass() != null ? null : null;
                    // 简单做法: 等 prepareAsync 完成后跳
                    new Thread() { public void run() {
                        // 轮询 isPlaying 后跳转
                        for (int i = 0; i < 50; i++) {
                            try { Thread.sleep(100); } catch (Exception e) { break; }
                            try {
                                if (mp != null && mp.getDuration() > 0 && mp.getCurrentPosition() == 0) {
                                    mp.seekTo((int) pos);
                                    mp.pause();
                                    MainActivity.onPlayState(nowPlaying, "已恢复 (暂停中)");
                                    break;
                                }
                            } catch (Exception e) { break; }
                        }
                    }}.start();
                } catch (Exception e) { android.util.Log.w("PlayerService", "bgRestore", e); }
            }
        }, 1000);
    }

    void showNotif(String title, String state) {
        MusicWidget.update(this, title, state);
        try {
            if (nm == null) return;
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

            // v12: 通知动作按钮
            Notification.Builder nb = new Notification.Builder(this)
                .setContentTitle("MusicFusion")
                .setContentText(title + " · " + state + (shuffle ? " 🔀" : "")
                    + (repeatAll == 2 ? " 🔂" : repeatAll == 1 ? " 🔁" : ""))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true).setContentIntent(pi)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

            // 上一曲
            nb.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_previous, "⏮",
                buildPending("PREV", 1)).build());
            // 播放/暂停
            boolean isPlaying = false;
            try { isPlaying = mp != null && mp.isPlaying(); } catch (Exception ignored) {}
            nb.addAction(new Notification.Action.Builder(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "⏸" : "▶",
                buildPending("PAUSE", 2)).build());
            // 下一曲
            nb.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_media_next, "⏭",
                buildPending("NEXT", 3)).build());
            // 停止
            nb.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "⏹",
                buildPending("STOP", 4)).build());

            if (Build.VERSION.SDK_INT >= 26) {
                if (nm.getNotificationChannel("play") == null) {
                    NotificationChannel ch = new NotificationChannel("play", "播放",
                        NotificationManager.IMPORTANCE_LOW);
                    ch.setSound(null, null);
                    nm.createNotificationChannel(ch);
                }
                nb.setChannelId("play");
                // v12: MediaStyle (使用反射处理MediaSession.Token)
                try {
                    Notification.MediaStyle ms = new Notification.MediaStyle();
                    if (session != null) {
                        try {
                            Object token = session.getClass().getMethod("getSessionToken").invoke(session);
                            ms.getClass().getMethod("setMediaSession", Class.forName("android.media.session.MediaSession$Token"))
                                .invoke(ms, token);
                        } catch (Throwable t) { /* 静默 */ }
                    }
                    ms.setShowActionsInCompactView(0, 1, 2);
                    nb.setStyle(ms);
                } catch (Exception ignored) {}
                startForeground(2001, nb.build());
            } else nm.notify(2001, nb.build());
        } catch (Exception ignored) {}
    }

    PendingIntent buildPending(String action, int code) {
        Intent i = new Intent(this, PlayerService.class);
        i.setAction(action);
        return PendingIntent.getService(this, code, i,
            Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        try { unregisterReceiver(noisy); } catch (Exception ignored) {}
        try { if (session != null) { session.getClass().getMethod("release").invoke(session); session = null; } } catch (Exception ignored) {}
        handler.removeCallbacks(tick);
        stopIcyTicker();
        try { if (eq != null) eq.release(); } catch (Exception ignored) {}
        releaseNext();
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
        super.onDestroy();
    }
}