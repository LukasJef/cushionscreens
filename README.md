# CushionScreens

Turn a wall of cushions into a working screen. Show static images, animated GIFs, and videos — with synced audio — directly inside your Minecraft world.

> **Beta.** This is an early release. Commands, defaults, and the exact color values may still change between versions. Feedback and bug reports are very welcome.

## Features

- **Images** — any PNG/JPG, dithered down to the cushion color palette.
- **GIFs** — full animation support, frame timing preserved.
- **Video** — plays any video file FFmpeg can decode, at a frame rate you choose.
- **Audio** — video playback can stream synced audio to players' game clients (no resource pack needed).
- **Up to 176 colors** — instead of just the 16 cushion dye colors, the mod can place a different light-emitting block *underneath* each cushion (copper bulbs in various oxidation stages, a lit furnace, a respawn anchor, crying obsidian, a sculk catalyst, a magma block...) to get extra brightness levels per color. All screens glow at night by default, even without this.
- **Survives restarts** — a built screen is remembered across server restarts and rejoining; no need to rebuild it every time.
- **Bake** — turn a specific image into a permanent, un-managed picture, then build a new screen somewhere else.
- **Command hook** — other commands/datapacks can check whether something is currently playing via a scoreboard value, e.g. for `/execute if score`.

## Requirements

| | |
|---|---|
| Minecraft | `26.3-snapshot-3` |
| Fabric Loader | `>=0.19.3` |
| Fabric API | Required |
| Java | `25+` |
| Environment | **Client AND server both required** (audio playback and a render-distance fix run on the client) |
| FFmpeg | Required **only** for `/video` and audio extraction — must be installed and available on `PATH`. Images and GIFs don't need it. |

## Getting started

```
/cushionscreens build <width> <height>
```

Builds a screen directly in front of you. Put your images/GIFs/videos in the `cushionscreens/` folder inside your game/server directory (it's created automatically the first time you use a command), then:

```
/cushionscreens image cat.png
/cushionscreens gif dance.gif
/cushionscreens video 20 trailer.mp4
```

## Full command list

| Command | Description |
|---|---|
| `/cushionscreens build <width> <height>` | Create a screen in front of you |
| `/cushionscreens image <file> [colors=64\|176] [bake]` | Show an image |
| `/cushionscreens gif <file> [colors=64\|176]` | Play a GIF |
| `/cushionscreens video <fps> <file> [colors=64\|176]` | Play a video (needs FFmpeg) |
| `/cushionscreens video <fps> <file> [colors=64\|176] audio [targets]` | Play a video with synced audio, optionally only for specific players/selectors (`@a`, `@p`, a player name...) |
| `/cushionscreens play <pattern> <speed>` | Play a generated animation: `plasma`, `rainbow`, `bars`, or `noise` |
| `/cushionscreens range <chunks>` | Set how far away the screen renders from, then rebuild to apply |
| `/cushionscreens stop` | Stop whatever is currently playing |
| `/cushionscreens clear` | Remove the screen entirely |
| `/cushionscreens help` | In-game command reference |

## Colors

By default every screen uses all 16 cushion colors, sitting on a lit copper bulb (so it glows at night). Two optional upgrades trade performance for a bigger color range:

- `colors=64` — adds the 4 copper bulb oxidation stages under each cushion (64 total combinations).
- `colors=176` — adds all 11 supported light-level blocks under each cushion (176 total combinations).

Higher color counts look noticeably better for photos/video, but changing the block under a cushion is much more expensive than just changing its color — expect more lag on `/video` and `/gif` at `colors=176`, especially on large screens. Static `/image` is unaffected by this since it's a one-time change.

## Bake

```
/cushionscreens image cat.png bake
```

Shows the image like normal, then the mod "forgets" that spot is a screen — the cushions and blocks stay exactly as they are forever, unaffected by `/stop`, `/clear`, or future `/image`/`/video`/`/gif` calls, and aren't tracked for restart persistence. Use this to permanently commit a picture and free up the screen for something else.

## Playing sound with video

```
/cushionscreens video 24 trailer.mp4 audio
/cushionscreens video 24 trailer.mp4 colors=176 audio @a
```

Audio is extracted from the video and streamed to players over the network — it plays through their own game client, so it works even on a dedicated server with players far from each other. Without a target selector, audio plays for every player on the server.

## Checking playback state from other commands/datapacks

The mod keeps a scoreboard value in sync with whether anything is currently playing:

```
/execute if score CushionScreens cc_playing matches 1 run say something is playing!
```

## Known limitations (beta)

- Only one screen is tracked at a time.
- The color values used for `colors=64`/`colors=176` are based on real in-game measurements, but may still be refined in future updates.
- After a server restart, restoring a saved screen can take a little while if the area isn't already loaded (up to ~60 seconds before it gives up).

## License

MIT
