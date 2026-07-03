/**
 * Represents a circle shape to be drawn on the whiteboard.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;

public class CirclePrimitive implements Primitive {

    private final Color color;        // Color of the circle
    private final int strokeWidth;    // Width of the circle's outline
    private final Point center;       // Center point of the circle
    private final int radius;         // Radius of the circle

    // Constructor with default stroke width
    public CirclePrimitive(Color color, Point center, int radius) {
        this(color, center, radius, 1);
    }

    // Constructor with specified stroke width
    public CirclePrimitive(Color color, Point center, int radius, int strokeWidth) {
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.center = center;
        this.radius = radius;
    }

    // Draws the circle using Graphics2D
    @Override
    public void draw(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(strokeWidth));
        draw((Graphics) graphics);
    }

    // Draws the circle using Graphics
    @Override
    public void draw(Graphics graphics) {
        graphics.setColor(color);
        graphics.drawOval(center.x - radius, center.y - radius, radius * 2, radius * 2);
    }

}
