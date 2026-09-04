# Contributing to MusicFusion

We love PRs! Here's how to get started.

## Development Setup

```bash
git clone https://github.com/ice-wocker/MusicFusion
cd MusicFusion

# Build (uses Termux + android-tools + apkbuild)
./build.sh

# Or just check Java syntax
javac -source 1.8 -d /tmp/check src/com/musicfusion/app/*.java
```

## How to Add a New Music Source

This is the most common contribution. Each source implements `Source.java`:

```java
package com.musicfusion.app;

public interface Source {
    String getName();
    String getId();

    // Search returns list of tracks
    void search(String query, int limit, SearchCallback callback);

    // Stream a track
    void stream(String trackId, StreamCallback callback);
}
```

### Example: Adding a Subsonic Source

1. Create `src/com/musicfusion/app/Subsonic.java`:
```java
public class Subsonic implements Source {
    @Override public String getName() { return "Subsonic"; }
    @Override public String getId() { return "subsonic"; }
    // implement search() and stream() using HttpURLConnection
}
```

2. Register in `MainActivity.java`:
```java
sources.add(new Subsonic());
```

## Code Style

- Pure Java 8 (no Kotlin)
- 0 dependencies
- 4-space indent
- Use `final` wherever possible
- Prefer `HttpURLConnection`

## Testing

Before submitting a PR:
1. `./build.sh` must succeed
2. App must launch without crash
3. New source returns real results

## License

By contributing, you agree your code is MIT licensed.
