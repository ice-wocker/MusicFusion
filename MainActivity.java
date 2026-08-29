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

    static String[] TABS = {"首页", "搜索", "电台", "目录", "我的"};
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


    // ══════ v12: 歌词抽屉 (多源 + 同步) ══════

    // ══════ v12: 可视化对话框 ══════

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

    // ══════ v12: 设置面板 12+ 项 ══════
    void settingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("设置 v12")
            .setItems(new String[]{
                L10n.s("sleep"), L10n.s("speed"), L10n.s("equalizer"),
                L10n.s("preset"), L10n.s("noise"),
                L10n.s("stats"), L10n.s("theme"), L10n.s("datasaver"),
                L10n.s("refresh_cat"),
                L10n.s("clear_hist"), L10n.s("lang"),
                "🎨 可视化", "📊 ReplayGain", "💾 备份/恢复",
                "🎵 智能歌单", "🛠 崩溃报告", L10n.s("about")
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
                        else aboutDialog();
                    } catch (Throwable t) { toast("操作失败: " + t.getMessage()); }
                }
            }).show();
    }





    /** v12: ReplayGain 设置 */

    /** v12: 备份/恢复 */

    /** v12: 智能歌单对话框 */

    /** v12: 崩溃报告 */



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
            if (q.length() >= 2 && idx != 3 && idx != 4) { doSearch(q); return; }
            if (idx == 0) loadTrending();
            else if (idx == 1) showSearchHistory();
            else if (idx == 2) loadRadio("");
            else if (idx == 3) loadCatalog("");
            else if (idx == 4) loadMine();
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
    static final String PREF_FAV = "fav", PREF_REC = "recent";

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
                loadPlaylists(out);
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
                            if (w == 1) { playlistPickDialog(title, sub, url); return; }
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

    // ══════ v12: 歌单系统 (精简) ══════
    void playlistPickDialog(final String title, final String sub, final String url) {
        ArrayList<String> names = playlistNames();
        final ArrayList<String> opts = new ArrayList<String>(names);
        opts.add("+ 新建歌单");
        new AlertDialog.Builder(this)
            .setTitle("加入歌单")
            .setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        String e = title + "\u0001" + sub + "\u0001" + url;
                        if (w == names.size()) {
                            final EditText et = new EditText(MainActivity.this);
                            et.setHint("歌单名称");
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("新建歌单")
                                .setView(et)
                                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dd, int ww) {
                                        try {
                                            String name = et.getText().toString().trim();
                                            if (name.isEmpty()) return;
                                            addToPlaylist(name, e);
                                            toast("已创建并加入「" + name + "」");
                                        } catch (Throwable t) {}
                                    }
                                }).setNegativeButton("取消", null).show();
                        } else {
                            addToPlaylist(names.get(w), e);
                            toast("已加入「" + names.get(w) + "」");
                        }
                    } catch (Throwable t) {}
                }
            }).show();
    }
    ArrayList<String> playlistNames() {
        ArrayList<String> l = new ArrayList<String>();
        String raw = getSharedPreferences("mf", MODE_PRIVATE).getString("playlists", "");
        if (!raw.isEmpty()) for (String n : raw.split("\n")) if (!n.isEmpty()) l.add(n);
        return l;
    }
    void addToPlaylist(String name, String entry) {
        SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        ArrayList<String> names = playlistNames();
        if (!names.contains(name)) { names.add(name);
            StringBuilder nb = new StringBuilder();
            for (String n : names) nb.append(n).append("\n");
            sp.edit().putString("playlists", nb.toString()).apply();
        }
        String key = "pl_" + name.hashCode();
        ArrayList<String> items = loadEntries(key);
        if (!items.contains(entry)) { items.add(entry); saveEntries(key, items); }
    }
    void loadPlaylists(final ArrayList<Object[]> out) {
        for (String name : playlistNames()) {
            ArrayList<String> items = loadEntries("pl_" + name.hashCode());
            if (!items.isEmpty())
                out.add(new Object[]{"[歌单] " + name + " (" + items.size() + "首)",
                    items.get(0).split("\u0001")[0], "\u0001PL:" + name, "单"});
        }
    }
    void showLyricsSheet() { MenuDialogs.showLyricsSheet(this); }
    void showVisualizerDialog() { MenuDialogs.showVisualizerDialog(this); }
    void noiseDialog() { MenuDialogs.noiseDialog(this); }
    void eqPresetDialog() { MenuDialogs.eqPresetDialog(this); }
    void sleepDialog() { MenuDialogs.sleepDialog(this); }
    void speedDialog() { MenuDialogs.speedDialog(this); }
    void eqDialog() { MenuDialogs.eqDialog(this); }
    void replayGainDialog() { MenuDialogs.replayGainDialog(this); }
    void backupDialog() { MenuDialogs.backupDialog(this); }
    void smartPlaylistDialog() { MenuDialogs.smartPlaylistDialog(this); }
    void crashReportDialog() { MenuDialogs.crashReportDialog(this); }
    void statsDialog() { MenuDialogs.statsDialog(this); }
    void aboutDialog() { MenuDialogs.aboutDialog(this); }
    void langDialog() { MenuDialogs.langDialog(this); }
    void showMiniMenu() { MenuDialogs.showMiniMenuV13(this); }

}