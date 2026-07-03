# Distributed Shared Whiteboard (Java RMI)

A real-time collaborative whiteboard built on **Java RMI** with a client-server architecture. A manager creates a session; peers request to join and are admitted by manager approval. All drawing, chat, and user-management updates flow through the server, which fans them out to every connected peer via RMI callbacks.

## Features

- **Drawing tools:** pen (free draw), eraser (adjustable 1–100 px), line, triangle, circle, rectangle, and text — 16 preset colours
- **Real-time preview streaming:** peers see each other's strokes *while they're being drawn* (throttled to ~20 fps), via a `PreviewPrimitive` overlay that never pollutes the authoritative canvas
- **Hybrid canvas sync:** full PNG snapshot on join, lightweight incremental primitives thereafter
- **Manager controls:** approve/reject join requests, kick peers, and File → New / Open / Save / Save As / Close
- **Chat** with full-history sync for late joiners
- **Failure detection:** 1 s client heartbeats with a server-side eviction thread (3 s threshold), so crashed or disconnected peers are removed automatically — including graceful session termination if the manager disappears

## Architecture

| Process | Entry point | Role |
|---|---|---|
| `Server.jar` | `Server.java` | Hosts all shared state; runs the RMI registry |
| `CreateWhiteBoard.jar` | `CreateWhiteBoard.java` | Starts a GUI session as the whiteboard **manager** |
| `JoinWhiteBoard.jar` | `JoinWhiteBoard.java` | Starts a GUI session as a regular **peer** |

Two remote services (`SessionService`, `BoardService`) are exposed by the server; each client exports a `SessionCallback` the server uses to push events (primitives, chat, user-list changes, kicks, termination).

## Building

```bash
# Clone and enter the repository
git clone https://github.com/<your-username>/distributed-shared-whiteboard.git
cd distributed-shared-whiteboard

# Verify prerequisites (Java 11+, Maven 3.6+)
java -version
mvn -version

# Build all three JARs
mvn clean package
```

Build output lands in `target/`:

```bash
ls target/*.jar
# target/Server.jar
# target/CreateWhiteBoard.jar
# target/JoinWhiteBoard.jar
```

## Running

Start the three processes in order — server first, then the manager, then any number of peers:

```bash
# Terminal 1 — server (creates the RMI registry on <port>)
java -jar target/Server.jar 1099

# Terminal 2 — manager (creates the session)
java -jar target/CreateWhiteBoard.jar localhost 1099 manager

# Terminal 3+ — peers (join requests pop up on the manager's screen for approval)
java -jar target/JoinWhiteBoard.jar localhost 1099 alice
java -jar target/JoinWhiteBoard.jar localhost 1099 bob
```

| Argument | Description | Example |
|---|---|---|
| `<port>` | Port for the RMI registry | `1099` |
| `<serverIP>` | Hostname or IP of the server | `localhost` |
| `<username>` | Display name; must be unique within the session | `alice` |

To run across machines, replace `localhost` with the server's IP and ensure the port is reachable.

## Trying the collaboration features

```bash
# Kick a peer:       manager right-clicks a name in the user list → Kick out
# Live previews:     drag a stroke on one client and watch it stream on the others
# Canvas sync test:  manager File → Open a PNG — every peer's canvas switches
# Failure detection: force-kill a peer (Ctrl+C / kill -9) — it vanishes from
#                    everyone's user list within ~3 seconds via heartbeat eviction
```

## Limitations

- Canvas fixed at 800×600; opened images are scaled without preserving aspect ratio
- No persistent storage — canvas and chat history are in server memory only
- One session per server instance; committed shapes cannot be moved or resized
