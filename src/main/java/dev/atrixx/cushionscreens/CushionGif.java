package dev.atrixx.cushionscreens;

import dev.atrixx.cushionscreens.CushionGif;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

public final class CushionGif {
    private CushionGif() {
    }

    public static final class Clip {
        public final BufferedImage[] frames;
        public final int[] delayCentis;

        Clip(BufferedImage[] frames, int[] delayCentis) {
            this.frames = frames;
            this.delayCentis = delayCentis;
        }
    }

    public static Clip decode(File file, int maxFrames) throws IOException {
        ImageReader reader = null;
        try {
            Clip clip;
            block17: {
                ImageInputStream in = ImageIO.createImageInputStream(file);
                try {
                    if (in == null) {
                        throw new IOException("cannot open image stream");
                    }
                    Iterator<ImageReader> it = ImageIO.getImageReaders(in);
                    if (!it.hasNext()) {
                        throw new IOException("no GIF reader available");
                    }
                    reader = it.next();
                    reader.setInput(in, false);
                    int count = reader.getNumImages(true);
                    if (count <= 0) {
                        throw new IOException("no frames");
                    }
                    count = Math.min(count, maxFrames);
                    int w = reader.getWidth(0);
                    int h = reader.getHeight(0);
                    for (int i = 0; i < count; ++i) {
                        w = Math.max(w, CushionGif.frameAttr(reader, i, "imageLeftPosition") + reader.getWidth(i));
                        h = Math.max(h, CushionGif.frameAttr(reader, i, "imageTopPosition") + reader.getHeight(i));
                    }
                    BufferedImage canvas = new BufferedImage(w, h, 2);
                    Graphics2D g = canvas.createGraphics();
                    ArrayList<BufferedImage> frames = new ArrayList<BufferedImage>();
                    ArrayList<Integer> delays = new ArrayList<Integer>();
                    for (int i = 0; i < count; ++i) {
                        BufferedImage frame = reader.read(i);
                        int x = CushionGif.frameAttr(reader, i, "imageLeftPosition");
                        int y = CushionGif.frameAttr(reader, i, "imageTopPosition");
                        int delay = CushionGif.gceAttr(reader, i, "delayTime", 10);
                        String disposal = CushionGif.gceStr(reader, i, "disposalMethod", "none");
                        BufferedImage before = disposal.startsWith("restoreToPrevious") ? CushionGif.deepCopy(canvas) : null;
                        g.drawImage((Image)frame, x, y, null);
                        frames.add(CushionGif.flatten(canvas));
                        delays.add(delay <= 0 ? 10 : delay);
                        if (disposal.startsWith("restoreToBackgroundColor")) {
                            g.clearRect(x, y, frame.getWidth(), frame.getHeight());
                            continue;
                        }
                        if (before == null) continue;
                        Graphics2D bg = canvas.createGraphics();
                        bg.setComposite(AlphaComposite.Src);
                        bg.drawImage((Image)before, 0, 0, null);
                        bg.dispose();
                    }
                    g.dispose();
                    int[] d = new int[delays.size()];
                    for (int i = 0; i < d.length; ++i) {
                        d[i] = (Integer)delays.get(i);
                    }
                    clip = new Clip(frames.toArray(new BufferedImage[0]), d);
                    if (in == null) break block17;
                }
                catch (Throwable throwable) {
                    if (in != null) {
                        try {
                            in.close();
                        }
                        catch (Throwable throwable2) {
                            throwable.addSuppressed(throwable2);
                        }
                    }
                    throw throwable;
                }
                in.close();
            }
            return clip;
        }
        finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private static BufferedImage flatten(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), 1);
        Graphics2D g = out.createGraphics();
        g.drawImage((Image)src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage((Image)src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static int frameAttr(ImageReader reader, int i, String attr) {
        try {
            IIOMetadataNode node = CushionGif.childNamed(reader.getImageMetadata(i), "ImageDescriptor");
            if (node != null) {
                return Integer.parseInt(node.getAttribute(attr));
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return 0;
    }

    private static int gceAttr(ImageReader reader, int i, String attr, int def) {
        try {
            IIOMetadataNode node = CushionGif.childNamed(reader.getImageMetadata(i), "GraphicControlExtension");
            if (node != null) {
                return Integer.parseInt(node.getAttribute(attr));
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return def;
    }

    private static String gceStr(ImageReader reader, int i, String attr, String def) {
        try {
            String v;
            IIOMetadataNode node = CushionGif.childNamed(reader.getImageMetadata(i), "GraphicControlExtension");
            if (node != null && (v = node.getAttribute(attr)) != null && !v.isEmpty()) {
                return v;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return def;
    }

    private static IIOMetadataNode childNamed(IIOMetadata meta, String name) {
        if (meta == null) {
            return null;
        }
        Node root = meta.getAsTree(meta.getNativeMetadataFormatName());
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!name.equals(n.getNodeName()) || !(n instanceof IIOMetadataNode)) continue;
            IIOMetadataNode metaNode = (IIOMetadataNode)n;
            return metaNode;
        }
        return null;
    }
}
