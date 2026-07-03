/**
 * A custom JPanel for displaying and drawing on a BufferedImage that maintains a transient overlay of in-progress preview strokes from remote peers.
 * Jx2
 * v.22/05/2026
 **/

package client.ui;

import base.primitives.PreviewPrimitive;
import base.primitives.Primitive;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BufferedImagePanel extends JPanel {

    private BufferedImage bufferedImage;

    // Overlay of in-progress previews keyed by sender UUID, drawn on top of the committed canvas.
    private final ConcurrentHashMap<UUID, PreviewPrimitive> previewOverlay = new ConcurrentHashMap<>();

    public BufferedImagePanel() {
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    public void setBufferedImage(BufferedImage newBufferedImage) {
        bufferedImage = newBufferedImage;
        setPreferredSize(new Dimension(bufferedImage.getWidth(), bufferedImage.getHeight()));
        revalidate();
        repaint();
    }

    public void drawPrimitive(Primitive primitive) {
        var img = bufferedImage;
        if (img == null) return;
        var g2d = img.createGraphics();
        primitive.draw(g2d);
        g2d.dispose();
    }

    public void setPreview(UUID peerId, PreviewPrimitive preview) {
        previewOverlay.put(peerId, preview);
        repaint();
    }

    public void clearPreview(UUID peerId) {
        previewOverlay.remove(peerId);
        repaint();
    }

    public void clearAllPreviews() {
        previewOverlay.clear();
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (bufferedImage != null) {
            g.drawImage(bufferedImage, 0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), this);
        }
        // Draw remote peer previews on top of the committed canvas.
        for (PreviewPrimitive preview : previewOverlay.values()) {
            preview.draw((Graphics2D) g);
        }
    }
}
