/**
 * Base class for implementing SessionService.SessionCallback in RMI that provides constructors for different RMI socket configurations.
 * Jx2
 * v.22/05/2026
 **/

package client;

import base.rmi.SessionService;

import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

public abstract class SessionCallbackBase extends UnicastRemoteObject implements SessionService.SessionCallback {

    // Default constructor
    protected SessionCallbackBase() throws RemoteException {
    }

    // Constructor with custom port
    protected SessionCallbackBase(int port) throws RemoteException {
        super(port);
    }

    // Constructor with custom port and socket factories
    protected SessionCallbackBase(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
    }

}