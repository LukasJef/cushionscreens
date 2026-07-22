package dev.atrixx.cushionscreens.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Prazdny signal "zastav prehravani zvuku ted" (napr. /cushionscreens stop). */
public record CushionAudioStop() implements CustomPacketPayload {

    public static final Type<CushionAudioStop> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("cushionscreens", "audio_stop"));

    public static final StreamCodec<FriendlyByteBuf, CushionAudioStop> CODEC =
        StreamCodec.unit(new CushionAudioStop());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
