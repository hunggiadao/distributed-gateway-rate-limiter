package com.ds.ratelimiter;

import com.ds.ratelimiter.gui.ClientDemoFrame;
import com.ds.ratelimiter.gui.GatewayDemoFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // 1. Fire up the core gateway engine dashboard
            GatewayDemoFrame gatewayFrame = new GatewayDemoFrame();
            
            // 2. Create 3 independent clients, stacking them vertically on the left side of the screen
            ClientDemoFrame client1 = new ClientDemoFrame(gatewayFrame, "client-1", 10, 10);
            ClientDemoFrame client2 = new ClientDemoFrame(gatewayFrame, "client-2", 10, 320);
            ClientDemoFrame client3 = new ClientDemoFrame(gatewayFrame, "client-3", 10, 630);
			ClientDemoFrame client4 = new ClientDemoFrame(gatewayFrame, "client-4", 770, 10);
            
            // 3. Connect Client 1's text fields to the Gateway's live token tracking status bar.
            // (The status bar will reflect Client 1's settings, but all 4 clients share the global limits)
            gatewayFrame.setClientFrame(client1);
            
            // 4. Reveal all windows
            gatewayFrame.setVisible(true);
            client1.setVisible(true);
            client2.setVisible(true);
            client3.setVisible(true);
			client4.setVisible(true);
        });
    }
}