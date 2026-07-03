/**
 * Represents a closed loop of connected lines to be drawn on the whiteboard.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;
import java.util.List;

public class LineLoopPrimitive implements Primitive {

    private final Color color;           // Color of the line loop
    private final int strokeWidth;       // Width of the lines
    private final List<Point> points;    // Points defining the loop

    // Constructor with default stroke width
    public LineLoopPrimitive(Color color, List<Point> loopPoints) {
        this(color, loopPoints, 1);
    }

    // Constructor with specified stroke width
    public LineLoopPrimitive(Color color, List<Point> loopPoints, int strokeWidth) {
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.points = loopPoints;
    }

    // Draws the loop using Graphics2D (sets stroke width)
    @Override
    public void draw(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(strokeWidth));
        draw((Graphics) graphics);
    }

    // Draws the loop using Graphics that connects each point to the next, closing the loop
    @Override
    public void draw(Graphics graphics) {
        graphics.setColor(color);
        for (int i = 0, pointsSize = points.size(); i < pointsSize; i++) {
            Point pointA = points.get(i);
            Point pointB = points.get((i + 1) % pointsSize); // Wraps around to close the loop
            graphics.drawLine(pointA.x, pointA.y, pointB.x, pointB.y);
        }
    }

}