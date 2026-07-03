/**
 * Implementation of the BoardService interface for whiteboard operations, handling drawing, clearing, previews, and broadcasting updates to all peers.
 * Jx2
 * v.22/05/2026
 **/

package server;

import base.Constants;
import base.SerializableImage;
import base.primitives.PreviewPrimitive;
import base.primitives.Primitive;
import base.rmi.BoardService;
import base.rmi.UserNotFoundException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.rmi.RemoteException;
import java.util.UUID;
import java.util.logging.Logger;

public class BoardServiceImpl implements BoardService {

    private static final Logger logger = Logger.getLogger(SessionServiceImpl.class.getName());

    private final PeersManager peersMgr;
    private final transient BufferedImage bufferedImage;

    public BoardServiceImpl(PeersManager peersManager) {
        this(peersManager, new BufferedImage(Constants.DEFAULT_CANVAS_WIDTH, Constants.DEFAULT_CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB));
    }

    public BoardServiceImpl(PeersManager peersManager, BufferedImage bufferedImage) {
        this.peersMgr = peersManager;
        this.bufferedImage = bufferedImage;
    }

    public void clear() {
        var g2d = bufferedImage.createGraphics();
        g2d.setColor(Constants.DEFAULT_BG_COLOR);
        g2d.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
        g2d.dispose();
    }

    private void drawPrimitive(Primitive primitive) {
        var g2d = bufferedImage.createGraphics();
        primitive.draw(g2d);
        g2d.dispose();
    }

    @Override
    public SerializableImage getCurrentCanvas() throws RemoteException {
        return new SerializableImage(bufferedImage);
    }

    @Override
    public void clear(UUID peerId) throws RemoteException, UserNotFoundException {
        var peersKv = peersMgr.getPeers();
        synchronized (peersKv) {
            var peerSender = peersKv.get(peerId);
            if (peerSender == null) throw new UserNotFoundException();

            clear();

            peersKv.values().forEach(peer -> {
                try {
                    peer.getCallback().onCanvasSwitched(peerSender.getUserName());
                } catch (RemoteException e) {
                    logger.warning("(To %s) onCanvasSwitched: %s".formatted(peer.getUserName(), e.getMessage()));
                }
            });
        }
    }

    @Override
    public void draw(UUID peerId, Primitive primitive) throws RemoteException, UserNotFoundException {
        var peersKv = peersMgr.getPeers();
        synchronized (peersKv) {
            var peerSender = peersKv.get(peerId);
            if (peerSender == null) throw new UserNotFoundException();

            drawPrimitive(primitive);

            peersKv.values().forEach(peer -> {
                try {
                    peer.getCallback().onPrimitiveAdded(peerId, peerSender.getUserName(), primitive);
                } catch (RemoteException e) {
                    logger.warning("(To %s) onPrimitiveAdded: %s".formatted(peer.getUserName(), e.getMessage()));
                }
            });
        }
    }

    // Broadcasts a preview to all peers without committing to the canvas.
    @Override
    public void preview(UUID peerId, PreviewPrimitive preview) throws RemoteException, UserNotFoundException {
        var peersKv = peersMgr.getPeers();
        synchronized (peersKv) {
            var peerSender = peersKv.get(peerId);
            if (peerSender == null) throw new UserNotFoundException();

            peersKv.values().forEach(peer -> {
                try {
                    peer.getCallback().onPrimitiveAdded(peerId, peerSender.getUserName(), preview);
                } catch (RemoteException e) {
                    logger.warning("(To %s) preview: %s".formatted(peer.getUserName(), e.getMessage()));
                }
            });
        }
    }

    // Replaces the canvas content and notifies all peers to re-fetch.
    // Draws onto the existing bufferedImage (I decided to keep dimensions fixed at 800×600).
    @Override
    public void setCurrentCanvas(UUID peerId, SerializableImage image) throws RemoteException, UserNotFoundException {
        var peersKv = peersMgr.getPeers();
        synchronized (peersKv) {
            var peerSender = peersKv.get(peerId);
            if (peerSender == null) throw new UserNotFoundException();

            BufferedImage src = image.getImage();
            Graphics2D g = bufferedImage.createGraphics();
            g.setColor(Constants.DEFAULT_BG_COLOR);
            g.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
            g.drawImage(src, 0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null);
            g.dispose();

            peersKv.values().forEach(peer -> {
                try {
                    peer.getCallback().onCanvasSwitched(peerSender.getUserName());
                } catch (RemoteException e) {
                    logger.warning("(To %s) onCanvasSwitched: %s".formatted(peer.getUserName(), e.getMessage()));
                }
            });
        }
    }
}
