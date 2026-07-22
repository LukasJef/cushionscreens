package dev.atrixx.cushionscreens.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Prehrava syrove 16-bit PCM na klientovi bez zavislosti na Minecraftim
 * SoundManageru (ktery by vyzadoval registrovany sound event v resource
 * packu). Bezi na vlastnim vlakne, aby neblokoval render/network thread.
 *
 * Pauza je resena rucne (zapis po malych kouscich + priznak "paused", na
 * kterem vlakno ceka), NE pres SourceDataLine.stop()/start(). Spolehat na
 * to, ze stop() behem blokujiciho write() spolehlive "zamrzne" pozici a
 * start() pokracuje presne odtud, je zavisle na konkretnim zvukovem
 * ovladaci/platforme a na Windows se ukazalo, ze to nefunguje (zvuk se po
 * odpauzovani neobnovil od spravneho mista). Tenhle pristup na tom
 * nezavisi - o pauzu se stara vyhradne nase vlakno.
 */
public final class CushionAudioPlayer {

    private static final Object PAUSE_LOCK = new Object();
    // ~46 ms pri 44100 Hz / 16-bit / stereo - dost maly kousek na rychlou
    // reakci na pauzu, dost velky, aby to nebylo zbytecne rezijni.
    private static final int CHUNK_BYTES = 8192;

    private static volatile SourceDataLine currentLine;
    private static volatile boolean paused;
    // Kazdy play()/stop() zvysi "token" - bezici vlakno prehravani si token
    // pri startu zapamatuje a pri kazde prilezitosti overi, ze je porad
    // aktualni; pokud ne (bylo zavolano stop() nebo zacalo jine video),
    // vlakno se hned ukonci misto aby dal zapisovalo stara data.
    private static volatile long playToken;

    private CushionAudioPlayer() {
    }

    public static synchronized void play(byte[] pcm, int sampleRate, int channels) {
        stop();
        paused = false;
        long token = ++playToken;
        Thread t = new Thread(() -> runPlayback(pcm, sampleRate, channels, token), "cushionscreens-audio-playback");
        t.setDaemon(true);
        t.start();
    }

    private static void runPlayback(byte[] pcm, int sampleRate, int channels, long token) {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            currentLine = line;
            line.open(format);
            line.start();
            int offset = 0;
            while (offset < pcm.length) {
                if (playToken != token) return;
                synchronized (PAUSE_LOCK) {
                    while (paused && playToken == token) {
                        try {
                            PAUSE_LOCK.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                if (playToken != token) return;
                int len = Math.min(CHUNK_BYTES, pcm.length - offset);
                line.write(pcm, offset, len);
                offset += len;
            }
            if (playToken == token) {
                line.drain();
            }
        } catch (LineUnavailableException e) {
            // zadne dostupne zvukove zarizeni na klientovi - ticho ignorujeme
        } finally {
            if (playToken == token) {
                currentLine = null;
            }
        }
    }

    public static void pause() {
        paused = true;
        // stop() na lince navic - na ovladacich, kde funguje spravne, tim
        // ztichne okamzite misto az po dopsani rozjeteho CHUNK_BYTES kousku.
        // I kdyby se na dane platforme choval nespolehlive, na spravnost
        // pauzy uz to nema vliv - tu resi vyhradne priznak "paused" vyse.
        SourceDataLine line = currentLine;
        if (line != null) {
            line.stop();
        }
    }

    public static void resume() {
        synchronized (PAUSE_LOCK) {
            paused = false;
            PAUSE_LOCK.notifyAll();
        }
        SourceDataLine line = currentLine;
        if (line != null) {
            line.start();
        }
    }

    public static synchronized void stop() {
        paused = false;
        ++playToken;
        synchronized (PAUSE_LOCK) {
            PAUSE_LOCK.notifyAll();
        }
        SourceDataLine line = currentLine;
        currentLine = null;
        if (line != null) {
            line.stop();
            line.close();
        }
    }
}
