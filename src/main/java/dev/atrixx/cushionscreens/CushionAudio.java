package dev.atrixx.cushionscreens;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Prevadi zvukovou stopu videa na syrove 16-bit PCM pres ffmpeg.
 * Zamerne se NEpouziva OGG/Vorbis - Java samotna (javax.sound.sampled) umi
 * bez dalsich knihoven prehravat jen nekomprimovane PCM/WAV, takze je
 * nejjednodussi poslat klientovi rovnou syrova data a nechat ho je pustit
 * pres SourceDataLine. ffmpeg musi byt na PATH stejne jako pro video.
 */
public final class CushionAudio {

    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNELS = 2;
    public static final int BITS_PER_SAMPLE = 16;

    private CushionAudio() {
    }

    public static byte[] extractPcm(String ffmpeg, File file) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            ffmpeg, "-nostdin", "-i", file.getAbsolutePath(),
            "-vn",
            "-ac", String.valueOf(CHANNELS),
            "-ar", String.valueOf(SAMPLE_RATE),
            "-f", "s16le",
            "-acodec", "pcm_s16le",
            "-v", "error",
            "pipe:1"
        );
        pb.redirectErrorStream(false);

        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException("could not run '" + ffmpeg + "' for audio extraction: " + e.getMessage());
        }

        StringBuilder err = new StringBuilder();
        Thread drain = new Thread(() -> {
            try (InputStream es = proc.getErrorStream()) {
                int n;
                byte[] b = new byte[4096];
                while ((n = es.read(b)) > 0) {
                    if (err.length() < 8192) err.append(new String(b, 0, n));
                }
            } catch (IOException ignored) {
            }
        }, "cushionscreens-ffmpeg-audio-stderr");
        drain.setDaemon(true);
        drain.start();

        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        try (InputStream in = proc.getInputStream()) {
            in.transferTo(out);
        }
        int code = proc.waitFor();
        byte[] pcm = out.toByteArray();
        if (pcm.length == 0) {
            throw new IOException("no audio decoded"
                + (err.length() > 0 ? ": " + err.toString().trim() : " (ffmpeg exit " + code + ", file may have no audio track)"));
        }
        return pcm;
    }
}
