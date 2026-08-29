package com.musicfusion.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MusicFusion v12 "Nebula" — 极全面升级
 *  保留 v11 全部功能 (9大特性), 新增 22+ 升级:
 *  P0:  1. 全链路 try-catch + Crash 上报 JSON v2 (CrashReporter.java)
 *  P0:  2. 主/后台线程严格分离 (ui/bg 封装)
 *  P1:  3. Gapless 播放 (PlayerService 双 MediaPlayer)
 *  P1:  4. Crossfade 切换 (PlayerService.setNextMediaPlayer)
 *  P1:  5. ReplayGain 响度归一化 (ReplayGainParser.java)
 *  P1:  6. 队列持久化 + 恢复 (PlayerService.saveQueue/restoreQueue)
 *  P1:  7. MediaSession + 锁屏/通知/线控/Assistant 统一入口
 *  P1:  8. 三模式循环 (关/列表/单曲)
 *  P1:  9. 睡眠淡出模式 (30s 渐弱)
 *  P2: 10. Audius GraphQL 深度挖掘 (Audius.java 新增 playlist/underground/remixes)
 *  P2: 11. Internet Archive 高级检索 (Archive.java 字段组合)
 *  P2: 12. RadioBrowser 标签/语言/编解码器筛选 (RadioBrowser.java)
 *  P2: 13. Openverse 许可证/来源/扩展名筛选 (Openverse.java)
 *  P3: 14. 智能歌单生成 (SmartPlaylist.java)
 *  P3: 15. 多源歌词聚合 + 时间轴同步 (LyricsEngine.java)
 *  P3: 16. 音频可视化 (VisualizerView.java 波形/柱/圆环/粒子)
 *  P3: 17. 备份/恢复 (BackupManager.java)
 *  P3: 18. 图片缓存 (ImageCache.java)
 *  P3: 19. 搜索建议+纠错+热词 (SearchSuggest.java)
 *  P3: 20. Material You 动态色 (MaterialColor.java, API 31+)
 *  P4: 21. 通知 MediaStyle + 动作按钮 (PlayerService)
 *  P4: 22. 设置面板 12项 全面扩展
 *  P5: 23. 内容导入: 解析 Audius/IA/Spotify 链接
 */
public class MainActivity extends Activity {

    static boolean lightTheme = false;
    static int C(String hex) {
        if (lightTheme) {
            if ("#0b0e14".equals(hex) || "#0d1117".equals(hex)) return Color.parseColor("#f6f8fa");
            if ("#161c28".equals(hex) || "#10151d".equals(hex)) return Color.parseColor("#ffffff");
            if ("#232c3d".equals(hex)) return Color.parseColor("#d0d7de");
            if ("#e6edf3".equals(hex) || "#ffffff".equals(hex)) return Color.parseColor("#1f2328");
            if ("#8b949e".equals(hex)) return Color.parseColor("#656d76");
            if ("#000000".equals(hex)) return Color.parseColor("#ffffff");
            if ("#aaaaaa".equals(hex)) return Color.parseColor("#57606a");
            if ("#132a1c".equals(hex)) return Color.parseColor("#dafbe1");
            if ("#7d2d2d".equals(hex)) return Color.parseColor("#ffebe9");
            if ("#1d3a5c".equals(hex)) return Color.parseColor("#ddf4ff");
        }
        try { return Color.parseColor(hex); }
        catch (Throwable t) { return Color.GRAY; }
    }
    static final String C_BG = "#0b0e14", C_CARD = "#161c28", C_LINE = "#232c3d",
        C_GREEN = "#1db954", C_TXT = "#e6edf3", C_DIM = "#8b949e", C_ACC = "#58a6ff",
        C_ERR = "#f85149", C_WARN = "#d29922", C_INFO_BG = "#1d3a5c", C_ERR_BG = "#7d2d2d";

    TextView status, nowBar, timeLabel, miniTitle, miniState, miniStream, errorBanner;
    SeekBar seek;
    EditText searchBox;
    ListView resultList;
    RowAdapter adapter;
    LinearLayout tabsView, miniBar;
    ArrayList<Object[]> rows = new ArrayList<Object[]>();
    int curTab = 0, playingPos = -1;
    boolean seeking = false;
    Timer searchTimer;
    String curTrackId = null, curUserId = null;
    Map<String, String> srcErrors = new LinkedHashMap<String, String>();

