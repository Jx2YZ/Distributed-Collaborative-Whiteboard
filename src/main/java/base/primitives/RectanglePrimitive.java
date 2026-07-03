/**
 * Represents a rectangle shape to be drawn on the whiteboard.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;

public class RectanglePrimitive implements Primitive {

    private final Color color;           // Color of the rectangle
    private final int strokeWidth;       // Width of the rectangle's outline
    private final Rectangle rectangle;   // Rectangle bounds

    // Constructor with default stroke width
    public RectanglePrimitive(Color color, Rectangle rectangle) {
        this(color, rectangle, 1);
    }

    // Constructor with specified stroke width
    public RectanglePrimitive(Color color, Rectangle rectangle, int strokeWidth) {
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.rectangle = rectangle;
    }

    // Draws the rectangle using Graphics2D that sets stroke width
    @Override
    public void draw(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(strokeWidth));
        draw((Graphics) graphics);
    }

    // Draws the rectangle using Graphics
    @Override
    public void draw(Graphics graphics) {
        graphics.setColor(color);
        graphics.drawRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }

}