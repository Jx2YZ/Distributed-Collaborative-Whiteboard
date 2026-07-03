/**
 * Manages all connected peers (users and manager) in the whiteboard server that provides methods to query, add, and manage peer connections.
 * Jx2
 * v.22/05/2026
 **/

package server;

import base.UserRole;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PeersManager {

    private final Map<UUID, Peer> peers = new ConcurrentHashMap<>(); // Stores all connected peers

    // Returns the map of all peers
    public Map<UUID, Peer> getPeers() {
        return peers;
    }

    // Checks if a peer with the given UUID exists
    public boolean containsUser(UUID uuid) {
        return peers.containsKey(uuid);
    }

    // Checks if a peer with the given username exists
    public boolean containsUser(String userName) {
        for (Peer peer : peers.values()) {
            if (userName.equals(peer.getUserName())) {
                return true;
            }
        }
        return false;
    }

    // Gets a peer by username, if present
    public Optional<Peer> getPeerByUserName(String userName) {
        return peers.values().stream()
                .filter(p -> userName.equals(p.getUserName()))
                .findAny();
    }

    // Gets the manager peer, if present
    public Optional<Peer> getManager() {
        return peers.values().stream()
                .filter(p -> p.getRole() == UserRole.MANAGER)
                .findAny();
    }

}