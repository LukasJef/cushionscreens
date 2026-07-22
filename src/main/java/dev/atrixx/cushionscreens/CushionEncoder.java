package dev.atrixx.cushionscreens;

import java.awt.image.BufferedImage;

public final class CushionEncoder {
    private CushionEncoder() {
    }

    public static int[] encode(BufferedImage img, int gw, int gh, int[] palette, boolean dither) {
        double[] buf = CushionEncoder.resample(img, gw, gh);
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

    private static double[] resample(BufferedImage img, int gw, int gh) {
        int iw = img.getWidth();
        int ih = img.getHeight();
        double[] buf = new double[gw * gh * 3];
        for (int ty = 0; ty < gh; ++ty) {
            int sy0 = (int)((long)ty * (long)ih / (long)gh);
            int sy1 = Math.max(sy0 + 1, (int)((long)(ty + 1) * (long)ih / (long)gh));
            for (int tx = 0; tx < gw; ++tx) {
                int sx0 = (int)((long)tx * (long)iw / (long)gw);
                int sx1 = Math.max(sx0 + 1, (int)((long)(tx + 1) * (long)iw / (long)gw));
                long sr = 0L;
                long sg = 0L;
                long sb = 0L;
                long n = 0L;
                for (int sy = sy0; sy < sy1 && sy < ih; ++sy) {
                    for (int sx = sx0; sx < sx1 && sx < iw; ++sx) {
                        int rgb = img.getRGB(sx, sy);
                        sr += (long)(rgb >> 16 & 0xFF);
                        sg += (long)(rgb >> 8 & 0xFF);
                        sb += (long)(rgb & 0xFF);
                        ++n;
                    }
                }
                int c = (ty * gw + tx) * 3;
                if (n <= 0L) continue;
                buf[c] = (double)sr / (double)n;
                buf[c + 1] = (double)sg / (double)n;
                buf[c + 2] = (double)sb / (double)n;
            }
        }
        return buf;
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
