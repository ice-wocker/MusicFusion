package com.musicfusion.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * MusicFusion — 一站式聚合音乐
 * 源: Audius(去中心化音乐) + Internet Archive(公有领域/CC) + RadioBrowser(5万电台)
 * 全部为公开API/开放授权内容
 */
public class MainActivity extends Activity {

    TextView status, nowBar;
    EditText searchBox;
    ListView resultList;
    ArrayAdapter<String> adapter;
    ArrayList<String> items = new ArrayList<String>();   // 显示行
    ArrayList<String> streams = new ArrayList<String>(); // 播放地址(IA的为PENDING)
    int curTab = 0;
    java.util.Timer searchTimer;

    static final String[] TABS = {"🔥 热门", "🔍 搜索", "📻 电台", "📡 目录"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 40, 24, 16);
        root.setBackgroundColor(Color.parseColor("#0b0e14"));

        TextView title = new TextView(this);
        title.setText("🎵 MusicFusion");
        title.setTextSize(22); title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#1db954"));
        root.addView(title);

        nowBar = new TextView(this);
        nowBar.setText("未在播放");
        nowBar.setTextSize(12);
        nowBar.setTextColor(Color.parseColor("#8b949e"));
        nowBar.setPadding(0, 8, 0, 8);
        root.addView(nowBar);

        searchBox = new EditText(this);
        searchBox.setHint("搜索歌曲 / 歌手 / 电台…");
        searchBox.setTextSize(14);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(Color.GRAY);
        GradientDrawable eg = new GradientDrawable();
        eg.setCornerRadius(dp(12));
        eg.setColor(Color.parseColor("#1a2030"));
        eg.setStroke(dp(1), Color.parseColor("#2c3a4f"));
        searchBox.setBackground(eg);
        searchBox.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(searchBox);

