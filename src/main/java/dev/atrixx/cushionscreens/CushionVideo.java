package dev.atrixx.cushionscreens;

import dev.atrixx.cushionscreens.CushionEncoder;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public final class CushionVideo {
    private CushionVideo() {
    }

    public static int[][] decodeToIndices(String ffmpeg, File file, int gw, int gh, int fps, int maxFrames, int[] palette,
                                           CushionEncoder.ScaleMode scaleMode, double seekSeconds) throws IOException, InterruptedException {
        Process proc;
        // STRETCH: roztahne presne na gw:gh (ignoruje pomer stran).
        // CROP: preskaluje tak, aby cela cilova plocha byla pokryta (bez
        // deformace), a orizne prebytek na stred - standardni ffmpeg idiom
        // scale+force_original_aspect_ratio=increase,crop.
        // FIT: preskaluje tak, aby se cely obraz vesel dovnitr (bez
        // deformace i bez oriznuti) a zbytek se dopadduje cerne -
        // scale+force_original_aspect_ratio=decrease,pad.
        String filter = switch (scaleMode) {
            case CROP -> "fps=" + fps + ",scale=" + gw + ":" + gh + ":force_original_aspect_ratio=increase:flags=area,crop=" + gw + ":" + gh;
            case FIT -> "fps=" + fps + ",scale=" + gw + ":" + gh + ":force_original_aspect_ratio=decrease:flags=area,pad=" + gw + ":" + gh + ":(ow-iw)/2:(oh-ih)/2:color=black";
            default -> "fps=" + fps + ",scale=" + gw + ":" + gh + ":flags=area";
        };
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-nostdin");
        if (seekSeconds > 0) {
            // -ss PRED -i = rychle a presne seekovani na klicovy snimek
            // (ffmpeg preskoci, misto aby dekodoval a zahazoval snimky).
            cmd.add("-ss");
            cmd.add(String.valueOf(seekSeconds));
        }
        cmd.add("-i");
        cmd.add(file.getAbsolutePath());
        cmd.add("-vf");
        cmd.add(filter);
        cmd.add("-f");
        cmd.add("rawvideo");
        cmd.add("-pix_fmt");
        cmd.add("rgb24");
        cmd.add("-v");
        cmd.add("error");
        cmd.add("pipe:1");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        try {
            proc = pb.start();
        }
        catch (IOException e) {
            throw new IOException("could not run '" + ffmpeg + "'. Make sure FFmpeg is installed and on your PATH. (" + e.getMessage() + ")");
        }
        StringBuilder err = new StringBuilder();
        Thread drain = new Thread(() -> {
            try (InputStream es = proc.getErrorStream();){
                int n;
                byte[] b = new byte[4096];
                while ((n = es.read(b)) > 0) {
                    if (err.length() >= 8192) continue;
                    err.append(new String(b, 0, n));
                }
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }, "cushionscreens-ffmpeg-stderr");
        drain.setDaemon(true);
        drain.start();
        ArrayList<int[]> frames = new ArrayList<int[]>();
        byte[] buf = new byte[gw * gh * 3];
        boolean hitCap = false;
        try (DataInputStream in = new DataInputStream(proc.getInputStream());){
            while (true) {
                if (frames.size() >= maxFrames) {
                    hitCap = true;
                    break;
                }
                try {
                    in.readFully(buf);
                }
                catch (EOFException eof) {
                    break;
                }
                BufferedImage img = new BufferedImage(gw, gh, 1);
                int p = 0;
                for (int y = 0; y < gh; ++y) {
                    for (int x = 0; x < gw; ++x) {
                        int r = buf[p++] & 0xFF;
                        int g = buf[p++] & 0xFF;
                        int b = buf[p++] & 0xFF;
                        img.setRGB(x, y, r << 16 | g << 8 | b);
                    }
                }
                frames.add(CushionEncoder.encode(img, gw, gh, palette, true));
            }
        }
        if (hitCap) {
            proc.destroy();
        }
        int code = proc.waitFor();
        if (frames.isEmpty()) {
            throw new IOException("no frames decoded" + (err.length() > 0 ? ": " + err.toString().trim() : " (ffmpeg exit " + code + ")"));
        }
        return frames.toArray(new int[0][]);
    }
}
