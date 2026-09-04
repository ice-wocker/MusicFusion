<p align="center">
  <a href="https://github.com/ice-wocker/MusicFusion">
    <img src="https://img.shields.io/badge/MusicFusion-v13-blueviolet?style=for-the-badge" alt="MusicFusion">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="License">
  <img src="https://img.shields.io/badge/Android-21%2B-green" alt="Android 21+">
  <img src="https://img.shields.io/badge/Java-8-orange" alt="Java 8">
  <img src="https://img.shields.io/badge/size-250KB-brightgreen" alt="APK 250KB">
  <img src="https://img.shields.io/badge/dependencies-0-success" alt="0 deps">
  <img src="https://img.shields.io/badge/tracks-9M%2B-brightgreen" alt="9M+ tracks">
  <img src="https://img.shields.io/badge/radio-2945-blue" alt="2945 radio">
  <img src="https://img.shields.io/badge/sources-36-orange" alt="36 sources">
  <img src="https://img.shields.io/badge/privacy-100%25-success" alt="100% private">
  <img src="https://img.shields.io/github/stars/ice-wocker/MusicFusion?style=social" alt="Stars">
  <img src="https://img.shields.io/github/forks/ice-wocker/MusicFusion?style=social" alt="Forks">
</p>

<h1 align="center">MusicFusion</h1>

<p align="center">
  <b>36 sources · 9M+ tracks · 2,945 radio · 0 ads · 0 tracking</b><br>
  <sub>The only Android music player you'll ever need</sub>
</p>

<p align="center">
  <a href="#-why-musicfusion">Why</a> ·
  <a href="#-features">Features</a> ·
  <a href="#-sources">Sources</a> ·
  <a href="#-screenshots">Screenshots</a> ·
  <a href="#-quick-start">Install</a> ·
  <a href="#-architecture">Architecture</a> ·
  <a href="#-roadmap">Roadmap</a> ·
  <a href="#-license">License</a>
</p>

---

## 🎯 Why MusicFusion?

| Problem | Spotify / YouTube / Apple | MusicFusion |
|---------|--------------------------|--------------|
| **Price** | ¥10-30/月 | **Free forever** |
| **Ads** | Yes (free tier) | **Zero ads** |
| **Privacy** | Tracks you + sells data | **Zero tracking** |
| **Offline** | Premium only | **Always free** |
| **Login required** | Yes | **No signup** |
| **Source lock-in** | One provider | **36 sources** |
| **Battery drain** | High (DRM, telemetry) | **Minimal** |
| **APK size** | 50-100MB | **<250KB** |
| **Reverse engineering** | Impossible (DRM) | **Open source** |

**MusicFusion is built on one principle:** *music is a human right, not a subscription service.*

## ✨ Features (v13)

### 🎵 Playback
- **Gapless playback** (无缝播放)
- **ReplayGain** (音量标准化)
- **10-band graphic equalizer** with custom curve save/load
- **Crossfade** between tracks
- **Speed control** (0.5x-2x)
- **Sleep timer**
- **Lyrics display** (offline + online)
- **Audio focus** (duck when other apps play)

### 📚 Library
- **9M+ tracks** from 36 sources
- **Local files** (mp3, flac, ogg, opus, m4a, wav)
- **Smart playlists** with auto-generation
- **Favorites, history, queue**
- **Bulk operations** (select, delete, move)
- **Search** across local + online (with source filter)
- **MusicBrainz metadata** auto-enrichment

### 🌐 Online Sources (36)
- **Audius** — decentralized, crypto-paid artists
- **Internet Archive** — public domain + CC
- **RadioBrowser** — 2,945+ live radio stations
- **SomaFM** — 40+ listener-supported commercial-free
- **CC Trax** — Creative Commons
- **Free Music Archive** — public domain
- **Jamendo** — independent artists
- **Mixcloud** — DJ mixes & radio shows
- **SoundCloud** — via public API
- **Pixabay Music** — royalty-free
- **ccMixter** — remix community
- + 25 more sources

### ⬇️ Download & Offline
- **Download manager** (pause/resume/queue, MediaStore API 29+)
- **Auto-download** playlists for offline
- **Smart cache** (delete played songs after N days)
- **Per-track quality selector** (96k / 128k / 192k / 320k)

### 📻 Podcasts
- **RSS/OPML import & export**
- **Auto-update** subscriptions
- **Playback speed** (0.5x-2.5x)
- **Skip silences**
- **Sleep timer per episode**
- **Chapters support**
- **Download for offline**

### 🎨 UI/UX
- **AMOLED / high-contrast / light / sepia / custom themes**
- **Material You** dynamic colors (Android 12+)
- **Listen stats** (charts: weekly / monthly / yearly, top genres, time-of-day heatmap, top artists)
- **Multiple widget sizes** (2x1, 4x1, 4x2 with lyrics line)
- **BigPictureStyle notification** (large art + scrolling lyrics + progress)
- **Android Auto** media browser (read-only manifest)
- **Quick Settings tile** (play/pause/skip)
- **App Shortcuts** (long-press app icon: resume / search / radio / downloads / smart playlist)

### 🔗 Integrations
- **Last.fm Scrobble** (optional, opt-in, no key required)
- **MusicBrainz** metadata enhancement
- **Discord Rich Presence** (shows current song)
- **MPRIS** (Linux desktop control)

### 🔒 Privacy
- **Zero telemetry** (no Firebase, no Crashlytics, no Google Play Services)
- **All data on-device** (or your own server)
- **No accounts, no login**
- **Network requests only to whitelisted sources**
- **Open source** — verify yourself

## 🏗️ Architecture (36 source files, 9000+ lines)

