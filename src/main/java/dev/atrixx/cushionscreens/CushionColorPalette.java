package dev.atrixx.cushionscreens;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;

/**
 * Rozsirena paleta barev: kombinace 16 barev polstare x blok pod nim.
 * Cushion vzdy sedi -0.22 bloku pod svou "normalni" pozici (viz
 * CushionScreenCommand - fyzicky zasahuje do bloku pod sebou, aby pri
 * renderovani bral v potaz jeho svetlo) a pod nim vzdy sviti nejaky blok
 * misto obycejneho kamene - i ve vychozim rezimu (Mode.DEFAULT), ktery
 * pouziva vzdy jen tier 0 (waxed copper bulb, plne rozsviceny).
 *
 * RGB hodnoty v MEASURED jsou SKUTECNE ZMERENE (ne teoreticky dopocitane)
 * ze skutecnych in-game screenshotu.
 */
public final class CushionColorPalette {

    public enum Mode {
        // index 0 = waxed copper bulb (viz TIERS)
        DEFAULT(new int[]{0}),
        // 4 stupne oxidace medene bulvy - indexy do TIERS, NE prvni 4 v
        // poli (poradi TIERS odpovida puvodni tabulce, kde jsou mezi nimi
        // proloz furnace/respawn anchor apod.)
        COPPER(new int[]{0, 2, 5, 8}),
        FULL(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        public final int[] tierIndices;

        Mode(int[] tierIndices) {
            this.tierIndices = tierIndices;
        }

        public static Mode fromArgument(String arg) {
            if (arg == null) return DEFAULT;
            return switch (arg) {
                case "64" -> COPPER;
                case "176" -> FULL;
                default -> DEFAULT;
            };
        }
    }

    public record Tier(String blockId, Map<String, String> state, String label) {
    }

    // Poradi MUSI odpovidat sloupcum v MEASURED nize (odpovida puvodni
    // tabulce namerenych hodnot).
    public static final Tier[] TIERS = new Tier[]{
        tier("minecraft:waxed_copper_bulb", "waxed copper bulb", "lit", "true"),               // 0
        tier("minecraft:furnace", "burning furnace", "lit", "true"),                            // 1
        tier("minecraft:waxed_exposed_copper_bulb", "waxed exposed copper bulb", "lit", "true"), // 2
        tier("minecraft:respawn_anchor", "respawn anchor (mostly filled)", "charges", "3"),      // 3
        tier("minecraft:crying_obsidian", "crying obsidian"),                                    // 4
        tier("minecraft:waxed_weathered_copper_bulb", "waxed weathered copper bulb", "lit", "true"), // 5
        tier("minecraft:respawn_anchor", "respawn anchor (partially filled)", "charges", "2"),   // 6
        tier("minecraft:sculk_catalyst", "sculk catalyst"),                                       // 7
        tier("minecraft:waxed_oxidized_copper_bulb", "waxed oxidized copper bulb", "lit", "true"), // 8
        tier("minecraft:magma_block", "magma block"),                                             // 9
        tier("minecraft:smooth_stone", "any block (no light)"),                                   // 10
    };