        // 标签行
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, 10, 0, 4);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(TABS[i]);
            t.setTextSize(13);
            t.setPadding(dp(14), dp(8), dp(14), dp(8));
            GradientDrawable tg = new GradientDrawable();
            tg.setCornerRadius(dp(18));
            tg.setColor(Color.parseColor(i == 0 ? "#1db954" : "#1a2030"));
            t.setBackground(tg);
            t.setTextColor(Color.parseColor(i == 0 ? "#000" : "#aaa"));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                setTab(idx);
            }});
            tabs.addView(t);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
            lp.rightMargin = dp(8);
        }
        root.addView(tabs);

        status = new TextView(this);
        status.setTextSize(11);
        status.setTextColor(Color.GRAY);
        status.setPadding(0, 6, 0, 6);
        root.addView(status);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);
        resultList = new ListView(this);
        resultList.setAdapter(adapter);
        resultList.setDividerHeight(dp(1));
        resultList.setBackgroundColor(Color.parseColor("#10151d"));
        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                playAt(pos);
            }
        });
        root.addView(resultList, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        // 防抖搜索
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

    void setTab(int idx) {
        curTab = idx;
        String q = searchBox.getText().toString().trim();
        if (idx == 0 && q.isEmpty()) loadTrending();
        else if (idx == 2 && q.isEmpty()) loadRadio("");
        else if (idx == 3 && q.isEmpty()) loadCatalog();
        else if (!q.isEmpty()) doSearch(q);
    }

    /** 离线电台目录(内置722台真实数据, 断网可浏览) */
    void loadCatalog() {
        status("加载离线目录…");
        new Thread(new Runnable() { public void run() {
            ArrayList<String> out = new ArrayList<String>();
            ArrayList<String> urls = new ArrayList<String>();
            try {
                java.io.InputStream in = getAssets().open("stations.json");
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                org.json.JSONObject rootJ = new org.json.JSONObject(bo.toString());
                org.json.JSONArray st = rootJ.getJSONArray("stations");
                for (int i = 0; i < st.length(); i++) {
                    org.json.JSONObject s = st.getJSONObject(i);
                    String line = s.optString("n", "?") + "\u0001"
                        + s.optString("c", "") + " · " + s.optString("t", "") + "\u0001"
                        + s.optInt("b", 0) + "kbps\u0001" + s.optString("u", "");
                    out.add("📡 " + disp(line));
                    urls.add(s.optString("u", ""));
                }
            } catch (Exception e) { out.add("目录加载失败: " + e.getMessage()); urls.add(""); }
            final ArrayList<String> fo = out, fu = urls;
            runOnUiThread(new Runnable() { public void run() {
                items.clear(); streams.clear();
                items.addAll(fo); streams.addAll(fu);
                adapter.notifyDataSetChanged();
                status("内置离线目录 " + items.size() + " 台(点按联网播放)");
            }});
        }}).start();
    }

    void doSearch(final String q) {
        status("搜索中: " + q + " …");
        new Thread(new Runnable() { public void run() {
            ArrayList<String> out = new ArrayList<String>();
            ArrayList<String> urls = new ArrayList<String>();
            try {
                if (curTab == 2) {
                    for (String s : RadioBrowser.parse(RadioBrowser.search(q))) {
                        out.add("📻 " + s.replace("\u0001", "  ·  "));
                        urls.add(rawUrl(s));
                    }
                } else {
                    try {
                        for (String s : Audius.parse(Audius.search(q))) {
                            out.add("🎵 " + disp(s));
                            urls.add(stream(s));
                        }
                    } catch (Exception e) { out.add("🎵 Audius不可达"); urls.add(""); }
                    try {
                        for (String s : Archive.parse(Archive.search(q))) {
                            out.add("📚 " + disp(s) + "  (点按加载曲目)");
                            urls.add(stream(s));
                        }
                    } catch (Exception e) { out.add("📚 Archive不可达"); urls.add(""); }
                }
            } catch (Exception e) {
                out.add("网络错误: " + e.getMessage()); urls.add("");
            }
            final ArrayList<String> fo = out, fu = urls;
            runOnUiThread(new Runnable() { public void run() {
                items.clear(); streams.clear();
                items.addAll(fo); streams.addAll(fu);
                adapter.notifyDataSetChanged();
                status(items.size() + " 条结果");
            }});
        }}).start();
    }

    void loadTrending() {
        status("加载 Audius 热门榜…");
        new Thread(new Runnable() { public void run() {
            ArrayList<String> out = new ArrayList<String>();
            ArrayList<String> urls = new ArrayList<String>();
            try {
                for (String s : Audius.parse(Audius.trending())) {
                    out.add("🔥 " + disp(s));
                    urls.add(stream(s));
                }
            } catch (Exception e) { out.add("加载失败: " + e.getMessage()); urls.add(""); }
            final ArrayList<String> fo = out, fu = urls;
            runOnUiThread(new Runnable() { public void run() {
                items.clear(); streams.clear();
                items.addAll(fo); streams.addAll(fu);
                adapter.notifyDataSetChanged();
                status(items.size() + " 首 (Audius去中心化平台)");
            }});
        }}).start();
    }

    void loadRadio(final String q) {
        status("加载热门电台…");
        new Thread(new Runnable() { public void run() {
            ArrayList<String> out = new ArrayList<String>();
            ArrayList<String> urls = new ArrayList<String>();
            try {
                for (String s : RadioBrowser.parse(q.isEmpty()
                        ? RadioBrowser.popular() : RadioBrowser.search(q))) {
                    out.add("📻 " + disp(s));
                    urls.add(stream(s));
                }
            } catch (Exception e) { out.add("加载失败: " + e.getMessage()); urls.add(""); }
            final ArrayList<String> fo = out, fu = urls;
            runOnUiThread(new Runnable() { public void run() {
                items.clear(); streams.clear();
                items.addAll(fo); streams.addAll(fu);
                adapter.notifyDataSetChanged();
                status(items.size() + " 个电台 (RadioBrowser全球目录)");
            }});
        }}).start();
    }

    void playAt(int pos) {
        if (pos >= streams.size()) return;
        String url = streams.get(pos);
        if (url == null || url.isEmpty()) return;
        if (url.equals("IA")) {
            // Internet Archive: 先解析条目内MP3
            final String line = items.get(pos);
            final int p = pos;
            status("解析 Archive 条目…");
            new Thread(new Runnable() { public void run() {
                try {
                    String id = line.substring(line.indexOf("IA:") + 3);
                    String audio = Archive.firstAudio(id.trim());
                    if (audio != null) {
                        streams.set(p, audio);
                        String title = line.substring(0, line.indexOf("\u0001"));
                        startPlay(audio, title);
                    } else status("该条目无MP3音频");
                } catch (Exception e) { status("解析失败: " + e.getMessage()); }
            }}).start();
            return;
        }
        String title = items.get(pos);
        startPlay(url, title);
    }

    void startPlay(String url, String title) {
        Intent i = new Intent(this, PlayerService.class);
        i.putExtra("url", url);
        i.putExtra("title", title);
        startService(i);
        nowBar.setText("▶ " + title);
        nowBar.setTextColor(Color.parseColor("#1db954"));
        hideKb();
    }

    static void onPlayState(final String title, final String state) {
        // 从Service线程回调
        if (inst != null) inst.runOnUiThread(new Runnable() { public void run() {
            inst.nowBar.setText(state + " · " + title);
        }});
    }
    static MainActivity inst;
    {
        inst = this;
    }

    String disp(String row) {
        String[] p = row.split("\u0001");
        return p[0] + "  ·  " + (p.length > 1 ? p[1] : "") + (p.length > 2 ? "  [" + p[2] + "]" : "");
    }
    String stream(String row) {
        String[] p = row.split("\u0001");
        return p.length > 3 ? p[3] : "";
    }
    String rawUrl(String row) {
        String[] p = row.split("\u0001");
        return p.length > 3 ? p[3] : "";
    }
    void status(final String s) { runOnUiThread(new Runnable() { public void run() {
        status.setText(s); }});
    }
    void hideKb() {
        try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(searchBox.getWindowToken(), 0); } catch (Exception e) {}
    }
    int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
}
