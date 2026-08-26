package com.musicfusion.app;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.TextView;

import java.util.ArrayList;

/**
 * MusicFusion v3.0 — 对标主流音乐App
 * 新增: 收藏/最近播放/播放队列自动连播/随机FM/长按下载/迷你控制条
 */
public class MainActivity extends Activity {

    static int C(String hex) {
        try { return Color.parseColor(hex); }
        catch (Throwable t) { return Color.GRAY; }
    }
    static final String C_BG = "#0b0e14", C_CARD = "#161c28", C_LINE = "#232c3d",
        C_GREEN = "#1db954", C_TXT = "#e6edf3", C_DIM = "#8b949e";

    TextView status, nowBar;
    EditText searchBox;
    ListView resultList;
    RowAdapter adapter;
    ArrayList<Object[]> rows = new ArrayList<Object[]>(); // {title, sub, url, icon}
    int curTab = 0, playingPos = -1;
    java.util.Timer searchTimer;

    static final String[] TABS = {"🔥 热门", "🔍 搜索", "📻 电台", "📡 目录", "💚 我的"};
    static MainActivity inst;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        inst = this;
        installCrashHook();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(10));
        root.setBackgroundColor(C(C_BG));

        TextView title = new TextView(this);
        title.setText("🎵 MusicFusion");
        title.setTextSize(22); title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(C(C_GREEN));
        root.addView(title);

        // ── 迷你控制条 ──
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable mb = new GradientDrawable();
        mb.setCornerRadius(dp(10)); mb.setColor(C(C_CARD));
        mini.setBackground(mb);
        mini.setPadding(dp(10), dp(8), dp(10), dp(8));

        nowBar = new TextView(this);
        nowBar.setText("点按任意曲目开始播放");
        nowBar.setTextSize(12); nowBar.setTextColor(C(C_DIM));
        nowBar.setSingleLine(true);
        mini.addView(nowBar);

        LinearLayout ctrls = new LinearLayout(this);
        String[] btns = {"⏮", "⏯", "⏭", "🔀"};
        final String[] acts = {"PREV", "PAUSE", "NEXT", "SHUFFLE"};
        for (int i = 0; i < btns.length; i++) {
            final String a = acts[i];
            TextView c = new TextView(this);
            c.setText(btns[i]); c.setTextSize(18);
            c.setGravity(Gravity.CENTER);
            c.setTextColor(C(C_TXT));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Intent s = new Intent(MainActivity.this, PlayerService.class);
                s.setAction(a);
                startService(s);
            }});
            ctrls.addView(c, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        mini.addView(ctrls);
        root.addView(mini);

        searchBox = new EditText(this);
        searchBox.setHint("搜索歌曲 / 歌手 / 电台…");
        searchBox.setTextSize(14);
        searchBox.setTextColor(C(C_TXT));
        searchBox.setHintTextColor(C(C_DIM));
        GradientDrawable eg = new GradientDrawable();
        eg.setCornerRadius(dp(12)); eg.setColor(C(C_CARD));
        eg.setStroke(dp(1), C(C_LINE));
        searchBox.setBackground(eg);
        searchBox.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(searchBox);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, dp(10), 0, dp(4));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(TABS[i]); t.setTextSize(13);
            t.setPadding(dp(12), dp(7), dp(12), dp(7));
            t.setTag(i);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                setTab(idx);
            }});
            tabs.addView(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            t.setLayoutParams(lp);
        }
        root.addView(tabs);

        status = new TextView(this);
        status.setTextSize(11); status.setTextColor(C(C_DIM));
        status.setPadding(dp(4), dp(6), dp(4), dp(6));
        root.addView(status);

        adapter = new RowAdapter();
        resultList = new ListView(this);
        resultList.setAdapter(adapter);
        resultList.setDividerHeight(dp(1));
        resultList.setDivider(new android.graphics.drawable.ColorDrawable(C(C_LINE)));
        GradientDrawable lg = new GradientDrawable();
        lg.setCornerRadius(dp(12)); lg.setColor(C("#10151d"));
        resultList.setBackground(lg);
        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { playAt(pos); }
        });
        resultList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                itemMenu(pos);
                return true;
            }
        });
        root.addView(resultList, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        installCrashHook();

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

        setTab(0);
    }

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

    // ══════ 标签与加载 ══════
    void setTab(int idx) {
        curTab = idx;
        refreshTabChips();
        String q = searchBox.getText().toString().trim();
        if (q.length() >= 2 && idx != 3 && idx != 4) { doSearch(q); return; }
        if (idx == 0) loadTrending();
        else if (idx == 2) loadRadio("");
        else if (idx == 3) loadCatalog("");
        else if (idx == 4) loadMine();
    }

    void refreshTabChips() {
        ViewGroup tabs = (ViewGroup) searchBox.getParent();
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View c = tabs.getChildAt(i);
            Object tag = c.getTag();
            if (tag instanceof Integer) {
                int idx = (Integer) tag;
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(18));
                g.setColor(C(idx == curTab ? C_GREEN : C_CARD));
                c.setBackground(g);
                ((TextView) c).setTextColor(C(idx == curTab ? "#000000" : "#aaaaaa"));
            }
        }
    }

    void doSearch(final String q) {
        status("搜索中: " + q + " …");
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            if (curTab == 2) {
                try {
                    for (String _l : RadioBrowser.parse(RadioBrowser.search(q))) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{"📻 " + s[0], s[1] + " · " + s[2], s[3], "📻"});
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
                        out.add(new Object[]{"🎵 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                    }
                } catch (Exception e) {
                    Audius.markFail();
                    try {
                        for (String _l : Audius.parse(Audius.search(q))) {
                            String[] s = _l.split("\u0001");
                            out.add(new Object[]{"🎵 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                        }
                    } catch (Exception e2) { out.add(err("Audius不可达", e2)); }
                }
                try {
                    for (String _l : Archive.parse(Archive.search(q))) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{"📚 " + s[0], s[1] + " · 点按解析曲目", "IA:" + s[2], "📚"});
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
                    out.add(new Object[]{"🔥 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                }
            } catch (Exception e) {
                Audius.markFail();
                try {
                    for (String _l : Audius.parse(Audius.trending())) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{"🔥 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                    }
                } catch (Exception e2) { out.add(err("加载失败", e2)); }
            }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("Audius 热门榜 · " + rows.size() + " 首 · 长按可收藏/下载");
            }});
        }});
    }

    void loadRadio(final String q) {
        status("加载电台…");
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                for (String[] ch : SomaFM.all())
                    out.add(new Object[]{"⭐ " + ch[0], ch[1], ch[2], "⭐"});
                for (String _l : RadioBrowser.parse(q.isEmpty()
                        ? RadioBrowser.popular() : RadioBrowser.search(q))) {
                    String[] s = _l.split("\u0001");
                    out.add(new Object[]{"📻 " + s[0], s[1] + " · " + s[2], s[3], "📻"});
                }
            } catch (Exception e) { out.add(err("加载失败", e)); }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("SomaFM精选 + RadioBrowser · " + rows.size() + " 台");
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
                status("内置离线目录 " + rows.size() + " 台");
            }});
        }});
    }

    @SuppressWarnings("unchecked")
    void filterCatalog(String q, ArrayList<Object[]> out) {
        try {
            java.io.InputStream in = getAssets().open("stations.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            org.json.JSONObject rootJ = new org.json.JSONObject(bo.toString());
            org.json.JSONArray st = rootJ.getJSONArray("stations");
            String low = q.toLowerCase();
            for (int i = 0; i < st.length(); i++) {
                org.json.JSONObject s = st.getJSONObject(i);
                String name = s.optString("n", "?"), cty = s.optString("c", ""),
                    tag = s.optString("t", ""), u = s.optString("u", "");
                if (!low.isEmpty() && !name.toLowerCase().contains(low)
                    && !tag.toLowerCase().contains(low) && !cty.toLowerCase().contains(low))
                    continue;
                out.add(new Object[]{"📡 " + name,
                    cty + " · " + tag + " · " + s.optInt("b", 0) + "kbps", u, "📡"});
            }
        } catch (Exception e) { out.add(err("目录加载失败", e)); }
    }

    // ══════ 我的: 收藏 + 最近 ══════
    static final String PREF_FAV = "fav_list", PREF_REC = "recent_list";

    void loadMine() {
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                for (String e : loadEntries(PREF_REC))
                    out.add(new Object[]{"🕘 " + titleOf(e), subOf(e), urlOf(e), "🕘"});
                for (String e : loadEntries(PREF_FAV))
                    out.add(new Object[]{"💚 " + titleOf(e), subOf(e), urlOf(e), "💚"});
            } catch (Exception ignored) {}
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("最近播放 " + loadEntries(PREF_REC).size()
                    + " · 收藏 " + loadEntries(PREF_FAV).size()
                    + " · 长按条目管理");
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
                    out.add(new Object[]{"🕘 " + t, subOf(e), urlOf(e), "🕘"});
            }
            for (String e : loadEntries(PREF_FAV)) {
                String t = titleOf(e);
                if (t.toLowerCase().contains(low))
                    out.add(new Object[]{"💚 " + t, subOf(e), urlOf(e), "💚"});
            }
        } catch (Exception ignored) {}
    }

    /** 条目编码: title\u0001sub\u0001url */
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

    void itemMenu(final int pos) {
        if (pos >= rows.size()) return;
        final String title = (String) rows.get(pos)[0];
        final String sub = (String) rows.get(pos)[1];
        final String url = (String) rows.get(pos)[2];
        new android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(new String[]{"💚 收藏", "⬇️ 下载到本机(直链曲目)",
                "🗑 从收藏移除", "❌ 取消"},
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) {
                        ArrayList<String> l = loadEntries(PREF_FAV);
                        String e = title + "\u0001" + sub + "\u0001" + url;
                        if (!l.contains(e)) { l.add(0, e); saveEntries(PREF_FAV, l); }
                        toast("已收藏");
                    } else if (w == 1) {
                        if (url.startsWith("http") && !url.startsWith("IA:"))
                            download(url, title);
                        else toast("该条目需先播放解析后才能下载");
                    } else if (w == 2) {
                        ArrayList<String> l = loadEntries(PREF_FAV);
                        l.remove(title + "\u0001" + sub + "\u0001" + url);
                        saveEntries(PREF_FAV, l);
                        toast("已移除");
                    }
                }
            }).show();
    }

    void download(final String url, final String title) {
        toast("后台下载中…");
        bg(new Runnable() { public void run() {
            try {
                String safe = title.replaceAll("[\\\\/:*?\"<>|]", "_");
                java.io.File dir = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory()
                        .getPath() + "/Download/MusicFusion");
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir, safe.replaceAll("^[^\\w\\u4e00-\\u9fa5]+", "") + ".mp3");
                java.io.InputStream in = new java.net.URL(url).openStream();
                java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                byte[] buf = new byte[65536]; int n; long total = 0;
                while ((n = in.read(buf)) > 0) { fo.write(buf, 0, n); total += n; }
                fo.close(); in.close();
                toast2("✓ 已下载 " + total / 1048576 + "MB → " + out.getName());
            } catch (Exception e) { toast2("下载失败: " + e.getMessage()); }
        }});
    }

    // ══════ 播放(整列表入队) ══════
    void playAt(final int pos) {
        if (pos >= rows.size()) return;
        final String url = (String) rows.get(pos)[2];
        if (url == null || url.isEmpty()) { toast("该条目无可播放地址"); return; }
        if (url.startsWith("IA:")) {
            status("解析 Internet Archive 条目…");
            bg(new Runnable() { public void run() {
                try {
                    String id = url.substring(3);
                    String audio = Archive.firstAudio(id);
                    if (audio != null) {
                        final String f = audio;
                        ui(new Runnable() { public void run() {
                            enqueue(f, pos);
                        }});
                    } else toast2("该条目无MP3音频");
                } catch (Exception e) { toast2("解析失败: " + e.getMessage()); }
            }});
            return;
        }
        enqueue(url, pos);
    }

    @SuppressWarnings("unchecked")
    void enqueue(String url, int pos) {
        // 整个当前列表作为队列, 从点击处开始自动连播
        int n = rows.size();
        String[] urls = new String[n];
        String[] titles = new String[n];
        for (int i = 0; i < n; i++) {
            urls[i] = (String) rows.get(i)[2] == null ? "" : (String) rows.get(i)[2];
            titles[i] = (String) rows.get(i)[0];
        }
        if (urls[pos] == null || urls[pos].isEmpty()) { toast("该条目无可播放地址"); return; }
        if (urls[pos].startsWith("IA:")) urls[pos] = url;   // 已解析的IA条目
        Intent i = new Intent(this, PlayerService.class);
        i.putExtra("urls", urls);
        i.putExtra("titles", titles);
        i.putExtra("index", pos);
        startService(i);
        playingPos = pos;
        nowBar.setText("⏳ " + titles[pos]);
        nowBar.setTextColor(C(C_GREEN));
        addRecent(titles[pos], (String) rows.get(pos)[1], urls[pos]);
        hideKb();
        adapter.notifyDataSetChanged();
    }

    static void onPlayState(final String title, final String state) {
        if (inst == null) return;
        inst.runOnUiThread(new Runnable() { public void run() {
            inst.nowBar.setText(state + " · " + title);
            if (state.startsWith("▶")) inst.nowBar.setTextColor(C(C_GREEN));
            else if (state.startsWith("✗")) inst.nowBar.setTextColor(C("#f85149"));
        }});
    }

    // ══════ 列表适配器 ══════
    class RowAdapter extends BaseAdapter {
        public int getCount() { return rows.size(); }
        public Object getItem(int i) { return rows.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View cv, ViewGroup vg) {
            LinearLayout l = new LinearLayout(MainActivity.this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(dp(14), dp(8), dp(14), dp(8));
            if (pos == playingPos) l.setBackgroundColor(C("#132a1c"));
            Object[] r = rows.get(pos);
            TextView t1 = new TextView(MainActivity.this);
            t1.setText((String) r[0]);
            t1.setTextSize(13);
            t1.setTextColor(C(pos == playingPos ? C_GREEN : C_TXT));
            t1.setTypeface(null, Typeface.BOLD);
            l.addView(t1);
            String sub = (String) r[1];
            if (sub != null && !sub.isEmpty()) {
                TextView t2 = new TextView(MainActivity.this);
                t2.setText(sub); t2.setTextSize(11); t2.setTextColor(C(C_DIM));
                l.addView(t2);
            }
            return l;
        }
    }

    // ══════ 工具 ══════
    Object[] err(String msg, Exception e) {
        return new Object[]{"⚠️ " + msg,
            e.getMessage() != null ? e.getMessage() : "未知错误", "", "⚠️"};
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
    void toast2(String s) { toast(s); }
    void hideKb() {
        try { ((android.view.inputmethod.InputMethodManager)
            getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(searchBox.getWindowToken(), 0); } catch (Exception e) {}
    }
    int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
    @Override protected void onDestroy() {
        if (inst == this) inst = null;
        super.onDestroy();
    }
}
