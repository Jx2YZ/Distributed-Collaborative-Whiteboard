/**
 * Remote interface for whiteboard operations using Java RMI.
 * Jx2
 * v.22/05/2026
 **/

package base.rmi;

import base.SerializableImage;
import base.primitives.PreviewPrimitive;
import base.primitives.Primitive;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface BoardService extends Remote {

    SerializableImage getCurrentCanvas() throws RemoteException;

    void clear(UUID peerId) throws RemoteException, UserNotFoundException;

    void draw(UUID peerId, Primitive primitive) throws RemoteException, UserNotFoundException;

    // Broadcasts a preview snapshot without committing it to the authoritative canvas.
    void preview(UUID peerId, PreviewPrimitive preview) throws RemoteException, UserNotFoundException;

    void setCurrentCanvas(UUID peerId, SerializableImage image) throws RemoteException, UserNotFoundException;

}
