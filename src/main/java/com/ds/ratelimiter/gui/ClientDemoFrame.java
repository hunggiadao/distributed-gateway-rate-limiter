package com.ds.ratelimiter.gui;

import com.ds.ratelimiter.model.ApiRequest;
import com.ds.ratelimiter.model.ApiResponse;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.*;
import java.net.*;
import java.io.*;

public class ClientDemoFrame extends JFrame {
	// private final GatewayDemoFrame gatewayFrame;
	private final Random random = new Random();

	private final JTextField clientField = new JTextField();
	private final JComboBox<String> pathBox = new JComboBox<>(new String[]{"/users", "/orders", "/payments", "/unknown", "Randomize", "Randomize (Known)"});
	private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE", "Randomize"});
	private final JComboBox<String> gatewayBox = new JComboBox<>(new String[]{"Round Robin", "Gateway-1", "Gateway-2", "Gateway-3"});

	private final JSpinner burstSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000000, 10));
	private final JSpinner burstDelaySpinner = new JSpinner(new SpinnerNumberModel(1, 0, 5000, 1));

	private final JButton sendButton = new JButton("Send 1 Request");
	private final JButton burstButton = new JButton("Send Burst");

	private final JTextPane logArea = new JTextPane();
	private final JLabel remainingLabel = new JLabel("Remaining Tokens -> -");
	private final JButton inspectBtn = new JButton("Inspect DB State");
	private final JButton clearBtn = new JButton("Clear Console");

	// CRITICAL FIX: Replaced Object streams with raw Data streams to prevent NotSerializableException
	private Socket socket;
	private DataOutputStream out;
	private DataInputStream in;

	public ClientDemoFrame(GatewayDemoFrame gatewayFrame, String defaultClientId, int startX, int startY) {
		// this.gatewayFrame = gatewayFrame;
		setTitle("Client Request Dashboard - " + defaultClientId);
		setSize(750, 300);
		setLocation(startX, startY);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		clientField.setText(defaultClientId);
		pathBox.setSelectedItem("Randomize (Known)");

		sendButton.addActionListener(e -> sendSingleRequest());
		burstButton.addActionListener(e -> sendBurstRequests());

		JPanel panel = new JPanel(new GridLayout(2, 1, 0, 2));
		panel.setBorder(BorderFactory.createTitledBorder("Client Request Options"));

		JPanel rrRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		rrRow1.add(new JLabel("Client ID:"));
		rrRow1.add(clientField);
		clientField.setColumns(6);
		rrRow1.add(new JLabel("Method:"));
		rrRow1.add(methodBox);
		rrRow1.add(new JLabel("Path:"));
		rrRow1.add(pathBox);
		rrRow1.add(new JLabel("Gateway Target:"));
		rrRow1.add(gatewayBox);

		JPanel rrRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		rrRow2.add(new JLabel("Burst Count:"));
		rrRow2.add(burstSpinner);
		rrRow2.add(new JLabel("Burst Delay (ms):"));
		rrRow2.add(burstDelaySpinner);
		rrRow2.add(burstButton);
		rrRow2.add(sendButton);

		panel.add(rrRow1);
		panel.add(rrRow2);

		logArea.setEditable(false);
		JScrollPane scrollPane = new JScrollPane(logArea);
		scrollPane.setBorder(BorderFactory.createTitledBorder("Local Client Console Log"));

		add(panel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		bottomPanel.add(remainingLabel, BorderLayout.CENTER);
		JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		bottomButtonPanel.add(inspectBtn);
		bottomButtonPanel.add(clearBtn);
		bottomPanel.add(bottomButtonPanel, BorderLayout.EAST);
		add(bottomPanel, BorderLayout.SOUTH);

		clearBtn.addActionListener(e -> logArea.setText(""));
		inspectBtn.addActionListener(e -> updateRemainingTokens());

		log("Client dashboard initialized. Connecting to Gateway Engine...");
		connectToGateway();

		javax.swing.Timer autoRefreshTimer = new javax.swing.Timer(100, e -> updateRemainingTokens());
		autoRefreshTimer.start();
	}

	private void connectToGateway() {
		// CRITICAL FIX: Self-healing background reconnect loop
		new Thread(() -> {
			while (true) {
				if (socket == null || socket.isClosed()) {
					try {
						socket = new Socket("localhost", 12345);
						out = new DataOutputStream(socket.getOutputStream());
						in = new DataInputStream(socket.getInputStream());
						
						log("Successfully connected to Distributed Gateway Engine.");
						remoteRegisterClient(getClientId());
						
					} catch (Exception e) {
						socket = null;
						try { Thread.sleep(1000); } catch (InterruptedException ex) {}
						continue; // Keep trying until it wakes up
					}
				}
				
				try {
					synchronized (socket) {
						out.writeUTF("UPDATE_STATUS");
						out.writeUTF(getClientId());
						out.writeUTF(getSelectedPath());
						out.flush();
					}
					Thread.sleep(100);
				} catch (Exception e) {
					try { socket.close(); } catch (Exception ex) {}
					socket = null;
				}
			}
		}).start();
	}

	private void remoteRegisterClient(String clientId) {
		try {
			if (socket != null && !socket.isClosed()) {
				synchronized (socket) {
					out.writeUTF("REGISTER");
					out.writeUTF(clientId);
					out.flush();
				}
			}
		} catch (Exception e) {}
	}

	private void remoteLog(String text) {
		try {
			if (socket != null && !socket.isClosed()) {
				synchronized (socket) {
					out.writeUTF("LOG");
					out.writeUTF(text);
					out.flush();
				}
			}
		} catch (Exception e) {}
	}

	private void remoteUpdateRemainingTokensTrigger() {
		try {
			if (socket != null && !socket.isClosed()) {
				synchronized (socket) {
					out.writeUTF("UPDATE_REMAINING_TOKENS_TRIGGER");
					out.flush();
				}
			}
		} catch (Exception e) {}
	}

	// CRITICAL FIX: Manually sending primitive parameters to avoid NotSerializableException
	private ApiResponse remoteSendRoundRobin(ApiRequest request) {
		try {
			if (socket != null && !socket.isClosed()) {
				synchronized (socket) {
					out.writeUTF("SEND_ROUND_ROBIN");
					out.writeUTF(request.getClientId());
					out.writeUTF(request.getPath());
					out.writeUTF(request.getMethod());
					out.flush();
					
					int status = in.readInt();
					String msg = in.readUTF();
					String gw = in.readUTF();
					int tokens = in.readInt();
					return new ApiResponse(status, msg, gw, tokens);
				}
			}
		} catch (Exception e) {
			try { socket.close(); } catch (Exception ex) {}
			socket = null;
		}
		return new ApiResponse(503, "Cluster offline", "Local", 0);
	}

	private ApiResponse remoteSendToNode(ApiRequest request, int nodeIndex) {
		try {
			if (socket != null && !socket.isClosed()) {
				synchronized (socket) {
					out.writeUTF("SEND_TO_NODE");
					out.writeUTF(request.getClientId());
					out.writeUTF(request.getPath());
					out.writeUTF(request.getMethod());
					out.writeInt(nodeIndex);
					out.flush();
					
					int status = in.readInt();
					String msg = in.readUTF();
					String gw = in.readUTF();
					int tokens = in.readInt();
					return new ApiResponse(status, msg, gw, tokens);
				}
			}
		} catch (Exception e) {
			try { socket.close(); } catch (Exception ex) {}
			socket = null;
		}
		return new ApiResponse(503, "Cluster offline", "Local", 0);
	}

	public String getClientId() {
		return clientField.getText().trim().isEmpty() ? "anonymous-client" : clientField.getText().trim();
	}

	public String getSelectedPath() {
		return String.valueOf(pathBox.getSelectedItem());
	}

	private void sendSingleRequest() {
		ApiRequest request = createRequest();
		ApiResponse response = routeRequest(request);
		printResponse(request, response);
		remoteUpdateRemainingTokensTrigger();
	}

	private void sendBurstRequests() {
		int count = (int) burstSpinner.getValue();
		int delay = (int) burstDelaySpinner.getValue();

		new SwingWorker<Void, String>() {
			int totalUsers = 0, successUsers = 0;
			int totalOrders = 0, successOrders = 0;
			int totalPayments = 0, successPayments = 0;
			int totalUnknown = 0, successUnknown = 0;
			int totalSent = 0, totalSuccess = 0;

			@Override
			protected Void doInBackground() throws Exception {
				for (int i = 0; i < count; i++) {
					if (delay > 0 && i > 0) {
						Thread.sleep(delay);
					}
					totalSent++;
					
					ApiRequest request = createRequest();
					ApiResponse response = routeRequest(request);

					boolean isSuccess = (response.getStatusCode() == 200);
					if (isSuccess) totalSuccess++;

					if (request.getPath().startsWith("/users")) {
						totalUsers++;
						if (isSuccess) successUsers++;
					} else if (request.getPath().startsWith("/orders")) {
						totalOrders++;
						if (isSuccess) successOrders++;
					} else if (request.getPath().startsWith("/payments")) {
						totalPayments++;
						if (isSuccess) successPayments++;
					} else {
						totalUnknown++;
						if (isSuccess) successUnknown++;
					}

					publish(formatResponse(request, response, totalSuccess, count));
				}
				return null;
			}

			@Override
			protected void process(List<String> chunks) {
				for (String logLine : chunks) {
					remoteLog(logLine);
					log(logLine);
				}
				remoteUpdateRemainingTokensTrigger();
			}

			@Override
			protected void done() {
				String summaryHeader = "================ CLIENT BURST SUMMARY ================";
				String lineSuccess = String.format("Overall successful: %d / %d", totalSuccess, totalSent);
				String linePayments = String.format("  - /payments : %d / %d handled", successPayments, totalPayments);
				String lineUsers = String.format("  - /users    : %d / %d handled", successUsers, totalUsers);
				String lineOrders = String.format("  - /orders   : %d / %d handled", successOrders, totalOrders);
				String lineUnknown = String.format("  - /unknown  : %d / %d handled", successUnknown, totalUnknown);
				String summaryFooter = "======================================================";

				remoteLog(summaryHeader);
				remoteLog(lineSuccess);
				remoteLog(linePayments);
				remoteLog(lineUsers);
				remoteLog(lineOrders);
				remoteLog(lineUnknown);
				remoteLog(summaryFooter);

				log(summaryHeader);
				log(lineSuccess);
				log(linePayments);
				log(lineUsers);
				log(lineOrders);
				log(lineUnknown);
				log(summaryFooter);

				remoteUpdateRemainingTokensTrigger();
			}
		}.execute();
	}

	private ApiRequest createRequest() {
		String clientId = getClientId();
		String path = getSelectedPath();
		if ("Randomize".equals(path)) {
			String[] paths = {"/users", "/orders", "/payments", "/unknown"};
			path = paths[random.nextInt(paths.length)];
		} else if ("Randomize (Known)".equals(path)) {
			String[] paths = {"/users", "/orders", "/payments"};
			path = paths[random.nextInt(paths.length)];
		}
		
		String method = String.valueOf(methodBox.getSelectedItem());
		if ("Randomize".equals(method)) {
			String[] methods = {"GET", "POST", "PUT", "DELETE"};
			method = methods[random.nextInt(methods.length)];
		}
		
		return new ApiRequest(clientId, path, method);
	}

	private ApiResponse routeRequest(ApiRequest request) {
		int selected = gatewayBox.getSelectedIndex();
		if (selected == 0) return remoteSendRoundRobin(request);
		return remoteSendToNode(request, selected - 1);
	}

	private void updateRemainingTokens() {
		String currentClientId = clientField.getText();
		String selectedPath = (String) pathBox.getSelectedItem();
		
		String inspectionPath = "/users";
		if (selectedPath != null && !selectedPath.contains("Randomize")) {
			inspectionPath = selectedPath;
		}
		
		String details = remoteGetRemainingTokensDetailed(currentClientId, inspectionPath);
		remainingLabel.setText("Remaining Tokens -> " + details);
	}

	private String remoteGetRemainingTokensDetailed(String clientId, String path) {
		try {
			if (socket != null && !socket.isClosed()) {
				synchronized (socket) {
					out.writeUTF("GET_REMAINING_TOKENS");
					out.writeUTF(clientId);
					out.writeUTF(path);
					out.flush();
					return in.readUTF();
				}
			}
		} catch (Exception e) {
			try { socket.close(); } catch (Exception ex) {}
			socket = null;
		}
		return "Cluster offline";
	}

	private void printResponse(ApiRequest request, ApiResponse response) { 
		String output = formatResponse(request, response);
		remoteLog(output); 
		log(output);
	}

	public void log(String text) {
		Color color = Color.BLACK;
		if (text.contains("status=") && !text.contains("status=200")) {
			if (text.contains("status=429")) {
				color = new Color(255, 0, 0);
			} else if (text.contains("status=404")) {
				color = new Color(255, 179, 0);
			}
		}

		try {
			javax.swing.text.StyledDocument doc = logArea.getStyledDocument();
			javax.swing.text.SimpleAttributeSet aset = new javax.swing.text.SimpleAttributeSet();
			javax.swing.text.StyleConstants.setForeground(aset, color);
			doc.insertString(doc.getLength(), text + System.lineSeparator(), aset);
			logArea.setCaretPosition(doc.getLength());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String formatResponse(ApiRequest request, ApiResponse response, int handledCount, int totalCount) {
		return String.format(
				"[%s] handled=%d/%d tx=%s client=%s %s %s node=%s status=%d node_tokens=%d msg=%s",
				LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
				handledCount, totalCount,
				request.getRequestId(), request.getClientId(), request.getMethod(), request.getPath(),
				response.getGatewayName(), response.getStatusCode(), response.getRemainingTokens(), response.getMessage()
		);
	}

	private String formatResponse(ApiRequest request, ApiResponse response) {
		return String.format(
				"[%s] tx=%s client=%s %s %s node=%s status=%d node_tokens=%d msg=%s",
				LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
				request.getRequestId(), request.getClientId(), request.getMethod(), request.getPath(),
				response.getGatewayName(), response.getStatusCode(), response.getRemainingTokens(), response.getMessage()
		);
	}
}