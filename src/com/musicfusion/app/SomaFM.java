package com.musicfusion.app;

/** SomaFM — 美国非营利独立网络电台(听众赞助运营, 合法流媒体) */
public class SomaFM {

    // 官方公开直播流(128mp3), 全部合法可播
    public static final String[][] CHANNELS = {
        {"Groove Salad", "Downtempo/Chill", "https://ice1.somafm.com/groovesalad-128-mp3"},
        {"Drone Zone", "Atmospheric/Space", "https://ice1.somafm.com/dronezone-128-mp3"},
        {"Lush", "Vocal Chill", "https://ice1.somafm.com/lush-128-mp3"},
        {"Indie Pop Rocks", "Indie Pop", "https://ice1.somafm.com/indiepop-128-mp3"},
        {"Beat Blender", "Deep House", "https://ice1.somafm.com/beatblender-128-mp3"},
        {"The Trip", "Progressive House", "https://ice1.somafm.com/thetrip-128-mp3"},
        {"Secret Agent", "Spy Jazz/Lounge", "https://ice1.somafm.com/secretagent-128-mp3"},
        {"Sonic Universe", "Nu Jazz", "https://ice1.somafm.com/sonicuniverse-128-mp3"},
        {"Space Station Soma", "Electronica", "https://ice1.somafm.com/spacestation-128-mp3"},
        {"Seven Inch Soul", "Vintage Soul", "https://ice1.somafm.com/7soul-128-mp3"},
        {"Digitalis", "Analog Rock", "https://ice1.somafm.com/digitalis-128-mp3"},
        {"Left Coast 70s", "70s Rock", "https://ice1.somafm.com/seventies-128-mp3"},
        {"Underground 80s", "Early 80s Synth", "https://ice1.somafm.com/u80s-128-mp3"},
        {"Boot Liquor", "Americana", "https://ice1.somafm.com/bootliquor-128-mp3"},
        {"ThistleRadio", "Celtic/Folk", "https://ice1.somafm.com/thistle-128-mp3"},
        {"Black Rock FM", "Festival Mix", "https://ice1.somafm.com/brfm-128-mp3"},
        {"Deep Space One", "Deep Ambient", "https://ice1.somafm.com/deepspaceone-128-mp3"},
        {"Fluid", "Instrumental HipHop", "https://ice1.somafm.com/fluid-128-mp3"},
        {"Heavyweight Reggae", "Roots Reggae", "https://ice1.somafm.com/reggae-128-mp3"},
        {"Metal Detector", "Metal", "https://ice1.somafm.com/metal-128-mp3"},
        {"Suburbs of Goa", "Goa/World", "https://ice1.somafm.com/suburbsofgoa-128-mp3"},
        {"Dub Step Beyond", "Dubstep", "https://ice1.somafm.com/dubstep-256-mp3"},
        {"Cliqhop idm", "IDM/Blips", "https://ice1.somafm.com/cliqhop-128-mp3"},
        {"Vaporwaves", "Vaporwave", "https://ice1.somafm.com/vaporwaves-128-mp3"},
    };

    /** 统一行格式: name\u0001desc\u0001SomaFM\u0001url */
    public static String[][] all() {
        String[][] out = new String[CHANNELS.length][];
        for (int i = 0; i < CHANNELS.length; i++)
            out[i] = new String[]{CHANNELS[i][0],
                CHANNELS[i][1] + " · SomaFM非营利电台", CHANNELS[i][2]};
        return out;
    }
}
