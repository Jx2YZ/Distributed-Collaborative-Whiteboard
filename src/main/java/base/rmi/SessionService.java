/**
 * Remote interface for session management in the whiteboard application using Java RMI; defines user/session operations and callback methods for client notifications.
 * Jx2
 * v.22/05/2026
 **/

package base.rmi;

import base.UserRole;
import base.primitives.Primitive;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.UUID;

public interface SessionService extends Remote {

    // Exception thrown when a user already exists
    class UserAlreadyExistsException extends Exception implements Serializable {
        public UserAlreadyExistsException() {
            super("The specified user already exists");
        }
    }

    // Exception thrown when manager limit is exceeded
    class ManagerLimitExceededException extends Exception implements Serializable {
        public ManagerLimitExceededException() {
            super("The current number of managers has reached the limit");
        }
    }

    // Exception thrown when no manager is available
    class NoManagerAvailableException extends Exception implements Serializable {
        public NoManagerAvailableException() {
            super("There is currently no available manager");
        }
    }

    // Exception thrown when permission is denied
    class PermissionDeniedException extends SecurityException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }

    // Callback interface for session events, implemented by clients.
    interface SessionCallback extends Remote {

        /**
         * Accept/reject incoming join requests (only for Manager).
         * @param userName Username of the user awaiting approval
         */
        boolean onIncomingRequest(String userName) throws RemoteException;

        void onCanvasSwitched(String peerName) throws RemoteException;

        void onPrimitiveAdded(UUID peerId, String peerName, Primitive primitive) throws RemoteException;

        void onChatMessageAdded(String peerName, String chatMessage) throws RemoteException;

        void onUsersChanged(Collection<Pair<UserRole, String>> peers) throws RemoteException;

        void onKickedOut(String managerName) throws RemoteException;

        void onTerminated() throws RemoteException;

    }

    /**
     * Creates a new session for a user.
     * @param userName Username
     * @param sessionCallback Callback for session events
     * @return UUID of the new session (NotNull)
     * @throws RemoteException
     * @throws UserAlreadyExistsException
     * @throws ManagerLimitExceededException
     */
    UUID create(String userName, SessionCallback sessionCallback) throws RemoteException, UserAlreadyExistsException, ManagerLimitExceededException;

    /**
     * Joins an existing session.
     * @param userName Username
     * @param sessionCallback Callback for session events
     * @return UUID of the session (Nullable)
     * @throws RemoteException
     * @throws UserAlreadyExistsException
     * @throws NoManagerAvailableException
     */
    UUID join(String userName, SessionCallback sessionCallback) throws RemoteException, UserAlreadyExistsException, NoManagerAvailableException;

    // Heartbeat to keep the session alive
    void heartbeat(UUID peerId) throws RemoteException;

    // Leave the session
    void leave(UUID peerId) throws RemoteException, UserNotFoundException;

    // Send a chat message
    void sendChatMessage(UUID peerId, String message) throws RemoteException, UserNotFoundException;

    // Get the list of users in the session
    Collection<Pair<UserRole, String>> getUsers() throws RemoteException;

    // Get the chat message history
    Collection<Pair<String, String>> getChatMessages() throws RemoteException;

    // Kick out a user (manager only)
    boolean kickOut(UUID peerId, String targetUserName) throws RemoteException, UserNotFoundException, PermissionDeniedException;

}