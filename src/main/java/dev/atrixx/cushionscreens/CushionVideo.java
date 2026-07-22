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

    public static int[][] decodeToIndices(String ffmpeg, File file, int gw, int gh, int fps, int maxFrames, int[] palette) throws IOException, InterruptedException {
        Process proc;
        String filter = "fps=" + fps + ",scale=" + gw + ":" + gh + ":flags=area";
        ProcessBuilder pb = new ProcessBuilder(ffmpeg, "-nostdin", "-i", file.getAbsolutePath(), "-vf", filter, "-f", "rawvideo", "-pix_fmt", "rgb24", "-v", "error", "pipe:1");
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
