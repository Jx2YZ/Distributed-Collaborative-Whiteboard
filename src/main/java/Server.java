/**
 * Main entry point for the whiteboard server, sets up the RMI registry, initializes services, and binds them for remote access.
 * Jx2
 * v.22/05/2026
 **/

import base.rmi.BoardService;
import base.rmi.SessionService;
import server.BoardServiceImpl;
import server.PeersManager;
import server.SessionServiceImpl;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.LogManager;

public class Server {

    public static void main(String[] args) throws RemoteException {
        int serverPort = 1099; // Defualt port number as 1099, can be configured within bash command when before starting the server
        if (args.length > 0) {
            // System.out.println("java Server [serverPort]");
            serverPort = Integer.parseInt(args[0]);
        }

        try {
            LogManager.getLogManager().readConfiguration();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Create RMI registry on the specified port
        Registry registry = LocateRegistry.createRegistry(serverPort);

        // Initialize peer manager and services
        PeersManager peersManager = new PeersManager();

        SessionService sessionService = new SessionServiceImpl(peersManager);
        BoardServiceImpl boardService = new BoardServiceImpl(peersManager);
        boardService.clear();

        // Export remote objects for RMI
        SessionService sessionServiceSkeleton = (SessionService) UnicastRemoteObject.exportObject(sessionService, 0);
        BoardService boardServiceSkeleton = (BoardService) UnicastRemoteObject.exportObject(boardService, 0);

        // Bind services to the registry for remote access
        registry.rebind("rmi:///SessionService", sessionServiceSkeleton);
        registry.rebind("rmi:///BoardService", boardServiceSkeleton);
        System.out.println("Server running on port " + serverPort + "...");
    }

}