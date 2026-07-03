/**
 * Exception thrown when a specified user is not found in the system.
 * Jx2
 * v.22/05/2026
 **/

package base.rmi;

import java.io.Serializable;

public class UserNotFoundException extends Exception implements Serializable {
    public UserNotFoundException() {
        super("The specified user does not exist");
    }
}