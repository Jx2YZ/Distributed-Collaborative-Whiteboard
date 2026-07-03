/**
 * Custom JPanel for the whiteboard drawing canvas which handles drawing primitives, mouse events, and RMI submission.
 * Jx2
 * v.22/05/2026
 **/

package client.ui;

import base.Constants;
import base.primitives.*;
import base.rmi.BoardService;
import base.rmi.UserNotFoundException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class WhiteBoardCanvasPanel extends BufferedImagePanel implements MouseListener, MouseMotionListener {

    private static final Logger logger = Logger.getLogger(PeerForm.class.getName());

    // Minimum interval between preview broadcasts, 50ms is about 20fps
    private static final long PREVIEW_INTERVAL_MS = 50;

    public enum Mode {
        PEN, ERASE, LINE, TRIANGLE, CIRCLE, RECTANGLE, TEXT,
    }

    private Mode mode = Mode.PEN;
    private int eraseSize = 5;
    private Color color = Color.BLACK;

    private final List<Point> strokePoints = new LinkedList<>();
    private Point eraseCurrentPoint = null;

    private volatile boolean lineFlag = false;
    private Point lineStartPoint = new Point(0, 0);
    private Point lineEndPoint = new Point(0, 0);

    private final List<Point> trianglePoints = new ArrayList<>();

    private Point[] rect2Points = null;
    private Point[] circle2Points = null;  // Two points for circle bounding box

    private final Font textFont = new Font("Arial", Font.PLAIN, 32);

    private UUID peerId = null;
    private BoardService boardService = null;

    // Single-threaded executor for submitting final primitives to the server.
    ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()
    );

    // Throttle state for preview broadcasts.
    private final AtomicLong lastPreviewMs = new AtomicLong(0);
    private final AtomicInteger previewSeq = new AtomicInteger(0);

    public WhiteBoardCanvasPanel() {
        super();
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public Mode getMode() { return mode; }

    public void setMode(Mode mode) {
        if (this.mode != mode && mode == Mode.TRIANGLE) {
            trianglePoints.clear();
            trianglePoints.add(new Point());
        }
        this.mode = mode;
    }

    public int getEraseSize() { return eraseSize; }

    public void setEraseSize(int eraseSize) {
        if (eraseSize < 1) throw new IllegalArgumentException("eraseSize must be greater than 0");
        this.eraseSize = eraseSize;
    }

    public Color getColor() { return color; }

    public void setColor(Color color) {
        if (color != null) this.color = color;
    }

    public UUID getPeerId() { return peerId; }
    public void setPeerId(UUID peerId) { this.peerId = peerId; }

    public BoardService getBoardService() { return boardService; }
    public void setBoardService(BoardService boardService) { this.boardService = boardService; }

    public void submitPrimitive(Primitive primitive) {
        logger.info("Submit primitive: " + primitive);
        if (executor != null) {
            var service = getBoardService();
            var id = getPeerId();
            if (service != null && id != null) {
                executor.submit(() -> {
                    try {
                        service.draw(id, primitive);
                    } catch (RemoteException | UserNotFoundException e) {
                        logger.warning("Failed to submit primitive: %s: %s".formatted(primitive, e.getMessage()));
                    }
                });
            }
        }
    }

    // Sends a throttled preview of the current drag state to all peers.
    private void sendPreview(Primitive snapshot) {
        long now = System.currentTimeMillis();
        if (now - lastPreviewMs.get() < PREVIEW_INTERVAL_MS) return;
        lastPreviewMs.set(now);

        var service = getBoardService();
        var id = getPeerId();
        if (service == null || id == null) return;

        int seq = previewSeq.incrementAndGet();
        var preview = new PreviewPrimitive(id, seq, snapshot);
        executor.submit(() -> {
            try {
                service.preview(id, preview);
            } catch (RemoteException | UserNotFoundException e) {
                logger.fine("Preview send failed: " + e.getMessage());
            }
        });
    }

    public void drawPrimitive(Primitive primitive) {
        var img = getBufferedImage();
        if (img == null) return;
        var g2d = img.createGraphics();
        primitive.draw(g2d);
        g2d.dispose();
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g); // draws bufferedImage and remote peer overlays
        if (mode == Mode.PEN) {
            new StrokePrimitive(getColor(), strokePoints).draw((Graphics2D) g);
        } else if (mode == Mode.ERASE) {
            new ErasePrimitive(Constants.DEFAULT_BG_COLOR, strokePoints, getEraseSize()).draw((Graphics2D) g);
            var point = eraseCurrentPoint;
            if (point != null) {
                var size = getEraseSize();
                g.setColor(Color.BLACK);
                ((Graphics2D) g).setStroke(new BasicStroke(1));
                g.drawRect(point.x - size / 2, point.y - size / 2, size, size);
            }
        } else if (mode == Mode.LINE) {
            if (lineFlag) {
                new LinePrimitive(getColor(), lineStartPoint, lineEndPoint).draw((Graphics2D) g);
            }
        } else if (mode == Mode.TRIANGLE) {
            new LineLoopPrimitive(getColor(), trianglePoints).draw((Graphics2D) g);
        } else if (mode == Mode.CIRCLE) {
            if (circle2Points != null && circle2Points.length > 1) {
                var x = Math.min(circle2Points[0].x, circle2Points[1].x);
                var y = Math.min(circle2Points[0].y, circle2Points[1].y);
                var w = Math.abs(circle2Points[0].x - circle2Points[1].x);
                var h = Math.abs(circle2Points[0].y - circle2Points[1].y);
                int radius = Math.min(w, h) / 2;
                Point center = new Point(x + w / 2, y + h / 2);
                new CirclePrimitive(getColor(), center, radius).draw((Graphics2D) g);
            }
        } else if (mode == Mode.RECTANGLE) {
            if (rect2Points != null && rect2Points.length > 1) {
                var x = Math.min(rect2Points[0].x, rect2Points[1].x);
                var y = Math.min(rect2Points[0].y, rect2Points[1].y);
                var w = Math.abs(rect2Points[0].x - rect2Points[1].x);
                var h = Math.abs(rect2Points[0].y - rect2Points[1].y);
                new RectanglePrimitive(getColor(), new Rectangle(x, y, w, h)).draw((Graphics2D) g);
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (mode == Mode.TEXT) {
            var textContent = JOptionPane.showInputDialog(this, "Please enter a text to draw");
            var textPoint = e.getPoint();
            if (textContent != null && !textContent.isEmpty()) {
                submitPrimitive(new TextPrimitive(getColor(), textContent, textFont, textPoint.x, textPoint.y));
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        strokePoints.clear();
        lineStartPoint = e.getPoint();
        lineEndPoint = e.getPoint();
        lineFlag = true;
        rect2Points = new Point[]{e.getPoint(), e.getPoint()};
        circle2Points = new Point[]{e.getPoint(), e.getPoint()};
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (mode == Mode.PEN) {
            submitPrimitive(new StrokePrimitive(getColor(), List.copyOf(strokePoints)));
            strokePoints.clear();
            repaint();
        } else if (mode == Mode.ERASE) {
            submitPrimitive(new ErasePrimitive(Constants.DEFAULT_BG_COLOR, List.copyOf(strokePoints), getEraseSize()));
            strokePoints.clear();
            repaint();
        } else if (mode == Mode.LINE) {
            if (lineStartPoint != null && lineEndPoint != null) {
                submitPrimitive(new LinePrimitive(getColor(), lineStartPoint, lineEndPoint));
            }
            lineFlag = false;
            repaint();
        } else if (mode == Mode.TRIANGLE) {
            if (trianglePoints.size() >= 3) {
                submitPrimitive(new LineLoopPrimitive(getColor(), List.copyOf(trianglePoints)));
                trianglePoints.clear();
                trianglePoints.add(e.getPoint());
            } else {
                trianglePoints.add(e.getPoint());
                // Show partial polygon to peers after each click.
                if (trianglePoints.size() > 1) {
                    sendPreview(new LineLoopPrimitive(getColor(), List.copyOf(trianglePoints)));
                }
            }
            repaint();
        } else if (mode == Mode.CIRCLE) {
            if (circle2Points != null && circle2Points.length > 1) {
                var x = Math.min(circle2Points[0].x, circle2Points[1].x);
                var y = Math.min(circle2Points[0].y, circle2Points[1].y);
                var w = Math.abs(circle2Points[0].x - circle2Points[1].x);
                var h = Math.abs(circle2Points[0].y - circle2Points[1].y);
                int radius = Math.min(w, h) / 2;
                Point center = new Point(x + w / 2, y + h / 2);
                submitPrimitive(new CirclePrimitive(getColor(), center, radius));
            }
            circle2Points = null;
            repaint();
        } else if (mode == Mode.RECTANGLE) {
            if (rect2Points != null && rect2Points.length > 1) {
                var x = Math.min(rect2Points[0].x, rect2Points[1].x);
                var y = Math.min(rect2Points[0].y, rect2Points[1].y);
                var w = Math.abs(rect2Points[0].x - rect2Points[1].x);
                var h = Math.abs(rect2Points[0].y - rect2Points[1].y);
                submitPrimitive(new RectanglePrimitive(getColor(), new Rectangle(x, y, w, h)));
            }
            rect2Points = null;
            repaint();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        eraseCurrentPoint = new Point(e.getPoint());
    }

    @Override
    public void mouseExited(MouseEvent e) {
        strokePoints.clear();
        eraseCurrentPoint = null;
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (mode == Mode.PEN) {
            strokePoints.add(new Point(e.getX(), e.getY()));
            repaint();
            sendPreview(new StrokePrimitive(getColor(), List.copyOf(strokePoints)));
        } else if (mode == Mode.ERASE) {
            strokePoints.add(new Point(e.getX(), e.getY()));
            repaint();
            sendPreview(new ErasePrimitive(Constants.DEFAULT_BG_COLOR, List.copyOf(strokePoints), getEraseSize()));
        } else if (mode == Mode.LINE) {
            lineEndPoint = e.getPoint();
            repaint();
            sendPreview(new LinePrimitive(getColor(), lineStartPoint, lineEndPoint));
        } else if (mode == Mode.CIRCLE) {
            if (circle2Points != null && circle2Points.length > 1) circle2Points[1] = e.getPoint();
            repaint();
            if (circle2Points != null) {
                var x = Math.min(circle2Points[0].x, circle2Points[1].x);
                var y = Math.min(circle2Points[0].y, circle2Points[1].y);
                var w = Math.abs(circle2Points[0].x - circle2Points[1].x);
                var h = Math.abs(circle2Points[0].y - circle2Points[1].y);
                int radius = Math.min(w, h) / 2;
                Point center = new Point(x + w / 2, y + h / 2);
                sendPreview(new CirclePrimitive(getColor(), center, radius));
            }
        } else if (mode == Mode.RECTANGLE) {
            if (rect2Points != null && rect2Points.length > 1) {
                rect2Points[1] = e.getPoint();
            }
            repaint();
            if (rect2Points != null) {
                var x = Math.min(rect2Points[0].x, rect2Points[1].x);
                var y = Math.min(rect2Points[0].y, rect2Points[1].y);
                var w = Math.abs(rect2Points[0].x - rect2Points[1].x);
                var h = Math.abs(rect2Points[0].y - rect2Points[1].y);
                sendPreview(new RectanglePrimitive(getColor(), new Rectangle(x, y, w, h)));
            }
        }
        if (eraseCurrentPoint != null) {
            eraseCurrentPoint.setLocation(e.getPoint());
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (mode == Mode.ERASE) repaint();
        if (mode == Mode.TRIANGLE) {
            if (!trianglePoints.isEmpty()) {
                trianglePoints.set(trianglePoints.size() - 1, e.getPoint());
                repaint();
            }
        }
        if (eraseCurrentPoint != null) {
            eraseCurrentPoint.setLocation(e.getPoint());
        }
    }
}
