/**
 * Represents an eraser stroke primitive for the whiteboard application.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import base.Constants;
import java.awt.*;
import java.util.Collections;
import java.util.List;

// Represents an eraser stroke on the whiteboard
public class ErasePrimitive extends StrokePrimitive {

    // Default eraser with background color and no points
    public ErasePrimitive() {
        this(Constants.DEFAULT_BG_COLOR, Collections.emptyList());
    }

    // Eraser with color and points, default stroke width 1
    public ErasePrimitive(Color color, List<Point> points) {
        this(color, points, 1);
    }

    // Eraser with color, points, and stroke width
    public ErasePrimitive(Color color, List<Point> points, int strokeWidth) {
        super(color, points, strokeWidth);
    }

}