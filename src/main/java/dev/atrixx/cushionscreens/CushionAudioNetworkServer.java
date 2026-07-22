package dev.atrixx.cushionscreens;

import dev.atrixx.cushionscreens.network.CushionAudioChunk;
import dev.atrixx.cushionscreens.network.CushionAudioStart;
import dev.atrixx.cushionscreens.network.CushionAudioStop;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Rozesila zvuk hracum na serveru. Pokud "targets" je null, posle se
 * vsem hracum (vychozi chovani pro /cushionscreens video ... audio bez
 * ciloveho vyberu). Jinak jen hracum v predanem seznamu (napr. vysledek
 * EntityArgument.players() z prikazu "audio @a"/"audio @p"/"audio Jmeno").
 */
public final class CushionAudioNetworkServer {

    private static final int CHUNK_SIZE = 32 * 1024;

    private CushionAudioNetworkServer() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.clientboundPlay().register(CushionAudioStart.TYPE, CushionAudioStart.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CushionAudioChunk.TYPE, CushionAudioChunk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CushionAudioStop.TYPE, CushionAudioStop.CODEC);
    }

    public static void broadcast(MinecraftServer server, byte[] pcm, int sampleRate, int channels,
                                  Collection<ServerPlayer> targets) {
        Collection<ServerPlayer> players = targets != null ? targets : server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (!ServerPlayNetworking.canSend(player, CushionAudioStart.TYPE)) continue;
            ServerPlayNetworking.send(player, new CushionAudioStart(sampleRate, channels, pcm.length));
            for (int off = 0; off < pcm.length; off += CHUNK_SIZE) {
                int len = Math.min(CHUNK_SIZE, pcm.length - off);
                byte[] chunk = new byte[len];
                System.arraycopy(pcm, off, chunk, 0, len);
                ServerPlayNetworking.send(player, new CushionAudioChunk(chunk));
            }
        }
    }

    public static void stopAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, CushionAudioStop.TYPE)) continue;
            ServerPlayNetworking.send(player, new CushionAudioStop());
        }
    }
}
