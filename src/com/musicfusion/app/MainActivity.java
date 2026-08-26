package com.musicfusion.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
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
 * MusicFusion v2.0 — 一站式聚合音乐
 * 防御性重写: 全颜色兜底/全网络线程化/全UI主线程化
 */
public class MainActivity extends Activity {

    // ── 安全色板(永不崩溃) ──
    static int C(String hex) {
        try { return Color.parseColor(hex); }
        catch (Throwable t) { return Color.GRAY; }
    }
    static final String C_BG = "#0b0e14", C_CARD = "#161c28", C_LINE = "#232c3d",
        C_GREEN = "#1db954", C_TXT = "#e6edf3", C_DIM = "#8b949e", C_ACC = "#58a6ff";

    TextView status, nowBar;
    EditText searchBox;
    ListView resultList;
    RowAdapter adapter;
    ArrayList<Object[]> rows = new ArrayList<Object[]>(); // {title, sub, url, icon}
    int curTab = 0, playingPos = -1;
    java.util.Timer searchTimer;

    static final String[] TABS = {"🔥 热门", "🔍 搜索", "📻 电台", "📡 目录"};
    static MainActivity inst;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        inst = this;
        installCrashHook();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(36), dp(20), dp(12));
        root.setBackgroundColor(C(C_BG));

        TextView title = new TextView(this);
        title.setText("🎵 MusicFusion");
        title.setTextSize(22); title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(C(C_GREEN));
        root.addView(title);

        nowBar = new TextView(this);
        nowBar.setText("⏸ 未在播放 — 点此暂停/继续");
        nowBar.setTextSize(12);
        nowBar.setTextColor(C(C_DIM));
        nowBar.setPadding(dp(4), dp(10), dp(4), dp(10));
        GradientDrawable nb = new GradientDrawable();
        nb.setCornerRadius(dp(10)); nb.setColor(C(C_CARD));
        nowBar.setBackground(nb);
        nowBar.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, PlayerService.class);
            i.setAction("PAUSE");
            startService(i);
        }});
        root.addView(nowBar);

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
            t.setText(TABS[i]);
            t.setTextSize(13);
            t.setPadding(dp(14), dp(8), dp(14), dp(8));
            t.setTag(i);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                setTab(idx);
            }});
            tabs.addView(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
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
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                playAt(pos);
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
                    e.printStackTrace(pw);
                    pw.close();
                } catch (Exception ignored) {}
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    void setTab(int idx) {
        curTab = idx;
        refreshTabChips();
        String q = searchBox.getText().toString().trim();
        if (q.length() >= 2 && idx != 3) { doSearch(q); return; }
        if (idx == 0) loadTrending();
        else if (idx == 2) loadRadio("");
        else if (idx == 3) loadCatalog("");
    }

    void refreshTabChips() {
        ViewGroup tabs = (ViewGroup) searchBox.getParent();
        // tabs容器是root的第4个子view, 逐个找带tag的
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
                    for (String _l : RadioBrowser.parse(RadioBrowser.search(q))) { String[] s = _l.split("\u0001");
                        out.add(new Object[]{"📻 " + s[0], s[1] + " · " + s[2], s[3], "📻"});
                        }
                } catch (Exception e) { out.add(err("电台搜索失败", e)); }
            } else if (curTab == 3) {
                filterCatalog(q, out);
            } else {
                try {
                    for (String _l : Audius.parse(Audius.search(q))) { String[] s = _l.split("\u0001");
                        out.add(new Object[]{"🎵 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                        }
                } catch (Exception e) {
                    Audius.markFail();
                    try {
                        for (String _l : Audius.parse(Audius.search(q))) { String[] s = _l.split("\u0001");
                            out.add(new Object[]{"🎵 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                            }
                    } catch (Exception e2) { out.add(err("Audius不可达", e2)); }
                }
                try {
                    for (String _l : Archive.parse(Archive.search(q))) {
                        String[] s = _l.split("\u0001");
                        out.add(new Object[]{"📚 " + s[0], s[1] + " · 点按解析曲目", "IA:" + s[2], "📚"});
                        }
                } catch (Exception e) { /* Archive失败静默 */ }
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
                for (String _l : Audius.parse(Audius.trending())) { String[] s = _l.split("\u0001");
                    out.add(new Object[]{"🔥 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                    }
            } catch (Exception e) {
                Audius.markFail();
                try {
                    for (String _l : Audius.parse(Audius.trending())) { String[] s = _l.split("\u0001");
                        out.add(new Object[]{"🔥 " + s[0], s[1] + " · " + s[2], s[3], "🎵"});
                        }
                } catch (Exception e2) { out.add(err("加载失败", e2)); }
            }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("Audius 热门榜 · " + rows.size() + " 首");
            }});
        }});
    }

    void loadRadio(final String q) {
        status("加载电台…");
        bg(new Runnable() { public void run() {
            final ArrayList<Object[]> out = new ArrayList<Object[]>();
            try {
                for (String _l : RadioBrowser.parse(q.isEmpty()
                        ? RadioBrowser.popular() : RadioBrowser.search(q))) {
                    String[] s = _l.split("\u0001");
                    out.add(new Object[]{"📻 " + s[0], s[1] + " · " + s[2], s[3], "📻"});
                    }
            } catch (Exception e) { out.add(err("加载失败", e)); }
            ui(new Runnable() { public void run() {
                rows.clear(); rows.addAll(out);
                adapter.notifyDataSetChanged();
                status("RadioBrowser · " + rows.size() + " 个电台");
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

    Object[] err(String msg, Exception e) {
        return new Object[]{"⚠️ " + msg, e.getMessage() != null ? e.getMessage() : "未知错误", "", "⚠️"};
    }

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
                            startPlay(f, (String) rows.get(pos)[0]);
                        }});
                    } else toast2("该条目无MP3音频");
                } catch (Exception e) { toast2("解析失败: " + e.getMessage()); }
            }});
            return;
        }
        startPlay(url, (String) rows.get(pos)[0]);
    }

    void startPlay(String url, String title) {
        playingPos = -1;
        for (int i = 0; i < rows.size(); i++)
            if (rows.get(i)[2].equals(url)) playingPos = i;
        Intent i = new Intent(this, PlayerService.class);
        i.putExtra("url", url);
        i.putExtra("title", title);
        startService(i);
        nowBar.setText("⏳ 缓冲 · " + title + " — 点此暂停/继续");
        nowBar.setTextColor(C(C_GREEN));
        hideKb();
        adapter.notifyDataSetChanged();
    }

    static void onPlayState(final String title, final String state) {
        if (inst == null) return;
        inst.runOnUiThread(new Runnable() { public void run() {
            inst.nowBar.setText(state + " · " + title + " — 点此暂停/继续");
            if (state.startsWith("▶")) inst.nowBar.setTextColor(C(C_GREEN));
            else if (state.startsWith("✗")) inst.nowBar.setTextColor(C("#f85149"));
        }});
    }

    // ── 双行列表适配器 ──
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
                t2.setText(sub);
                t2.setTextSize(11);
                t2.setTextColor(C(C_DIM));
                l.addView(t2);
            }
            return l;
        }
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
    void toast2(String s) { ui(new Runnable() { public void run() {
        android.widget.Toast.makeText(MainActivity.this, s,
            android.widget.Toast.LENGTH_SHORT).show(); }});
    }
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
