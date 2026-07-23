package dev.atrixx.cushionscreens.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Prazdny signal "pozastav prehravani zvuku" (/cushionscreens pause). */
public record CushionAudioPause() implements CustomPacketPayload {

    public static final Type<CushionAudioPause> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("cushionscreens", "audio_pause"));

    public static final StreamCodec<FriendlyByteBuf, CushionAudioPause> CODEC =
        StreamCodec.unit(new CushionAudioPause());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
