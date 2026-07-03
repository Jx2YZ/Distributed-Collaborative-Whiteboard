/**
 * Carries a snapshot of a peer's in-progress drawing gesture.
 * Broadcast by the server via onPrimitiveAdded without committing to the canvas.
 * Jx2
 * v.22/05/2026
 **/

package base.primitives;

import java.awt.*;
import java.util.UUID;

public class PreviewPrimitive implements Primitive {

    private final UUID senderPeerId;
    private final int sequenceNumber;
    private final Primitive payload;

    public PreviewPrimitive(UUID senderPeerId, int sequenceNumber, Primitive payload) {
        this.senderPeerId = senderPeerId;
        this.sequenceNumber = sequenceNumber;
        this.payload = payload;
    }

    public UUID getSenderPeerId() { return senderPeerId; }
    public int getSequenceNumber() { return sequenceNumber; }
    public Primitive getPayload() { return payload; }

    @Override
    public void draw(Graphics2D graphics) {
        if (payload != null) payload.draw(graphics);
    }

    @Override
    public void draw(Graphics graphics) {
        if (payload != null) payload.draw(graphics);
    }
}
