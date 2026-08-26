package com.musicfusion.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * L10n v10 — 应用内多语言(中文/English)
 * 程序化UI无资源体系, 用键表直查; 语言存prefs, 重启生效
 */
public class L10n {
    public static final int ZH = 0, EN = 1;
    public static int lang = ZH;

    public static void load(Context ctx) {
        lang = ctx.getSharedPreferences("mf", Context.MODE_PRIVATE)
            .getInt("lang", ZH);
    }

    private static final String[][] T = {
        // key            zh                 en
        {"tab_home",     "首页",            "Home"},
        {"tab_search",   "搜索",            "Search"},
        {"tab_radio",    "电台",            "Radio"},
        {"tab_catalog",  "目录",            "Catalog"},
        {"tab_mine",     "我的",            "Library"},
        {"search_hint",  "搜索歌曲 / 歌手 / 电台", "Songs / artists / stations"},
        {"settings",     "设置",            "Settings"},
        {"not_playing",  "选择曲目开始播放", "Pick a track to start"},
        {"paused",       "已暂停",          "Paused"},
        {"playing",      "播放中",          "Playing"},
        {"buffering",    "缓冲",            "Buffering"},
        {"error",        "错误",            "Error"},
        {"sleep",        "睡眠定时",        "Sleep timer"},
        {"speed",        "播放倍速",        "Playback speed"},
        {"equalizer",    "均衡器",          "Equalizer"},
        {"stats",        "统计数据",        "Statistics"},
        {"theme",        "浅色主题切换",    "Toggle light theme"},
        {"datasaver",    "省流量模式(过滤高码率)", "Data saver (filter hi-bitrate)"},
        {"clear_hist",   "清除搜索历史",    "Clear search history"},
        {"about",        "关于与开源致谢",  "About & open-source credits"},
        {"fav",          "收藏",            "Favorite"},
        {"add_pl",       "加入歌单",        "Add to playlist"},
        {"download",     "下载到本机",      "Download"},
        {"share",        "分享",            "Share"},
        {"copy",         "复制链接",        "Copy link"},
        {"unfav",        "从收藏移除",      "Remove from favorites"},
        {"cancel",       "取消",            "Cancel"},
        {"close",        "关闭",            "Close"},
        {"new_pl",       "+ 新建歌单",      "+ New playlist"},
        {"results",      " 条结果",         " results"},
        {"searching",    "搜索中: ",        "Searching: "},
        {"loading",      "加载中…",         "Loading…"},
        {"lyrics",       "词",              "LRC"},
        {"lyr_none",     "未找到歌词(LRCLIB)", "Lyrics not found (LRCLIB)"},
        {"lyr_query",    "查询歌词中…",     "Fetching lyrics…"},
        {"fav_done",     "已收藏",          "Favorited"},
        {"fav_rm",       "已移除",          "Removed"},
        {"copied",       "已复制链接",      "Link copied"},
        {"dl_ok",        "已下载: ",        "Downloaded: "},
        {"dl_fail",      "下载失败: ",      "Download failed: "},
        {"no_url",       "该条目不可播放",  "This item is not playable"},
        {"random",       "随机",            "Shuffle"},
        {"repeat",       "循环",            "Repeat"},
        {"repeat_one",   "单曲循环",        "Repeat one"},
        {"shuffle_on",   "随机开",          "Shuffle on"},
        {"order",        "顺序播",          "Sequential"},
        {"lang",         "语言/Language",   "Language/Language"},
    };

    public static String s(String key) {
        for (String[] r : T)
            if (r[0].equals(key))
                return lang == EN ? (r[2] != null ? r[2] : r[1]) : r[1];
        return key;
    }
}
