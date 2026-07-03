/**
 * Represents a freehand stroke basically a series of connected points to be drawn on the whiteboard.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;
import java.util.List;

public class StrokePrimitive implements Primitive {

    private final Color color;           // Color of the stroke
    private final int strokeWidth;       // Width of the stroke
    private final List<Point> points;    // List of points forming the stroke

    // Constructor with default stroke width
    public StrokePrimitive(Color color, List<Point> points) {
        this(color, points, 1);
    }

    // Constructor with specified stroke width
    public StrokePrimitive(Color color, List<Point> points, int strokeWidth) {
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.points = points;
    }

    public Color getColor() {
        return color;
    }

    public List<Point> getPoints() {
        return points;
    }

    // Draws the stroke using Graphics2D, sets stroke width
    public void draw(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(strokeWidth));
        draw((Graphics) graphics);
    }

    // Draws the stroke using Graphics
    public void draw(Graphics graphics) {
        if (points.isEmpty())
            return;

        graphics.setColor(color);

        if (points.size() == 1) {
            // Draw a single point as a dot
            graphics.drawLine(points.get(0).x, points.get(0).y, points.get(0).x, points.get(0).y);
        } else {
            // Draw lines between consecutive points
            for (int i = 0, pointsSize = points.size(); i + 1 < pointsSize; ++i) {
                Point pointA = points.get(i);
                Point pointB = points.get(i + 1);
                graphics.drawLine(pointA.x, pointA.y, pointB.x, pointB.y);
            }
        }
    }

}