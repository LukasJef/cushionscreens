package dev.atrixx.cushionscreens.client;

import dev.atrixx.cushionscreens.network.CushionAudioChunk;
import dev.atrixx.cushionscreens.network.CushionAudioStart;
import dev.atrixx.cushionscreens.network.CushionAudioStop;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;

/**
 * Musi byt zaregistrovany jako "client" entrypoint ve fabric.mod.json,
 * jinak se v tride nesmi objevit zadne client-only API (jinak crash na
 * dedikovanem serveru). Viz upraveny fabric.mod.json.
 *
 * Payload typy (PayloadTypeRegistry) se NEregistruji tady - registruji se
 * jednou v CushionScreens.onInitialize() (registrace je globalni pro cely
 * JVM, ne zvlast pro klienta/server). "main" entrypoint bezi driv nez
 * "client" entrypoint, takze v dobe, kdy se spusti tahle trida, uz jsou
 * typy zaregistrovane. Kdyby se zaregistrovaly znovu i tady, Fabric to
 * shodi vyjimkou "already registered".
 */
public final class CushionScreensClient implements ClientModInitializer {

    private static ByteArrayOutputStream buffer;
    private static int expectedBytes;
    private static int sampleRate;
    private static int channels;
    private static boolean wasPaused;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CushionAudioStart.TYPE, (payload, context) -> {
            buffer = new ByteArrayOutputStream(Math.max(64, payload.totalBytes()));
            expectedBytes = payload.totalBytes();
            sampleRate = payload.sampleRate();
            channels = payload.channels();
        });

        ClientPlayNetworking.registerGlobalReceiver(CushionAudioChunk.TYPE, (payload, context) -> {
            ByteArrayOutputStream buf = buffer;
            if (buf == null) return;
            buf.writeBytes(payload.data());
            if (buf.size() >= expectedBytes) {
                byte[] pcm = buf.toByteArray();
                buffer = null;
                int sr = sampleRate;
                int ch = channels;
                context.client().execute(() -> CushionAudioPlayer.play(pcm, sr, ch));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(CushionAudioStop.TYPE, (payload, context) -> {
            buffer = null;
            context.client().execute(CushionAudioPlayer::stop);
        });

        // Minecraft.isPaused() vraci true jedine kdyz je pozastaveny lokalni
        // (singleplayer/integrovany) svet - na multiplayeru/dedikovanem
        // serveru vraci vzdy false, takze se timhle automaticky resi
        // "jen kdyz to neni server".
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean paused = client.isPaused();
            if (paused == wasPaused) return;
            wasPaused = paused;
            if (paused) {
                CushionAudioPlayer.pause();
            } else {
                CushionAudioPlayer.resume();
            }
        });

        // Navrat do hlavniho menu / odpojeni od serveru - audio by jinak
        // hralo dal, protoze pauza (viz vyse) tohle neresi (odpojeni neni
        // "pauza", je to konec relace).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            buffer = null;
            wasPaused = false;
            CushionAudioPlayer.stop();
        });
    }
}

