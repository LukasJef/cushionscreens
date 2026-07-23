package dev.atrixx.cushionscreens.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Oznamuje klientovi, ze prijde stream zvuku: kolik bajtu celkem cekat a s
 * jakym vzorkovacim kmitoctem/poctem kanalu ho prehrat (viz CushionAudio).
 *
 * POZNAMKA: presne API pro CustomPacketPayload / StreamCodec / Identifier se
 * mezi verzemi Minecraftu a Fabric API casto meni (napr. RegistryFriendlyByteBuf
 * misto FriendlyByteBuf, jina signatura StreamCodec.of/composite atd.).
 * Tohle je napsane podle aktualne bezneho vzoru - pred pouzitim over/uprav
 * podle verze, na kterou tvuj projekt cili (stejne jako u ostatnich API,
 * ktera uz mod pouziva, napr. Identifier misto ResourceLocation).
 */
public record CushionAudioStart(int sampleRate, int channels, int totalBytes, boolean loop, int volume) implements CustomPacketPayload {

    public static final Type<CushionAudioStart> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("cushionscreens", "audio_start"));

    public static final StreamCodec<FriendlyByteBuf, CushionAudioStart> CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeInt(value.sampleRate);
            buf.writeInt(value.channels);
            buf.writeInt(value.totalBytes);
            buf.writeBoolean(value.loop);
            buf.writeInt(value.volume);
        },
        buf -> new CushionAudioStart(buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
