/**
 * Represents a connected peer user/manager in the whiteboard server which stores user information, role, callback reference, and heartbeat timestamp.
 * Jx2
 * v.22/05/2026
 **/

package server;

import base.Constants;
import base.UserRole;
import base.rmi.SessionService;

import java.util.Objects;

public final class Peer {

    private final String userName;                         // Username of the peer
    private final UserRole role;                           // Role (MANAGER/USER)
    private final SessionService.SessionCallback callback; // RMI callback for client notifications
    private long lastHeartbeatTimeMs;                      // Last heartbeat timestamp

    // Constructs a Peer with the given information
    public Peer(String userName, UserRole role, SessionService.SessionCallback callback, long lastHeartbeatTimeMs) {
        this.userName = userName;
        this.role = role;
        this.callback = callback;
        this.lastHeartbeatTimeMs = lastHeartbeatTimeMs;
    }

    public String getUserName() {
        return userName;
    }

    public UserRole getRole() {
        return role;
    }

    public SessionService.SessionCallback getCallback() {
        return callback;
    }

    public long getLastHeartbeatTimeMs() {
        return lastHeartbeatTimeMs;
    }

    public void setLastHeartbeatTimeMs(long lastHeartbeatTimeMs) {
        this.lastHeartbeatTimeMs = lastHeartbeatTimeMs;
    }

    // Checks if the peer is considered alive based on the last heartbeat
    public boolean isAlive() {
        return System.currentTimeMillis() - this.lastHeartbeatTimeMs <= Constants.HEARTBEAT_INTERVAL_MS_MAX;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Peer) obj;
        return Objects.equals(this.userName, that.userName) &&
                Objects.equals(this.role, that.role) &&
                Objects.equals(this.callback, that.callback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, role, callback);
    }

    @Override
    public String toString() {
        return "Peer[" +
                "userName=" + userName + ", " +
                "role=" + role + ", " +
                "callback=" + callback + ']';
    }

}