    // Zmerene RGB hodnoty [barva][tier], barva = poradi DyeColor.values()
    // (WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY,
    // LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK), tier =
    // index do TIERS.
    private static final int[][] MEASURED = {
        {0xFFFFFF, 0xE9DBBD, 0xE8D5AD, 0xE4CD9F, 0xD4BD8F, 0xC1AA7E, 0xB09D76, 0x9F8F6E, 0x797060, 0x797060, 0x2D2F32}, // WHITE
        {0xFAAB51, 0xFA9F42, 0xF99B3E, 0xF5953A, 0xEB8E36, 0xCF7C30, 0xBC732D, 0xAA692B, 0x805327, 0x6C4825, 0x2F251A}, // ORANGE
        {0xD264AF, 0xD25D8B, 0xD15B80, 0xCE5876, 0xC6546D, 0xAE4A5E, 0x9E4557, 0x8F3F52, 0x6C3348, 0x5B2D41, 0x281926}, // MAGENTA
        {0x3EBBD7, 0x3EAEAA, 0x3EAA9C, 0x3DA38F, 0x3B9C84, 0x358872, 0x317E6A, 0x2D7263, 0x245A56, 0x204E4E, 0x12272D}, // LIGHT_BLUE
        {0xFADF77, 0xFACF60, 0xF9CA59, 0xF5C252, 0xEBB94D, 0xCFA143, 0xBC953F, 0xAA873B, 0x806A35, 0x6C5B31, 0x2F2C1F}, // YELLOW
        {0xA7CF42, 0xA7C137, 0xA6BB33, 0xA3B530, 0x9DAC2D, 0x8B9629, 0x7F8B27, 0x727E25, 0x576322, 0x4A5520, 0x222A17}, // LIME
        {0xF197B6, 0xF18C91, 0xF08885, 0xEC847A, 0xE37E71, 0xC86E61, 0xB6665B, 0xA35C55, 0x7C494A, 0x684044, 0x2D2127}, // PINK
        {0x748088, 0x74776E, 0x737465, 0x71705E, 0x6D6B57, 0x615E4C, 0x595847, 0x515043, 0x3F403B, 0x363837, 0x1B1F23}, // GRAY
        {0xB5B2AE, 0xB5A68B, 0xB4A280, 0xB19C76, 0xAA956D, 0x96825E, 0x897858, 0x7C6D53, 0x5F5749, 0x504B43, 0x504B43}, // LIGHT_GRAY
        {0x37B5AD, 0x37A88A, 0x37A47F, 0x369E75, 0x34976C, 0x2F835D, 0x2C7A57, 0x286E51, 0x205748, 0x1D4B41, 0x112627}, // CYAN
        {0xB759DB, 0xB754AD, 0xB6529F, 0xB34F92, 0xAC4B87, 0x984374, 0x8A3E6C, 0x7D3964, 0x5F2E58, 0x502950, 0x24182D}, // PURPLE
        {0x4C7CBC, 0x4C7495, 0x4C7189, 0x4B6D7E, 0x486875, 0x415B64, 0x3B555E, 0x364D58, 0x2B3E4D, 0x253646, 0x141D29}, // BLUE
        {0xB0764D, 0xB06F40, 0xB06C3B, 0xAC6838, 0xA66334, 0x93582E, 0x86512C, 0x794A2A, 0x5C3C26, 0x4E3524, 0x241D1A}, // BROWN
        {0x7A902D, 0x7A8626, 0x798324, 0x777E22, 0x737921, 0x666A1E, 0x5D621D, 0x54591C, 0x41471A, 0x383E19, 0x1C2014}, // GREEN
        {0xD7493F, 0xD74535, 0xD64331, 0xD2412E, 0xCA3F2C, 0xB23827, 0xA23425, 0x923024, 0x6F2821, 0x5E241F, 0x2A1617}, // RED
        {0x414153, 0x413D44, 0x413C3F, 0x403A3B, 0x3E3838, 0x373231, 0x332F2F, 0x2F2C2C, 0x262528, 0x212126, 0x000000}, // BLACK
    };

    private static final BlockState DEFAULT_STATE_FALLBACK = Blocks.SMOOTH_STONE.defaultBlockState();

    private static Tier tier(String blockId, String label) {
        return new Tier(blockId, Map.of(), label);
    }

    private static Tier tier(String blockId, String label, String propKey, String propVal) {
        return new Tier(blockId, Map.of(propKey, propVal), label);
    }

    private CushionColorPalette() {
    }

    /** Sestavi plochou paletu RGB hodnot: index = poradi_tieru_v_modu*16 + barva. */
    public static int[] buildFlatPalette(Mode mode) {
        int[] tiers = mode.tierIndices;
        int[] flat = new int[tiers.length * 16];
        for (int ti = 0; ti < tiers.length; ++ti) {
            int t = tiers[ti];
            for (int d = 0; d < 16; ++d) {
                flat[ti * 16 + d] = MEASURED[d][t];
            }
        }
        return flat;
    }

    public static int dyeIndexForPixel(int flatIndex) {
        return flatIndex % 16;
    }

    public static BlockState blockStateForPixel(Mode mode, int flatIndex) {
        int ti = flatIndex / 16;
        int t = mode.tierIndices[ti];
        BlockState state = stateFor(TIERS[t]);
        return state != null ? state : DEFAULT_STATE_FALLBACK;
    }

    /** Vychozi (tier 0 = waxed copper bulb) support block - pouziva build(). */
    public static BlockState defaultSupportState() {
        BlockState state = stateFor(TIERS[0]);
        return state != null ? state : DEFAULT_STATE_FALLBACK;
    }

    private static BlockState stateFor(Tier t) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(t.blockId()));
        if (block == null || block == Blocks.AIR) return null;
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> e : t.state().entrySet()) {
            state = trySetProperty(state, e.getKey(), e.getValue());
        }
        return state;
    }

    private static BlockState trySetProperty(BlockState state, String propName, String value) {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty(propName);
        if (prop == null) return state;
        return setTyped(state, prop, value);
    }

    private static <T extends Comparable<T>> BlockState setTyped(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }
}
