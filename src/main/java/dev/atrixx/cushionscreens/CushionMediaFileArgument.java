package dev.atrixx.cushionscreens;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Nazev souboru pro /cushionscreens image|gif|video.
 *
 * Na rozdil od beznehe greedyString cte cely zbytek radku AZ na prvni
 * "rezervovane" slovo - "audio" - NEBO az na zacatek NBT compound tagu
 * "{...}" s dalsimi atributy (colors, bake, ...), pokud tam je. Diky tomu
 * jde za timhle argumentem v strome prikazu navazat dalsi uzly (napr.
 * "audio" + volitelny seznam hracu, a/nebo {atributy}), a zaroven funguje
 * napoveda (tab-completion) na skutecne soubory ve slozce cushionscreens/.
 *
 * Ocekavane poradi je <soubor> [audio [hraci]] [{atributy}] pro video,
 * resp. <soubor> [{atributy}] pro image/gif - jine poradi neni
 * podporovane. Samotne {atributy} uz parsuje vanilla CompoundTagArgument.
 *
 * Omezeni: pokud by nazev souboru sam obsahoval samostatne slovo "audio"
 * oddelene mezerami (napr. "muj audio soubor.mp4"), bude to chybne
 * rozpoznano jako prepinac. V praxi je to velmi vzacne, ale je dobre o
 * tom vedet.
 */
public final class CushionMediaFileArgument implements ArgumentType<String> {

    private static final SimpleCommandExceptionType EMPTY =
        new SimpleCommandExceptionType(Component.literal("Expected a file name"));

    private static final String[] STOP_WORDS = {"audio"};

    private CushionMediaFileArgument() {
    }

    public static CushionMediaFileArgument file() {
        return new CushionMediaFileArgument();
    }

    public static String getFile(CommandContext<?> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String remaining = reader.getRemaining();
        // Index mezery TESNE PRED prvnim rezervovanym slovem / zacatkem
        // "{" (ne indexu slova/znaku samotneho) - Brigadier po parse()
        // ocekava, ze kurzor stoji bud na konci vstupu, nebo presne na
        // mezere, jinak to nahlasi "Expected whitespace to end one
        // argument, but found trailing data". Tu mezeru pak sam Brigadier
        // preskoci pred parsovanim dalsiho uzlu.
        int splitAt = findSplitPoint(remaining);
        String file;
        if (splitAt >= 0) {
            file = remaining.substring(0, splitAt).trim();
            reader.setCursor(start + splitAt);
        } else {
            file = remaining.trim();
            reader.setCursor(reader.getTotalLength());
        }
        if (file.isEmpty()) {
            throw EMPTY.createWithContext(reader);
        }
        return file;
    }

    // Najde index mezery, ktera predchazi prvnimu vyskytu nektereho ze
    // STOP_WORDS, NEBO prvnimu slovu zacinajicimu "{" (zacatek NBT
    // compound tagu s atributy) - vse pred touto mezerou je nazev
    // souboru.
    private static int findSplitPoint(String remaining) {
        int len = remaining.length();
        int cursor = 0;
        while (cursor < len) {
            while (cursor < len && remaining.charAt(cursor) == ' ') cursor++;
            int wordStart = cursor;
            while (cursor < len && remaining.charAt(cursor) != ' ') cursor++;
            int wordLen = cursor - wordStart;
            if (wordLen > 0 && wordStart > 0) {
                if (remaining.charAt(wordStart) == '{') {
                    return wordStart - 1;
                }
                for (String stop : STOP_WORDS) {
                    if (wordLen == stop.length() && remaining.regionMatches(true, wordStart, stop, 0, wordLen)) {
                        return wordStart - 1;
                    }
                }
            }
        }
        return -1;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        List<String> options = new ArrayList<>();
        try (Stream<Path> files = Files.list(mediaDir())) {
            files.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .forEach(options::add);
        } catch (IOException ignored) {
        }
        String typedTrimmed = typed.stripTrailing();
        String lower = typedTrimmed.toLowerCase();
        boolean alreadyHasStopWord = lower.endsWith("audio") || typedTrimmed.endsWith("{");
        if (!typed.isBlank() && !alreadyHasStopWord) {
            options.add(typedTrimmed + " audio");
            options.add(typedTrimmed + " {");
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static Path mediaDir() {
        return FabricLoader.getInstance().getGameDir().resolve("cushionscreens");
    }
}
