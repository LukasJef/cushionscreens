package dev.atrixx.cushionscreens.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Jeden kus (napr. 32 KB) syroveho PCM streamu. Viz CushionAudioStart. */
public record CushionAudioChunk(byte[] data) implements CustomPacketPayload {

    public static final Type<CushionAudioChunk> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("cushionscreens", "audio_chunk"));

    public static final StreamCodec<FriendlyByteBuf, CushionAudioChunk> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeByteArray(value.data),
        buf -> new CushionAudioChunk(buf.readByteArray())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
