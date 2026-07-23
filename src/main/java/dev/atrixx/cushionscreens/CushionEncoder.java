package dev.atrixx.cushionscreens;

import java.awt.image.BufferedImage;

public final class CushionEncoder {
    private CushionEncoder() {
    }

    public enum ScaleMode {
        STRETCH, // vychozi/puvodni chovani - roztahne obrazek presne na gw x gh, ignoruje pomer stran
        CROP,    // zachova pomer stran, orizne prebytek (stred obrazku)
        FIT      // zachova pomer stran, nic neorizne - zbytek se necha cerny (letterbox/pillarbox)
    }

    public static int[] encode(BufferedImage img, int gw, int gh, int[] palette, boolean dither) {
        return encode(img, gw, gh, palette, dither, ScaleMode.STRETCH);
    }

    public static int[] encode(BufferedImage img, int gw, int gh, int[] palette, boolean dither, ScaleMode scaleMode) {
        double[] buf = CushionEncoder.resample(img, gw, gh, scaleMode);
        int[] out = new int[gw * gh];
        for (int y = 0; y < gh; ++y) {
            for (int x = 0; x < gw; ++x) {
                int idx;
                int c = (y * gw + x) * 3;
                double r = CushionEncoder.clamp(buf[c]);
                double g = CushionEncoder.clamp(buf[c + 1]);
                double b = CushionEncoder.clamp(buf[c + 2]);
                out[y * gw + x] = idx = CushionEncoder.nearest(palette, r, g, b);
                if (!dither) continue;
                int p = palette[idx];
                double er = r - (double)(p >> 16 & 0xFF);
                double eg = g - (double)(p >> 8 & 0xFF);
                double eb = b - (double)(p & 0xFF);
                CushionEncoder.diffuse(buf, gw, gh, x + 1, y, er, eg, eb, 0.4375);
                CushionEncoder.diffuse(buf, gw, gh, x - 1, y + 1, er, eg, eb, 0.1875);
                CushionEncoder.diffuse(buf, gw, gh, x, y + 1, er, eg, eb, 0.3125);
                CushionEncoder.diffuse(buf, gw, gh, x + 1, y + 1, er, eg, eb, 0.0625);
            }
        }
        return out;
    }

