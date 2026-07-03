/**
 * Interface for drawable primitives on the whiteboard.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;
import java.io.Serializable;

// All drawable primitives must implement these methods
public interface Primitive extends Serializable {

    // Draws the primitive using Graphics2D
    void draw(Graphics2D graphics);

    // Draws the primitive using Graphics
    void draw(Graphics graphics);

}