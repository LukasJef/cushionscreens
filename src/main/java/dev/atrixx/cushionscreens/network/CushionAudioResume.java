package dev.atrixx.cushionscreens.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Prazdny signal "obnov prehravani zvuku" (/cushionscreens resume). */
public record CushionAudioResume() implements CustomPacketPayload {

    public static final Type<CushionAudioResume> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("cushionscreens", "audio_resume"));

    public static final StreamCodec<FriendlyByteBuf, CushionAudioResume> CODEC =
        StreamCodec.unit(new CushionAudioResume());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