    private static double[] resample(BufferedImage img, int gw, int gh, ScaleMode scaleMode) {
        int iw = img.getWidth();
        int ih = img.getHeight();
        // Vychozi (0.0 = cerna) - dulezite pro FIT, kde zbytek plochy
        // (pruhy) zustane zaplneny cernou.
        double[] buf = new double[gw * gh * 3];

        if (scaleMode == ScaleMode.FIT && iw > 0 && ih > 0) {
            // Zmensit tak, aby se cely obrazek vesel dovnitr cilove mrizky
            // (mensi z pomeru) - nic se neorizne, zbytek plochy zustane
            // cerny (letterbox/pillarbox).
            double scale = Math.min((double) gw / iw, (double) gh / ih);
            int destW = Math.max(1, Math.min(gw, (int) Math.round(iw * scale)));
            int destH = Math.max(1, Math.min(gh, (int) Math.round(ih * scale)));
            int destX0 = (gw - destW) / 2;
            int destY0 = (gh - destH) / 2;
            for (int ty = 0; ty < destH; ++ty) {
                int sy0 = (int) ((long) ty * ih / destH);
                int sy1 = Math.max(sy0 + 1, (int) ((long) (ty + 1) * ih / destH));
                for (int tx = 0; tx < destW; ++tx) {
                    int sx0 = (int) ((long) tx * iw / destW);
                    int sx1 = Math.max(sx0 + 1, (int) ((long) (tx + 1) * iw / destW));
                    double[] avg = averageBox(img, sx0, sx1, sy0, sy1, iw, ih);
                    if (avg == null) continue;
                    int c = ((ty + destY0) * gw + (tx + destX0)) * 3;
                    buf[c] = avg[0];
                    buf[c + 1] = avg[1];
                    buf[c + 2] = avg[2];
                }
            }
            return buf;
        }

        // STRETCH (vychozi): cely obrazek 0..iw/0..ih se namapuje na
        // cilovou mrizku, bez ohledu na pomer stran (puvodni chovani).
        int srcX0 = 0;
        int srcY0 = 0;
        int srcW = iw;
        int srcH = ih;
        if (scaleMode == ScaleMode.CROP && iw > 0 && ih > 0) {
            // Zvetsit tak, aby cilova mrizka byla cela pokryta (vetsi z
            // pomeru), a pak vzit jen prostredni okno odpovidajici
            // velikosti - prebytek na delsi strane se orizne.
            double scale = Math.max((double) gw / iw, (double) gh / ih);
            srcW = Math.max(1, Math.min(iw, (int) Math.round(gw / scale)));
            srcH = Math.max(1, Math.min(ih, (int) Math.round(gh / scale)));
            srcX0 = Math.max(0, (iw - srcW) / 2);
            srcY0 = Math.max(0, (ih - srcH) / 2);
        }
        for (int ty = 0; ty < gh; ++ty) {
            int sy0 = srcY0 + (int) ((long) ty * (long) srcH / (long) gh);
            int sy1 = Math.max(sy0 + 1, srcY0 + (int) ((long) (ty + 1) * (long) srcH / (long) gh));
            for (int tx = 0; tx < gw; ++tx) {
                int sx0 = srcX0 + (int) ((long) tx * (long) srcW / (long) gw);
                int sx1 = Math.max(sx0 + 1, srcX0 + (int) ((long) (tx + 1) * (long) srcW / (long) gw));
                double[] avg = averageBox(img, sx0, sx1, sy0, sy1, iw, ih);
                if (avg == null) continue;
                int c = (ty * gw + tx) * 3;
                buf[c] = avg[0];
                buf[c + 1] = avg[1];
                buf[c + 2] = avg[2];
            }
        }
        return buf;
    }

    private static double[] averageBox(BufferedImage img, int sx0, int sx1, int sy0, int sy1, int iw, int ih) {
        long sr = 0L;
        long sg = 0L;
        long sb = 0L;
        long n = 0L;
        for (int sy = sy0; sy < sy1 && sy < ih; ++sy) {
            for (int sx = sx0; sx < sx1 && sx < iw; ++sx) {
                int rgb = img.getRGB(sx, sy);
                sr += (long) (rgb >> 16 & 0xFF);
                sg += (long) (rgb >> 8 & 0xFF);
                sb += (long) (rgb & 0xFF);
                ++n;
            }
        }
        if (n <= 0L) return null;
        return new double[]{(double) sr / (double) n, (double) sg / (double) n, (double) sb / (double) n};
    }

    private static int nearest(int[] palette, double r, double g, double b) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < palette.length; ++i) {
            int p = palette[i];
            double dr = r - (double)(p >> 16 & 0xFF);
            double dg = g - (double)(p >> 8 & 0xFF);
            double db = b - (double)(p & 0xFF);
            double d = dr * dr + dg * dg + db * db;
            if (!(d < bestD)) continue;
            bestD = d;
            best = i;
        }
        return best;
    }

    private static void diffuse(double[] buf, int gw, int gh, int x, int y, double er, double eg, double eb, double f) {
        int c;
        if (x < 0 || x >= gw || y < 0 || y >= gh) {
            return;
        }
        int n = c = (y * gw + x) * 3;
        buf[n] = buf[n] + er * f;
        int n2 = c + 1;
        buf[n2] = buf[n2] + eg * f;
        int n3 = c + 2;
        buf[n3] = buf[n3] + eb * f;
    }

    private static double clamp(double v) {
        return v < 0.0 ? 0.0 : (v > 255.0 ? 255.0 : v);
    }
}
