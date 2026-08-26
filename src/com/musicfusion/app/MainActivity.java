package com.musicfusion.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * MusicFusion v4.0 — 专业版UI
 * 底部导航 / 播放进度条 / 均衡器 / 倍速 / 睡眠定时 / 循环模式 / 分享 / 统计
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
        }
        try { return Color.parseColor(hex); }
        catch (Throwable t) { return Color.GRAY; }
    }
    static final String C_BG = "#0b0e14", C_CARD = "#161c28", C_LINE = "#232c3d",
        C_GREEN = "#1db954", C_TXT = "#e6edf3", C_DIM = "#8b949e", C_ACC = "#58a6ff";

    TextView status, nowBar, timeLabel;
    SeekBar seek;
    EditText searchBox;
    ListView resultList;
    RowAdapter adapter;
    LinearLayout tabsView;
    ArrayList<Object[]> rows = new ArrayList<Object[]>();
    int curTab = 0, playingPos = -1;
    boolean seeking = false;
    java.util.Timer searchTimer;

    static final String[] TABS = {"首页", "搜索", "电台", "目录", "我的"};
    static MainActivity inst;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        inst = this;
        lightTheme = getSharedPreferences("mf", MODE_PRIVATE)
            .getBoolean("light", false);
        installCrashHook();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C(C_BG));

        // ═══ 顶部区 ═══
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(36), dp(20), dp(10));
        top.setBackgroundColor(C("#0d1117"));
        root.addView(top);

        LinearLayout head = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText("MusicFusion");
        title.setTextSize(20); title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(C(C_GREEN));
        head.addView(title, w(1));
        TextView gear = new TextView(this);
        gear.setText("设置");
        gear.setTextSize(13); gear.setTextColor(C(C_ACC));
        gear.setPadding(dp(10), dp(4), dp(2), dp(4));
        gear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            settingsDialog(); }});
        head.addView(gear);
        top.addView(head);

        // 播放器卡
        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pb = new GradientDrawable();
        pb.setCornerRadius(dp(12)); pb.setColor(C(C_CARD));
        player.setBackground(pb);
        player.setPadding(dp(14), dp(10), dp(14), dp(10));

        nowBar = new TextView(this);
        nowBar.setText("选择曲目开始播放");
        nowBar.setTextSize(13); nowBar.setTextColor(C(C_TXT));
        nowBar.setTypeface(null, Typeface.BOLD);
        nowBar.setSingleLine(true);
        nowBar.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            send("PAUSE"); }});
        player.addView(nowBar);

        timeLabel = new TextView(this);
        timeLabel.setText("--:-- / --:--");
        timeLabel.setTextSize(10); timeLabel.setTextColor(C(C_DIM));
        timeLabel.setPadding(0, dp(4), 0, dp(2));
        player.addView(timeLabel);

        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.getProgressDrawable().setColorFilter(C(C_GREEN),
            android.graphics.PorterDuff.Mode.SRC_IN);
        seek.getThumb().setColorFilter(C(C_GREEN),
            android.graphics.PorterDuff.Mode.SRC_IN);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {}
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                try {
                    MediaPlayer mp = MediaPlayer.class.cast(null);
                } catch (Exception ignored) {}
                Intent i = new Intent(MainActivity.this, PlayerService.class);
                i.setAction("SEEK");
                // 由Service侧用当前时长换算
                i.putExtra("frac", (float) s.getProgress() / 1000f);
                startService(i);
            }
        });
        player.addView(seek);

        LinearLayout ctrls = new LinearLayout(this);
        String[] btns = {"⏮", "⏯", "⏭", "随机", "循环", "词"};
        final String[] acts = {"PREV", "PAUSE", "NEXT", "SHUFFLE", "REPEAT", "LYRICS"};
        for (int i = 0; i < btns.length; i++) {
            final String a = acts[i];
            TextView c = new TextView(this);
            c.setText(btns[i]); c.setTextSize(14);
            c.setGravity(Gravity.CENTER);
            c.setTextColor(C(C_TXT));
            c.setPadding(0, dp(6), 0, dp(2));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if ("LYRICS".equals(a)) lyricsDialog(); else send(a); }});
            ctrls.addView(c, w(1));
        }
        player.addView(ctrls);
        top.addView(player);

        searchBox = new EditText(this);
        searchBox.setHint("搜索歌曲 / 歌手 / 电台");
        searchBox.setTextSize(14);
        searchBox.setTextColor(C(C_TXT));
        searchBox.setHintTextColor(C(C_DIM));
        GradientDrawable eg = new GradientDrawable();
        eg.setCornerRadius(dp(12)); eg.setColor(C(C_CARD));
        eg.setStroke(dp(1), C(C_LINE));
        searchBox.setBackground(eg);
        searchBox.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(10);
        top.addView(searchBox, sp);

        tabsView = new LinearLayout(this);
        tabsView.setPadding(0, dp(10), 0, dp(4));
        for (int i = 0; i < TABS.length; i++) addTab(tabsView, i);
        top.addView(tabsView);

        status = new TextView(this);
        status.setTextSize(11); status.setTextColor(C(C_DIM));
        status.setPadding(dp(4), dp(6), dp(4), dp(6));
        top.addView(status);

        // ═══ 列表区 ═══
        adapter = new RowAdapter();
        resultList = new ListView(this);
        resultList.setAdapter(adapter);
        resultList.setDividerHeight(dp(1));
        resultList.setDivider(new android.graphics.drawable.ColorDrawable(C(C_LINE)));
        resultList.setBackgroundColor(C(C_BG));
        resultList.setPadding(dp(10), 0, dp(10), dp(10));
        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { playAt(pos); }
        });
        resultList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < rows.size() && "H".equals(rows.get(pos)[3])) {
                    String t = (String) rows.get(pos)[0];
                    if (t.startsWith("搜索: ")) {
                        String q = t.substring(4);
                        java.util.Set<String> h = new java.util.HashSet<>(
                            getSharedPreferences("mf", MODE_PRIVATE)
                                .getStringSet("search_history", new java.util.HashSet<String>()));
                        h.remove(q);
                        getSharedPreferences("mf", MODE_PRIVATE).edit()
                            .putStringSet("search_history", h).apply();
                        toast("已删除历史: " + q);
                        showSearchHistory();
                    }
                    return true;
                }
                itemMenu(pos); return true;
            }
        });
        root.addView(resultList, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        searchBox.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                if (searchTimer != null) searchTimer.cancel();
                final String q = s.toString().trim();
                if (q.length() < 2) return;
                searchTimer = new java.util.Timer();
                searchTimer.schedule(new java.util.TimerTask() {
                    public void run() { doSearch(q); }
                }, 600);
            }
            public void beforeTextChanged(CharSequence c, int a, int b2, int d) {}
            public void onTextChanged(CharSequence c, int a, int b2, int d) {}
        });

        setTab(getSharedPreferences("mf", MODE_PRIVATE).getInt("last_tab", 0));
    }

    void addTab(LinearLayout parent, final int idx) {
        TextView t = new TextView(this);
        t.setText(TABS[idx]); t.setTextSize(13);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setTag(idx);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            setTab(idx); }});
        parent.addView(t, w(1));
    }

    // ══════ 设置面板 ══════
    void settingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(new String[]{
                "睡眠定时", "播放倍速", "均衡器", "统计数据",
                "浅色主题切换", "省流量模式(过滤高码率)",
                "清除搜索历史", "关于与开源致谢"},
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) sleepDialog();
                    else if (w == 1) speedDialog();
                    else if (w == 2) eqDialog();
                    else if (w == 3) statsDialog();
                    else if (w == 4) {
                        boolean now = !getSharedPreferences("mf", MODE_PRIVATE)
                            .getBoolean("light", false);
                        getSharedPreferences("mf", MODE_PRIVATE)
                            .edit().putBoolean("light", now).apply();
                        toast(now ? "已切浅色, 重启生效" : "已切深色, 重启生效");
                    } else if (w == 5) {
                        boolean now = !getSharedPreferences("mf", MODE_PRIVATE)
                            .getBoolean("datasaver", false);
                        getSharedPreferences("mf", MODE_PRIVATE)
                            .edit().putBoolean("datasaver", now).apply();
                        toast(now ? "省流量模式开(过滤>128kbps电台)" : "省流量模式关");
                        setTab(curTab);
                    } else if (w == 6) {
                        getSharedPreferences("mf", MODE_PRIVATE)
                            .edit().remove("search_history").apply();
                        toast("已清除搜索历史");
                    } else aboutDialog();
                }
            }).show();
    }
    void sleepDialog() {
        new AlertDialog.Builder(this)
            .setTitle("睡眠定时")
            .setItems(new String[]{"关闭", "15分钟", "30分钟", "60分钟"},
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    long min = new long[]{0, 15, 30, 60}[w];
                    Intent i = new Intent(MainActivity.this, PlayerService.class);
                    i.setAction("SLEEP"); i.putExtra("min", min);
                    startService(i);
                }
            }).show();
    }
    void speedDialog() {
        final String[] opts = {"0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
        final float[] vals = {0.75f, 1f, 1.25f, 1.5f, 2f};
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
    void lyricsDialog() {
        String full = nowBar.getText().toString();
        if (full.startsWith("选择")) { toast("先播放一首歌"); return; }
        String t = full.contains(" · ") ? full.substring(full.indexOf(" · ") + 3) : full;
        final String track = t;
        toast("查询歌词中…");
        bg(new Runnable() { public void run() {
            String ly = null;
            try {
                String artist = "", name = track;
                String[] parts = track.split(" · ");
                if (parts.length >= 2) { name = parts[0]; artist = parts[1]; }
                ly = Lyrics.get(artist, name);
            } catch (Exception ignored) {}
            final String f = ly;
            ui(new Runnable() { public void run() {
                if (f == null) toast("未找到歌词(LRCLIB)");
                else new AlertDialog.Builder(MainActivity.this)
                    .setTitle(track)
                    .setMessage(f.length() > 4000 ? f.substring(0, 4000) + "…" : f)
                    .setPositiveButton("关闭", null).show();
            }});
        }});
    }

    // ══════ 歌单系统 ══════
    void playlistPickDialog(final String title, final String sub, final String url) {
        ArrayList<String> names = playlistNames();
        final ArrayList<String> opts = new ArrayList<String>(names);
        opts.add("+ 新建歌单");
        new AlertDialog.Builder(this)
            .setTitle("加入歌单")
            .setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String e = title + "\u0001" + sub + "\u0001" + url;
                    if (w == names.size()) {
                        final EditText et = new EditText(MainActivity.this);
                        et.setHint("歌单名称");
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("新建歌单")
                            .setView(et)
                            .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dd, int ww) {
                                    String name = et.getText().toString().trim();
                                    if (name.isEmpty()) return;
                                    addToPlaylist(name, e);
                                    toast("已创建并加入「" + name + "」");
                                }
                            }).setNegativeButton("取消", null).show();
                    } else {
                        addToPlaylist(names.get(w), e);
                        toast("已加入「" + names.get(w) + "」");
                    }
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
        android.content.SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
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

    void statsDialog() {
        android.content.SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        int plays = sp.getInt("total_plays", 0);
        new AlertDialog.Builder(this)
            .setTitle("统计数据")
            .setMessage("累计播放: " + plays + " 次\n"
                + "收藏: " + loadEntries(PREF_FAV).size() + " 首\n"
                + "最近播放: " + loadEntries(PREF_REC).size() + " 条\n"
                + "搜索历史: " + sp.getStringSet("search_history",
                    new java.util.HashSet<String>()).size() + " 条")
            .setPositiveButton("知道了", null).show();
    }
    void aboutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("关于 MusicFusion")
            .setMessage("一站式聚合音乐播放器 v4.0\n\n"
                + "音乐源(全部合法开放):\n"
                + "· Audius — 去中心化音乐平台\n"
                + "· Internet Archive — 公有领域/CC档案\n"
                + "· RadioBrowser — 全球电台目录\n"
                + "· SomaFM — 非营利独立电台\n\n"
                + "开源致谢: 上述平台公开API\n"
                + "License: MIT")
            .setPositiveButton("知道了", null).show();
    }

    // ══════ 标签与加载 ══════
    void installCrashHook() {
        final Thread.UncaughtExceptionHandler prev =
            Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.FileWriter w = new java.io.FileWriter(
                        android.os.Environment.getExternalStorageDirectory()
                            .getPath() + "/Download/mf_crash.txt", true);
                    java.io.PrintWriter pw = new java.io.PrintWriter(w);
                    pw.println("── " + new java.util.Date() + " ──");
                    e.printStackTrace(pw); pw.close();
                } catch (Exception ignored) {}
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    void setTab(int idx) {
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
        else if (idx == 2) loadRadio("");
        else if (idx == 3) loadCatalog("");
        else if (idx == 4) loadMine();
        else if (idx == 1 && q.isEmpty()) showSearchHistory();
    }

    void showSearchHistory() {
        rows.clear();
        java.util.Set<String> h = getSharedPreferences("mf", MODE_PRIVATE)
            .getStringSet("search_history", new java.util.HashSet<String>());
        ArrayList<String> l = new ArrayList<String>(h);
        java.util.Collections.sort(l);
        for (String s : l)
            rows.add(new Object[]{"搜索: " + s, "点按重新搜索", "\u0001HIST:" + s, "H"});
        if (rows.isEmpty())
            rows.add(new Object[]{"输入关键词开始搜索", "历史记录会显示在这里", "", "H"});
        adapter.notifyDataSetChanged();
        status("搜索历史");
    }

    void doSearch(final String q) {
        status("搜索中: " + q + " …");
        // 记录历史
        android.content.SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        java.util.Set<String> h = new java.util.HashSet<String>(
            sp.getStringSet("search_history", new java.util.HashSet<String>()));
        h.add(q);
        sp.edit().putStringSet("search_history", h)
          .putInt("total_searches", sp.getInt("total_searches", 0) + 1).apply();

        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            if (curTab == 2) {
                try {
                    for (String _l : RadioBrowser.parse(RadioBrowser.search(q))) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "电台"});
                    }
                } catch (Exception e) { out.add(err("电台搜索失败", e)); }
            } else if (curTab == 3) {
                filterCatalog(q, out);
            } else if (curTab == 4) {
                filterMine(q, out);
            } else {
                try {
                    for (String _l : Audius.parse(Audius.search(q))) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                    }
                } catch (Exception e) {
                    Audius.markFail();
                    try {
                        for (String _l : Audius.parse(Audius.search(q))) {
                            String[] s = _l.split("\u0001");
                            out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                        }
                    } catch (Exception e2) { out.add(err("Audius不可达", e2)); }
                }
                try {
                    for (String _l : Archive.parse(Archive.search(q))) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{s[0], s[1] + " · 点按解析曲目", "IA:" + s[2], "档案"});
                    }
                } catch (Exception e) { /* 静默 */ }
                try {
                    for (String _l : Openverse.search(q, 1)) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "CC"});
                    }
                } catch (Exception e) { /* 静默 */ }
                try {
                    for (String _l : Archive.parse(Archive.searchCollection(q, "etree"))) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{s[0], s[1] + " · 现场演出 · 点按解析", "IA:" + s[2], "现场"});
                    }
                } catch (Exception e) { /* 静默 */ }
            }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status(rows.size() + " 条结果");
            }});
        }});
    }

    void loadTrending() {
        status("加载 Audius 热门榜…");
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                for (String _l : Audius.parse(Audius.trending())) {
                    String[] s = _l.split("\u0001");
                    out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                }
            } catch (Exception e) {
                Audius.markFail();
                try {
                    for (String _l : Audius.parse(Audius.trending())) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
                    }
                } catch (Exception e2) { out.add(err("加载失败", e2)); }
            }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("Audius 热门榜 · " + rows.size() + " 首 · 长按更多操作");
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

    static org.json.JSONObject catalogCache;
    boolean dataSaver() {
        return getSharedPreferences("mf", MODE_PRIVATE).getBoolean("datasaver", false);
    }
    boolean bitrateOk(String sub) {
        if (!dataSaver() || sub == null) return true;
        try {
            int k = sub.indexOf("· ") ;
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)kbps").matcher(sub);
            return !m.find() || Integer.parseInt(m.group(1)) <= 128;
        } catch (Exception e) { return true; }
    }
    @SuppressWarnings("unchecked")
    void filterCatalog(String q, ArrayList<Object[]> out) {
        try {
            if (catalogCache != null) { filterCatalogCached(q, out); return; }
            java.io.InputStream in = getAssets().open("stations.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            org.json.JSONObject rootJ = new org.json.JSONObject(bo.toString());
            catalogCache = rootJ;
            org.json.JSONArray st = rootJ.getJSONArray("stations");
            String low = q.toLowerCase();
            for (int i = 0; i < st.length(); i++) {
                org.json.JSONObject s = st.getJSONObject(i);
                String name = s.optString("n", "?"), cty = s.optString("c", ""),
                    tag = s.optString("t", ""), u = s.optString("u", "");
                if (!low.isEmpty() && !name.toLowerCase().contains(low)
                    && !tag.toLowerCase().contains(low) && !cty.toLowerCase().contains(low))
                    continue;
                String sub = cty + " · " + tag + " · " + s.optInt("b", 0) + "kbps";
                if (bitrateOk(sub))
                    out.add(new Object[]{name, sub, u, "台"});
            }
        } catch (Exception e) { out.add(err("目录加载失败", e)); }
    }
    void filterCatalogCached(String q, ArrayList<Object[]> out) {
        try {
            org.json.JSONArray st = catalogCache.getJSONArray("stations");
            String low = q.toLowerCase();
            for (int i = 0; i < st.length(); i++) {
                org.json.JSONObject s = st.getJSONObject(i);
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
    static final String PREF_FAV = "fav_list", PREF_REC = "recent_list";

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
                for (java.io.File f : downloadedFiles())
                    out.add(new Object[]{f.getName().replace(".mp3", ""),
                        (f.length() / 1048576) + "MB · 本机文件",
                        f.getAbsolutePath(), "本"});
            } catch (Exception ignored) {}
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("最近 " + loadEntries(PREF_REC).size()
                    + " · 收藏 " + loadEntries(PREF_FAV).size() + " · 长按管理");
            }});
        }});
    }

    @SuppressWarnings("unchecked")
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
            org.json.JSONObject pc = new org.json.JSONObject(
                getSharedPreferences("mf", MODE_PRIVATE).getString("playcounts", "{}"));
            ArrayList<String> keys = new ArrayList<String>();
            java.util.Iterator<String> it = pc.keys();
            while (it.hasNext()) keys.add(it.next());
            java.util.Collections.sort(keys, new java.util.Comparator<String>() {
                public int compare(String a, String b) {
                    return pc.optInt(b) - pc.optInt(a); }});
            for (int i = 0; i < Math.min(10, keys.size()); i++) {
                String k = keys.get(i);
                out.add(new Object[]{k, String.valueOf(pc.optInt(k)), "", ""});
            }
        } catch (Exception ignored) {}
        return out;
    }
    ArrayList<java.io.File> downloadedFiles() {
        ArrayList<java.io.File> out = new ArrayList<java.io.File>();
        java.io.File dir = new java.io.File(
            android.os.Environment.getExternalStorageDirectory()
                .getPath() + "/Download/MusicFusion");
        java.io.File[] fs = dir.listFiles();
        if (fs != null)
            for (java.io.File f : fs)
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
        final Object[] r = rows.get(pos);
        final String title = (String) r[0], sub = (String) r[1], url = (String) r[2];
        if (url == null || url.isEmpty()) return;
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(new String[]{
                "收藏", "加入歌单", "下载到本机", "分享", "复制链接", "从收藏移除", "取消"},
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 1) { playlistPickDialog(title, sub, url); return; }
                    if (w >= 2) w--;
                    if (w == 0) {
                        ArrayList<String> l = loadEntries(PREF_FAV);
                        String e = title + "\u0001" + sub + "\u0001" + url;
                        if (!l.contains(e)) { l.add(0, e); saveEntries(PREF_FAV, l); }
                        toast("已收藏");
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
                        toast("已移除");
                    }
                }
            }).show();
    }

    void copy(String s) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("url", s));
            toast("已复制链接");
        } catch (Exception e) { toast("复制失败"); }
    }

    void download(final String url, final String title) {
        toast("后台下载中…");
        bg(new Runnable() { public void run() {
            try {
                String safe = title.replaceAll("[\\\\/:*?\"<>|·—\\[\\]]", "_");
                java.io.File dir = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory()
                        .getPath() + "/Download/MusicFusion");
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir,
                    safe.replaceAll("^[^\\w\\u4e00-\\u9fa5]+", "") + ".mp3");
                java.io.InputStream in = new java.net.URL(url).openStream();
                java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                byte[] buf = new byte[65536]; int n; long total = 0;
                while ((n = in.read(buf)) > 0) { fo.write(buf, 0, n); total += n; }
                fo.close(); in.close();
                toast("已下载 " + total / 1048576 + "MB: " + out.getName());
            } catch (Exception e) { toast("下载失败: " + e.getMessage()); }
        }});
    }

    // ══════ 播放 ══════
    void playAt(final int pos) {
        if (pos >= rows.size()) return;
        final String url = (String) rows.get(pos)[2];
        if (url == null || url.isEmpty()) { toast("该条目不可播放"); return; }
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
        if (urls[pos].isEmpty()) { toast("该条目不可播放"); return; }
        Intent i = new Intent(this, PlayerService.class);
        i.putExtra("urls", urls);
        i.putExtra("titles", titles);
        i.putExtra("index", pos);
        startService(i);
        playingPos = pos;
        nowBar.setText("缓冲 · " + titles[pos]);
        nowBar.setTextColor(C(C_GREEN));
        addRecent(titles[pos], (String) rows.get(pos)[1], urls[pos]);
        android.content.SharedPreferences sp = getSharedPreferences("mf", MODE_PRIVATE);
        sp.edit().putInt("total_plays", sp.getInt("total_plays", 0) + 1).apply();
        try {
            org.json.JSONObject pc = new org.json.JSONObject(sp.getString("playcounts", "{}"));
            pc.put(titles[pos], pc.optInt(titles[pos]) + 1);
            sp.edit().putString("playcounts", pc.toString()).apply();
        } catch (Exception ignored) {}
        hideKb();
        adapter.notifyDataSetChanged();
    }

    void send(String action) {
        Intent i = new Intent(this, PlayerService.class);
        i.setAction(action);
        startService(i);
    }

    static void onPlayState(final String title, final String state) {
        if (inst == null) return;
        inst.runOnUiThread(new Runnable() { public void run() {
            inst.nowBar.setText(state + " · " + title);
            inst.nowBar.setTextColor(C(state.startsWith("播放") ? C_GREEN
                : state.startsWith("✗") ? "#f85149" : C_DIM));
        }});
    }
    static void onProgress(final int pos, final int dur) {
        if (inst == null) return;
        inst.runOnUiThread(new Runnable() { public void run() {
            if (!inst.seeking && dur > 0) {
                inst.seek.setMax(dur);
                inst.seek.setProgress(pos);
                inst.timeLabel.setText(fmt(pos) + " / " + fmt(dur));
            } else if (dur == 0) {
                inst.timeLabel.setText("直播流 · 无进度");
            }
        }});
    }
    static String fmt(int ms) {
        int s = ms / 1000;
        return s / 60 + ":" + String.format("%02d", s % 60);
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
                "现场".equals(srcTag) ? "[现场] " : "台".equals(srcTag) ? "[电台] " : "";
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
        status.setText(s); }});
    }
    void ui(final Runnable r) { runOnUiThread(r); }
    void bg(final Runnable r) { new Thread(r).start(); }
    void toast(String s) { ui(new Runnable() { public void run() {
        android.widget.Toast.makeText(MainActivity.this, s,
            android.widget.Toast.LENGTH_SHORT).show(); }});
    }
    void hideKb() {
        try { ((android.view.inputmethod.InputMethodManager)
            getSystemService(INPUT_METHOD_SERVICE))
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
}
