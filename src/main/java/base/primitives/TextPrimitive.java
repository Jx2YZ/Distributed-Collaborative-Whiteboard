/**
 * Represents a text label to be shown on the whiteboard.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;

public class TextPrimitive implements Primitive {

    private final Color color;     // Color of the text
    private final String text;     // The text content
    private final Font font;       // Font used for the text
    private final int x;           // X coordinate for the text position
    private final int y;           // Y coordinate for the text position

    // Constructor to initialize all fields
    public TextPrimitive(Color color, String text, Font font, int x, int y) {
        this.color = color;
        this.text = text;
        this.font = font;
        this.x = x;
        this.y = y;
    }

    // Draws the text using Graphics2D
    @Override
    public void draw(Graphics2D graphics) {
        draw((Graphics) graphics);
    }

    // Draws the text using Graphics
    @Override
    public void draw(Graphics graphics) {
        graphics.setColor(color);
        graphics.setFont(font);
        graphics.drawString(text, x, y);
    }

}