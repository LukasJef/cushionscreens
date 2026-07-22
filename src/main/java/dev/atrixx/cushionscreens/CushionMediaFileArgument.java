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
 * "rezervovane" slovo - "audio", "colors=64", "colors=176" nebo "bake"
 * (pokud tam je) - diky tomu jde za timhle argumentem v strome prikazu
 * navazat dalsi uzly (napr. "colors=176" a pak "audio" + volitelny seznam
 * hracu, nebo "bake" u obrazku), a zaroven funguje napoveda
 * (tab-completion) na skutecne soubory ve slozce cushionscreens/.
 *
 * Ocekavane poradi je <soubor> [colors=64|colors=176] [audio [hraci]] pro
 * video, resp. <soubor> [colors=64|colors=176] [bake] pro image - jine
 * poradi neni podporovane.
 *
 * Omezeni: pokud by nazev souboru sam obsahoval jedno z rezervovanych slov
 * oddelene mezerami (napr. "muj audio soubor.mp4"), bude to chybne
 * rozpoznano jako prepinac. V praxi je to velmi vzacne, ale je dobre o
 * tom vedet.
 */
public final class CushionMediaFileArgument implements ArgumentType<String> {

    private static final SimpleCommandExceptionType EMPTY =
        new SimpleCommandExceptionType(Component.literal("Expected a file name"));

    private static final String[] STOP_WORDS = {"audio", "colors=64", "colors=176", "bake"};

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
        // Index mezery TESNE PRED prvnim rezervovanym slovem (ne indexu
        // slova samotneho) - Brigadier po parse() ocekava, ze kurzor stoji
        // bud na konci vstupu, nebo presne na mezere, jinak to nahlasi
        // "Expected whitespace to end one argument, but found trailing
        // data". Tu mezeru pak sam Brigadier preskoci pred parsovanim
        // dalsiho uzlu.
        int spaceBeforeStopWord = findSpaceBeforeStopWord(remaining);
        String file;
        if (spaceBeforeStopWord >= 0) {
            file = remaining.substring(0, spaceBeforeStopWord).trim();
            reader.setCursor(start + spaceBeforeStopWord);
        } else {
            file = remaining.trim();
            reader.setCursor(reader.getTotalLength());
        }
        if (file.isEmpty()) {
            throw EMPTY.createWithContext(reader);
        }
        return file;
    }

    // Najde index mezery, ktera predchazi prvnimu vyskytu nektereho z
    // STOP_WORDS oddelenemu mezerami - vse pred touto mezerou je nazev
    // souboru.
    private static int findSpaceBeforeStopWord(String remaining) {
        int len = remaining.length();
        int cursor = 0;
        while (cursor < len) {
            while (cursor < len && remaining.charAt(cursor) == ' ') cursor++;
            int wordStart = cursor;
            while (cursor < len && remaining.charAt(cursor) != ' ') cursor++;
            int wordLen = cursor - wordStart;
            if (wordLen > 0 && wordStart > 0) {
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
        boolean alreadyHasStopWord = lower.endsWith("audio") || lower.endsWith("colors=64")
            || lower.endsWith("colors=176") || lower.endsWith("bake");
        if (!typed.isBlank() && !alreadyHasStopWord) {
            options.add(typedTrimmed + " colors=176");
            options.add(typedTrimmed + " colors=64");
            options.add(typedTrimmed + " audio");
            options.add(typedTrimmed + " bake");
        }
        return SharedSuggestionProvider.suggest(options, builder);
    }

    private static Path mediaDir() {
        return FabricLoader.getInstance().getGameDir().resolve("cushionscreens");
    }
}
