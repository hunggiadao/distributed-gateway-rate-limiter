package com.ds.ratelimiter;

import com.ds.ratelimiter.gui.GatewayDemoFrame;

import javax.swing.SwingUtilities;

/**
 * Application entry point.
 *
 * Swing applications should start their GUI on the Event Dispatch Thread.
 * SwingUtilities.invokeLater(...) schedules the window creation on that thread,
 * which keeps the GUI stable and responsive.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GatewayDemoFrame().setVisible(true));
    }
}
