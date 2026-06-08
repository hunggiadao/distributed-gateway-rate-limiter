package com.ds.ratelimiter.gui;

import com.ds.ratelimiter.model.ApiRequest;
import com.ds.ratelimiter.model.ApiResponse;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class ClientDemoFrame extends JFrame {
    private final GatewayDemoFrame gatewayFrame;
    private final Random random = new Random();

    private final JTextField clientField = new JTextField();
    private final JComboBox<String> pathBox = new JComboBox<>(new String[]{"/users", "/orders", "/payments", "/unknown", "Randomize", "Randomize (Known)"});
    private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE", "Randomize"});
    private final JComboBox<String> gatewayBox = new JComboBox<>(new String[]{"Round Robin", "Gateway-1", "Gateway-2", "Gateway-3"});

    private final JSpinner burstSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000000, 10));
    private final JSpinner burstDelaySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 5000, 1));

    private final JButton sendButton = new JButton("Send 1 Request");
    private final JButton burstButton = new JButton("Send Burst");

    // New console elements matching Gateway's design exactly
    private final JTextPane logArea = new JTextPane();

	// NEW: Bottom panel components
    private final JLabel remainingLabel = new JLabel("Remaining Tokens -> -");
    private final JButton inspectBtn = new JButton("Inspect DB State");
    private final JButton clearBtn = new JButton("Clear Console");

    public ClientDemoFrame(GatewayDemoFrame gatewayFrame, String defaultClientId, int startX, int startY) {
        this.gatewayFrame = gatewayFrame;
        setTitle("Client Request Dashboard - " + defaultClientId);
        setSize(750, 300); // Expanded vertical space to account for the console
        setLocation(startX, startY);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        clientField.setText(defaultClientId);
        pathBox.setSelectedItem("Randomize (Known)");

        sendButton.addActionListener(e -> {
			// String clientId = clientField.getText();
            
            // // NEW: Dynamically register this client ID with the gateway UI
            // gatewayFrame.registerClient(clientId);
			sendSingleRequest();
		});
        burstButton.addActionListener(e -> {
			// String clientId = clientField.getText();
            // gatewayFrame.registerClient(clientId); // NEW
			sendBurstRequests();
		});

        // Existing control setup panel
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

        // Configure new text pane log console exactly like the Gateway's console
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Local Client Console Log"));

        // Layout injection
        add(panel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        
        log("Client dashboard initialization complete. Connected to Gateway Engine.");

		// --- NEW: Bottom Panel Setup ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		// Add the label to the center
        bottomPanel.add(remainingLabel, BorderLayout.CENTER);
        // Group the buttons on the right side
        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        bottomButtonPanel.add(inspectBtn);
        bottomButtonPanel.add(clearBtn);
        bottomPanel.add(bottomButtonPanel, BorderLayout.EAST);
        // Add the whole bottom panel to the SOUTH of the main JFrame
        add(bottomPanel, BorderLayout.SOUTH);

        // --- NEW: Button Actions ---
        clearBtn.addActionListener(e -> logArea.setText(""));
        inspectBtn.addActionListener(e -> updateRemainingTokens());

		// Assuming your constructor has a parameter for the initial client ID, or just pull it from the field:
        gatewayFrame.registerClient(clientField.getText());

		// Auto-refresh token metrics
        javax.swing.Timer autoRefreshTimer = new javax.swing.Timer(100, e -> updateRemainingTokens());
        autoRefreshTimer.start();
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
        gatewayFrame.updateRemainingTokens();
    }

    private void sendBurstRequests() {
        int count = (int) burstSpinner.getValue();
        int delay = (int) burstDelaySpinner.getValue();

        String clientId = getClientId();
        String selectedPath = getSelectedPath();
        String selectedMethod = String.valueOf(methodBox.getSelectedItem());
        int nodeSelectionIndex = gatewayBox.getSelectedIndex();
        String[] finalPaths = {"/users", "/orders", "/payments", "/unknown"};
        String[] knownPaths = {"/users", "/orders", "/payments"};

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
                    
                    String reqPath;
                    if ("Randomize".equals(selectedPath)) {
                        reqPath = finalPaths[random.nextInt(finalPaths.length)];
                    } else if ("Randomize (Known)".equals(selectedPath)) {
                        reqPath = knownPaths[random.nextInt(knownPaths.length)];
                    } else {
                        reqPath = selectedPath;
                    }

                    String reqMethod = "Randomize".equals(selectedMethod) ? (random.nextBoolean() ? "GET" : "POST") : selectedMethod;
                    ApiRequest request = new ApiRequest(clientId, reqPath, reqMethod);
                    
                    ApiResponse response;
                    if (nodeSelectionIndex == 0) {
                        response = gatewayFrame.getCluster().sendRoundRobin(request);
                    } else {
                        response = gatewayFrame.getCluster().sendToNode(request, nodeSelectionIndex - 1);
                    }

                    boolean isSuccess = (response.getStatusCode() == 200);
                    if (isSuccess) totalSuccess++;

                    if (reqPath.startsWith("/users")) {
                        totalUsers++;
                        if (isSuccess) successUsers++;
                    } else if (reqPath.startsWith("/orders")) {
                        totalOrders++;
                        if (isSuccess) successOrders++;
                    } else if (reqPath.startsWith("/payments")) {
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
            protected void process(java.util.List<String> chunks) {
                for (String logLine : chunks) {
                    gatewayFrame.log(logLine);
                    log(logLine); // Live updates streamed into local client log window too
                }
                gatewayFrame.updateRemainingTokens();
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

                // 1. Post to core Gateway monitor log
                gatewayFrame.log(summaryHeader);
                gatewayFrame.log(lineSuccess);
                gatewayFrame.log(linePayments);
                gatewayFrame.log(lineUsers);
                gatewayFrame.log(lineOrders);
                gatewayFrame.log(lineUnknown);
                gatewayFrame.log(summaryFooter);

                // 2. Post matching metrics directly into this client's isolated console
                log(summaryHeader);
                log(lineSuccess);
                log(linePayments);
                log(lineUsers);
                log(lineOrders);
                log(lineUnknown);
                log(summaryFooter);

                gatewayFrame.updateRemainingTokens();
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
        if (selected == 0) return gatewayFrame.getCluster().sendRoundRobin(request);
        return gatewayFrame.getCluster().sendToNode(request, selected - 1);
    }

	private void updateRemainingTokens() {
        if (gatewayFrame.getCluster() == null) {
            remainingLabel.setText("Remaining Tokens -> Cluster offline");
            return;
        }

        // Fetch current UI states
        String currentClientId = clientField.getText();
        String selectedPath = (String) pathBox.getSelectedItem();
        
        // Default to a safe path for inspection if randomize is selected
        String inspectionPath = "/users";
        if (selectedPath != null && !selectedPath.contains("Randomize")) {
            inspectionPath = selectedPath;
        }
        
        // Query the cluster
        String details = gatewayFrame.getCluster().getRemainingTokensDetailed(currentClientId, inspectionPath);
        remainingLabel.setText("Remaining Tokens -> " + details);
    }

    private void printResponse(ApiRequest request, ApiResponse response) { 
        String output = formatResponse(request, response);
        gatewayFrame.log(output); 
        log(output); // Push single-invocation tracking line straight to local window log
    }

    // Reused color formatting engine matching the core Gateway logic exactly
    public void log(String text) {
        java.awt.Color color = java.awt.Color.BLACK;
        if (text.contains("status=") && !text.contains("status=200")) {
            if (text.contains("status=429")) {
                color = new java.awt.Color(255, 0, 0); // Rejection Red
            } else if (text.contains("status=404")) {
                color = new java.awt.Color(255, 179, 0); // Missing Routing Warning Orange
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