/**
 * Implements the SessionService interface for session management in the whiteboard server; handles user join/leave, manager assignment, chat, and user notifications.
 * Jx2
 * v.22/05/2026
 **/

package server;

import base.Constants;
import base.UserRole;
import base.rmi.SessionService;
import base.rmi.UserNotFoundException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.rmi.RemoteException;
import java.rmi.server.Unreferenced;
import java.util.*;
import java.util.logging.Logger;

public class SessionServiceImpl implements SessionService, Unreferenced {

    private static final Logger logger = Logger.getLogger(SessionServiceImpl.class.getName());

    private final transient PeersManager peersMgr; // Manages all connected peers

    // Stores chat messages as peerId, userName, message. synchronizedList guards concurrent sendChatMessage calls.
    private final transient List<Triple<UUID, String, String>> chatMessages = Collections.synchronizedList(new LinkedList<>());

    public SessionServiceImpl(PeersManager peersManager) {
        peersMgr = peersManager;

        // Background thread: periodically remove peers whose heartbeat has timed out.
        Thread eviction = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(Constants.HEARTBEAT_INTERVAL_MS_MAX);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                evictStalePeers();
            }
        }, "peer-eviction");
        eviction.setDaemon(true);
        eviction.start();
    }

    // Removes peers whose last heartbeat exceeds HEARTBEAT_INTERVAL_MS_MAX and notifies remaining peers.
    private void evictStalePeers() {
        var peers = peersMgr.getPeers();
        List<Peer> evicted = new ArrayList<>();
        boolean managerEvicted = false;

        synchronized (peers) {
            var iter = peers.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                if (!entry.getValue().isAlive()) {
                    logger.info("Evicting stale peer: " + entry.getValue().getUserName());
                    evicted.add(entry.getValue());
                    if (entry.getValue().getRole() == UserRole.MANAGER) managerEvicted = true;
                    iter.remove();
                }
            }
            if (!evicted.isEmpty()) {
                if (managerEvicted) {
                    // Manager timed out — terminate the session for all remaining peers.
                    peers.values().forEach(peer -> {
                        try { peer.getCallback().onTerminated(); }
                        catch (RemoteException e) { logger.warning("onTerminated (eviction): " + e.getMessage()); }
                    });
                }
                broadcastUsersNamesToPeers(peers);
            }
        }
    }

    /**
     * Broadcasts the user list to all peers.
     * @param peersKv Map of peer UUIDs to Peer objects
     */
    private void broadcastUsersNamesToPeers(Map<UUID, Peer> peersKv) {
        var peers = peersKv.values().stream().map(peer -> Pair.of(peer.getRole(), peer.getUserName())).toList();
        peersKv.values().forEach(peer -> {
            try {
                peer.getCallback().onUsersChanged(peers);
            } catch (Exception e) {
                logger.warning("(To %s) onUsersChanged(%s): %s: %s".formatted(peer.getUserName(), peers, e.getClass().getSimpleName(), e.getMessage()));
            }
        });
    }

    // Creates a new manager session and only one manager is allowed at a time.
    @Override
    public UUID create(String userName, SessionCallback sessionCallback) throws RemoteException, UserAlreadyExistsException, ManagerLimitExceededException {
        logger.info("create(%s, %s)".formatted(userName, sessionCallback));
        var peers = peersMgr.getPeers();

        synchronized (peers) {
            for (Peer peer : peers.values()) {
                // Only one manager allowed
                if (peer.getRole() == UserRole.MANAGER) {
                    throw new ManagerLimitExceededException();
                }
                // Prevent duplicate usernames
                if (peer.getUserName().equals(userName)) {
                    throw new UserAlreadyExistsException();
                }
            }

            var manager = new Peer(userName, UserRole.MANAGER, sessionCallback, System.currentTimeMillis());
            var uuid = UUID.randomUUID();
            while (peers.containsKey(uuid))
                uuid = UUID.randomUUID();

            peers.put(uuid, manager);

            try {
                broadcastUsersNamesToPeers(peers);
            } catch (Exception e) {
                logger.warning("broadcastUsersNamesToPeers(%s): %s".formatted(peers, e.getMessage()));
            }

            return uuid;
        }
    }

    // Joins a user to the session. Requires a manager to be present and manager approval.
    @Override
    public UUID join(String userName, SessionCallback sessionCallback) throws RemoteException, UserAlreadyExistsException, NoManagerAvailableException {
        logger.info("join(%s, %s)".formatted(userName, sessionCallback));
        var peers = peersMgr.getPeers();

        synchronized (peers) {
            Peer manager = null;
            for (Peer peer : peers.values()) {
                if (peer.getRole() == UserRole.MANAGER) {
                    manager = peer;
                }
                // Prevent duplicate usernames
                if (peer.getUserName().equals(userName)) {
                    throw new UserAlreadyExistsException();
                }
            }
            if (manager == null) {
                // Must have a manager present
                throw new NoManagerAvailableException();
            }

            // Wait for manager approval
            try {
                if (!manager.getCallback().onIncomingRequest(userName)) {
                    return null;
                }
            } catch (Exception ignored) {
                return null;
            }

            var user = new Peer(userName, UserRole.USER, sessionCallback, System.currentTimeMillis());

            var uuid = UUID.randomUUID();
            synchronized (peers) {
                while (peers.containsKey(uuid))
                    uuid = UUID.randomUUID();
                peers.put(uuid, user);
            }

            broadcastUsersNamesToPeers(peers);

            return uuid;
        }

    }

    // Removes a peer from the session. If the manager leaves, notifies all users to terminate.
    @Override
    public synchronized void leave(UUID peerId) throws RemoteException, UserNotFoundException {
        logger.info("leave(%s)".formatted(peerId));
        var peers = peersMgr.getPeers();

        synchronized (peers) {
            var peerLeaving = peers.get(peerId);
            if (peerLeaving == null) {
                throw new UserNotFoundException();
            }

            peers.remove(peerId);

            broadcastUsersNamesToPeers(peers);

            // If the manager leaves, all remaining peers must terminate their sessions.
            if (peerLeaving.getRole() == UserRole.MANAGER) {
                peers.values().forEach(peer -> {
                    try {
                        peer.getCallback().onTerminated();
                    } catch (RemoteException e) {
                        logger.warning("(To %s) RemoteException: %s".formatted(peer.getUserName(), e.getMessage()));
                    }
                });
                // Single-session design: nothing left to serve once the manager exits.
                // Delay slightly so the RMI reply can return before the JVM exits.
                new Thread(() -> {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    logger.info("Manager left — server shutting down.");
                    System.exit(0);
                }, "server-shutdown").start();
            }
            // The departing peer initiated this call themselves; no callback needed.
        }
    }

    // Sends a chat message from a peer to all others.
    @Override
    public void sendChatMessage(UUID peerId, String message) throws RemoteException, UserNotFoundException {
        var peersKv = peersMgr.getPeers();

        var peerSender = peersKv.get(peerId);
        if (peerSender == null) {
            throw new UserNotFoundException();
        }

        chatMessages.add(Triple.of(peerId, peerSender.getUserName(), message));

        peersKv.values().forEach(peer -> {
            try {
                peer.getCallback().onChatMessageAdded(peerSender.getUserName(), message);
            } catch (Exception e) {
                logger.warning("(To %s) onChatMessageAdded(%s, \"%s\"): %s: %s".formatted(peer.getUserName(), peerSender.getUserName(), message, e.getClass().getSimpleName(), e.getMessage()));
            }
        });
    }

    // Returns the list of users in the session.
    @Override
    public Collection<Pair<UserRole, String>> getUsers() throws RemoteException {
        return peersMgr.getPeers().values().stream().map(peer -> Pair.of(peer.getRole(), peer.getUserName())).toList();
    }

    // Returns the chat message history.
    @Override
    public Collection<Pair<String, String>> getChatMessages() throws RemoteException {
        // synchronizedList requires explicit lock for iteration/stream traversal
        synchronized (chatMessages) {
            return chatMessages.stream().map(item -> Pair.of(item.getMiddle(), item.getRight())).toList();
        }
    }

    // Updates the last heartbeat time for a peer.
    @Override
    public void heartbeat(UUID peerId) throws RemoteException {
        var peers = peersMgr.getPeers();
        var peer = peers.get(peerId);
        // Peer may have been removed (kicked/left) while this heartbeat was in flight.
        if (peer == null) {
            logger.fine(String.format("heartbeat from unknown peer %s — ignored", peerId));
            return;
        }
        peer.setLastHeartbeatTimeMs(System.currentTimeMillis());
        logger.fine(String.format("%s heartbeat", peer));
    }

    // Removes a user from the session. Caller must be the manager.
    @Override
    public boolean kickOut(UUID peerId, String targetUserName) throws RemoteException, UserNotFoundException {
        var peers = peersMgr.getPeers();
        synchronized (peers) {
            var caller = peers.get(peerId);
            if (caller == null || caller.getRole() != UserRole.MANAGER) {
                throw new SessionService.PermissionDeniedException("Only the manager can kick out users");
            }

            var targetEntry = peers.entrySet().stream()
                    .filter(e -> e.getValue().getUserName().equals(targetUserName))
                    .findFirst()
                    .orElseThrow(UserNotFoundException::new);

            var targetUUID = targetEntry.getKey();
            var target = targetEntry.getValue();
            final String managerName = caller.getUserName();

            // Notify the kicked peer asynchronously so the RMI call doesn't block.
            Thread t = new Thread(() -> {
                try {
                    target.getCallback().onKickedOut(managerName);
                } catch (Exception e) {
                    logger.warning("onKickedOut(%s): %s".formatted(targetUserName, e.getMessage()));
                }
            });
            t.setDaemon(true);
            t.start();

            peers.remove(targetUUID);
            broadcastUsersNamesToPeers(peers);
        }
        return true;
    }

    // Called when this remote object is no longer referenced.
    @Override
    public void unreferenced() {
        logger.info("This object is no longer referenced.");
    }

}