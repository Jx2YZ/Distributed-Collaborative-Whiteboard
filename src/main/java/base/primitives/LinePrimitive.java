/**
 * Represents a single line segment to be drawn on the whiteboard.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;

public class LinePrimitive implements Primitive {

    private final Color color;        // Color of the line
    private final int strokeWidth;    // Width of the line
    private final Point start;        // Starting point of the line
    private final Point end;          // Ending point of the line

    // Constructor with default stroke width
    public LinePrimitive(Color color, Point start, Point end) {
        this(color, start, end, 1);
    }

    // Constructor with specified stroke width
    public LinePrimitive(Color color, Point start, Point end, int strokeWidth) {
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.start = start;
        this.end = end;
    }

    // Draws the line using Graphics2D which sets stroke width
    public void draw(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(strokeWidth));
        draw((Graphics) graphics);
    }

    // Draws the line using Graphics
    public void draw(Graphics graphics) {
        graphics.setColor(color);
        graphics.drawLine(start.x, start.y, end.x, end.y);
    }

}