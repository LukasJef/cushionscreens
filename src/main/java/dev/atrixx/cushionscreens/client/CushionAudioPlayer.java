package dev.atrixx.cushionscreens.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
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

    public static synchronized void play(byte[] pcm, int sampleRate, int channels, boolean loop, int volume) {
        stop();
        paused = false;
        long token = ++playToken;
        Thread t = new Thread(() -> runPlayback(pcm, sampleRate, channels, loop, volume, token), "cushionscreens-audio-playback");
        t.setDaemon(true);
        t.start();
    }

    private static void runPlayback(byte[] pcm, int sampleRate, int channels, boolean loop, int volume, long token) {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            currentLine = line;
            line.open(format);
            applyVolume(line, volume);
            line.start();
            do {
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
                // Poznamka: audio se smycku nezavisle na videu - u delsich
                // prehravani muze casem mirne rozjet nesoulad s obrazem,
                // protoze delka PCM streamu a delka smycky videa nemusi
                // sedet uplne presne. Neresi se zatim explicitni resync.
            } while (loop && playToken == token);
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

    // Prevede procenta (0-100) na decibely pro MASTER_GAIN. Aplikuje se
    // primo na SourceDataLine misto prepocitavani PCM vzorku - zachova
    // kvalitu (zadne zaokrouhlovani/clipping) a je to co k tomu Java Sound
    // API nabizi.
    private static void applyVolume(SourceDataLine line, int volumePercent) {
        if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        int pct = Math.max(0, Math.min(100, volumePercent));
        float db;
        if (pct <= 0) {
            db = gain.getMinimum();
        } else {
            db = (float) (20.0 * Math.log10(pct / 100.0));
            db = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db));
        }
        gain.setValue(db);
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
