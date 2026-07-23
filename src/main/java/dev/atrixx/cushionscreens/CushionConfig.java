package dev.atrixx.cushionscreens;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Globalni (ne per-svet) nastaveni modu, ulozene v config slozce hry -
 * na rozdil od stavu obrazovky (CushionScreenState) tohle neni vazane na
 * konkretni save, plati napric vsemi svety.
 */
public final class CushionConfig {

    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cushionscreens.json");

    /** Kolik snimku videa se maximalne dekoduje - viz /cushionscreens maxframes. */
    public int maxFrames = 2000;

    public static CushionConfig load() {
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE);
                CushionConfig cfg = GSON.fromJson(json, CushionConfig.class);
                if (cfg != null) return cfg;
            }
        } catch (Exception e) {
            CushionScreens.LOG.warn("Failed to load config, using defaults", e);
        }
        return new CushionConfig();
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            CushionScreens.LOG.warn("Failed to save config", e);
        }
    }
}
