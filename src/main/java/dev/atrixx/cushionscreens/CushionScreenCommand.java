package dev.atrixx.cushionscreens;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.atrixx.cushionscreens.network.CushionAudioChunk;
import dev.atrixx.cushionscreens.network.CushionAudioStart;
import dev.atrixx.cushionscreens.network.CushionAudioStop;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class CushionScreenCommand {

    private static final int MAX_DIM = 512;
    private static final int PERF_WARN_PIXELS = 16384;
    private static final int MAX_GIF_FRAMES = 600;

    // Kolik snimku videa se maximalne dekoduje - nastavitelne prikazem
    // /cushionscreens maxframes <cislo>, uklada se do globalni konfigurace
    // (viz CushionConfig). Vychozi 2000 je puvodni hodnota modu - pokud
    // chces delsi videa (bez zacykleni po dosazeni limitu), zvys si to.
    private static final CushionConfig CONFIG = CushionConfig.load();

    private static final String FFMPEG = "ffmpeg";
    private static final DyeColor[] DYES = DyeColor.values();

    private static ServerLevel level;
    private static Cushion[] pixels;
    private static int gridW;
    private static int gridH;
    private static int originX;
    private static int cushionY;
    private static int originZ;
    private static int viewChunks = 32;

    private static boolean playing;
    private static String pattern = "plasma";
    private static int periodTicks = 1;
    private static int tickCounter;
    private static int frame;

    private static int[][] clipFrames;
    private static int[] clipFrameTicks;
    private static boolean clipPlaying;
    private static int clipIndex;
    private static int clipTickAcc;
    private static boolean clipLoop;

    // Zamrazi aktualne zobrazeny snimek/vzor (video, gif i /play) - viz
    // serverTick(). Zadne opetovne dekodovani, jen se prestane
    // aktualizovat, dokud se nezavola resume.
    private static boolean paused;

    private static volatile int[][] pendingFrames;
    private static volatile int pendingTicksPerFrame;
    private static volatile int pendingW;
    private static volatile int pendingH;
    private static volatile String pendingLabel;
    private static volatile String pendingError;
    private static volatile ServerPlayer pendingNotify;
    private static volatile byte[] pendingAudioPcm;
    private static volatile List<ServerPlayer> pendingAudioTargets;
    private static volatile CushionColorPalette.Mode pendingMode = CushionColorPalette.Mode.DEFAULT;
    private static volatile boolean pendingLoop;
    private static volatile int pendingVolume = 100;
    // Pokud neni null, pouzije se PRIMO jako pole delek jednotlivych
    // snimku (napr. pro GIF z URL s ruznou delkou kazdeho snimku) misto
    // jednotneho pendingTicksPerFrame (ktery pocita s konstantnim fps
    // videa).
    private static volatile int[] pendingFrameTicks;

    // Rezim barev pouzity pro aktualne prehravany klip (gif/video) -
    // potrebne, protoze serverTick() musi vedet, jak dekodovat "flat"
    // index z frames[] zpet na barvu+blok.
    private static CushionColorPalette.Mode clipMode = CushionColorPalette.Mode.DEFAULT;
    // Posledni pouzita "tier" (uroven jasu) pro kazdy pixel - diky tomu
    // pri prehravani meníme blok pod polstarem jen kdyz se skutecne zmenil,
    // ne kazdy snimek u kazdeho pixelu (setBlock je drahy - prepocet
    // osvetleni chunku).
    private static int[] lastTier;

    // Scoreboard "vlajka" pro /execute if score CushionScreens cc_playing matches 1
    public static final String SCORE_OBJECTIVE = "cc_playing";
    public static final String SCORE_HOLDER = "CushionScreens";
    private static boolean lastPlayingSignal = false;

    // Persistence obrazovky pres restart/znovu-pripojeni - viz
    // saveScreenState/onServerStarted/tryRestoreScreen.
    private static final Gson GSON = new Gson();
    private static final String SAVE_FILE = "cushionscreens_screen.json";
    private static volatile CushionScreenState pendingRestore;
    private static int pendingRestoreTicks;

    private CushionScreenCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            ((LiteralArgumentBuilder<CommandSourceStack>) Commands.literal("cushionscreens")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                .executes(CushionScreenCommand::help)
                .then(Commands.literal("help").executes(CushionScreenCommand::help))
                .then(Commands.literal("build")
                    .then(Commands.argument("width", IntegerArgumentType.integer(1, MAX_DIM))
                        .then(Commands.argument("height", IntegerArgumentType.integer(1, MAX_DIM))
                            .executes(ctx -> build(ctx,
                                IntegerArgumentType.getInteger(ctx, "width"),
                                IntegerArgumentType.getInteger(ctx, "height"))))))
                .then(Commands.literal("image")
                    .then(Commands.argument("file", CushionMediaFileArgument.file())
                        .executes(ctx -> image(ctx, CushionMediaFileArgument.getFile(ctx, "file"), null))
                        .then(Commands.argument("attrs", CompoundTagArgument.compoundTag())
                            .executes(ctx -> image(ctx, CushionMediaFileArgument.getFile(ctx, "file"),
                                CompoundTagArgument.getCompoundTag(ctx, "attrs"))))))
                .then(Commands.literal("gif")
                    .then(Commands.argument("file", CushionMediaFileArgument.file())
                        .executes(ctx -> gif(ctx, CushionMediaFileArgument.getFile(ctx, "file"), null))
                        .then(Commands.argument("attrs", CompoundTagArgument.compoundTag())
                            .executes(ctx -> gif(ctx, CushionMediaFileArgument.getFile(ctx, "file"),
                                CompoundTagArgument.getCompoundTag(ctx, "attrs"))))))
                .then(Commands.literal("video")
                    .then(Commands.argument("fps", IntegerArgumentType.integer(1, 30))
                        .then(Commands.argument("file", CushionMediaFileArgument.file())
                            .executes(ctx -> video(ctx,
                                IntegerArgumentType.getInteger(ctx, "fps"),
                                CushionMediaFileArgument.getFile(ctx, "file"),
                                false, null, null))
                            .then(Commands.argument("attrs", CompoundTagArgument.compoundTag())
                                .executes(ctx -> video(ctx,
                                    IntegerArgumentType.getInteger(ctx, "fps"),
                                    CushionMediaFileArgument.getFile(ctx, "file"),
                                    false, null, CompoundTagArgument.getCompoundTag(ctx, "attrs"))))
                            .then(videoAudioNode()))))
                .then(Commands.literal("url")
                    .then(Commands.argument("url", CushionMediaFileArgument.file())
                        .executes(ctx -> url(ctx, CushionMediaFileArgument.getFile(ctx, "url"), null))
                        .then(Commands.argument("attrs", CompoundTagArgument.compoundTag())
                            .executes(ctx -> url(ctx, CushionMediaFileArgument.getFile(ctx, "url"),
                                CompoundTagArgument.getCompoundTag(ctx, "attrs"))))))
                .then(Commands.literal("play")
                    .then(Commands.argument("pattern", StringArgumentType.word())
                        .then(Commands.argument("speed", IntegerArgumentType.integer(1, 200))
                            .executes(ctx -> play(ctx,
                                StringArgumentType.getString(ctx, "pattern"),
                                IntegerArgumentType.getInteger(ctx, "speed"))))))
                .then(Commands.literal("range")
                    .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> range(ctx, IntegerArgumentType.getInteger(ctx, "chunks")))))
                .then(Commands.literal("maxframes")
                    .then(Commands.argument("frames", IntegerArgumentType.integer(1))
                        .executes(ctx -> maxFrames(ctx, IntegerArgumentType.getInteger(ctx, "frames")))))
                .then(Commands.literal("pause").executes(CushionScreenCommand::pause))
                .then(Commands.literal("resume").executes(CushionScreenCommand::resume))
                .then(Commands.literal("stop").executes(CushionScreenCommand::stop))
                .then(Commands.literal("clear").executes(CushionScreenCommand::clear))
        );
    }

    // Vetev "audio [hraci] [{atributy}]" pod /cushionscreens video <fps> <file>.
    private static LiteralArgumentBuilder<CommandSourceStack> videoAudioNode() {
        return Commands.literal("audio")
            .executes(ctx -> video(ctx,
                IntegerArgumentType.getInteger(ctx, "fps"),
                CushionMediaFileArgument.getFile(ctx, "file"),
                true, null, null))
            .then(Commands.argument("attrs", CompoundTagArgument.compoundTag())
                .executes(ctx -> video(ctx,
                    IntegerArgumentType.getInteger(ctx, "fps"),
                    CushionMediaFileArgument.getFile(ctx, "file"),
                    true, null, CompoundTagArgument.getCompoundTag(ctx, "attrs"))))
            .then(Commands.argument("targets", EntityArgument.players())
                .executes(ctx -> video(ctx,
                    IntegerArgumentType.getInteger(ctx, "fps"),
                    CushionMediaFileArgument.getFile(ctx, "file"),
                    true, EntityArgument.getPlayers(ctx, "targets"), null))
                .then(Commands.argument("attrs", CompoundTagArgument.compoundTag())
                    .executes(ctx -> video(ctx,
                        IntegerArgumentType.getInteger(ctx, "fps"),
                        CushionMediaFileArgument.getFile(ctx, "file"),
                        true, EntityArgument.getPlayers(ctx, "targets"),
                        CompoundTagArgument.getCompoundTag(ctx, "attrs")))));
    }

    // --- Parsovani {atributy} ---

    private static CushionColorPalette.Mode parseColorsMode(CompoundTag attrs) {
        if (attrs == null) return CushionColorPalette.Mode.DEFAULT;
        int colors = attrs.getInt("colors").orElse(16);
        return CushionColorPalette.Mode.fromColorsValue(colors);
    }

    private static boolean parseBake(CompoundTag attrs) {
        return attrs != null && attrs.getBoolean("bake").orElse(false);
    }

    private static boolean parseLoop(CompoundTag attrs) {
        return attrs != null && attrs.getBoolean("loop").orElse(false);
    }

    private static int parseVolume(CompoundTag attrs) {
        if (attrs == null) return 100;
        int v = attrs.getInt("volume").orElse(100);
        return Math.max(0, Math.min(100, v));
    }

    private static CushionEncoder.ScaleMode parseScaling(CompoundTag attrs) {
        if (attrs == null) return CushionEncoder.ScaleMode.STRETCH;
        String s = attrs.getString("scaling").orElse("stretch");
        if ("crop".equalsIgnoreCase(s)) return CushionEncoder.ScaleMode.CROP;
        if ("fit".equalsIgnoreCase(s)) return CushionEncoder.ScaleMode.FIT;
        return CushionEncoder.ScaleMode.STRETCH;
    }

    private static double parseSeek(CompoundTag attrs) {
        if (attrs == null) return 0;
        return Math.max(0, attrs.getInt("seek").orElse(0));
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        line(src, header("CushionScreens"));
        line(src, cmdLine("/cushionscreens build <width> <height>", "Create a screen in front of you"));
        line(src, cmdLine("/cushionscreens image <file> [{attrs}]", "Show an image"));
        line(src, cmdLine("/cushionscreens gif <file> [{attrs}]", "Play a GIF"));
        line(src, cmdLine("/cushionscreens url <url> [{attrs}]", "Show an image or GIF from a direct http(s) link"));
        line(src, cmdLine("/cushionscreens video <fps> <file> [{attrs}]", "Play a video (needs FFmpeg)"));
        line(src, cmdLine("/cushionscreens video <fps> <file> audio [targets] [{attrs}]", "Play with sound, optionally only for @a/@p/a player"));
        line(src, cmdLine("/cushionscreens play <pattern> <speed>", "Patterns: plasma, rainbow, bars, noise"));
        line(src, cmdLine("/cushionscreens range <chunks>", "Set view distance, then rebuild"));
        line(src, cmdLine("/cushionscreens maxframes <number>", "Max video frames to decode (default 2000)"));
        line(src, cmdLine("/cushionscreens pause", "Freeze the current frame/pattern (and audio)"));
        line(src, cmdLine("/cushionscreens resume", "Continue exactly where it was paused"));
        line(src, cmdLine("/cushionscreens stop", "Stop playback"));
        line(src, cmdLine("/cushionscreens clear", "Remove the screen"));
        line(src, header("Attributes"));
        line(src, info("Attributes go after the file/url (and after audio/targets for video),"));
        line(src, info("in NBT form, e.g.: video 20 clip.mp4 audio @a {colors:176,seek:120}"));
        line(src, info("colors: 16 (default), 17 (16 + a true black), 64 (copper bulb"));
        line(src, info("stages), 80 (64 + no-light variant), or 176 (all light levels)."));
        line(src, info("loop: true - video/gif repeats forever (audio too) instead of"));
        line(src, info("stopping on a blank screen after playing once."));
        line(src, info("volume: 0-100 - video audio volume, independent of Minecraft's own"));
        line(src, info("sound sliders (default 100)."));
        line(src, info("seek: seconds - video only, start playback (and audio) partway in."));
        line(src, info("scaling: stretch (default), crop, or fit. stretch fills the screen"));
        line(src, info("exactly but can distort the image; crop keeps proportions and cuts"));
        line(src, info("off the overflow; fit keeps proportions and letterboxes with black"));
        line(src, info("bars instead of cropping."));
        line(src, info("bake: true - image only, see below."));
        line(src, header("Colors"));
        line(src, info("Default is 16 colors, always glowing (waxed copper bulb underneath -"));
        line(src, info("visible at night too). Higher color counts change a block every"));
        line(src, info("pixel, which is much slower than the default, especially for"));
        line(src, info("/video and /gif."));
        line(src, header("Bake"));
        line(src, info("image ... {bake:true} shows the image, then forgets the cushions were a"));
        line(src, info("screen - they stay forever as a normal picture, and you can build a"));
        line(src, info("new screen elsewhere. stop/clear/video/gif no longer affect it."));
        line(src, header("Persistence"));
        line(src, info("The screen (if not baked) is remembered across server restarts and"));
        line(src, info("rejoining - no need to rebuild it."));
        line(src, header("Files"));
        line(src, info("Put images, GIFs, and videos in " + mediaDir()));
        line(src, header("Tips"));
        line(src, info("Raise Video Settings, Entity Distance to see large screens from far."));
        line(src, info("Other commands can check /execute if score " + SCORE_HOLDER + " " + SCORE_OBJECTIVE + " matches 1"));
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> ctx, int w, int h) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel lvl = src.getLevel();
        EntityType<?> rawType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("cushion"));
        if (rawType == null || !"cushion".equals(EntityType.getKey(rawType).getPath())) {
            src.sendFailure(Component.literal("Cushions are not available in this Minecraft version."));
            return 0;
        }
        applyViewRange(rawType);
        stopAll(src.getServer());

        BlockPos base = player.blockPosition();
        int ox = base.getX() + 2;
        int oz = base.getZ() + 2;
        int cy = base.getY();
        int py = cy - 1;
        // Cushion vzdy sedi -0.22 bloku pod svou "normalni" pozici a pod
        // nim vzdy sviti waxed copper bulb (misto obycejneho kamene) -
        // diky tomu obrazovka sviti i v noci, a pripadny prechod na
        // colors=64/176 uz nemusi nic presouvat/menit pozici, jen se
        // strida MEZI ruznymi svitivymi bloky (viz applyMediaFrame).
        BlockState support = CushionColorPalette.defaultSupportState();
        Cushion[] grid = new Cushion[w * h];
        int placed = 0;
        for (int z = 0; z < h; ++z) {
            for (int x = 0; x < w; ++x) {
                lvl.setBlock(new BlockPos(ox + x, py, oz + z), support, 2, 512);
                @SuppressWarnings("unchecked")
                Cushion c = new Cushion((EntityType<Cushion>) rawType, (Level) lvl);
                c.setColor(DyeColor.WHITE);
                c.setPos(ox + x + 0.5, cy - 0.22, oz + z + 0.5);
                if (!lvl.addFreshEntity(c) || !c.survives()) continue;
                grid[z * w + x] = c;
                ++placed;
            }
        }
        level = lvl;
        pixels = grid;
        gridW = w;
        gridH = h;
        originX = ox;
        cushionY = cy;
        originZ = oz;
        lastTier = new int[w * h];
        // Podpurny blok uz je pri stavbe waxed copper bulb (tier 0), takze
        // rovnou nastavime lastTier na 0 - prvni applyMediaFrame tak
        // nebude zbytecne znovu pokladat uplne stejny blok.
        Arrays.fill(lastTier, 0);
        say(src, "Built a " + w + "x" + h + " screen.");
        if (placed < w * h) {
            say(src, (w * h - placed) + " cushions could not be placed (need open space to build in).");
        }
        if (w * h > PERF_WARN_PIXELS) {
            say(src, "That is a large screen and may cause lag.");
        }
        saveScreenState(src.getServer());
        return 1;
    }

    private static int image(CommandContext<CommandSourceStack> ctx, String file, CompoundTag attrs) {
        CommandSourceStack src = ctx.getSource();
        if (pixels == null) return needScreen(src);
        CushionColorPalette.Mode mode = parseColorsMode(attrs);
        boolean bake = parseBake(attrs);
        CushionEncoder.ScaleMode scaling = parseScaling(attrs);
        stopAll(src.getServer());
        try {
            File f = resolveMedia(file);
            if (f == null) return notFound(src, file);
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                src.sendFailure(Component.literal("Could not read that image. Use PNG or JPG."));
                return 0;
            }
            int[] palette = CushionColorPalette.buildFlatPalette(mode);
            int[] idx = CushionEncoder.encode(img, gridW, gridH, palette, true, scaling);
            applyMediaFrame(idx, mode);
            if (bake) {
                // "Zapece" aktualni obsah natrvalo - cushiony a bloky
                // zustanou presne takhle stat, ale mod uz je dal
                // NEBUDE sledovat (zadny /cushionscreens stop/clear/
                // video/gif uz na ne nebude mit vliv a nebudou se ani
                // ukladat do stavoveho souboru pro obnoveni po
                // restartu - proste uz od teto chvile nejsou "obrazovka",
                // jsou to jen normalni entity a bloky ve svete).
                pixels = null;
                gridW = 0;
                gridH = 0;
                lastTier = null;
                deleteScreenState(src.getServer());
                say(src, "Baked " + file + colorSuffix(mode) + " permanently. Build a new screen to keep using CushionScreens here.");
            } else {
                say(src, "Showing " + file + colorSuffix(mode) + ".");
            }
            return 1;
        } catch (Throwable t) {
            CushionScreens.LOG.error("Failed to load image", t);
            src.sendFailure(Component.literal("Could not load that image."));
            return 0;
        }
    }

    private static int gif(CommandContext<CommandSourceStack> ctx, String file, CompoundTag attrs) {
        CommandSourceStack src = ctx.getSource();
        if (pixels == null) return needScreen(src);
        CushionColorPalette.Mode mode = parseColorsMode(attrs);
        boolean loop = parseLoop(attrs);
        CushionEncoder.ScaleMode scaling = parseScaling(attrs);
        stopAll(src.getServer());
        try {
            File f = resolveMedia(file);
            if (f == null) return notFound(src, file);
            CushionGif.Clip clip = CushionGif.decode(f, MAX_GIF_FRAMES);
            int n = clip.frames.length;
            int[] palette = CushionColorPalette.buildFlatPalette(mode);
            int[][] frames = new int[n][];
            int[] ticks = new int[n];
            for (int i = 0; i < n; ++i) {
                frames[i] = CushionEncoder.encode(clip.frames[i], gridW, gridH, palette, true, scaling);
                ticks[i] = Math.max(1, Math.round(clip.delayCentis[i] / 5.0f));
            }
            startClip(src.getServer(), frames, ticks, mode, loop);
            say(src, "Playing " + file + colorSuffix(mode) + " (" + n + " frames)" + (loop ? ", looping" : "") + ".");
            return 1;
        } catch (Throwable t) {
            CushionScreens.LOG.error("Failed to load GIF", t);
            src.sendFailure(Component.literal("Could not load that GIF."));
            return 0;
        }
    }

    private static int video(CommandContext<CommandSourceStack> ctx, int fps, String file, boolean audio,
                              Collection<ServerPlayer> targets, CompoundTag attrs) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (pixels == null) return needScreen(src);
        CushionColorPalette.Mode mode = parseColorsMode(attrs);
        boolean loop = parseLoop(attrs);
        int volume = parseVolume(attrs);
        CushionEncoder.ScaleMode scaling = parseScaling(attrs);
        double seek = parseSeek(attrs);
        final String fileName = file.trim();
        // null = vysilat vsem hracum na serveru (vychozi chovani).
        final List<ServerPlayer> audioTargets = targets == null ? null : new ArrayList<>(targets);

        File f;
        try {
            f = resolveMedia(fileName);
        } catch (IOException e) {
            src.sendFailure(Component.literal("Could not open the media folder."));
            return 0;
        }
        if (f == null) return notFound(src, fileName);

        ServerPlayer player = src.getPlayerOrException();
        int gw = gridW;
        int gh = gridH;
        int[] palette = CushionColorPalette.buildFlatPalette(mode);
        say(src, "Loading " + fileName + colorSuffix(mode) + (audio ? " (with audio)" : "") + (loop ? " (looping)" : "")
            + (seek > 0 ? " (seek " + seek + "s)" : "") + ". It will start playing shortly."
            + (mode != CushionColorPalette.Mode.DEFAULT ? " This may cause lag while playing (changes blocks every frame)." : ""));
        Thread t = new Thread(() -> {
            try {
                int[][] frames = CushionVideo.decodeToIndices(FFMPEG, f, gw, gh, fps, CONFIG.maxFrames, palette, scaling, seek);
                byte[] pcm = null;
                if (audio) {
                    try {
                        pcm = CushionAudio.extractPcm(FFMPEG, f, seek);
                    } catch (Throwable audioErr) {
                        CushionScreens.LOG.warn("Failed to extract audio, playing silently", audioErr);
                    }
                }
                pendingW = gw;
                pendingH = gh;
                pendingTicksPerFrame = Math.max(1, Math.round(20.0f / fps));
                pendingLabel = fileName;
                pendingNotify = player;
                pendingAudioPcm = pcm;
                pendingAudioTargets = audioTargets;
                pendingMode = mode;
                pendingLoop = loop;
                pendingVolume = volume;
                pendingFrames = frames;
            } catch (Throwable e) {
                pendingNotify = player;
                pendingError = e.getMessage() == null ? e.toString() : e.getMessage();
                CushionScreens.LOG.error("Failed to load video", e);
            }
        }, "cushionscreens-video");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    // Stahne obrazek nebo GIF z http(s) URL a zobrazi ho stejne jako
    // /cushionscreens image resp. gif - poznamena se podle prvnich bajtu
    // (GIF87a/GIF89a magic), ne podle koncovky URL (ta casto chybi, napr.
    // i.imgur.com/xyz bez pripony). Video/audio streamy z URL zatim
    // nejsou podporovane.
    private static int url(CommandContext<CommandSourceStack> ctx, String url, CompoundTag attrs) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        if (pixels == null) return needScreen(src);
        CushionColorPalette.Mode mode = parseColorsMode(attrs);
        boolean loopAttr = parseLoop(attrs);
        CushionEncoder.ScaleMode scaling = parseScaling(attrs);
        ServerPlayer player = src.getPlayerOrException();
        int gw = gridW;
        int gh = gridH;
        say(src, "Downloading " + url + "...");
        Thread t = new Thread(() -> {
            try {
                byte[] data = CushionUrlFetch.download(url);
                Path cacheFile = mediaDir().resolve(".cache-url");
                Files.createDirectories(cacheFile.getParent());
                Files.write(cacheFile, data);

                int[] palette = CushionColorPalette.buildFlatPalette(mode);
                int[][] frames;
                int[] frameTicks;
                String label;
                boolean loop;
                if (CushionUrlFetch.looksLikeGif(data)) {
                    CushionGif.Clip clip = CushionGif.decode(cacheFile.toFile(), MAX_GIF_FRAMES);
                    int n = clip.frames.length;
                    frames = new int[n][];
                    frameTicks = new int[n];
                    for (int i = 0; i < n; ++i) {
                        frames[i] = CushionEncoder.encode(clip.frames[i], gw, gh, palette, true, scaling);
                        frameTicks[i] = Math.max(1, Math.round(clip.delayCentis[i] / 5.0f));
                    }
                    loop = loopAttr;
                    label = "GIF from " + url + (loop ? ", looping" : "");
                } else {
                    BufferedImage img = ImageIO.read(cacheFile.toFile());
                    if (img == null) throw new IOException("Could not decode that as an image or GIF");
                    int[] idx = CushionEncoder.encode(img, gw, gh, palette, true, scaling);
                    frames = new int[][]{idx};
                    // Staticky obrazek - obrovsky pocet ticku na "snimek" =
                    // v praxi nikdy nedobehne, chova se jako trvaly obrazek.
                    // "loop" na jedinem snimku stejne nic nemeni.
                    frameTicks = new int[]{Integer.MAX_VALUE / 2};
                    loop = true;
                    label = "image from " + url;
                }
                pendingW = gw;
                pendingH = gh;
                pendingFrameTicks = frameTicks;
                pendingLabel = label;
                pendingNotify = player;
                pendingMode = mode;
                pendingLoop = loop;
                pendingFrames = frames;
            } catch (Throwable e) {
                pendingNotify = player;
                pendingError = e.getMessage() == null ? e.toString() : e.getMessage();
                CushionScreens.LOG.error("Failed to load URL", e);
            }
        }, "cushionscreens-url");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> ctx, String pat, int speed) {
        CommandSourceStack src = ctx.getSource();
        if (pixels == null) return needScreen(src);
        stopAudioBroadcast(src.getServer());
        clipPlaying = false;
        paused = false;
        pattern = pat.toLowerCase();
        periodTicks = speed;
        tickCounter = 0;
        frame = 0;
        playing = true;
        say(src, "Playing " + pattern + ".");
        return 1;
    }

    private static int range(CommandContext<CommandSourceStack> ctx, int chunks) {
        viewChunks = chunks;
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("cushion"));
        if (t != null) applyViewRange(t);
        say(ctx.getSource(), "View distance set to " + chunks + " chunks. Rebuild the screen to apply.");
        return 1;
    }

    private static int maxFrames(CommandContext<CommandSourceStack> ctx, int frames) {
        CONFIG.maxFrames = frames;
        CONFIG.save();
        say(ctx.getSource(), "Max video frames set to " + frames + " (applies to videos loaded from now on).");
        return 1;
    }

    private static int pause(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!playing && !clipPlaying) {
            say(src, "Nothing is currently playing.");
            return 0;
        }
        if (paused) {
            say(src, "Already paused.");
            return 0;
        }
        paused = true;
        CushionAudioNetworkServer.pauseAll(src.getServer());
        say(src, "Paused.");
        return 1;
    }

    private static int resume(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!paused) {
            say(src, "Not paused.");
            return 0;
        }
        paused = false;
        CushionAudioNetworkServer.resumeAll(src.getServer());
        say(src, "Resumed.");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        stopAll(ctx.getSource().getServer());
        say(ctx.getSource(), "Stopped.");
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        stopAll(server);
        clipFrames = null;
        if (pixels != null) {
            for (Cushion c : pixels) {
                if (c == null || c.isRemoved()) continue;
                c.discard();
            }
        }
        if (level != null && gridW > 0) {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int z = 0; z < gridH; ++z) {
                for (int x = 0; x < gridW; ++x) {
                    level.setBlock(new BlockPos(originX + x, cushionY - 1, originZ + z), air, 2, 512);
                }
            }
        }
        pixels = null;
        gridH = 0;
        gridW = 0;
        lastTier = null;
        pendingRestore = null;
        deleteScreenState(server);
        say(ctx.getSource(), "Screen removed.");
        return 1;
    }

    public static void serverTick(MinecraftServer server) {
        syncPlayingFlag(server);
        if (pendingRestore != null) {
            tryRestoreScreen(server);
        }
        installPendingVideo(server);
        if (pixels == null) return;
        // Zamrzne presne na aktualnim snimku/vzoru - nic se dal
        // nedekoduje ani neaktualizuje, dokud nepijde /cushionscreens
        // resume. Zadne opetovne nacitani od zacatku.
        if (paused) return;

        if (clipPlaying && clipFrames != null && clipFrames.length > 0) {
            if (clipFrames[clipIndex].length != gridW * gridH) {
                clipPlaying = false;
                return;
            }
            if (++clipTickAcc >= clipFrameTicks[clipIndex]) {
                clipTickAcc = 0;
                if (clipIndex + 1 >= clipFrames.length) {
                    if (clipLoop) {
                        clipIndex = 0;
                        applyMediaFrame(clipFrames[0], clipMode);
                    } else {
                        // Konec bez loop - zastavit a ukazat vychozi
                        // bilou obrazovku (16 barev, waxed copper bulb).
                        clipPlaying = false;
                        stopAudioBroadcast(server);
                        showDefaultBlankScreen();
                    }
                } else {
                    clipIndex++;
                    applyMediaFrame(clipFrames[clipIndex], clipMode);
                }
            }
            return;
        }
        if (playing) {
            if (++tickCounter < periodTicks) return;
            tickCounter = 0;
            int[] buf = new int[gridW * gridH];
            for (int z = 0; z < gridH; ++z) {
                for (int x = 0; x < gridW; ++x) {
                    buf[z * gridW + x] = colorIndexAt(x, z, frame);
                }
            }
            applyMediaFrame(buf, CushionColorPalette.Mode.DEFAULT);
            ++frame;
        }
    }

    // Vychozi bila obrazovka po doběhnutí videa/gifu bez loop - 16 barev,
    // flat index 0 = (dye WHITE, tier 0/waxed copper bulb).
    private static void showDefaultBlankScreen() {
        if (pixels == null) return;
        int[] blank = new int[gridW * gridH];
        applyMediaFrame(blank, CushionColorPalette.Mode.DEFAULT);
    }

    private static void installPendingVideo(MinecraftServer server) {
        String err = pendingError;
        if (err != null) {
            ServerPlayer p = pendingNotify;
            pendingError = null;
            pendingNotify = null;
            if (p != null) {
                p.sendSystemMessage(Component.literal("Could not play the video. Make sure FFmpeg is installed."));
            }
            return;
        }
        int[][] frames = pendingFrames;
        if (frames == null) return;
        pendingFrames = null;
        ServerPlayer p = pendingNotify;
        pendingNotify = null;
        byte[] pcm = pendingAudioPcm;
        pendingAudioPcm = null;
        List<ServerPlayer> audioTargets = pendingAudioTargets;
        pendingAudioTargets = null;
        CushionColorPalette.Mode mode = pendingMode;
        pendingMode = CushionColorPalette.Mode.DEFAULT;
        boolean loop = pendingLoop;
        pendingLoop = false;
        int volume = pendingVolume;
        pendingVolume = 100;
        int[] presetTicks = pendingFrameTicks;
        pendingFrameTicks = null;

        if (pixels == null || pendingW != gridW || pendingH != gridH) {
            if (p != null) {
                p.sendSystemMessage(Component.literal("The screen changed while loading. Rebuild and try again."));
            }
            return;
        }
        int[] ticks;
        if (presetTicks != null) {
            ticks = presetTicks;
        } else {
            ticks = new int[frames.length];
            Arrays.fill(ticks, pendingTicksPerFrame);
        }
        startClip(server, frames, ticks, mode, loop);

        if (pcm != null) {
            CushionAudioNetworkServer.broadcast(server, pcm, CushionAudio.SAMPLE_RATE, CushionAudio.CHANNELS, audioTargets, loop, volume);
        }
        if (p != null) {
            p.sendSystemMessage(Component.literal("Now playing " + pendingLabel + (pcm != null ? " (with audio)" : "") + "."));
        }
    }

    private static void startClip(MinecraftServer server, int[][] frames, int[] ticks, CushionColorPalette.Mode mode, boolean loop) {
        stopAll(server);
        clipFrames = frames;
        clipFrameTicks = ticks;
        clipIndex = 0;
        clipTickAcc = 0;
        clipPlaying = true;
        clipMode = mode;
        clipLoop = loop;
        applyMediaFrame(frames[0], mode);
    }

    // Pouziva image/gif/video/play - idx jsou indexy do "ploche" palety
    // (CushionColorPalette): tier*16+barva. Blok pod polstarem se meni jen
    // kdyz se tier skutecne zmenil oproti minulemu snimku (viz lastTier) -
    // setBlock je drahy (prepocet osvetleni chunku), takze se timhle
    // vyhneme zbytecnym volanim kdyz se jas na danem pixelu nezmenil.
    private static void applyMediaFrame(int[] idx, CushionColorPalette.Mode mode) {
        for (int z = 0; z < gridH; ++z) {
            for (int x = 0; x < gridW; ++x) {
                int i = z * gridW + x;
                int flat = idx[i];
                int dye = CushionColorPalette.dyeIndexForPixel(mode, flat);
                Cushion c = pixels[i];
                if (c != null && !c.isRemoved()) {
                    c.setColor(DYES[Math.floorMod(dye, DYES.length)]);
                }
                int tier = flat / 16;
                if (lastTier[i] != tier) {
                    lastTier[i] = tier;
                    BlockState state = CushionColorPalette.blockStateForPixel(mode, flat);
                    level.setBlock(new BlockPos(originX + x, cushionY - 1, originZ + z), state, 2, 512);
                }
            }
        }
    }

    private static String colorSuffix(CushionColorPalette.Mode mode) {
        return mode == CushionColorPalette.Mode.DEFAULT ? "" : " (" + mode.totalColors + " colors)";
    }

    private static int colorIndexAt(int x, int y, int f) {
        switch (pattern) {
            case "rainbow":
                return Math.floorMod(x + y + f, 16);
            case "bars":
                return Math.floorMod((x + f) / 2, 16);
            case "noise": {
                int h = x * 73856093 ^ y * 19349663 ^ f * 83492791;
                return Math.floorMod(h, 16);
            }
        }
        double v = Math.sin(x * 0.5) + Math.sin(y * 0.5) + Math.sin((x + y + f) * 0.3)
            + Math.sin(Math.sqrt((double) x * x + (double) y * y) * 0.4 - f * 0.2);
        return (int) ((v + 4.0) / 8.0 * 15.0);
    }

    private static void applyViewRange(EntityType<?> cushionType) {
        try {
            Field f = EntityType.class.getDeclaredField("clientTrackingRange");
            f.setAccessible(true);
            f.setInt(cushionType, viewChunks);
        } catch (Throwable ignored) {
        }
    }

    // Zavola se pri startu serveru (viz CushionScreens.onInitialize). Jen
    // NACTE ulozeny stav do pameti - samotne hledani entit podle UUID
    // probiha az v tryRestoreScreen(), volane kazdy tick, protoze hned po
    // startu jeste nemusi byt chunky se skrinou nactene.
    public static void onServerStarted(MinecraftServer server) {
        try {
            Path file = server.getWorldPath(LevelResource.ROOT).resolve(SAVE_FILE);
            if (!Files.exists(file)) return;
            String json = Files.readString(file);
            CushionScreenState state = GSON.fromJson(json, CushionScreenState.class);
            if (state == null || state.uuids == null) return;
            pendingRestore = state;
            pendingRestoreTicks = 0;
            CushionScreens.LOG.info("Found a saved cushion screen, trying to restore it...");
        } catch (Exception e) {
            CushionScreens.LOG.warn("Failed to load saved cushion screen state", e);
        }
    }

    private static void saveScreenState(MinecraftServer server) {
        if (pixels == null || level == null) return;
        CushionScreenState state = new CushionScreenState();
        state.gridW = gridW;
        state.gridH = gridH;
        state.originX = originX;
        state.originZ = originZ;
        state.cushionY = cushionY;
        state.viewChunks = viewChunks;
        state.dimension = level.dimension().identifier().toString();
        state.uuids = new String[pixels.length];
        for (int i = 0; i < pixels.length; ++i) {
            Cushion c = pixels[i];
            state.uuids[i] = (c != null && !c.isRemoved()) ? c.getUUID().toString() : "";
        }
        try {
            Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(SAVE_FILE), GSON.toJson(state));
        } catch (IOException e) {
            CushionScreens.LOG.warn("Failed to save cushion screen state", e);
        }
    }

    private static void deleteScreenState(MinecraftServer server) {
        try {
            Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve(SAVE_FILE));
        } catch (IOException ignored) {
        }
    }

    // Zkusi kazdy tick znovu najit vsechny cushiony podle ulozenych UUID.
    // Hned po startu serveru jeste nemusi byt prislusne chunky nactene
    // (level.getEntity(UUID) najde jen entity z JIZ nactenych chunku),
    // proto se to zkousi opakovane, dokud se nenajdou vsechny, nebo dokud
    // nevyprsi casovy limit (obrazovka uz nejspis neexistuje - rucne
    // smazana mimo mod apod.).
    private static void tryRestoreScreen(MinecraftServer server) {
        CushionScreenState st = pendingRestore;
        if (++pendingRestoreTicks > 20 * 60) {
            pendingRestore = null;
            CushionScreens.LOG.warn("Giving up trying to restore the cushion screen after 60s.");
            return;
        }
        ServerLevel lvl = resolveDimension(server, st.dimension);
        if (lvl == null) return;

        int total = st.uuids.length;
        Cushion[] grid = new Cushion[total];
        int found = 0;
        int expected = 0;
        for (int i = 0; i < total; ++i) {
            String us = st.uuids[i];
            if (us == null || us.isEmpty()) continue;
            ++expected;
            try {
                Entity e = lvl.getEntity(UUID.fromString(us));
                if (e instanceof Cushion c && !c.isRemoved()) {
                    grid[i] = c;
                    ++found;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (found < expected) return; // zkusime znovu priste tick

        level = lvl;
        pixels = grid;
        gridW = st.gridW;
        gridH = st.gridH;
        originX = st.originX;
        originZ = st.originZ;
        cushionY = st.cushionY;
        viewChunks = st.viewChunks;
        lastTier = new int[grid.length];
        Arrays.fill(lastTier, -1);
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("cushion"));
        if (t != null) applyViewRange(t);
        pendingRestore = null;
        CushionScreens.LOG.info("Restored cushion screen ({} cushions).", found);
    }

    private static ServerLevel resolveDimension(MinecraftServer server, String dimensionId) {
        if (dimensionId == null) return server.overworld();
        try {
            Identifier id = Identifier.parse(dimensionId);
            ServerLevel lvl = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            return lvl != null ? lvl : server.overworld();
        } catch (Exception e) {
            return server.overworld();
        }
    }

    // stopAll se vola i pri build/image/gif atd. jako "reset stavu", ne jen na
    // /cushionscreens stop - proto tady take zastavime pripadne hrajici audio.
    private static void stopAll(MinecraftServer server) {
        playing = false;
        clipPlaying = false;
        paused = false;
        stopAudioBroadcast(server);
    }

    private static void stopAudioBroadcast(MinecraftServer server) {
        if (server == null) return;
        CushionAudioNetworkServer.stopAll(server);
    }

    // Udrzuje scoreboard "cc_playing" synchronizovany se skutecnym stavem
    // prehravani, aby to slo testovat vanilla prikazem:
    //   /execute if score CushionScreens cc_playing matches 1 run ...
    private static void syncPlayingFlag(MinecraftServer server) {
        boolean now = playing || clipPlaying;
        if (now == lastPlayingSignal) return;
        lastPlayingSignal = now;
        try {
            server.getCommands().getDispatcher().execute(
                "scoreboard players set " + SCORE_HOLDER + " " + SCORE_OBJECTIVE + " " + (now ? 1 : 0),
                server.createCommandSourceStack().withSuppressedOutput()
            );
        } catch (Throwable t) {
            CushionScreens.LOG.warn("Failed to sync cc_playing scoreboard flag", t);
        }
    }

    private static Path mediaDir() {
        return FabricLoader.getInstance().getGameDir().resolve("cushionscreens");
    }

    private static File resolveMedia(String file) throws IOException {
        Path dir = mediaDir();
        Files.createDirectories(dir, new FileAttribute[0]);
        File f = dir.resolve(file).toFile();
        return f.isFile() ? f : null;
    }

    private static int needScreen(CommandSourceStack src) {
        src.sendFailure(Component.literal("Build a screen first: /cushionscreens build <width> <height>"));
        return 0;
    }

    private static int notFound(CommandSourceStack src, String file) {
        src.sendFailure(Component.literal("File not found. Put " + file + " in " + mediaDir()));
        return 0;
    }

    private static void say(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
    }

    private static void line(CommandSourceStack src, Component c) {
        src.sendSuccess(() -> c, false);
    }

    private static Component header(String text) {
        return Component.literal(text).withStyle(s -> s.withColor(TextColor.fromRgb(0xFFAA00)).withBold(true));
    }

    private static Component cmdLine(String cmd, String desc) {
        return Component.literal(cmd).withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(desc).withStyle(ChatFormatting.GRAY));
    }

    private static Component info(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }
}