    static String[] TABS = {"首页", "搜索", "电台", "目录", "播客", "下载", "统计", "我的"};
    static MainActivity inst;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        inst = this;
        L10n.load(this);
        CrashReporter.install(this); // v12: JSON v2 崩溃上报
        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        lightTheme = sp.getBoolean("light", false);
        // v12: 加载 ReplayGain 偏好
        try {
            // PlayerService 会在 onStartCommand 时读取
        } catch (Throwable t) { /* 静默 */ }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C(C_BG));
        setContentView(root);

        // ═══ 顶部区 (Header) ═══
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(36), dp(20), dp(10));
        top.setBackgroundColor(C("#0d1117"));
        root.addView(top);

        LinearLayout head = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText("MusicFusion v12 Nebula");
        title.setTextSize(20); title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(C(C_GREEN));
        title.setContentDescription("MusicFusion v12 Nebula");
        head.addView(title, w(1));
        TextView gear = new TextView(this);
        gear.setText(L10n.s("settings"));
        gear.setTextSize(13); gear.setTextColor(C(C_ACC));
        gear.setPadding(dp(10), dp(4), dp(2), dp(4));
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { settingsDialog(); } catch (Throwable t) { toast("设置打开失败: " + t.getMessage()); }
        }});
        gear.setContentDescription(L10n.s("settings"));
        head.addView(gear);
        top.addView(head);

        // 搜索框
        searchBox = new EditText(this);
        searchBox.setHint(L10n.s("search_hint"));
        searchBox.setTextSize(14);
        searchBox.setTextColor(C(C_TXT));
        searchBox.setHintTextColor(C(C_DIM));
        GradientDrawable eg = new GradientDrawable();
        eg.setCornerRadius(dp(12)); eg.setColor(C(C_CARD));
        eg.setStroke(dp(1), C(C_LINE));
        searchBox.setBackground(eg);
        searchBox.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchBox.setContentDescription(L10n.s("search_hint"));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.topMargin = dp(10);
        top.addView(searchBox, sp2);

        // v12: 搜索建议容器
        final ListView suggestList = new ListView(this);
        suggestList.setBackgroundColor(C(C_CARD));
        suggestList.setVisibility(View.GONE);
        suggestList.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(200)));
        top.addView(suggestList);

        tabsView = new LinearLayout(this);
        tabsView.setPadding(0, dp(10), 0, dp(4));
        for (int i = 0; i < TABS.length; i++) addTab(tabsView, i);
        top.addView(tabsView);

        // v12: 顶部播放信息行 (nowBar + timeLabel)
        LinearLayout nowRow = new LinearLayout(this);
        nowRow.setOrientation(LinearLayout.HORIZONTAL);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        nowRow.setPadding(dp(4), dp(2), dp(4), dp(2));
        nowBar = new TextView(this);
        nowBar.setText(L10n.s("not_playing"));
        nowBar.setTextSize(12);
        nowBar.setTextColor(C(C_DIM));
        nowBar.setSingleLine(true);
        nowBar.setTypeface(null, Typeface.BOLD);
        nowBar.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { send("PAUSE"); } catch (Throwable t) {}
        }});
        nowBar.setContentDescription("Now playing");
        nowRow.addView(nowBar, w(1));
        timeLabel = new TextView(this);
        timeLabel.setText("--:--");
        timeLabel.setTextSize(10);
        timeLabel.setTextColor(C(C_DIM));
        timeLabel.setPadding(dp(6), 0, 0, 0);
        timeLabel.setContentDescription("Time");
        nowRow.addView(timeLabel);
        top.addView(nowRow);

        // 兼容引用 (不显示)
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setVisibility(View.GONE);

        // 错误横幅
        errorBanner = new TextView(this);
        errorBanner.setTextSize(11);
        errorBanner.setTextColor(C(C_TXT));
        errorBanner.setBackgroundColor(C(C_ERR_BG));
        errorBanner.setPadding(dp(12), dp(6), dp(12), dp(6));
        errorBanner.setVisibility(View.GONE);
        errorBanner.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            errorBanner.setVisibility(View.GONE);
            srcErrors.clear();
        }});
        top.addView(errorBanner);

        // ═══ Mini Bar ═══
        buildMiniBar(root);

        // 列表区
        status = new TextView(this);
        status.setTextSize(11); status.setTextColor(C(C_DIM));
        status.setPadding(dp(4), dp(6), dp(4), dp(6));
        top.addView(status);

        adapter = new RowAdapter();
        resultList = new ListView(this);
        resultList.setAdapter(adapter);
        resultList.setDividerHeight(dp(1));
        resultList.setDivider(new ColorDrawable(C(C_LINE)));
        resultList.setBackgroundColor(C(C_BG));
        resultList.setPadding(dp(10), 0, dp(10), dp(10));
        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                try { playAt(pos); } catch (Throwable t) { toast("播放失败: " + t.getMessage()); }
            }
        });
        resultList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                try {
                    if (pos < rows.size() && "H".equals(rows.get(pos)[3])) {
                        String t = (String) rows.get(pos)[0];
                        if (t.startsWith("搜索: ")) {
                            String q = t.substring(4);
                            Set<String> h = new HashSet<>(
                                getSharedPreferences("mf", MODE_PRIVATE)
                                    .getStringSet("search_history", new HashSet<String>()));
                            h.remove(q);
                            getSharedPreferences("mf", MODE_PRIVATE).edit()
                                .putStringSet("search_history", h).apply();
                            toast("已删除历史: " + q);
                            showSearchHistory();
                        }
                        return true;
                    }
                    itemMenu(pos); return true;
                } catch (Throwable t) { toast("操作失败: " + t.getMessage()); return true; }
            }
        });
        root.addView(resultList, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // v12: 搜索框监听 (建议 + 防抖)
        searchBox.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (searchTimer != null) searchTimer.cancel();
                final String q = s.toString().trim();
                if (q.length() < 2) { suggestList.setVisibility(View.GONE); return; }
                // v12: 实时搜索建议
                SearchSuggest.suggest(MainActivity.this, q, new SearchSuggest.SuggestCallback() {
                    public void onSuggest(final List<SearchSuggest.SuggestItem> items) {
                        runOnUiThread(new Runnable() { public void run() {
                            try {
                                if (items.isEmpty()) { suggestList.setVisibility(View.GONE); return; }
                                final ArrayList<Object[]> suggRows = new ArrayList<Object[]>();
                                for (SearchSuggest.SuggestItem it : items) {
                                    String prefix = it.isCorrection ? "✏ " :
                                        "history".equals(it.source) ? "🕐 " :
                                        "hot".equals(it.source) ? "🔥 " : "🔍 ";
                                    suggRows.add(new Object[]{prefix + it.text, "点按搜索", "\u0001SUGG:" + it.text, "S"});
                                }
                                final ListView sl = suggestList;
                                sl.setAdapter(new BaseAdapter() {
                                    public int getCount() { return suggRows.size(); }
                                    public Object getItem(int i) { return suggRows.get(i); }
                                    public long getItemId(int i) { return i; }
                                    public View getView(int i, View cv, ViewGroup vg) {
                                        LinearLayout l = new LinearLayout(MainActivity.this);
                                        l.setOrientation(LinearLayout.VERTICAL);
                                        l.setPadding(dp(12), dp(6), dp(12), dp(6));
                                        TextView t1 = new TextView(MainActivity.this);
                                        t1.setText((String) suggRows.get(i)[0]);
                                        t1.setTextSize(13); t1.setTextColor(C(C_TXT));
                                        l.addView(t1);
                                        return l;
                                    }
                                });
                                sl.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                    public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                                        try {
                                            Object[] row = suggRows.get(pos);
                                            String text = (String) row[0];
                                            // 去掉前缀
                                            int sp = text.indexOf(' ');
                                            if (sp > 0 && sp < 5) text = text.substring(sp + 1);
                                            searchBox.setText(text);
                                            sl.setVisibility(View.GONE);
                                        } catch (Throwable t) {}
                                    }
                                });
                                sl.setVisibility(View.VISIBLE);
                            } catch (Throwable t) { suggestList.setVisibility(View.GONE); }
                        }});
                    }
                });
                // 搜索
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    public void run() {
                        try { doSearch(q); } catch (Throwable t) {}
                    }
                }, 600);
            }
            public void beforeTextChanged(CharSequence c, int a, int b2, int d) {}
            public void onTextChanged(CharSequence c, int a, int b2, int d) {}
        });

        TABS = new String[]{L10n.s("tab_home"), L10n.s("tab_search"),
            L10n.s("tab_radio"), L10n.s("tab_catalog"), L10n.s("tab_mine")};
        setTab(getSharedPreferences("mf", MODE_PRIVATE).getInt("last_tab", 0));

        // v12: 启动时检查未上报崩溃
        checkUnreportedCrashes();
    }

    /** v12: 启动时检查未上报崩溃 */
    void checkUnreportedCrashes() {
        try {
            final JSONArray arr = CrashReporter.getUnreportedCrashes(this);
            if (arr.length() == 0) return;
            // 静默计数, 在统计里展示
            getSharedPreferences("mf", MODE_PRIVATE).edit()
                .putInt("unreported_crashes", arr.length()).apply();
        } catch (Throwable t) {}
    }

    void addTab(LinearLayout parent, final int idx) {
        TextView t = new TextView(this);
        t.setText(TABS[idx]); t.setTextSize(13);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setTag(idx);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { setTab(idx); } catch (Throwable e) { toast("切换失败: " + e.getMessage()); }
        }});
        t.setContentDescription("Tab " + TABS[idx]);
        parent.addView(t, w(1));
    }

    void buildMiniBar(LinearLayout parent) {
        miniBar = new LinearLayout(this);
        miniBar.setOrientation(LinearLayout.HORIZONTAL);
        miniBar.setBackgroundColor(C(C_CARD));
        GradientDrawable mg = new GradientDrawable();
        mg.setCornerRadius(dp(10)); mg.setColor(C(C_CARD));
        mg.setStroke(dp(1), C(C_LINE));
        miniBar.setBackground(mg);
        miniBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.setMargins(0, dp(4), 0, dp(4));
        miniBar.setLayoutParams(mp);
        miniBar.setVisibility(View.GONE);
        miniBar.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try { showMiniMenu(); } catch (Throwable t) {}
        }});
        miniBar.setContentDescription(L10n.s("minibar") + " " + L10n.s("click_more"));

        LinearLayout txtCol = new LinearLayout(this);
        txtCol.setOrientation(LinearLayout.VERTICAL);
        txtCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        miniTitle = new TextView(this);
        miniTitle.setTextSize(13); miniTitle.setTextColor(C(C_TXT));
        miniTitle.setTypeface(null, Typeface.BOLD);
        miniTitle.setSingleLine(true);
        txtCol.addView(miniTitle);
        miniState = new TextView(this);
        miniState.setTextSize(10); miniState.setTextColor(C(C_DIM));
        miniState.setSingleLine(true);
        txtCol.addView(miniState);
        miniStream = new TextView(this);
        miniStream.setTextSize(9); miniStream.setTextColor(C(C_ACC));
        miniStream.setSingleLine(true);
        txtCol.addView(miniStream);
        miniBar.addView(txtCol);

        // v12: 4个核心按钮 (⏮⏯⏭词) + 可视化开关
        String[] btns = {"⏮", "⏯", "⏭", "词", "🎨"};
        final String[] acts = {"PREV", "PAUSE", "NEXT", "LYRICS", "VIS"};
        for (int i = 0; i < btns.length; i++) {
            final String a = acts[i];
            TextView c = new TextView(this);
            c.setText(btns[i]); c.setTextSize(15);
            c.setGravity(Gravity.CENTER);
            c.setTextColor(C(C_TXT));
            c.setPadding(dp(8), dp(2), dp(8), dp(2));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                try {
                    if ("LYRICS".equals(a)) showLyricsSheet();
                    else if ("VIS".equals(a)) showVisualizerDialog();
                    else send(a);
                } catch (Throwable t) {}
            }});
            c.setContentDescription("Player " + a);
            miniBar.addView(c);
        }
        // 关闭
        TextView cClose = new TextView(this);
        cClose.setText("✕"); cClose.setTextSize(13);
        cClose.setGravity(Gravity.CENTER);
        cClose.setTextColor(C(C_DIM));
        cClose.setPadding(dp(8), dp(2), dp(8), dp(2));
        cClose.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try {
                send("CLEAR");
                miniBar.setVisibility(View.GONE);
                toast("已停止");
            } catch (Throwable t) {}
        }});
        cClose.setContentDescription("Stop playback");
        miniBar.addView(cClose);
        parent.addView(miniBar);
    }

    void showMiniMenu() {
        try {
            new AlertDialog.Builder(this)
                .setTitle(miniTitle.getText().toString())
                .setItems(new String[]{
                    L10n.s("artist") + " (下钻)",
                    L10n.s("lyrics_panel"),
                    "暂停/继续",
                    "可视化",
                    "切换循环"
                }, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            if (w == 0 && curUserId != null) drillArtist(curUserId);
                            else if (w == 1) showLyricsSheet();
                            else if (w == 2) send("PAUSE");
                            else if (w == 3) showVisualizerDialog();
                            else if (w == 4) send("REPEAT");
                        } catch (Throwable t) {}
                    }
                }).show();
        } catch (Throwable t) {}
    }

    // ══════ v12: 歌词抽屉 (多源 + 同步) ══════
    void showLyricsSheet() {
        try {
            String full = miniTitle.getText().toString();
            if (full.startsWith("选择")) { toast(L10n.s("not_playing")); return; }
            final String track = full;
            final android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.setBackgroundColor(C(C_CARD));
            final TextView tv = new TextView(this);
            tv.setText(L10n.s("lyr_query"));
            tv.setTextSize(14);
            tv.setTextColor(C(C_TXT));
            tv.setPadding(dp(20), dp(20), dp(20), dp(20));
            tv.setTypeface(Typeface.MONOSPACE);
            sv.addView(tv);
            final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(track + "  ·  歌词")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .create();
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface di) {
                    try {
                        android.view.Window w = d.getWindow();
                        if (w != null) {
                            android.view.WindowManager.LayoutParams lp = w.getAttributes();
                            lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
                            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95f);
                            lp.gravity = Gravity.BOTTOM;
                            w.setAttributes(lp);
                        }
                    } catch (Exception e) {}
                }
            });
            d.show();
            // v12: 用 LyricsEngine 多源搜索
            String artist = "", name = track;
            String[] parts = track.split(" · ");
            if (parts.length >= 2) { name = parts[0]; artist = parts[1]; }
            LyricsEngine.search(this, name, artist, 0, new LyricsEngine.Callback() {
                public void onResult(final LyricsEngine.LyricsResult r) {
                    runOnUiThread(new Runnable() { public void run() {
                        try {
                            if (r.lines.isEmpty()) { tv.setText(L10n.s("lyr_none")); return; }
                            StringBuilder sb = new StringBuilder();
                            for (LyricsEngine.LyricsResult.Line l : r.lines) {
                                if (l.timeMs > 0) sb.append("[").append(fmt((int) l.timeMs)).append("]\n");
                                sb.append(l.text).append("\n\n");
                            }
                            tv.setText(sb.toString());
                        } catch (Throwable t) { tv.setText("解析失败"); }
                    }});
                }
                public void onError(final String error) {
                    runOnUiThread(new Runnable() { public void run() {
                        tv.setText(L10n.s("lyr_none") + "\n" + error);
                    }});
                }
            });
        } catch (Throwable t) { toast("歌词打开失败: " + t.getMessage()); }
    }

    // ══════ v12: 可视化对话框 ══════
    void showVisualizerDialog() {
        try {
            final String[] modes = {"波形", "频谱柱", "圆环", "粒子"};
            final String[] shapes = {"▁▂▃", "▌▎▍", "○", "✦"};
            final int[] barCounts = {32, 48, 64, 96, 128};
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(20), dp(10), dp(20), 0);
            // 模式选择
            for (int i = 0; i < modes.length; i++) {
                final int idx = i;
                TextView t = new TextView(this);
                t.setText(shapes[i] + "  " + modes[i]);
                t.setTextSize(14);
                t.setTextColor(C(C_TXT));
                t.setPadding(0, dp(8), 0, dp(8));
                t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    try {
                        SharedPreferences sp = getSharedPreferences("mf_vis", MODE_PRIVATE);
                        sp.edit().putInt("vis_mode", idx).apply();
                        toast("已切换: " + modes[idx]);
                    } catch (Throwable t) {}
                }});
                box.addView(t);
            }
            // 灵敏度调节
            final SeekBar sensBar = new SeekBar(this);
            sensBar.setMax(50);
            int cur = (int) (getSharedPreferences("mf_vis", MODE_PRIVATE).getFloat("vis_sens", 1.0f) * 10);
            sensBar.setProgress(cur);
            sensBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean f) {
                    getSharedPreferences("mf_vis", MODE_PRIVATE).edit()
                        .putFloat("vis_sens", p / 10f).apply();
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            TextView sensLabel = new TextView(this);
            sensLabel.setText("灵敏度: " + String.format(Locale.US, "%.1f", cur / 10f));
            box.addView(sensLabel);
            box.addView(sensBar);
            new AlertDialog.Builder(this)
                .setTitle("🎨 可视化")
                .setView(box)
                .setPositiveButton("完成", null)
                .setNeutralButton("重置", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        getSharedPreferences("mf_vis", MODE_PRIVATE).edit().clear().apply();
                        toast("已重置");
                    }
                }).show();
        } catch (Throwable t) { toast("可视化打开失败"); }
    }

    // ══════ Audius 作者下钻 ══════
    void drillArtist(final String userId) {
        status("加载作者曲目…");
        curTab = 1;
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                String info = Audius.userInfo(userId);
                JSONObject u = new JSONObject(info).getJSONObject("data");
                String name = u.optString("name", "?");
                out.add(new Object[]{"👤 作者: " + name, "Audius 创作者 · 点按单曲播放", "", "头"});
                String[] r = Audius.parseWithIds(Audius.userTracks(userId));
                for (String _l : r) {
                    String[] s = _l.split("\u0001");
                    out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                }
            } catch (Exception e) { out.add(err("作者加载失败", e)); }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("作者曲目 · " + rows.size() + " 首");
            }});
        }});
    }

    void noiseDialog() {
        final SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        final int[] cur = {sp.getInt("noise_rain", 0), sp.getInt("noise_fire", 0), sp.getInt("noise_brown", 50)};
        final SeekBar[] sbs = new SeekBar[3];
        final String[] names = {"🌧 雨声", "🔥 柴火", "🌑 棕噪声"};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        for (int i = 0; i < 3; i++) {
            TextView lab = new TextView(this);
            lab.setText(names[i] + "  " + cur[i] + "%");
            lab.setTextSize(12); lab.setTextColor(C(C_TXT));
            final int idx = i;
            box.addView(lab);
            SeekBar sb = new SeekBar(this);
            sb.setMax(100); sb.setProgress(cur[i]);
            final TextView fl = lab;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean u) {
                    cur[idx] = p;
                    fl.setText(names[idx] + "  " + p + "%");
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            sbs[i] = sb;
            box.addView(sb);
        }
        new AlertDialog.Builder(this)
            .setTitle("白噪声/睡眠音")
            .setView(box)
            .setPositiveButton("▶ 播放", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    sp.edit().putInt("noise_rain", cur[0])
                        .putInt("noise_fire", cur[1])
                        .putInt("noise_brown", cur[2]).apply();
                    bg(new Runnable() { public void run() {
                        String path = WhiteNoise.generate(getCacheDir(), cur[0], cur[1], cur[2]);
                        if (path == null) { ui(new Runnable() { public void run() {
                            toast("生成失败"); }}); return; }
                        Intent i = new Intent(MainActivity.this, PlayerService.class);
                        i.putExtra("urls", new String[]{path});
                        i.putExtra("titles", new String[]{"白噪声 · 雨" + cur[0] + "% 火" + cur[1] + "% 棕" + cur[2] + "%"});
                        i.putExtra("index", 0);
                        startService(i);
                        ui(new Runnable() { public void run() { toast("白噪声已生成并播放"); }});
                    }});
                }
            })
            .setNeutralButton("删除缓存", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    File[] fs = getCacheDir().listFiles();
                    int n = 0;
                    if (fs != null) for (File f : fs) if (f.getName().startsWith("noise_")) { f.delete(); n++; }
                    toast("已清理 " + n + " 个缓存");
                }
            })
            .setNegativeButton("取消", null).show();
    }

    void eqPresetDialog() {
        final String[] names = new String[EqPresets.count()];
        for (int i = 0; i < names.length; i++) names[i] = EqPresets.name(i);
        new AlertDialog.Builder(this)
            .setTitle("EQ 预设(5种风格)")
            .setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    Intent i = new Intent(MainActivity.this, PlayerService.class);
                    i.setAction("EQ_PRESET"); i.putExtra("idx", w);
                    startService(i);
                }
            })
            .setNegativeButton("取消", null).show();
    }

    void refreshCatalog() {
        toast("刷新中…");
        bg(new Runnable() { public void run() {
            try {
                String json = RadioBrowser.topByVotes(100);
                JSONArray arr = new JSONArray(json);
                JSONArray out = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    JSONObject o = new JSONObject();
                    o.put("n", s.optString("name", "?"));
                    o.put("c", s.optString("country", ""));
                    String tags = s.optString("tags", "");
                    o.put("t", tags.length() > 30 ? tags.substring(0, 30) : tags);
                    o.put("u", s.optString("url_resolved", ""));
                    o.put("b", s.optInt("bitrate", 0));
                    out.put(o);
                }
                JSONObject root = new JSONObject();
                root.put("stations", out);
                root.put("updated", System.currentTimeMillis());
                File f = new File(getFilesDir(), "stations_live.json");
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(root.toString().getBytes("UTF-8"));
                fo.close();
                final int n = arr.length();
                ui(new Runnable() { public void run() {
                    catalogCache = root;
                    toast("已刷新 " + n + " 台电台");
                    if (curTab == 3) loadCatalog(searchBox.getText().toString().trim());
                }});
            } catch (Exception e) {
                final String m = e.getMessage() != null ? e.getMessage() : "未知错误";
                ui(new Runnable() { public void run() { toast("刷新失败: " + m); }});
            }
        }});
    }

    // ══════ v13: 设置面板 20+ 项 ══════
    void settingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("设置 v13 Cosmic")
            .setItems(new String[]{
                L10n.s("sleep"), L10n.s("speed"), L10n.s("equalizer"),
                L10n.s("preset"), L10n.s("noise"),
                L10n.s("stats"), L10n.s("theme"), L10n.s("datasaver"),
                L10n.s("refresh_cat"),
                L10n.s("clear_hist"), L10n.s("lang"),
                "🎨 可视化", "📊 ReplayGain", "💾 备份/恢复",
                "🎵 智能歌单", "🛠 崩溃报告",
                // v13 新增
                "📈 统计图表", "🎛️ 10段均衡器", "🎧 播客管理",
                "⬇️ 下载设置", "🎨 主题编辑器",
                "🌐 Last.fm Scrobble", "🏷️ MusicBrainz 补全",
                "🔍 搜索筛选", "📱 小组件/快捷方式",
                L10n.s("about")
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        if (w == 0) sleepDialog();
                        else if (w == 1) speedDialog();
                        else if (w == 2) eqDialog();
                        else if (w == 3) eqPresetDialog();
                        else if (w == 4) noiseDialog();
                        else if (w == 5) statsDialog();
                        else if (w == 6) {
                            boolean now = !getSharedPreferences("mf", MODE_PRIVATE)
                                .getBoolean("light", false);
                            getSharedPreferences("mf", MODE_PRIVATE)
                                .edit().putBoolean("light", now).apply();
                            toast(now ? "已切浅色, 重启生效" : "已切深色, 重启生效");
                        } else if (w == 7) {
                            boolean now = !getSharedPreferences("mf", MODE_PRIVATE)
                                .getBoolean("datasaver", false);
                            getSharedPreferences("mf", MODE_PRIVATE)
                                .edit().putBoolean("datasaver", now).apply();
                            toast(now ? "省流量模式开" : "省流量模式关");
                            setTab(curTab);
                        } else if (w == 8) refreshCatalog();
                        else if (w == 9) {
                            getSharedPreferences("mf", MODE_PRIVATE)
                                .edit().remove("search_history").apply();
                            SearchSuggest.clearHistory(MainActivity.this);
                            toast(L10n.s("clear_hist") + " OK");
                        } else if (w == 10) langDialog();
                        else if (w == 11) showVisualizerDialog();
                        else if (w == 12) replayGainDialog();
                        else if (w == 13) backupDialog();
                        else if (w == 14) smartPlaylistDialog();
                        else if (w == 15) crashReportDialog();
                        // v13 新增
                        else if (w == 16) showStatsCharts();
                        else if (w == 17) showGraphicEqDialog();
                        else if (w == 18) podcastSettingsDialog();
                        else if (w == 19) downloadSettingsDialog();
                        else if (w == 20) themeEditorDialog();
                        else if (w == 21) lastFmDialog();
                        else if (w == 22) musicBrainzDialog();
                        else if (w == 23) searchFiltersDialog();
                        else if (w == 24) widgetShortcutsDialog();
                        else aboutDialog();
                    } catch (Throwable t) { toast("操作失败: " + t.getMessage()); }
                }
            }).show();
    }

    void langDialog() {
        new AlertDialog.Builder(this)
            .setTitle(L10n.s("lang"))
            .setItems(new String[]{"中文", "English"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    getSharedPreferences("mf", MODE_PRIVATE)
                        .edit().putInt("lang", w).apply();
                    toast(w == 0 ? "已切换中文, 重启生效" : "Switched to English, restart to apply");
                }
            }).show();
    }

    void sleepDialog() {
        // v12: 4 模式: 关闭/按时间/播完当前曲/淡出
        final String[] opts = {"关闭", "15分钟", "30分钟", "45分钟", "60分钟",
            "90分钟", "播完当前曲", "淡出(30s)", "自定义…"};
        final long[] vals = {0, 15, 30, 45, 60, 90, -1, -3, -2};
        final int[] modes = {0, 1, 1, 1, 1, 1, 2, 3, 1};
        new AlertDialog.Builder(this)
            .setTitle("睡眠定时")
            .setItems(opts, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        if (vals[w] == -2) {
                            final EditText et = new EditText(MainActivity.this);
                            et.setHint("分钟数 1-999");
                            et.setInputType(InputType.TYPE_CLASS_NUMBER);
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("自定义睡眠分钟")
                                .setView(et)
                                .setPositiveButton("设定", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dd, int ww) {
                                        try {
                                            int m = Integer.parseInt(et.getText().toString().trim());
                                            if (m < 1 || m > 999) { toast("请输入1-999"); return; }
                                            Intent i = new Intent(MainActivity.this, PlayerService.class);
                                            i.setAction("SLEEP"); i.putExtra("min", m); i.putExtra("mode", 1);
                                            startService(i);
                                        } catch (Exception e) { toast("无效数字"); }
                                    }
                                }).setNegativeButton("取消", null).show();
                        } else {
                            Intent i = new Intent(MainActivity.this, PlayerService.class);
                            i.setAction("SLEEP");
                            i.putExtra("min", vals[w] < 0 ? 0 : vals[w]);
                            i.putExtra("mode", modes[w]);
                            startService(i);
                        }
                    } catch (Throwable t) {}
                }
            }).show();
    }

    void speedDialog() {
        final String[] opts = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "3.0x"};
        final float[] vals = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f};
        new AlertDialog.Builder(this)
            .setTitle("播放倍速")
            .setItems(opts, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    Intent i = new Intent(MainActivity.this, PlayerService.class);
                    i.setAction("SPEED"); i.putExtra("v", vals[w]);
                    startService(i);
                }
            }).show();
    }

    void eqDialog() {
        final short[] range = PlayerService.eqBands();
        if (range == null) { toast("当前播放器未初始化或设备不支持均衡器"); return; }
        short bands = PlayerService.bandCount();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        final SeekBar[] seeks = new SeekBar[bands];
        for (short i = 0; i < bands; i++) {
            TextView lab = new TextView(this);
            lab.setText(PlayerService.bandFreq(i) + "  当前 " + PlayerService.bandLevel(i) / 100 + "dB");
            lab.setTextSize(11); lab.setTextColor(C(C_DIM));
            box.addView(lab);
            final short bi = i;
            SeekBar sb = new SeekBar(this);
            sb.setMax(range[1] - range[0]);
            sb.setProgress(PlayerService.bandLevel(i) - range[0]);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean f) {
                    PlayerService.setBand(bi, (short) (p + range[0]));
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
            box.addView(sb);
            seeks[i] = sb;
        }
        new AlertDialog.Builder(this)
            .setTitle("均衡器(" + bands + "段)")
            .setView(box)
            .setPositiveButton("完成", null)
            .setNeutralButton("重置", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    for (short i = 0; i < bands; i++) {
                        PlayerService.setBand(i, (short) 0);
                        seeks[i].setProgress(-range[0]);
                    }
                }
            }).show();
    }

    /** v12: ReplayGain 设置 */
    void replayGainDialog() {
        final SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        final boolean[] enabled = {sp.getBoolean("replaygain", true)};
        final boolean[] useAlbum = {sp.getBoolean("replaygain_album", false)};
        new AlertDialog.Builder(this)
            .setTitle("📊 ReplayGain 响度归一化")
            .setMultiChoiceItems(new String[]{
                "启用 ReplayGain",
                "使用专辑增益(否则用曲目增益)"
            }, enabled, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int w, boolean c) {
                    if (w == 0) enabled[0] = c;
                    else useAlbum[0] = c;
                }
            })
            .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    sp.edit().putBoolean("replaygain", enabled[0])
                        .putBoolean("replaygain_album", useAlbum[0]).apply();
                    Intent i = new Intent(MainActivity.this, PlayerService.class);
                    i.setAction("RG_TOGGLE"); i.putExtra("on", enabled[0]);
                    startService(i);
                    i.setAction("RG_ALBUM"); i.putExtra("album", useAlbum[0]);
                    startService(i);
                    toast("ReplayGain " + (enabled[0] ? "开" : "关") + " · " +
                        (useAlbum[0] ? "专辑" : "曲目"));
                }
            })
            .setNegativeButton("取消", null).show();
    }

    /** v12: 备份/恢复 */
    void backupDialog() {
        new AlertDialog.Builder(this)
            .setTitle("💾 备份/恢复")
            .setItems(new String[]{"📤 导出到 Download", "📥 导入备份", "🗑 清除所有备份"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        if (w == 0) {
                            bg(new Runnable() { public void run() {
                                try {
                                    final File f = BackupManager.exportBackup(MainActivity.this);
                                    ui(new Runnable() { public void run() {
                                        toast("已导出: " + f.getName());
                                    }});
                                } catch (Exception e) {
                                    ui(new Runnable() { public void run() {
                                        toast("导出失败: " + e.getMessage());
                                    }});
                                }
                            }});
                        } else if (w == 1) {
                            final List<File> files = BackupManager.listBackupFiles();
                            if (files.isEmpty()) { toast("Download 中无备份文件"); return; }
                            String[] names = new String[files.size()];
                            for (int i = 0; i < files.size(); i++) names[i] = files.get(i).getName();
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("选择备份文件")
                                .setItems(names, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) {
                                        try {
                                            final int n = BackupManager.importBackup(MainActivity.this, files.get(w));
                                            toast("已合并 " + n + " 条");
                                        } catch (Exception e) { toast("导入失败: " + e.getMessage()); }
                                    }
                                }).show();
                        } else {
                            final List<File> files = BackupManager.listBackupFiles();
                            int n = 0; for (File f : files) if (f.delete()) n++;
                            toast("已删除 " + n + " 个备份");
                        }
                    } catch (Throwable t) { toast("操作失败: " + t.getMessage()); }
                }
            }).show();
    }

    /** v12: 智能歌单对话框 */
    void smartPlaylistDialog() {
        final String[][] options = {
            {"流派: 流行", "genre:pop"},
            {"流派: 摇滚", "genre:rock"},
            {"流派: 古典", "genre:classical"},
            {"流派: 爵士", "genre:jazz"},
            {"流派: 电子", "genre:electronic"},
            {"流派: 嘻哈", "genre:hiphop"},
            {"流派: 民谣", "genre:folk"},
            {"心情: 放松", "mood:chill"},
            {"心情: 运动", "mood:energetic"},
            {"心情: 黑暗", "mood:dark"},
            {"心情: 浪漫", "mood:romantic"},
            {"心情: 专注", "mood:focus"},
        };
        final boolean[] checked = new boolean[options.length];
        new AlertDialog.Builder(this)
            .setTitle("🎵 智能歌单规则")
            .setMultiChoiceItems(new String[]{
                "流行", "摇滚", "古典", "爵士", "电子", "嘻哈", "民谣",
                "放松", "运动", "黑暗", "浪漫", "专注"
            }, checked, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int w, boolean c) { checked[w] = c; }
            })
            .setPositiveButton("生成", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    List<SmartPlaylist.Rule> rules = new ArrayList<SmartPlaylist.Rule>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) {
                        String[] kv = options[i][1].split(":");
                        rules.add(new SmartPlaylist.Rule(kv[0], kv[1]));
                    }
                    if (rules.isEmpty()) { toast("请至少选择一项"); return; }
                    status("生成中…");
                    SmartPlaylist.generate(MainActivity.this, rules, "智能歌单", new SmartPlaylist.Callback() {
                        public void onResult(final SmartPlaylist.SmartPlaylistResult r) {
                            runOnUiThread(new Runnable() { public void run() {
                                try {
                                    curTab = 4;
                                    ArrayList<Object[]> out = new ArrayList<Object[]>();
                                    out.add(new Object[]{r.coverEmoji + " " + r.name, r.description, "", "头"});
                                    for (SmartPlaylist.SmartTrack t : r.tracks) {
                                        out.add(new Object[]{t.title, t.artist + " · " + t.source, t.url, "曲"});
                                    }
                                    rows.clear(); rows.addAll(out);
                                    adapter.notifyDataSetChanged();
                                    status("智能歌单 · " + r.tracks.size() + " 首 · " + r.description);
                                } catch (Throwable t) { toast("显示失败"); }
                            }});
                        }
                        public void onError(final String err) {
                            runOnUiThread(new Runnable() { public void run() {
                                toast("生成失败: " + err);
                            }});
                        }
                    });
                }
            })
            .setNegativeButton("取消", null).show();
    }

    /** v12: 崩溃报告 */
    void crashReportDialog() {
        try {
            final JSONArray arr = CrashReporter.getUnreportedCrashes(this);
            if (arr.length() == 0) {
                new AlertDialog.Builder(this)
                    .setTitle("🛠 崩溃报告")
                    .setMessage("无未读崩溃\n\n系统:\n" + Build.MANUFACTURER + " " + Build.MODEL
                        + "\nAndroid " + Build.VERSION.RELEASE)
                    .setPositiveButton("知道了", null)
                    .setNegativeButton("清除记录", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            CrashReporter.clearCrashes(MainActivity.this);
                            toast("已清除");
                        }
                    }).show();
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(5, arr.length()); i++) {
                try {
                    JSONObject o = arr.getJSONObject(i);
                    sb.append("[").append(o.optString("iso_time", "")).append("]\n")
                      .append(o.optString("error_class", "")).append(": ")
                      .append(o.optString("error_message", "")).append("\n\n");
                } catch (Exception e) {}
            }
            if (arr.length() > 5) sb.append("... 还有 ").append(arr.length() - 5).append(" 条\n");
            new AlertDialog.Builder(this)
                .setTitle("🛠 崩溃报告 (" + arr.length() + " 条)")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .setNegativeButton("清除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        CrashReporter.clearCrashes(MainActivity.this);
                        toast("已清除");
                    }
                }).show();
        } catch (Throwable t) { toast("崩溃报告打开失败"); }
    }

    void statsDialog() {
        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        int plays = sp.getInt("total_plays", 0);
        int searches = sp.getInt("total_searches", 0);
        int crashes = sp.getInt("unreported_crashes", 0);
        new AlertDialog.Builder(this)
            .setTitle("统计数据")
            .setMessage("累计播放: " + plays + " 次\n"
                + "累计搜索: " + searches + " 次\n"
                + "收藏: " + loadEntries(PREF_FAV).size() + " 首\n"
                + "最近播放: " + loadEntries(PREF_REC).size() + " 条\n"
                + "搜索历史: " + getSharedPreferences("mf", MODE_PRIVATE).getStringSet("search_history", new HashSet<String>()).size() + " 条\n"
                + "未读崩溃: " + crashes + " 条\n"
                + "图片缓存: " + ImageCache.get(this).stats())
            .setPositiveButton("知道了", null).show();
    }

    void aboutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("关于 MusicFusion v12")
            .setMessage("一站式聚合音乐播放器 v12 \"Nebula\"\n\n"
                + "v12 新增 22+ 升级:\n"
                + "P0 稳定: 全链路 try-catch + JSON 崩溃上报\n"
                + "P1 播放: Gapless/Crossfade/ReplayGain/媒体会话/队列恢复/三模式循环/睡眠淡出\n"
                + "P2 检索: Audius GraphQL 挖掘/IA 高级搜索/RadioBrowser 多维筛选/Openverse 许可证\n"
                + "P3 智能: 智能歌单/多源歌词/音频可视化/备份恢复/图片缓存/搜索建议纠错\n"
                + "P4 系统: 通知 MediaStyle+4动作/动态色(API 31+)/快捷操作\n\n"
                + "音乐源 (合法开放):\n"
                + "· Audius / Internet Archive / RadioBrowser / SomaFM / Openverse\n\n"
                + "License: MIT\n体积: <200KB · 28 源文件")
            .setPositiveButton("知道了", null).show();
    }

    // ══════ 标签与加载 ══════
    void setTab(int idx) {
        try {
            curTab = idx;
            getSharedPreferences("mf", MODE_PRIVATE).edit().putInt("last_tab", idx).apply();
            for (int i = 0; i < tabsView.getChildCount(); i++) {
                View c = tabsView.getChildAt(i);
                Object tag = c.getTag();
                if (tag instanceof Integer) {
                    int t = (Integer) tag;
                    ((TextView) c).setTextColor(C(t == idx ? C_GREEN : C_DIM));
                    ((TextView) c).setTypeface(null, t == idx ? Typeface.BOLD : Typeface.NORMAL);
                }
            }
            String q = searchBox.getText().toString().trim();
            if (q.length() >= 2 && idx != 3 && idx != 4 && idx != 5 && idx != 6) { doSearch(q); return; }
            if (idx == 0) loadTrending();
            else if (idx == 1) showSearchHistory();
            else if (idx == 2) loadRadio("");
            else if (idx == 3) loadCatalog("");
            else if (idx == 4) loadPodcasts();
            else if (idx == 5) loadDownloads();
            else if (idx == 6) loadStats();
            else if (idx == 7) loadMine();
        } catch (Throwable t) { toast("标签切换失败: " + t.getMessage()); }
    }

    void showSearchHistory() {
        rows.clear();
        Set<String> h = getSharedPreferences("mf", MODE_PRIVATE)
            .getStringSet("search_history", new HashSet<String>());
        ArrayList<String> l = new ArrayList<String>(h);
        Collections.sort(l);
        for (String s : l)
            rows.add(new Object[]{"搜索: " + s, "点按重新搜索", "\u0001HIST:" + s, "H"});
        if (rows.isEmpty())
            rows.add(new Object[]{"输入关键词开始搜索", "历史记录会显示在这里", "", "H"});
        adapter.notifyDataSetChanged();
        status("搜索历史");
    }

    void doSearch(final String q) {
        try {
            status(L10n.s("searching") + q + " …");
            SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
            Set<String> h = new HashSet<String>(
                sp.getStringSet("search_history", new HashSet<String>()));
            h.add(q);
            sp.edit().putStringSet("search_history", h)
              .putInt("total_searches", sp.getInt("total_searches", 0) + 1).apply();
            // v12: 调用 SearchSuggest 添加历史
            SearchSuggest.addHistory(this, q);
            srcErrors.clear();

            bg(new Runnable() { public void run() {
                final ArrayList<Object[]> out = new ArrayList<Object[]>();
                if (curTab == 2) {
                    try {
                        for (String _l : RadioBrowser.parse(RadioBrowser.search(q))) {
                            String[] s = _l.split("\u0001");
                            out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "电台"});
                        }
                    } catch (Exception e) { addErr("RadioBrowser", e); out.add(err("电台搜索失败", e)); }
                } else if (curTab == 3) {
                    filterCatalog(q, out);
                } else if (curTab == 4) {
                    filterMine(q, out);
                } else {
                    try {
                        String[] r = Audius.parseWithIds(Audius.search(q));
                        for (String _l : r) {
                            String[] s = _l.split("\u0001");
                            out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                        }
                    } catch (Exception e) {
                        Audius.markFail();
                        try {
                            String[] r = Audius.parseWithIds(Audius.search(q));
                            for (String _l : r) {
                                String[] s = _l.split("\u0001");
                                out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                            }
                        } catch (Exception e2) { addErr("Audius", e2); out.add(err("Audius不可达", e2)); }
                    }
                    try {
                        for (String _l : Archive.parse(Archive.search(q))) {
                            String[] s = _l.split("\u0001");
                            out.add(new Object[]{s[0], s[1], "IA:" + s[2], "档案"});
                        }
                    } catch (Exception e) { addErr("Archive", e); }
                    try {
                        for (String _l : Openverse.search(q, 1)) {
                            String[] s = _l.split("\u0001");
                            out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "CC"});
                        }
                    } catch (Exception e) { addErr("Openverse", e); }
                }
                ui(new Runnable() { public void run() {
                    rows.clear(); rows.addAll(out);
                    adapter.notifyDataSetChanged();
                    status(rows.size() + L10n.s("results"));
                    renderErrorBanner();
                }});
            }});
        } catch (Throwable t) { toast("搜索失败: " + t.getMessage()); }
    }

    void addErr(String src, Exception e) {
        try {
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            if (msg.length() > 60) msg = msg.substring(0, 60) + "…";
            srcErrors.put(src, msg);
        } catch (Throwable t) {}
    }
    void renderErrorBanner() {
        try {
            if (srcErrors.isEmpty()) { errorBanner.setVisibility(View.GONE); return; }
            StringBuilder sb = new StringBuilder("⚠ ");
            boolean first = true;
            for (Map.Entry<String, String> en : srcErrors.entrySet()) {
                if (!first) sb.append(" · ");
                sb.append(en.getKey()).append(": ").append(en.getValue());
                first = false;
            }
            sb.append("  (点按关闭)");
            errorBanner.setText(sb.toString());
            errorBanner.setVisibility(View.VISIBLE);
        } catch (Throwable t) {}
    }

    void loadTrending() {
        status("加载 Audius 热门榜…");
        srcErrors.clear();
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                for (String _l : Audius.parseWithIds(Audius.trending())) {
                    String[] s = _l.split("\u0001");
                    out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                }
            } catch (Exception e) {
                Audius.markFail();
                try {
                    for (String _l : Audius.parseWithIds(Audius.trending())) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                    }
                } catch (Exception e2) { addErr("Audius", e2); out.add(err("加载失败", e2)); }
            }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("Audius 热门榜 · " + rows.size() + " 首");
                renderErrorBanner();
            }});
        }});
    }

    void loadRadio(final String q) {
        status("加载电台…");
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            out.add(new Object[]{"— 公共电台直播 —", "公共广播/社区电台直链", "", "头"});
            String[][] pub = {
                {"Radio Paradise", "美国·独立策展混合电台", "https://stream.radioparadise.com/mp3-128"},
                {"FIP", "法国·无广告综合音乐", "https://icecast.radiofrance.fr/fip-midfi.mp3"},
                {"France Musique", "法国·古典/爵士", "https://icecast.radiofrance.fr/francemusique-midfi.mp3"},
                {"WFMU Freeform", "美国·自由格式传奇电台", "https://stream0.wfmu.org/freeform-128k"},
                {"KEXP", "美国·西雅图独立音乐", "https://kexp-mp3-128.streamguys1.com/kexp128.mp3"},
                {"KCRW", "美国·洛杉矶公播", "https://kcrw.streamguys1.com/kcrw_192k_mp3_e24_internet_radio"},
            };
            for (String[] ch : pub)
                out.add(new Object[]{ch[0], ch[1], ch[2], "台"});
            out.add(new Object[]{"— SomaFM 精选频道 —", "非营利独立电台(24频道)", "", "头"});
            for (String[] ch : SomaFM.all())
                out.add(new Object[]{ch[0], ch[1], ch[2], "台"});
            out.add(new Object[]{"— RadioBrowser 热门 —", "全球社区电台", "", "头"});
            try {
                for (String _l : RadioBrowser.parse(q.isEmpty()
                        ? RadioBrowser.popular() : RadioBrowser.search(q))) {
                    String[] s = _l.split("\u0001");
                    out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "台"});
                }
            } catch (Exception e) { out.add(err("加载失败", e)); }
            // v12: RadioBrowser 标签筛选入口
            out.add(new Object[]{"🎵 按流派/语言/国家筛选电台", "v12 新增 · 长按查看", "", "头"});
            for (String tag : RadioBrowser.topTags().split(",")) {
                out.add(new Object[]{"📻 #" + tag, "点按加载此标签电台", "\u0001TAG:" + tag, "筛选"});
            }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("SomaFM + RadioBrowser · " + rows.size() + " 台");
            }});
        }});
    }

    void loadCatalog(final String q) {
        status("加载离线目录…");
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            filterCatalog(q, out);
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("离线目录 " + rows.size() + " 台");
            }});
        }});
    }

    static JSONObject catalogCache;
    boolean dataSaver() {
        return getSharedPreferences("mf", MODE_PRIVATE).getBoolean("datasaver", false);
    }
    boolean bitrateOk(String sub) {
        if (!dataSaver() || sub == null) return true;
        try {
            Matcher m = Pattern.compile("(\\d+)kbps").matcher(sub);
            return !m.find() || Integer.parseInt(m.group(1)) <= 128;
        } catch (Exception e) { return true; }
    }
    @SuppressWarnings("unchecked")
    void filterCatalog(String q, ArrayList<Object[]> out) {
        try {
            if (catalogCache != null) { filterCatalogCached(q, out); return; }
            File live = new File(getFilesDir(), "stations_live.json");
            JSONObject rootJ;
            if (live.exists()) {
                FileInputStream fi = new FileInputStream(live);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = fi.read(buf)) > 0) bo.write(buf, 0, n);
                fi.close();
                rootJ = new JSONObject(bo.toString());
            } else {
                InputStream in = getAssets().open("stations.json");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                rootJ = new JSONObject(bo.toString());
            }
            catalogCache = rootJ;
            filterCatalogCached(q, out);
        } catch (Exception e) { out.add(err("目录加载失败", e)); }
    }
    void filterCatalogCached(String q, ArrayList<Object[]> out) {
        try {
            JSONArray st = catalogCache.getJSONArray("stations");
            String low = q.toLowerCase();
            for (int i = 0; i < st.length(); i++) {
                JSONObject s = st.getJSONObject(i);
                String name = s.optString("n", "?"), cty = s.optString("c", ""),
                    tag = s.optString("t", ""), u = s.optString("u", "");
                if (!low.isEmpty() && !name.toLowerCase().contains(low)
                    && !tag.toLowerCase().contains(low) && !cty.toLowerCase().contains(low))
                    continue;
                String sub = cty + " · " + tag + " · " + s.optInt("b", 0) + "kbps";
                if (bitrateOk(sub))
                    out.add(new Object[]{name, sub, u, "台"});
            }
        } catch (Exception e) { out.add(err("目录过滤失败", e)); }
    }

    // ══════ 我的 ══════
    static final String PREF_FAV = "fav", PREF_REC = "recent", PREF_PL = "pl";

    void loadMine() {
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                out.add(new Object[]{"— 最近播放 —", "", "", "头"});
                for (String e : loadEntries(PREF_REC))
                    out.add(new Object[]{titleOf(e), subOf(e), urlOf(e), "历"});
                out.add(new Object[]{"— 我的收藏 —", "", "", "头"});
                for (String e : loadEntries(PREF_FAV))
                    out.add(new Object[]{titleOf(e), subOf(e), urlOf(e), "藏"});
                // 加载歌单
                ArrayList<String> playlists = loadEntries(PREF_PL);
                for (String pl : playlists) {
                    out.add(new Object[]{"📂 " + pl, "歌单 · 长按编辑", "\u0001PL:" + pl, "单"});
                }
                out.add(new Object[]{"— 播放排行榜 Top10 —", "按本地播放次数", "", "头"});
                for (Object[] tp : topPlayed())
                    out.add(new Object[]{tp[0] + "  (" + tp[1] + "次)",
                        "点按播放", tp[2], "榜"});
                out.add(new Object[]{"— 已下载(本机) —", "Download/MusicFusion", "", "头"});
                for (File f : downloadedFiles())
                    out.add(new Object[]{f.getName().replace(".mp3", ""),
                        (f.length() / 1048576) + "MB · 本机文件",
                        f.getAbsolutePath(), "本"});
                // v12: 智能歌单入口
                out.add(new Object[]{"🎵 生成智能歌单", "v12 新增 · 设置→智能歌单", "", "头"});
            } catch (Exception ignored) {}
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("最近 " + loadEntries(PREF_REC).size()
                    + " · 收藏 " + loadEntries(PREF_FAV).size() + " · 长按管理");
            }});
        }});
    }

    void filterMine(String q, ArrayList<Object[]> out) {
        try {
            String low = q.toLowerCase();
            for (String e : loadEntries(PREF_REC)) {
                String t = titleOf(e);
                if (t.toLowerCase().contains(low))
                    out.add(new Object[]{t, subOf(e), urlOf(e), "历"});
            }
            for (String e : loadEntries(PREF_FAV)) {
                String t = titleOf(e);
                if (t.toLowerCase().contains(low))
                    out.add(new Object[]{t, subOf(e), urlOf(e), "藏"});
            }
        } catch (Exception ignored) {}
    }

    ArrayList<Object[]> topPlayed() {
        ArrayList<Object[]> out = new ArrayList<Object[]>();
        try {
            JSONObject pc = new JSONObject(
                getSharedPreferences("mf", MODE_PRIVATE).getString("playcounts", "{}"));
            ArrayList<String> keys = new ArrayList<String>();
            Iterator<String> it = pc.keys();
            while (it.hasNext()) keys.add(it.next());
            Collections.sort(keys, new Comparator<String>() {
                public int compare(String a, String b) {
                    return pc.optInt(b) - pc.optInt(a); }});
            for (int i = 0; i < Math.min(10, keys.size()); i++) {
                String k = keys.get(i);
                out.add(new Object[]{k, String.valueOf(pc.optInt(k)), "", ""});
            }
        } catch (Exception ignored) {}
        return out;
    }
    ArrayList<File> downloadedFiles() {
        ArrayList<File> out = new ArrayList<File>();
        File dir = new File(Environment.getExternalStorageDirectory()
            .getPath() + "/Download/MusicFusion");
        File[] fs = dir.listFiles();
        if (fs != null)
            for (File f : fs)
                if (f.getName().endsWith(".mp3")) out.add(f);
        return out;
    }

    // ══════ 播客 ══════
    void loadPodcasts() {
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                // 加载已订阅播客
                final Context ctx = MainActivity.this;
                PodcastEngine.Feed[] feeds = PodcastEngine.getSubscriptions(ctx);
                if (feeds.length == 0) {
                    out.add(new Object[]{"暂无订阅", "点击右上角 + 添加播客", "", "头"});
                } else {
                    out.add(new Object[]{"— 我的订阅 —", "", "", "头"});
                    for (PodcastEngine.Feed feed : feeds) {
                        out.add(new Object[]{feed.title, feed.author + " · " + feed.link, feed.url, "POD"});
                    }
                }
                // 推荐/热门播客
                out.add(new Object[]{"— 发现播客 —", "搜索/导入 OPML", "", "头"});
                out.add(new Object[]{"➕ 添加播客 (RSS/OPML)", "输入 Feed URL 或导入 OPML 文件", "ADD_PODCAST", "POD"});
                out.add(new Object[]{"🔍 搜索播客", "在 iTunes/Listen Notes 搜索", "SEARCH_PODCAST", "POD"});
                out.add(new Object[]{"📥 导入 OPML", "从文件导入订阅列表", "IMPORT_OPML", "POD"});
            } catch (Exception ignored) {}
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("播客");
            }});
        }});
    }

    // ══════ 下载 ══════
    void loadDownloads() {
        DownloadManager.restore(this);
        DownloadManager.registerCallback(new DownloadManager.Callback() {
            public void onProgress(DownloadManager.DownloadTask task) { uiRefreshDownloads(); }
            public void onComplete(DownloadManager.DownloadTask task) { uiRefreshDownloads(); }
            public void onError(DownloadManager.DownloadTask task, String error) { uiRefreshDownloads(); }
            public void onQueueChanged() { uiRefreshDownloads(); }
        });
        uiRefreshDownloads();
    }

    void uiRefreshDownloads() {
        ui(new Runnable() { public void run() {
            try {
                java.util.List<DownloadManager.DownloadTask> tasks = DownloadManager.getAll();
                rows.clear();
                if (tasks.isEmpty()) {
                    rows.add(new Object[]{"暂无下载任务", "搜索结果长按 → 下载", "", "头"});
                } else {
                    rows.add(new Object[]{"— 下载队列 (" + tasks.size() + ") —", "进行中: " + DownloadManager.getActiveCount(), "", "头"});
                    for (DownloadManager.DownloadTask task : tasks) {
                        String statusStr = task.status == 1 ? "⬇ " :
                            task.status == 2 ? "⏸ " :
                            task.status == 3 ? "✅ " : "❌ ";
                        String prog = task.totalBytes > 0 ?
                            String.format("%.1f%%", task.downloadedBytes * 100f / task.totalBytes) : "—";
                        String sub = statusStr + task.title + " · " + prog;
                        if (task.status == 4) sub += " · " + task.errorMsg;
                        rows.add(new Object[]{task.artist, sub, task.id, "DL"});
                    }
                }
                adapter.notifyDataSetChanged();
                status("下载管理");
            } catch (Throwable t) { toast("刷新失败: " + t.getMessage()); }
        }});
    }

    // ══════ 统计 ══════
    void loadStats() {
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                final StatsEngine.StatsReport report = StatsEngine.generateReport(MainActivity.this);
                out.add(new Object[]{"📊 听歌总览", "总播放: " + report.totalPlays + " · 总时长: " + fmt(report.totalMs) + " · 歌曲数: " + report.uniqueTracks, "", "头"});
                out.add(new Object[]{"— 周听歌趋势 —", "最近 7 天", "", "头"});
                for (StatsEngine.DayStat ds : report.weekStats) {
                    out.add(new Object[]{ds.date, ds.plays + " 首 · " + fmt(ds.ms), "", "统"});
                }
                out.add(new Object[]{"— 流派分布 —", "Top 10", "", "头"});
                for (Map.Entry<String, Integer> e : report.genreTop.entrySet()) {
                    out.add(new Object[]{e.getKey(), e.getValue() + " 次", "", "统"});
                }
                out.add(new Object[]{"— 时段分布 —", "24 小时热力", "", "头"});
                for (int h = 0; h < 24; h++) {
                    int c = report.hourly[h];
                    if (c > 0) out.add(new Object[]{String.format("%02d:00", h), c + " 次", "", "统"});
                }
                out.add(new Object[]{"— 艺术家 Top 10 —", "", "", "头"});
                for (Map.Entry<String, Integer> e : report.artistTop.entrySet()) {
                    out.add(new Object[]{e.getKey(), e.getValue() + " 次", "", "统"});
                }
                out.add(new Object[]{"📤 导出统计报告", "JSON 文件分享/备份", "EXPORT_STATS", "统"});
            } catch (Exception ignored) {}
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("统计分析");
            }});
        }});
    }

    ArrayList<String> loadEntries(String key) {
        ArrayList<String> l = new ArrayList<String>();
        String raw = getSharedPreferences("mf", MODE_PRIVATE).getString(key, "");
        if (!raw.isEmpty()) for (String e : raw.split("\n")) if (!e.isEmpty()) l.add(e);
        return l;
    }
    void saveEntries(String key, ArrayList<String> l) {
        StringBuilder sb = new StringBuilder();
        int max = key.equals(PREF_FAV) ? 500 : 50;
        for (int i = 0; i < Math.min(l.size(), max); i++) sb.append(l.get(i)).append("\n");
        getSharedPreferences("mf", MODE_PRIVATE).edit()
            .putString(key, sb.toString()).apply();
    }
    void addRecent(String title, String sub, String url) {
        ArrayList<String> l = loadEntries(PREF_REC);
        String e = title + "\u0001" + sub + "\u0001" + url;
        l.remove(e); l.add(0, e);
        saveEntries(PREF_REC, l);
    }
    String titleOf(String e) { String[] p = e.split("\u0001"); return p[0]; }
    String subOf(String e) { String[] p = e.split("\u0001"); return p.length > 1 ? p[1] : ""; }
    String urlOf(String e) { String[] p = e.split("\u0001"); return p.length > 2 ? p[2] : ""; }

    // ══════ 条目菜单 ══════
    void itemMenu(final int pos) {
        if (pos >= rows.size()) return;
        try {
            final Object[] r = rows.get(pos);
            final String title = (String) r[0], sub = (String) r[1], url = (String) r[2];
            if (url == null || url.isEmpty()) return;
            // v12: 标签筛选特殊处理
            if (url.startsWith("\u0001TAG:")) {
                String tag = url.substring(5);
                loadRadioByTag(tag);
                return;
            }
            new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(new String[]{
                    L10n.s("fav"), L10n.s("add_pl"), L10n.s("download"),
                    L10n.s("share"), L10n.s("copy"), L10n.s("unfav"), L10n.s("cancel")
                }, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            if (w == 1) { addToPlaylistDialog(title, sub, url); return; }
                            if (w >= 2) w--;
                            if (w == 0) {
                                ArrayList<String> l = loadEntries(PREF_FAV);
                                String e = title + "\u0001" + sub + "\u0001" + url;
                                if (!l.contains(e)) { l.add(0, e); saveEntries(PREF_FAV, l); }
                                toast(L10n.s("fav_done"));
                            } else if (w == 1) {
                                if (url.startsWith("http")) download(url, title);
                                else toast("请先播放解析直链");
                            } else if (w == 2) {
                                if (url.startsWith("http")) {
                                    Intent s = new Intent(Intent.ACTION_SEND);
                                    s.setType("text/plain");
                                    s.putExtra(Intent.EXTRA_TEXT, title + " " + url);
                                    startActivity(Intent.createChooser(s, "分享"));
                                } else toast("请先播放解析直链");
                            } else if (w == 3) {
                                copy(url);
                            } else if (w == 4) {
                                ArrayList<String> l = loadEntries(PREF_FAV);
                                l.remove(title + "\u0001" + sub + "\u0001" + url);
                                saveEntries(PREF_FAV, l);
                                toast(L10n.s("fav_rm"));
                            }
                        } catch (Throwable t) { toast("操作失败: " + t.getMessage()); }
                    }
                }).show();
        } catch (Throwable t) { toast("菜单打开失败"); }
    }

    /** v12: 按标签加载电台 */
    void loadRadioByTag(final String tag) {
        status("加载流派电台: " + tag);
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                for (String _l : RadioBrowser.parse(RadioBrowser.byTag(tag, 30))) {
                    String[] s = _l.split("\u0001");
                    out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "台"});
                }
            } catch (Exception e) { out.add(err("加载失败", e)); }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("流派: " + tag + " · " + rows.size() + " 台");
            }});
        }});
    }

    void copy(String s) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("url", s));
            toast(L10n.s("copied"));
        } catch (Exception e) { toast("复制失败"); }
    }

    void download(final String url, final String title) {
        toast("…");
        bg(new Runnable() { public void run() {
            try {
                String safe = title.replaceAll("[\\\\/:*?\"<>|·—\\[\\]]", "_")
                    .replaceAll("^[^\\w\\u4e00-\\u9fa5]+", "") + ".mp3";
                InputStream in = new URL(url).openStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[65536]; int n; long total = 0;
                while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); total += n; }
                in.close();
                byte[] data = bo.toByteArray();
                String where;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg");
                    Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(data); os.close();
                    where = "Download/" + safe;
                } else {
                    File dir = new File(Environment.getExternalStorageDirectory()
                        .getPath() + "/Download/MusicFusion");
                    if (!dir.exists()) dir.mkdirs();
                    File out = new File(dir, safe);
                    FileOutputStream fo = new FileOutputStream(out);
                    fo.write(data); fo.close();
                    where = out.getAbsolutePath();
                }
                final String msg = L10n.s("dl_ok") + total / 1048576 + "MB: " + where;
                ui(new Runnable() { public void run() { toast(msg); }});
            } catch (Exception e) {
                final String msg = L10n.s("dl_fail") + e.getMessage();
                ui(new Runnable() { public void run() { toast(msg); }});
            }
        }});
    }

    // ══════ 播放 ══════
    void playAt(final int pos) {
        if (pos >= rows.size()) return;
        try {
            final String url = (String) rows.get(pos)[2];
            if (url == null || url.isEmpty()) { toast(L10n.s("no_url")); return; }
            if (url.startsWith("\u0001HIST:")) {
                searchBox.setText(url.substring(7));
                return;
            }
            if (url.startsWith("\u0001PL:")) {
                String name = url.substring(7);
                ArrayList<String> items = loadEntries("pl_" + name.hashCode());
                if (items.isEmpty()) { toast("歌单为空"); return; }
                String[] urls = new String[items.size()];
                String[] titles = new String[items.size()];
                for (int i = 0; i < items.size(); i++) {
                    urls[i] = urlOf(items.get(i));
                    titles[i] = titleOf(items.get(i));
                }
                Intent i2 = new Intent(this, PlayerService.class);
                i2.putExtra("urls", urls);
                i2.putExtra("titles", titles);
                i2.putExtra("index", 0);
                startService(i2);
                nowBar.setText("歌单 · " + name);
                nowBar.setTextColor(C(C_GREEN));
                toast("播放歌单「" + name + "」(" + items.size() + "首)");
                return;
            }
            if (url.startsWith("IA:")) {
                status("解析 Internet Archive 条目…");
                final Object[] fr = rows.get(pos);
                bg(new Runnable() { public void run() {
                    try {
                        String audio = Archive.firstAudio(url.substring(3));
                        if (audio != null) {
                            fr[2] = audio;
                            ui(new Runnable() { public void run() { enqueue(audio, pos); }});
                        } else toast("该条目无MP3音频");
                    } catch (Exception e) { toast("解析失败: " + e.getMessage()); }
                }});
                return;
            }
            enqueue(url, pos);
        } catch (Throwable t) { toast("播放失败: " + t.getMessage()); }
    }

    void enqueue(String url, int pos) {
        int n = rows.size();
        String[] urls = new String[n];
        String[] titles = new String[n];
        for (int i = 0; i < n; i++) {
            String u = (String) rows.get(i)[2];
            urls[i] = u == null ? "" : u;
            titles[i] = (String) rows.get(i)[0];
        }
        if (urls[pos] == null || urls[pos].isEmpty()) { toast(L10n.s("no_url")); return; }
        try {
            Intent i = new Intent(this, PlayerService.class);
            i.putExtra("urls", urls);
            i.putExtra("titles", titles);
            i.putExtra("index", pos);
            startService(i);
            playingPos = pos;
            nowBar.setText("缓冲 · " + titles[pos]);
            nowBar.setTextColor(C(C_GREEN));
            addRecent(titles[pos], rows.get(pos)[1] == null ? "" : (String) rows.get(pos)[1], urls[pos]);
            SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
            sp.edit().putInt("total_plays", sp.getInt("total_plays", 0) + 1).apply();
            try {
                JSONObject pc = new JSONObject(sp.getString("playcounts", "{}"));
                pc.put(titles[pos], pc.optInt(titles[pos]) + 1);
                sp.edit().putString("playcounts", pc.toString()).apply();
            } catch (Exception ignored) {}
            hideKb();
            adapter.notifyDataSetChanged();
        } catch (Throwable t) { toast("入队失败: " + t.getMessage()); }
    }

    void send(String action) {
        try {
            Intent i = new Intent(this, PlayerService.class);
            i.setAction(action);
            startService(i);
        } catch (Throwable t) {}
    }

    static void onPlayState(final String title, final String state) {
        if (inst == null) return;
        inst.runOnUiThread(new Runnable() { public void run() {
            try {
                if (inst.nowBar != null) {
                    inst.nowBar.setText(state + " · " + title);
                    inst.nowBar.setTextColor(C(state.startsWith("播放") ? C_GREEN
                        : state.startsWith("✗") ? C_ERR : C_DIM));
                }
                if (inst.miniBar != null) {
                    if (state.startsWith("✗") || title == null || title.isEmpty()
                        || title.startsWith("选择")) {
                        // 不显示
                    } else {
                        inst.miniBar.setVisibility(View.VISIBLE);
                        if (inst.miniTitle != null) inst.miniTitle.setText(title);
                        if (inst.miniState != null) inst.miniState.setText(state);
                    }
                }
            } catch (Exception e) { /* 静默 */ }
        }});
    }
    static void onProgress(final int pos, final int dur) {
        if (inst == null) return;
        inst.runOnUiThread(new Runnable() { public void run() {
            try {
                if (!inst.seeking && dur > 0) {
                    if (inst.seek != null) {
                        inst.seek.setMax(dur);
                        inst.seek.setProgress(pos);
                        inst.seek.postDelayed(new Runnable() { public void run() {
                            if (inst.seek != null) inst.seek.setVisibility(View.GONE);
                        }}, 50);
                    }
                    if (inst.timeLabel != null)
                        inst.timeLabel.setText(fmt(pos) + " / " + fmt(dur));
                } else if (dur == 0) {
                    if (inst.timeLabel != null) inst.timeLabel.setText("直播流");
                }
            } catch (Exception e) {}
        }});
    }
    static void onStreamMeta(final String meta) {
        if (inst == null || inst.miniStream == null) return;
        inst.runOnUiThread(new Runnable() { public void run() {
            try {
                if (meta == null || meta.isEmpty()) inst.miniStream.setText("");
                else inst.miniStream.setText("♪ " + meta);
            } catch (Exception e) {}
        }});
    }
    static String fmt(int ms) {
        int s = ms / 1000;
        return s / 60 + ":" + String.format(Locale.US, "%02d", s % 60);
    }
    static String fmt(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + String.format(Locale.US, "%02d", s % 60);
    }

    // ══════ 适配器 ══════
    class RowAdapter extends BaseAdapter {
        public int getCount() { return rows.size(); }
        public Object getItem(int i) { return rows.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View cv, ViewGroup vg) {
            LinearLayout l;
            if (cv instanceof LinearLayout) {
                l = (LinearLayout) cv;
                l.removeAllViews();
            } else {
                l = new LinearLayout(MainActivity.this);
                l.setOrientation(LinearLayout.VERTICAL);
            }
            l.setPadding(dp(12), dp(8), dp(12), dp(8));
            if (pos == playingPos) l.setBackgroundColor(C("#132a1c"));
            Object[] r = rows.get(pos);
            boolean header = "头".equals(r[3]);
            TextView t1 = new TextView(MainActivity.this);
            String srcTag = (String) r[3];
            String prefix = "曲".equals(srcTag) ? "[Audius] " :
                "CC".equals(srcTag) ? "[CC] " : "档案".equals(srcTag) ? "[档案] " :
                "现场".equals(srcTag) ? "[现场] " : "台".equals(srcTag) ? "[电台] " :
                "筛选".equals(srcTag) ? "[流派] " : "";
            t1.setText(prefix + r[0]);
            t1.setTextSize(header ? 12 : 13);
            t1.setTextColor(C(header ? C_DIM : (pos == playingPos ? C_GREEN : C_TXT)));
            t1.setTypeface(null, header ? Typeface.NORMAL : Typeface.BOLD);
            l.addView(t1);
            String sub = (String) r[1];
            if (sub != null && !sub.isEmpty() && !header) {
                TextView t2 = new TextView(MainActivity.this);
                t2.setText(sub); t2.setTextSize(11); t2.setTextColor(C(C_DIM));
                l.addView(t2);
            }
            return l;
        }
    }

    // ══════ 工具 ══════
    Object[] err(String msg, Exception e) {
        return new Object[]{"[加载失败] " + msg,
            e.getMessage() != null ? e.getMessage() : "未知错误", "", "头"};
    }
    void status(final String s) { ui(new Runnable() { public void run() {
        try { status.setText(s); } catch (Throwable t) {}
    }}); }
    void ui(final Runnable r) { runOnUiThread(r); }
    void bg(final Runnable r) { new Thread(r).start(); }
    void toast(String s) { try { ui(new Runnable() { public void run() {
        Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
    }}); } catch (Throwable t) {} }
    void hideKb() {
        try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(searchBox.getWindowToken(), 0); } catch (Exception e) {}
    }
    LinearLayout.LayoutParams w(float weight) {
        return new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }
    int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
    @Override protected void onDestroy() {
        if (inst == this) inst = null;
        super.onDestroy();
    }

    // ══════ v13: 新增对话框 ══════

    /** 统计图表对话框 */
    void showStatsCharts() {
        new AlertDialog.Builder(this)
            .setTitle("📈 统计图表")
            .setItems(new String[]{
                "📊 本周趋势图",
                "📊 本月趋势图",
                "📊 本年趋势图",
                "🥧 流派饼图",
                "🕐 时段热力图",
                "👤 艺术家排行",
                "📤 导出完整报告 (JSON)"
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        if (w <= 2) {
                            String period = w == 0 ? "week" : w == 1 ? "month" : "year";
                            StatsEngine.StatsReport report = StatsEngine.generateReport(MainActivity.this);
                            // 显示 ASCII 图表或跳转到可视化
                            showAsciiChart(report, period);
                        } else if (w == 3) {
                            StatsEngine.StatsReport report = StatsEngine.generateReport(MainActivity.this);
                            showGenrePie(report);
                        } else if (w == 4) {
                            StatsEngine.StatsReport report = StatsEngine.generateReport(MainActivity.this);
                            showHourlyHeatmap(report);
                        } else if (w == 5) {
                            StatsEngine.StatsReport report = StatsEngine.generateReport(MainActivity.this);
                            showArtistRanking(report);
                        } else if (w == 6) {
                            exportFullStats();
                        }
                    } catch (Throwable t) { toast("失败: " + t.getMessage()); }
                }
            }).show();
    }

    private void showAsciiChart(StatsEngine.StatsReport report, String period) {
        // 简化: 显示文本统计
        StringBuilder sb = new StringBuilder();
        sb.append(period.equals("week") ? "周" : period.equals("month") ? "月" : "年").append("趋势:\n");
        for (StatsEngine.DayStat ds : period.equals("week") ? report.weekStats :
            period.equals("month") ? report.monthStats : report.yearStats) {
            int barLen = Math.min(30, ds.plays);
            String bar = new String(new char[barLen]).replace("\0", "█");
            sb.append(ds.date).append(" ").append(bar).append(" ").append(ds.plays).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("趋势图").setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    private void showGenrePie(StatsEngine.StatsReport report) {
        StringBuilder sb = new StringBuilder("流派分布:\n");
        int total = 0; for (int v : report.genreTop.values()) total += v;
        for (Map.Entry<String, Integer> e : report.genreTop.entrySet()) {
            int pct = total > 0 ? e.getValue() * 100 / total : 0;
            int bar = pct / 3;
            sb.append(e.getKey()).append(": ").append(new String(new char[bar]).replace("\0", "█"))
              .append(" ").append(pct).append("%\n");
        }
        new AlertDialog.Builder(this).setTitle("流派分布").setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    private void showHourlyHeatmap(StatsEngine.StatsReport report) {
        StringBuilder sb = new StringBuilder("24h 热力:\n");
        int max = 0; for (int v : report.hourly) if (v > max) max = v;
        for (int h = 0; h < 24; h++) {
            int bar = max > 0 ? report.hourly[h] * 20 / max : 0;
            sb.append(String.format("%02d:00 ", h)).append(new String(new char[bar]).replace("\0", "█"))
              .append(" ").append(report.hourly[h]).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("时段热力图").setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    private void showArtistRanking(StatsEngine.StatsReport report) {
        StringBuilder sb = new StringBuilder("艺术家 Top 20:\n");
        int i = 0;
        for (Map.Entry<String, Integer> e : report.artistTop.entrySet()) {
            if (i++ >= 20) break;
            sb.append(i).append(". ").append(e.getKey()).append(" - ").append(e.getValue()).append(" 次\n");
        }
        new AlertDialog.Builder(this).setTitle("艺术家排行").setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    private void exportFullStats() {
        bg(new Runnable() { public void run() {
            try {
                String json = StatsEngine.exportReport(MainActivity.this);
                File file = new File(getFilesDir(), "musicfusion_stats_" + System.currentTimeMillis() + ".json");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(json.getBytes("UTF-8")); fos.close();
                final File f = file;
                ui(new Runnable() { public void run() {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/json");
                    share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                    startActivity(Intent.createChooser(share, "分享统计报告"));
                }});
            } catch (Exception e) {
                ui(new Runnable() { public void run() { toast("导出失败: " + e.getMessage()); }});
            }
        }});
    }

    /** 10段图形均衡器对话框 */
    void showGraphicEqDialog() {
        final GraphicEqualizer eqView = new GraphicEqualizer(this);
        eqView.setOnGainChangeListener(new GraphicEqualizer.OnGainChangeListener() {
            public void onGainChange(int band, float gain) {}
            public void onGainChangeFinish(int[] bands, float[] gains) {
                // 保存用户预设
            }
        });
        // 加载当前预设
        String currentPreset = getSharedPreferences("mf", MODE_PRIVATE).getString("eq_preset", "flat");
        eqView.loadPreset(this, currentPreset);

        new AlertDialog.Builder(this)
            .setTitle("🎛️ 10段图形均衡器")
            .setView(eqView)
            .setPositiveButton("保存为预设", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    final EditText et = new EditText(MainActivity.this);
                    et.setHint("预设名称");
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("保存预设")
                        .setView(et)
                        .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dd, int ww) {
                                String name = et.getText().toString().trim();
                                if (!name.isEmpty()) {
                                    eqView.savePreset(MainActivity.this, name);
                                    getSharedPreferences("mf", MODE_PRIVATE).edit().putString("eq_preset", name).apply();
                                    toast("已保存: " + name);
                                }
                            }
                        }).setNegativeButton("取消", null).show();
                }
            })
            .setNeutralButton("预设列表", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String[] presets = GraphicEqualizer.listPresets(MainActivity.this);
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("选择预设")
                        .setItems(presets, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dd, int ww) {
                                eqView.loadPreset(MainActivity.this, presets[ww]);
                                getSharedPreferences("mf", MODE_PRIVATE).edit().putString("eq_preset", presets[ww]).apply();
                                toast("已应用: " + presets[ww]);
                            }
                        }).show();
                }
            })
            .setNegativeButton("关闭", null).show();
    }

    /** 播客设置 */
    void podcastSettingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🎧 播客管理")
            .setItems(new String[]{
                "➕ 添加 RSS Feed",
                "📥 导入 OPML",
                "📤 导出 OPML",
                "🔄 刷新所有订阅",
                "⚙️ 自动更新间隔",
                "🗑 清除已播放记录",
                "📱 下载设置 (仅WiFi/画质)"
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        if (w == 0) addPodcastFeedDialog();
                        else if (w == 1) importOpmlDialog();
                        else if (w == 2) exportOpmlDialog();
                        else if (w == 3) { PodcastEngine.refreshAll(MainActivity.this, new Runnable() { public void run() { toast("刷新完成"); }}); }
                        else if (w == 4) autoUpdateIntervalDialog();
                        else if (w == 5) { PodcastEngine.clearPlayed(MainActivity.this); toast("已清除"); }
                        else if (w == 6) downloadSettingsDialog();
                    } catch (Throwable t) { toast("失败: " + t.getMessage()); }
                }
            }).show();
    }

    private void addPodcastFeedDialog() {
        final EditText et = new EditText(this);
        et.setHint("RSS Feed URL (如 https://example.com/feed.xml)");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
            .setTitle("添加播客 Feed")
            .setView(et)
            .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String url = et.getText().toString().trim();
                    if (!url.isEmpty()) {
                        PodcastEngine.addFeed(MainActivity.this, url, new PodcastEngine.ParseCallback() {
                            public void onFeedParsed(final PodcastEngine.Feed feed, final java.util.List<PodcastEngine.Episode> episodes) {
                                ui(new Runnable() { public void run() {
                                    if (feed != null) toast("添加成功: " + feed.title);
                                    else toast("解析失败: 无 Feed");
                                }});
                            }
                            public void onError(final String err) {
                                ui(new Runnable() { public void run() { toast("解析失败: " + err); }});
                            }
                        });
                        toast("已添加, 正在解析...");
                        setTab(4); // 切到播客标签
                    }
                }
            }).setNegativeButton("取消", null).show();
    }

    private void importOpmlDialog() {
        // 简化: 提示用户从文件选择
        toast("请使用文件管理器选择 OPML 文件, 然后分享给 MusicFusion");
    }

    private void exportOpmlDialog() {
        bg(new Runnable() { public void run() {
            try {
                String opml = PodcastEngine.exportOpml(MainActivity.this);
                File file = new File(getFilesDir(), "podcasts.opml");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(opml.getBytes("UTF-8")); fos.close();
                final File f = file;
                ui(new Runnable() { public void run() {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/xml");
                    share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                    startActivity(Intent.createChooser(share, "分享 OPML"));
                }});
            } catch (Exception e) {
                ui(new Runnable() { public void run() { toast("导出失败: " + e.getMessage()); }});
            }
        }});
    }

    private void autoUpdateIntervalDialog() {
        final String[] opts = {"关闭", "15分钟", "30分钟", "1小时", "6小时", "12小时", "每日"};
        final int[] mins = {0, 15, 30, 60, 360, 720, 1440};
        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        int current = sp.getInt("podcast_auto_update", 60);
        int checked = 3;
        for (int i = 0; i < mins.length; i++) if (mins[i] == current) { checked = i; break; }
        new AlertDialog.Builder(this)
            .setTitle("自动更新间隔")
            .setSingleChoiceItems(opts, checked, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    getSharedPreferences("mf", MODE_PRIVATE).edit().putInt("podcast_auto_update", mins[w]).apply();
                    toast("已设置: " + opts[w]);
                    d.dismiss();
                }
            }).show();
    }

    /** 下载设置 */
    void downloadSettingsDialog() {
        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        final boolean[] onlyWifi = {sp.getBoolean("download_wifi_only", true)};
        final String[] quality = {"最高可用", "320kbps", "256kbps", "192kbps", "128kbps"};
        int qi = sp.getInt("download_quality", 0);
        new AlertDialog.Builder(this)
            .setTitle("⬇️ 下载设置")
            .setMultiChoiceItems(new String[]{"仅 WiFi 下载"}, onlyWifi, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int w, boolean c) { onlyWifi[0] = c; }
            })
            .setSingleChoiceItems(quality, qi, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    getSharedPreferences("mf", MODE_PRIVATE).edit().putInt("download_quality", w).apply();
                    d.dismiss();
                }
            })
            .setPositiveButton("完成", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    getSharedPreferences("mf", MODE_PRIVATE).edit().putBoolean("download_wifi_only", onlyWifi[0]).apply();
                }
            }).show();
    }

    /** 主题编辑器 */
    void themeEditorDialog() {
        final String[] builtins = {"默认 (Nebula)", "AMOLED 纯黑", "高对比", "护眼暖色", "浅色"};
        final String[] builtinKeys = {ThemeManager.THEME_DEFAULT, ThemeManager.THEME_AMOLED,
            ThemeManager.THEME_HIGH_CONTRAST, ThemeManager.THEME_EYE_CARE, ThemeManager.THEME_LIGHT};
        final String current = ThemeManager.getCurrentTheme(this);
        int checked = 0;
        for (int i = 0; i < builtinKeys.length; i++) if (builtinKeys[i].equals(current)) { checked = i; break; }
        final int checkedFinal = checked;

        new AlertDialog.Builder(this)
            .setTitle("🎨 主题编辑器")
            .setItems(new String[]{
                "🎨 选择内置主题",
                "✏️ 自定义色板",
                "💾 导出主题",
                "📥 导入主题",
                "🗑 删除自定义主题"
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("内置主题")
                            .setSingleChoiceItems(builtins, checkedFinal, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dd, int ww) {
                                    ThemeManager.setTheme(MainActivity.this, builtinKeys[ww]);
                                    if (ThemeManager.THEME_CUSTOM.equals(builtinKeys[ww])) {
                                        // 用户自定义主题
                                    }
                                    toast("已切换: " + builtins[ww]);
                                    recreate();
                                    dd.dismiss();
                                }
                            }).show();
                    } else if (w == 1) customColorDialog();
                    else if (w == 2) {
                        String json = ThemeManager.exportTheme(MainActivity.this, current);
                        if (json != null) {
                            File f = new File(getFilesDir(), "theme_" + current + ".json");
                            try {
                                FileOutputStream fos = new FileOutputStream(f);
                                fos.write(json.getBytes("UTF-8")); fos.close();
                                Intent share = new Intent(Intent.ACTION_SEND);
                                share.setType("application/json");
                                share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                                startActivity(Intent.createChooser(share, "分享主题"));
                            } catch (Exception e) { toast("导出失败: " + e.getMessage()); }
                        }
                    } else if (w == 3) {
                        toast("请用文件管理器选择 JSON 并分享给 MusicFusion");
                    } else if (w == 4) {
                        List<String> customs = ThemeManager.listThemes(MainActivity.this);
                        customs.removeAll(Arrays.asList(builtinKeys));
                        if (customs.isEmpty()) { toast("无自定义主题"); return; }
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("删除自定义主题")
                            .setItems(customs.toArray(new String[0]), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dd, int ww) {
                                    ThemeManager.deleteCustomTheme(MainActivity.this, customs.get(ww));
                                    toast("已删除: " + customs.get(ww));
                                }
                            }).show();
                    }
                }
            }).show();
    }

    private void customColorDialog() {
        final String[] colorKeys = {ThemeManager.C_BG, ThemeManager.C_CARD, ThemeManager.C_LINE,
            ThemeManager.C_TXT, ThemeManager.C_DIM, ThemeManager.C_PRI, ThemeManager.C_SEC,
            ThemeManager.C_GRN, ThemeManager.C_ERR, ThemeManager.C_WRN, ThemeManager.C_INF};
        final String[] colorLabels = {"背景", "卡片", "分割线", "主文字", "次要文字",
            "主强调", "次强调", "成功", "错误", "警告", "信息"};
        final Map<String, Integer> colors = new HashMap<>(ThemeManager.getThemeColors(this, ThemeManager.THEME_CUSTOM));

        new AlertDialog.Builder(this)
            .setTitle("自定义色板")
            .setItems(colorLabels, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    pickColorForKey(colorKeys[w], colorLabels[w], colors);
                }
            })
            .setPositiveButton("保存并应用", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String name = "custom_" + System.currentTimeMillis();
                    if (ThemeManager.saveCustomTheme(MainActivity.this, name, colors)) {
                        ThemeManager.setActiveCustomTheme(MainActivity.this, name);
                        toast("已保存并应用: " + name);
                        recreate();
                    } else {
                        toast("保存失败");
                    }
                }
            })
            .setNegativeButton("取消", null).show();
    }

    private void pickColorForKey(final String key, final String label, final Map<String, Integer> colors) {
        final int[] pickerColor = {colors.get(key)};
        // 简化: 用预设色板
        final String[] presets = {"#0B0E14", "#000000", "#161C28", "#1A1610", "#F6F8FA",
            "#1DB954", "#58A6FF", "#E8A838", "#F85149", "#D29922", "#0969DA", "#FFFFFF"};
        new AlertDialog.Builder(this)
            .setTitle(label + " 颜色")
            .setItems(presets, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    colors.put(key, Color.parseColor(presets[w]));
                    toast(label + " → " + presets[w]);
                }
            }).show();
    }

    /** Last.fm Scrobble 设置 */
    void lastFmDialog() {
        final SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        final boolean enabled = sp.getBoolean("lastfm_enabled", false);
        final String sessionKey = sp.getString("lastfm_session", "");

        if (!enabled || TextUtils.isEmpty(sessionKey)) {
            new AlertDialog.Builder(this)
                .setTitle("🌐 Last.fm Scrobble")
                .setMessage("启用后自动上报播放记录到 Last.fm\n\n无需 API Key, 使用官方授权流程")
                .setPositiveButton("授权并启用", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        LastFmScrobbler.getAuthUrl(new LastFmScrobbler.AuthCallback() {
                            public void onAuthUrl(String url) {
                                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                startActivity(i);
                            }
                            public void onAuthorized(String user) {
                                toast("授权完成: " + user);
                            }
                            public void onError(String err) { toast("失败: " + err); }
                        });
                    }
                })
                .setNegativeButton("取消", null).show();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("🌐 Last.fm Scrobble (已启用)")
                .setItems(new String[]{"禁用 Scrobble", "重新授权", "查看会话信息"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            sp.edit().putBoolean("lastfm_enabled", false).apply();
                            LastFmScrobbler.init(MainActivity.this);
                            toast("已禁用 Scrobble");
                        } else if (w == 1) {
                            LastFmScrobbler.getAuthUrl(new LastFmScrobbler.AuthCallback() {
                                public void onAuthUrl(String url) {
                                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                    startActivity(i);
                                }
                                public void onAuthorized(String user) {
                                    toast("授权完成: " + user);
                                }
                                public void onError(String err) { toast("失败: " + err); }
                            });
                        } else if (w == 2) {
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("会话信息")
                                .setMessage("Session Key: " + sessionKey.substring(0, 8) + "...")
                                .setPositiveButton("OK", null).show();
                        }
                    }
                }).show();
        }
    }

    /** MusicBrainz 元数据补全 */
    void musicBrainzDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🏷️ MusicBrainz 元数据补全")
            .setItems(new String[]{
                "🔍 当前播放曲目补全",
                "📁 批量补全本地库",
                "⚙️ 设置: 自动补全阈值",
                "📊 查看补全统计"
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) {
                        // 获取当前播放信息
                        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
                        String title = sp.getString("widget_title", "");
                        String artist = sp.getString("widget_artist", "");
                        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(artist)) {
                            toast("无正在播放的曲目");
                            return;
                        }
                        MusicBrainz.completeMetadata(MainActivity.this, title, artist, "",
                            new MusicBrainz.LookupCallback() {
                                public void onResult(final MusicBrainz.RecordingInfo track) {
                                    ui(new Runnable() { public void run() {
                                        String msg = "标题: " + track.title + "\n艺术家: " + track.artist +
                                            "\n专辑: " + track.album + "\n年份: " + track.year +
                                            "\n流派: " + (TextUtils.isEmpty(track.genre) ? "无" : track.genre) +
                                            "\nMBID: " + track.mbid;
                                        new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("MusicBrainz 补全结果")
                                            .setMessage(msg)
                                            .setPositiveButton("OK", null).show();
                                    }});
                                }
                                public void onError(final String err) {
                                    ui(new Runnable() { public void run() {
                                        toast("补全失败: " + err);
                                    }});
                                }
                            });
                    } else if (w == 1) {
                        toast("批量补全功能开发中...");
                    } else if (w == 2) {
                        final String[] thresholds = {"高 (0.9)", "中 (0.7)", "低 (0.5)"};
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("自动补全置信度阈值")
                            .setSingleChoiceItems(thresholds, 1, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dd, int ww) {
                                    getSharedPreferences("mf", MODE_PRIVATE).edit()
                                        .putFloat("mb_threshold", ww == 0 ? 0.9f : ww == 1 ? 0.7f : 0.5f).apply();
                                    toast("已设置: " + thresholds[ww]);
                                    dd.dismiss();
                                }
                            }).show();
                    } else if (w == 3) {
                        toast("统计功能开发中...");
                    }
                }
            }).show();
    }

    /** 搜索筛选 */
    void searchFiltersDialog() {
        final SearchFilters.FilterState state = SearchFilters.getCurrentFilters(this);
        final String[] sourceLabels = {"全部", "Audius", "Internet Archive", "RadioBrowser", "SomaFM", "Openverse", "Jamendo"};
        final String[] qualityLabels = {"全部", "320kbps+", "256kbps+", "192kbps+", "128kbps+", "任意"};
        final String[] genreLabels = {"全部", "Pop", "Rock", "Electronic", "Hip Hop", "Classical", "Jazz", "Folk", "Country", "Ambient"};
        final String[] yearLabels = {"全部", "2020s", "2010s", "2000s", "1990s", "1980s", "更早"};

        new AlertDialog.Builder(this)
            .setTitle("🔍 搜索筛选")
            .setMultiChoiceItems(sourceLabels, state.sources, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int w, boolean c) { state.sources[w] = c; }
            })
            .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    SearchFilters.save(MainActivity.this, state);
                    toast("筛选已保存");
                }
            })
            .setNeutralButton("重置", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    SearchFilters.resetFilters(MainActivity.this);
                    toast("已重置");
                }
            })
            .setNegativeButton("取消", null).show();
    }

    /** 小组件/快捷方式 */
    void widgetShortcutsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("📱 小组件与快捷方式")
            .setItems(new String[]{
                "📋 查看已添加小组件",
                "⚙️ 小组件设置 (尺寸/显示内容)",
                "⚡ 快捷设置磁贴 (下拉通知栏编辑)",
                "📌 App Shortcuts (长按图标)",
                "🔄 刷新所有小组件"
            }, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) toast("请在桌面长按 → 小组件 → MusicFusion");
                    else if (w == 1) widgetConfigDialog();
                    else if (w == 2) toast("下拉通知栏 → 编辑 → 找到 MusicFusion 添加");
                    else if (w == 3) toast("长按应用图标查看快捷方式");
                    else if (w == 4) {
                        Widget2x.updateFromService(MainActivity.this,
                            getSharedPreferences("mf", MODE_PRIVATE).getString("widget_title", ""),
                            getSharedPreferences("mf", MODE_PRIVATE).getString("widget_artist", ""),
                            getSharedPreferences("mf", MODE_PRIVATE).getInt("widget_progress", 0),
                            getSharedPreferences("mf", MODE_PRIVATE).getInt("widget_duration", 0),
                            getSharedPreferences("mf", MODE_PRIVATE).getBoolean("widget_playing", false),
                            getSharedPreferences("mf", MODE_PRIVATE).getString("widget_cover", ""),
                            getSharedPreferences("mf", MODE_PRIVATE).getString("widget_lyric", "")
                        );
                        toast("已刷新");
                    }
                }
            }).show();
    }

    private void widgetConfigDialog() {
        new AlertDialog.Builder(this)
            .setTitle("小组件设置")
            .setItems(new String[]{"显示歌词行", "显示进度条", "紧凑模式 (2x1)", "标准模式 (4x1)", "大卡模式 (4x2)"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
                    if (w == 0) sp.edit().putBoolean("widget_show_lyric", !sp.getBoolean("widget_show_lyric", true)).apply();
                    else if (w == 1) sp.edit().putBoolean("widget_show_progress", !sp.getBoolean("widget_show_progress", true)).apply();
                    else if (w >= 2) sp.edit().putString("widget_size", w == 2 ? "2x1" : w == 3 ? "4x1" : "4x2").apply();
                    toast("已保存, 请重新添加小组件生效");
                }
            }).show();
    }

    private void podcastEpisodeDialog(String title, String feedUrl) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(new String[]{"播放最新一集", "查看所有剧集", "取消订阅"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) {
                        // 播放最新
                        PodcastEngine.getLatestEpisode(MainActivity.this, feedUrl, new PodcastEngine.EpisodeCallback() {
                            public void onEpisodesLoaded(final java.util.List<PodcastEngine.Episode> eps) {
                                ui(new Runnable() { public void run() {
                                    if (!eps.isEmpty() && eps.get(0).audioUrl != null) {
                                        playUrl(eps.get(0).audioUrl, eps.get(0).title, eps.get(0).podcastTitle);
                                    } else {
                                        toast("无可播放链接");
                                    }
                                }});
                            }
                            public void onError(String err) { ui(new Runnable() { public void run() { toast("获取失败: " + err); }}); }
                        });
                    } else if (w == 1) {
                        toast("剧集列表功能开发中...");
                    } else if (w == 2) {
                        PodcastEngine.removeFeed(MainActivity.this, feedUrl);
                        toast("已取消订阅");
                        setTab(4);
                    }
                }
            }).show();
    }

    private void playLocalFile(String path, String title, String artist) {
        Intent i = new Intent(this, PlayerService.class);
        i.setAction("PLAY_FILE");
        i.putExtra("path", path);
        i.putExtra("title", title);
        i.putExtra("artist", artist);
        startService(i);
    }

    private void openFile(String path) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(new File(path)), "audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(intent); } catch (Exception e) { toast("无法打开"); }
    }

    /** 直接播放单个 URL (用于播客/外部源) */
    void playUrl(String url, String title, String artist) {
        try {
            Intent i = new Intent(this, PlayerService.class);
            i.putExtra("urls", new String[]{url});
            i.putExtra("titles", new String[]{title});
            i.putExtra("index", 0);
            startService(i);
            playingPos = 0;
            nowBar.setText("缓冲 · " + title);
            nowBar.setTextColor(C(C_GREEN));
            addRecent(title, artist, url);
            SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
            sp.edit().putInt("total_plays", sp.getInt("total_plays", 0) + 1).apply();
            hideKb();
        } catch (Throwable t) { toast("播放失败: " + t.getMessage()); }
    }

    // ══════ 处理搜索标签点击 ══════
    // ... 现有 playAt 等方法保持不变

    // ══════ 新增辅助方法 ══════
    void switchTab(int idx) { setTab(idx); }
    void focusSearch() { searchBox.requestFocus(); showKb(); }
    void showKb() {
        try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT); } catch (Exception e) {}
    }
    void showQueueDialog() {
        new AlertDialog.Builder(this)
            .setTitle("播放队列")
            .setMessage("当前队列: " + rows.size() + " 首\n正在播放: " + (playingPos >= 0 && playingPos < rows.size() ? (String) rows.get(playingPos)[0] : "无"))
            .setPositiveButton("关闭", null).show();
    }

    void addToPlaylistDialog(final String title, final String sub, final String url) {
        final ArrayList<String> playlists = loadEntries(PREF_PL);
        final String[] names = playlists.toArray(new String[0]);
        final boolean[] checked = new boolean[names.length];
        new AlertDialog.Builder(this)
            .setTitle("加入歌单: " + title)
            .setMultiChoiceItems(names.length > 0 ? names : new String[]{"暂无歌单"}, checked, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int w, boolean c) { checked[w] = c; }
            })
            .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    for (int i = 0; i < checked.length; i++) if (checked[i]) {
                        String pl = names[i];
                        ArrayList<String> plItems = loadEntries(PREF_PL + "_" + pl);
                        String e = title + "\u0001" + sub + "\u0001" + url;
                        if (!plItems.contains(e)) { plItems.add(0, e); saveEntries(PREF_PL + "_" + pl, plItems); }
                    }
                    toast("已添加到选中歌单");
                }
            })
            .setNegativeButton("取消", null).show();
    }
}