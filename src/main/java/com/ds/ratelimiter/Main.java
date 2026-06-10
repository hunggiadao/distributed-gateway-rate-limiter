package com.ds.ratelimiter;

import com.ds.ratelimiter.gui.ClientDemoFrame;
import com.ds.ratelimiter.gui.GatewayDemoFrame;

import java.io.File;

import javax.swing.SwingUtilities;

/**
 * The Main class serves as the entry point for the distributed rate limiter application.
 * It can operate in two modes:
 * 1. Orchestration coordinator mode: Spawns the gateway and client processes.
 * 2. Gateway or client mode: Launches the respective GUI for the gateway or client.
 */
public class Main {
	public static void main(String[] args) {
		if (args.length == 0) {
			// Orchestration coordinator mode: spawns individual memory processes via independent JVM calls
			try {
				String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
				String classpath = System.getProperty("java.class.path");
				String className = Main.class.getName();
				System.out.println("Class path: " + classpath);
				System.out.println("Class name: " + className);

				// 1. Spawns independent core cluster gateway server process
				new ProcessBuilder(javaBin, "-cp", classpath, className, "gateway").start();
				
				// CRITICAL FIX: Give the Gateway JVM 2 full seconds to boot and bind the TCP port
				Thread.sleep(2000);

				// 2. Spawn 4 isolated client cluster dashboard processes stack-aligned as requested
				new ProcessBuilder(javaBin, "-cp", classpath, className, "client", "client-1", "10", "10").start();
				new ProcessBuilder(javaBin, "-cp", classpath, className, "client", "client-2", "10", "320").start();
				new ProcessBuilder(javaBin, "-cp", classpath, className, "client", "client-3", "10", "630").start();
				new ProcessBuilder(javaBin, "-cp", classpath, className, "client", "client-4", "770", "10").start();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// If the first argument is "gateway", launch the gateway GUI
		else if ("gateway".equalsIgnoreCase(args[0])) {
			SwingUtilities.invokeLater(() -> {
				GatewayDemoFrame gatewayFrame = new GatewayDemoFrame();
				gatewayFrame.setVisible(true);
			});
		}
		// If the first argument is "client", launch the client GUI
		else if ("client".equalsIgnoreCase(args[0])) {
			String clientId = args[1];
			int startX = Integer.parseInt(args[2]);
			int startY = Integer.parseInt(args[3]);
			SwingUtilities.invokeLater(() -> {
				ClientDemoFrame clientFrame = new ClientDemoFrame(null, clientId, startX, startY);
				clientFrame.setVisible(true);
			});
		}
	}
}