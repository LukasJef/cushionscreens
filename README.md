# CushionScreens

Turn a wall of cushions into a working screen. Show static images, animated GIFs, and videos - with synced audio - directly inside your Minecraft world. Load content straight from a URL, control playback like a media player (pause, resume, seek, loop, volume), and pick how many colors the screen can show.

## Features

- **Images, GIFs, and video** - any file FFmpeg/Java's image libraries can read.
- **Audio** - video playback can stream synced audio to players' game clients (no resource pack needed), with per-command volume control.
- **Load directly from a URL** - `/cushionscreens url <link>` downloads and displays an image or GIF straight from the web.
- **Up to 176 colors** - instead of just the 16 cushion dye colors, the mod can place a different light-emitting block *underneath* each cushion (copper bulbs in various oxidation stages, a lit furnace, a respawn anchor, crying obsidian, a sculk catalyst, a magma block...) to get extra brightness levels per color. Every screen glows at night by default, even without this.
- **Playback controls** - `pause`/`resume` freeze and continue exactly where you left off (no reloading), `loop` repeats video/GIF (and audio) forever instead of stopping on a blank screen, `seek` jumps partway into a video.
- **Three scaling modes** - stretch to fill exactly, crop to fill without distortion, or fit with letterboxed black bars.
- **Survives restarts** - a built screen is remembered across server restarts and rejoining; no need to rebuild it.
- **Bake** - turn a specific image into a permanent, un-managed picture, then build a new screen somewhere else.
- **Command hook** - other commands/datapacks can check whether something is currently playing via a scoreboard value, e.g. for `/execute if score`.

## Requirements

| | |
|---|---|
| Minecraft | `26.3-snapshot-3` |
| Fabric Loader | `>=0.19.3` |
| Fabric API | Required |
| Java | `25+` |
| Environment | **Client AND server both required** (audio playback and a render-distance fix run on the client) |
| FFmpeg | Required **only** for `/video` and its audio - must be installed and available on `PATH`. Images, GIFs, and URLs don't need it. |

## Getting started

```
/cushionscreens build <width> <height>
```

Builds a screen directly in front of you. Put your images/GIFs/videos in the `cushionscreens/` folder inside your game/server directory (created automatically on first use), then:

```
/cushionscreens image cat.png
/cushionscreens gif dance.gif
/cushionscreens video 20 trailer.mp4
/cushionscreens url https://i.imgur.com/abc123.png
```

## Full command list

| Command | Description |
|---|---|
| `/cushionscreens build <width> <height>` | Create a screen in front of you |
| `/cushionscreens image <file> [{attrs}]` | Show an image |
| `/cushionscreens gif <file> [{attrs}]` | Play a GIF |
| `/cushionscreens url <link> [{attrs}]` | Show an image or GIF from a direct http(s) link |
| `/cushionscreens video <fps> <file> [{attrs}]` | Play a video (needs FFmpeg) |
| `/cushionscreens video <fps> <file> audio [targets] [{attrs}]` | Play with sound, optionally only for `@a`/`@p`/a player |
| `/cushionscreens play <pattern> <speed>` | Play a generated animation: `plasma`, `rainbow`, `bars`, or `noise` |
| `/cushionscreens pause` | Freeze the current frame/pattern (and audio) |
| `/cushionscreens resume` | Continue exactly where it was paused |
| `/cushionscreens stop` | Stop whatever is currently playing |
| `/cushionscreens clear` | Remove the screen entirely |
| `/cushionscreens range <chunks>` | Set how far away the screen renders from, then rebuild to apply |
| `/cushionscreens maxframes <number>` | Max video frames to decode (default 2000) |
| `/cushionscreens help` | In-game command reference |

## Attributes

Attributes go after the file/URL (and after `audio`/targets for video), written as an NBT compound - the same style Minecraft uses for item components:

```
/cushionscreens video 20 clip.mp4 audio @a {colors:176,loop:true,volume:75,seek:30,scaling:fit}
```

| Key | Values | Applies to | Description |
|---|---|---|---|
| `colors` | `16` (default), `17`, `64`, `80`, `176` | image, gif, video, url | How many color combinations to use - see below |
| `bake` | `true` | image only | Show it, then stop managing it as a screen (see below) |
| `loop` | `true` | gif, video, url (gif) | Repeat forever (audio too) instead of stopping on a blank screen |
| `volume` | `0`-`100` | video (with `audio`) | Playback volume, independent of Minecraft's own sound sliders |
| `seek` | seconds | video | Start playback (and audio) partway into the file |
| `scaling` | `stretch` (default), `crop`, `fit` | image, gif, video, url | How to fit content that doesn't match the screen's aspect ratio |

### Colors

Every screen always sits on a lit waxed copper bulb by default (16 colors, glows at night). Higher values trade performance for a wider color range - changing the block under a cushion is much more expensive than just changing its color, so expect more lag at higher color counts, especially on `/video` and `/gif` with large screens. Static `/image` is far less affected since it's a one-time change.

- `16` - default, just the cushion colors.
- `17` - the 16 default colors plus one true black (a black cushion on a non-glowing block, since black on a lit block isn't fully black).
- `64` - adds the 4 copper bulb oxidation stages under each cushion.
- `80` - the 64 above, plus a non-glowing variant for each color.
- `176` - all 11 supported light-level blocks under each cushion.

### Scaling

- `stretch` - fills the screen exactly, may distort the image if its aspect ratio doesn't match the screen.
- `crop` - scales to fill the screen completely without distortion, cropping whichever edge overflows.
- `fit` - scales to fit entirely within the screen without distortion or cropping; leftover space is filled with black bars (letterbox/pillarbox).

### Bake

```
/cushionscreens image cat.png {bake:true}
```

Shows the image like normal, then the mod "forgets" that spot is a screen - the cushions and blocks stay exactly as they are forever, unaffected by `/stop`, `/clear`, or future `/image`/`/video`/`/gif` calls, and aren't tracked for restart persistence. Use this to permanently commit a picture and free up the screen for something else.

## Checking playback state from other commands/datapacks

The mod keeps a scoreboard value in sync with whether anything is currently playing:

```
/execute if score CushionScreens cc_playing matches 1 run say something is playing!
```

## Known limitations

- Only one screen is tracked at a time.
- The color values used for `colors:64`/`80`/`176`/`17` are based on real in-game measurements, but may still be refined in future updates.
- Looped audio runs independently from the video loop and isn't explicitly resynced, so very long loops may drift slightly out of sync over time.
- After a server restart, restoring a saved screen can take a little while if the area isn't already loaded (up to ~60 seconds before it gives up).
- `/cushionscreens url` supports images and GIFs only, not video/live streams.

## License

MIT
