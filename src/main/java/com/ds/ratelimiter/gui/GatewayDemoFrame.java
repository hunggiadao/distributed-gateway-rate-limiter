package com.ds.ratelimiter.gui;

import com.ds.ratelimiter.gateway.GatewayCluster;
import com.ds.ratelimiter.model.ApiRequest;
import com.ds.ratelimiter.model.ApiResponse;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class GatewayDemoFrame extends JFrame {
    private GatewayCluster cluster;
    private final Random random = new Random();

    private final JTextField clientField = new JTextField("client-a");
    private final JComboBox<String> pathBox = new JComboBox<>(new String[]{"/users", "/orders", "/payments", "/unknown", "Randomize", "Randomize (Known)"});
    private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE", "Randomize"});
    private final JComboBox<String> gatewayBox = new JComboBox<>(new String[]{"Round Robin", "Gateway-1", "Gateway-2", "Gateway-3"});

    // Unique UI configs mapping directly to individual algorithms
    private final JCheckBox chkTokenBucket = new JCheckBox("Token Bucket", false);
    private final JSpinner tbCapacitySpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000000, 1));
    private final JSpinner tbRefillSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 1000000.0, 0.1));

    private final JCheckBox chkFixedWindow = new JCheckBox("Fixed Window", false);
    private final JSpinner fwLengthSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 100000, 100));
    private final JSpinner fwLimitSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000000, 1));

    private final JCheckBox chkSlidingWindow = new JCheckBox("Sliding Window", false);
    private final JSpinner swLengthSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 100000, 100));
    private final JSpinner swLimitSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000000, 1));

    private final JCheckBox chkLoadShedder = new JCheckBox("Fleet Load Shedder", false);
    // private final JSpinner lsUsersSpinner = new JSpinner(new SpinnerNumberModel(40.0, 0.0, 100.0, 5.0));
    // private final JSpinner lsOrdersSpinner = new JSpinner(new SpinnerNumberModel(35.0, 0.0, 100.0, 5.0));
    private final JSpinner lsPaymentsSpinner = new JSpinner(new SpinnerNumberModel(25.0, 0.0, 100.0, 5.0));

    private final JSpinner serverMaxRateSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 1000, 1));

    private final JSpinner burstSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 100000, 1));
    private final JSpinner burstDelaySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 5000, 1));
	// private final JSpinner burstDelayMicrosSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 5000000, 100)); // Default 0, min 0, max 5s, step 100us
    private final JLabel remainingLabel = new JLabel("Remaining tokens: -");
    private final JTextPane logArea = new JTextPane();

    // Reusable core functional buttons repositioned within panels
    private final JButton sendButton = new JButton("Send 1 Request");
    private final JButton burstButton = new JButton("Send Burst");
    private final JButton resetButton = new JButton("Apply Config / Reset Shared DB");
    private final JButton clearButton = new JButton("Clear Console");
    private final JButton tokensButton = new JButton("Inspect DB State");

    public GatewayDemoFrame() {
        setTitle("Distributed Rate Limiter and API Gateway Demo");
        setSize(1200, 850);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set pathBox default selection
        pathBox.setSelectedItem("Randomize (Known)");

        // Map Button Listeners early
        sendButton.addActionListener(e -> sendSingleRequest());
        burstButton.addActionListener(e -> sendBurstRequests());
        resetButton.addActionListener(e -> resetCluster());
        clearButton.addActionListener(e -> logArea.setText(""));
        tokensButton.addActionListener(e -> updateRemainingTokens());

        buildClusterInstance();

        add(createTopPanel(), BorderLayout.NORTH);
        add(createLogPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        logArea.setEditable(false);
        log("System initialized. Any number of algorithms can be enabled concurrently.");

		// Auto-refresh the remaining tokens display every 100ms
		javax.swing.Timer autoRefreshTimer = new javax.swing.Timer(100, e -> updateRemainingTokens());
		autoRefreshTimer.start();
    }

    private void buildClusterInstance() {
        cluster = new GatewayCluster(
                chkTokenBucket.isSelected(), (int) tbCapacitySpinner.getValue(), ((Number) tbRefillSpinner.getValue()).doubleValue(),
                chkFixedWindow.isSelected(), ((Number) fwLengthSpinner.getValue()).longValue(), (int) fwLimitSpinner.getValue(),
                chkSlidingWindow.isSelected(), ((Number) swLengthSpinner.getValue()).longValue(), (int) swLimitSpinner.getValue(),
                chkLoadShedder.isSelected(), ((Number) lsPaymentsSpinner.getValue()).doubleValue(), (int) serverMaxRateSpinner.getValue()
        );
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Use 2-row grid for the Client Request Options area
        JPanel requestPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        requestPanel.setBorder(BorderFactory.createTitledBorder("Client Request Options"));

        JPanel rrRow1 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        rrRow1.add(new JLabel("Client ID:"));
        rrRow1.add(clientField);
        clientField.setColumns(6);
        rrRow1.add(new JLabel("Method:"));
        rrRow1.add(methodBox);
        rrRow1.add(new JLabel("Path:"));
        rrRow1.add(pathBox);
        rrRow1.add(new JLabel("Gateway Target:"));
        rrRow1.add(gatewayBox);
        rrRow1.add(sendButton);

        JPanel rrRow2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        rrRow2.add(new JLabel("Burst Count:"));
        rrRow2.add(burstSpinner);
        rrRow2.add(new JLabel("Burst Delay (ms):"));
        rrRow2.add(burstDelaySpinner);
		// rrRow2.add(new JLabel("Burst Delay (μs):"));
		// rrRow2.add(burstDelayMicrosSpinner);
        rrRow2.add(burstButton);

        requestPanel.add(rrRow1);
        requestPanel.add(rrRow2);

        // Grid layout container matching exact 5 rows of content
        JPanel configPanel = new JPanel(new GridLayout(5, 1, 0, 0));
        configPanel.setBorder(BorderFactory.createTitledBorder("Isolated Algorithm Rows Config Matrix"));

        java.awt.Dimension uniformRowSize = new java.awt.Dimension(1100, 34);
        javax.swing.border.Border bottomLine = BorderFactory.createMatteBorder(0, 0, 1, 0, java.awt.Color.LIGHT_GRAY);

        // Row 1: Token Bucket Configuration
        JPanel r1 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r1.setPreferredSize(uniformRowSize);
        r1.setBorder(bottomLine);
        r1.add(chkTokenBucket); r1.add(new JLabel("   Capacity:")); r1.add(tbCapacitySpinner); r1.add(new JLabel("   Refill/s:")); r1.add(tbRefillSpinner);
        configPanel.add(r1);

        // Row 2: Fixed Window Configuration
        JPanel r2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r2.setPreferredSize(uniformRowSize);
        r2.setBorder(bottomLine);
        r2.add(chkFixedWindow); r2.add(new JLabel("   Window (ms):")); r2.add(fwLengthSpinner); r2.add(new JLabel("   Limit:")); r2.add(fwLimitSpinner);
        configPanel.add(r2);

        // Row 3: Sliding Window Configuration
        JPanel r3 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r3.setPreferredSize(uniformRowSize);
        r3.setBorder(bottomLine);
        r3.add(chkSlidingWindow); r3.add(new JLabel("   Window (ms):")); r3.add(swLengthSpinner); r3.add(new JLabel("   Limit:")); r3.add(swLimitSpinner);
        configPanel.add(r3);

        // Row 4: Fleet Load Shedder Weight Distribution
        JPanel r4 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r4.setPreferredSize(uniformRowSize);
        r4.setBorder(bottomLine);
        r4.add(chkLoadShedder); r4.add(new JLabel("   /payments min %:")); r4.add(lsPaymentsSpinner);
        configPanel.add(r4);

        // Row 5: Internal Server System Max Capacity Threshold
        JPanel r5 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r5.setPreferredSize(uniformRowSize);
        r5.add(new JLabel("Internal Server Limit Max Rate (req/ms):")); r5.add(serverMaxRateSpinner);
        configPanel.add(r5);

        // Apply config button action area attached cleanly under the matrix
        JPanel actionPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 5));
        actionPanel.add(resetButton);

        JPanel matrixWrapper = new JPanel(new BorderLayout());
        matrixWrapper.add(configPanel, BorderLayout.NORTH);
        matrixWrapper.add(actionPanel, BorderLayout.SOUTH);

        panel.add(requestPanel, BorderLayout.NORTH);
        panel.add(matrixWrapper, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane createLogPanel() {
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Cluster Runtime Activity Console Log"));
        return scrollPane;
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        panel.add(tokensButton);
        panel.add(remainingLabel);
        panel.add(clearButton);
        return panel;
    }

    private void sendSingleRequest() {
        ApiRequest request = createRequest();
        ApiResponse response = routeRequest(request);
        printResponse(request, response);
        updateRemainingTokens();
    }

    private void sendBurstRequests() {
        int count = (int) burstSpinner.getValue();
        int delay = (int) burstDelaySpinner.getValue();

        String clientId = clientField.getText().trim().isEmpty() ? "anonymous-client" : clientField.getText().trim();
        String selectedPath = String.valueOf(pathBox.getSelectedItem());
        String selectedMethod = String.valueOf(methodBox.getSelectedItem());
        int nodeSelectionIndex = gatewayBox.getSelectedIndex();
        String[] finalPaths = {"/users", "/orders", "/payments", "/unknown"};
        String[] knownPaths = {"/users", "/orders", "/payments"};

        new SwingWorker<Void, String>() {
            // Track totals and successes per path
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
                        response = cluster.sendRoundRobin(request);
                    } else {
                        response = cluster.sendToNode(request, nodeSelectionIndex - 1);
                    }

                    boolean isSuccess = (response.getStatusCode() == 200);
                    if (isSuccess) totalSuccess++;

                    // Categorize and tally the request based on its path
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
                    log(logLine);
                }
                updateRemainingTokens();
            }

            @Override
            protected void done() {
                // Output the broken-down summary to the log panel once the burst finishes
                log("================ BURST SUMMARY ================");
                log(String.format("Overall successful: %d / %d", totalSuccess, totalSent));
                log(String.format("  - /payments : %d / %d handled", successPayments, totalPayments));
                log(String.format("  - /users    : %d / %d handled", successUsers, totalUsers));
                log(String.format("  - /orders   : %d / %d handled", successOrders, totalOrders));
                log(String.format("  - /unknown  : %d / %d handled", successUnknown, totalUnknown));
                log("===============================================");
                updateRemainingTokens();
            }
        }.execute();
    }

    private ApiRequest createRequest() {
        String clientId = clientField.getText().trim().isEmpty() ? "anonymous-client" : clientField.getText().trim();
        
        String path = String.valueOf(pathBox.getSelectedItem());
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
        if (selected == 0) return cluster.sendRoundRobin(request);
        return cluster.sendToNode(request, selected - 1);
    }

    private void printResponse(ApiRequest request, ApiResponse response) { log(formatResponse(request, response)); }

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

    private void updateRemainingTokens() {
        String clientId = clientField.getText().trim().isEmpty() ? "anonymous-client" : clientField.getText().trim();
        String selectedPath = String.valueOf(pathBox.getSelectedItem());
        
        // Resolve a deterministic path placeholder if path choice is randomized
        String inspectionPath = "/users";
        if (!selectedPath.contains("Randomize")) {
            inspectionPath = selectedPath;
        }
        
        // Use the detailed method instead of the lowest cache value
        remainingLabel.setText("Remaining Tokens -> " + cluster.getRemainingTokensDetailed(clientId, inspectionPath));
    }

    private void resetCluster() {
        double pPct = ((Number) lsPaymentsSpinner.getValue()).doubleValue();

        // if (chkLoadShedder.isSelected() && (uPct + oPct + pPct != 100.0)) {
        //     JOptionPane.showMessageDialog(this, "Fleet Load Shedder microservice request percentages must sum to exactly 100%.", "Config Validation Warning", JOptionPane.ERROR_MESSAGE);
        //     return;
        // }

        buildClusterInstance();
        log("Distributed Cluster settings synced. Database maps truncated.");
        updateRemainingTokens();
    }

    private int getCapacity() { return (int) tbCapacitySpinner.getValue(); }
    private double getRefillRate() { return ((Number) tbRefillSpinner.getValue()).doubleValue(); }
    private void log(String text) {
        java.awt.Color color = java.awt.Color.BLACK;
        
        // Target only unhandled/failed transactions
        if (text.contains("status=") && !text.contains("status=200")) {
            if (text.contains("status=429")) {
				// server overloaded
                color = new java.awt.Color(255, 0, 0); // Red prioritized
            } else if (text.contains("status=404")) {
				// no backend service
                color = new java.awt.Color(255, 179, 0); // Orange/Yellow
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
}