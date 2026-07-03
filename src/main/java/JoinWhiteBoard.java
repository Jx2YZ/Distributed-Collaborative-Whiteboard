/**
 * Entry point for joining an existing whiteboard as a user; launches the PeerForm GUI and connects to the RMI server.
 * Jx2
 * v.22/05/2026
 **/

import client.ui.PeerForm;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

public class JoinWhiteBoard {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("java JoinWhiteBoard <serverIPAddress> <serverPort> username");
            return;
        }

        var serverIpAddress = args[0];
        var serverPort = Integer.parseInt(args[1]);
        var userName = args[2];

        System.out.printf("Join White Board: %s %d %s\n", serverIpAddress, serverPort, userName);

        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();

            // Create and configure the main PeerForm window as a user
            PeerForm frame = new PeerForm("Distributed Shared White Board", false, serverIpAddress, serverPort, userName);
            //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            // frame.pack();
            frame.setSize(1024, 768);
            frame.setLocationRelativeTo(null);

            frame.setVisible(true);
        });
    }

}