# MusicFusion FAQ

## General

**Q: What Android versions are supported?**
A: Android 5.0+ (API 21+). Tested up to Android 14.

**Q: Why is the APK so small (<250KB)?**
A: Zero third-party libraries. Pure Android framework + SQLite + Java standard library. No Firebase, no Google Play Services, no analytics.

**Q: Is it really 100% free?**
A: Yes. No ads, no subscriptions, no in-app purchases, no tracking. MIT licensed.

**Q: How is it different from Spotify/YouTube Music?**
A: See the comparison table in README. Key differences: no account required, no tracking, 36 sources (not locked to one), open source.

## Music Sources

**Q: Why doesn't it have Spotify/Apple Music?**
A: Those services use DRM and don't allow third-party access to their full catalog. We respect that. Use this app for the 95% of music that's available on open platforms.

**Q: Can I add my own music server (Subsonic/Navidrome)?**
A: Yes! See CONTRIBUTING.md. The Source interface is designed for this.

**Q: Why is my favorite track missing?**
A: Different sources have different catalogs. Try a different source. Or use a Subsonic-compatible server with your own library.

## Privacy

**Q: Does it phone home?**
A: No. Network requests are limited to whitelisted music source domains. No analytics, no crash reporting, no telemetry.

**Q: Can I verify this?**
A: Yes, the source is open. Search the codebase for `HttpURLConnection` and trace the URLs.

**Q: Where is my listening history stored?**
A: On-device, in the local SQLite database. Never uploaded anywhere unless you enable Last.fm scrobble (opt-in).

## Technical

**Q: Can I add custom EQ presets?**
A: v14 will support importing/exporting presets. v13 has 6 built-in.

**Q: Does it work with Bluetooth codecs (LDAC, aptX)?**
A: Yes, these are OS-level settings. We don't override them.

**Q: Can I use it on Android Auto?**
A: The MediaBrowserService is registered, so basic playback works. Full Auto UI requires a Google Play release.

## Troubleshooting

**Q: Audio stutters on slow devices**
A: Lower the bitrate preference to 128k, or disable gapless playback in settings.

**Q: Source X shows "fetch error"**
A: The source's API may be down. Try a different source. If the error persists, file an issue.

**Q: How do I report a bug?**
A: GitHub Issues with: device model, Android version, source affected, exact error message.