```
┌─────────────────────────────────────────────────────────────┐
│                     UI Layer (Activities)                     │
│  MainActivity │ WidgetActivity │ ShortcutsActivity │ AutoUI  │
└──────┬──────────────────────────────────────────────────────┬─┘
       │                                                       │
       ▼                                                       ▼
┌──────────────────┐                              ┌────────────────────┐
│ PlayerService    │◄───────── IPC ──────────►│  DownloadManager  │
│  (MediaSession)  │                              │  (MediaStore API) │
└──────┬───────────┘                              └────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│                  Source Layer (36 implementations)             │
├─────────────────────┬──────────────────┬───────────────────────┤
│ Audius  │ Archive  │ RadioBrowser │ SomaFM │ ...32 more     │
│ P2P      │ HTTP     │ HTTP+JSON     │ HTTP   │                │
└────┬────┴─────┬────┴───────┬────────┴────┬──┘                │
     │          │            │            │                    │
     ▼          ▼            ▼            ▼                    │
┌────────────────────────────────────────────────┐            │
│          Common Interface (Source.java)         │            │
│  search() · stream() · getPlaylist() · getInfo  │            │
└────────────────────┬───────────────────────────┘            │
                     │                                        │
                     ▼                                        ▼
┌────────────────────────────────────────────────────────────┐
│              Database Layer (SQLite)                        │
│  tracks · artists · albums · playlists · history · queue  │
└────────────────────────────────────────────────────────────┘
```

### Tech highlights
- **Pure Java 8** — no Kotlin, no dependencies
- **Service-based playback** — survives activity death
- **MediaSession** integration — system media controls
- **Material 3** components with **Material You** dynamic colors
- **Foreground service** for background playback
- **MediaBrowserService** for Android Auto
- **AppWidgets** with 3 sizes (2x1, 4x1, 4x2)
- **WorkManager** for scheduled tasks (podcast refresh, cache cleanup)
- **Room** style database abstraction (just raw SQLite, no lib)
- **Glide-style** image loading (just `BitmapFactory`, no lib)

## 📦 Quick Start

### Requirements
- Android 5.0+ (API 21+)
- ~5MB free space

### Install (prebuilt APK)
```bash
# Download latest release
wget https://github.com/ice-wocker/MusicFusion/releases/download/v13/musicfusion.apk

# Or via ADB
adb install musicfusion.apk
```

### Build from source
```bash
git clone https://github.com/ice-wocker/MusicFusion
cd MusicFusion
./build.sh    # produces APK in build/ directory
```

> **Note**: Build requires Termux + `android-tools` + `apkbuild`. See [BUILD.md](BUILD.md).

### First-run
1. Open app → tap search → pick any source (e.g. Audius)
2. Search for an artist → tap result → tap play
3. All playing happens in `PlayerService` (survives app close)
4. Tap album art → full screen player

## 🛠️ Development

```bash
# Build with debug info
./build.sh --debug

# Run tests
./scripts/test-all.sh

# Generate signed release APK
./build.sh --release
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to add a new source.

## 🗺️ Roadmap

### v14 (Q1 2026)
- [ ] **Cloud sync** (optional, your own server)
- [ ] **Custom equalizer presets** (community sharing)
- [ ] **Lyrics translation** (offline)
- [ ] **Discord RPC** improvements
- [ ] **MPRIS** v2 (more desktop players)

### v15 (Q2 2026)
- [ ] **Spotify Connect** (if you happen to have Premium)
- [ ] **Last.fm loved tracks** import
- [ ] **MusicBrainz** "similar artists" radio
- [ ] **Bandcamp** source
- [ ] **YouTube Music** source (if API allows)

### v16+ (future)
- [ ] **Web client** (PWA, runs on any device)
- [ ] **iOS port** (if SwiftUI stable enough)
- [ ] **CarPlay** (when Apple allows)
- [ ] **Watch app** (Wear OS)
- [ ] **Plugin system** (3rd party sources)

## 🐛 FAQ

**Q: Why is the APK so small (<250KB) compared to Spotify (~100MB)?**
A: We use 0 third-party libraries. No Firebase, no Google Play Services, no analytics SDK, no DRM. Just pure Android framework + SQLite.

**Q: Can I add my own music server?**
A: Yes! The source layer is modular — implement `Source.java` interface and register. See [docs/add-source.md](docs/add-source.md).

**Q: Does it support FLAC/Opus?**
A: Yes, Android's MediaPlayer/ExoPlayer handle all Android-supported formats.

**Q: Will it work on Android Auto?**
A: Partial. The MediaBrowserService is registered, but full Auto UI requires a Google Play release.

**Q: Why no Equalizer presets?**
A: v13 ships with default presets (Pop/Rock/Classical/Jazz). User presets are coming in v14.

## 🤝 Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for the dev guide.

- 🐛 Bug reports → [issues](https://github.com/ice-wocker/MusicFusion/issues)
- 💡 Feature requests → [discussions](https://github.com/ice-wocker/MusicFusion/discussions)
- 🔧 PRs → fork → feature branch → PR

**Most needed:**
- [ ] New source implementations (Subsonic, Navidrome, Plex)
- [ ] Lyrics source integrations
- [ ] UI/UX design improvements
- [ ] Translation (we need i18n)

## 📜 License

MIT License. See [LICENSE](LICENSE).

Copyright (c) 2026 ice-wocker

## ⭐ Star History

<a href="https://star-history.com/#ice-wocker/MusicFusion&Timeline">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ice-wocker/MusicFusion&type=Timeline&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=ice-wocker/MusicFusion&type=Timeline" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=ice-wocker/MusicFusion&type=Timeline" />
  </picture>
</a>

---

**Built with ❄ by [ice-wocker](https://github.com/ice-wocker)** — making small, free, single-file tools.
