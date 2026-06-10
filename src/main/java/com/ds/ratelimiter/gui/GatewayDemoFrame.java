package com.ds.ratelimiter.gui;

import com.ds.ratelimiter.gateway.GatewayCluster;
import com.ds.ratelimiter.limiter.RateLimitConfig;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.net.*;

public class GatewayDemoFrame extends JFrame {
    private GatewayCluster cluster;
    // private ClientDemoFrame clientFrame; // Only used when running single process mode

    private final JCheckBox chkTokenBucket = new JCheckBox("Token Bucket", false);
    private final JSpinner tbCapacitySpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000000, 1));
    private final JSpinner tbRefillSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 1000000.0, 1.0));

    private final JCheckBox chkFixedWindow = new JCheckBox("Fixed Window", false);
    private final JSpinner fwLengthSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 100000, 100));
    private final JSpinner fwLimitSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000000, 10));

    private final JCheckBox chkSlidingWindow = new JCheckBox("Sliding Window", false);
    private final JSpinner swLengthSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 100000, 100));
    private final JSpinner swLimitSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000000, 10));

    private final JCheckBox chkLoadShedder = new JCheckBox("Fleet Load Shedder", false);
    private final JSpinner lsPaymentsSpinner = new JSpinner(new SpinnerNumberModel(25.0, 0.0, 100.0, 5.0));
    private final JSpinner serverMaxRateSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 1000, 1));

    private final JLabel remainingLabel = new JLabel("Remaining Tokens -> -");
    private final JTextPane logArea = new JTextPane();

    private final JButton applyTokenButton = new JButton("Apply Client Token Config / Reset DB");
    private final JButton applyLoadShedderButton = new JButton("Apply Universal Fleet Load Shedder Config");
    private final JButton clearButton = new JButton("Clear Console");
    private final JButton tokensButton = new JButton("Inspect DB State");
    private final JComboBox<String> targetClientBox = new JComboBox<>(new String[]{"All Clients"});

    // Cached values to safely serve decoupled multi-process statuses
    private String lastRemoteClientId = "client-1";
    private String lastRemotePath = "/users";

    public GatewayDemoFrame() {
        setTitle("Distributed API Gateway Engine & Rate Limiter Dashboard");
        setSize(900, 680);
        setLocation(770, 320);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        applyTokenButton.addActionListener(e -> resetCluster());
        applyLoadShedderButton.addActionListener(e -> resetCluster());
        clearButton.addActionListener(e -> logArea.setText(""));
        tokensButton.addActionListener(e -> updateRemainingTokens());

        resetCluster();

        add(createTopPanel(), BorderLayout.NORTH);
        add(createLogPanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);

        logArea.setEditable(false);
        log("Gateway Core Cluster active. Waiting for client program traffic incoming streams...");

        startTcpServer();

        javax.swing.Timer autoRefreshTimer = new javax.swing.Timer(100, e -> updateRemainingTokens());
        autoRefreshTimer.start();
    }

    private void startTcpServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(12345)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleTcpClientConnection(clientSocket)).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleTcpClientConnection(Socket socket) {
        // CRITICAL FIX: Matching the raw Data Streams on the backend router
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(socket.getOutputStream());
             java.io.DataInputStream in = new java.io.DataInputStream(socket.getInputStream())) {
            
            while (true) {
                String cmd = in.readUTF();
                if ("REGISTER".equals(cmd)) {
                    String clientId = in.readUTF();
                    SwingUtilities.invokeLater(() -> registerClient(clientId));
                } else if ("LOG".equals(cmd)) {
                    String text = in.readUTF();
                    SwingUtilities.invokeLater(() -> log(text));
                } else if ("UPDATE_STATUS".equals(cmd)) {
                    String clientId = in.readUTF();
                    String path = in.readUTF();
                    lastRemoteClientId = clientId;
                    lastRemotePath = path;
                } else if ("SEND_ROUND_ROBIN".equals(cmd)) {
                    String clientId = in.readUTF();
                    String path = in.readUTF();
                    String method = in.readUTF();
                    
                    com.ds.ratelimiter.model.ApiRequest request = new com.ds.ratelimiter.model.ApiRequest(clientId, path, method);
                    com.ds.ratelimiter.model.ApiResponse response = cluster.sendRoundRobin(request);
                    
                    out.writeInt(response.getStatusCode());
                    out.writeUTF(response.getMessage());
                    out.writeUTF(response.getGatewayName());
                    out.writeInt(response.getRemainingTokens());
                    out.flush();
                } else if ("SEND_TO_NODE".equals(cmd)) {
                    String clientId = in.readUTF();
                    String path = in.readUTF();
                    String method = in.readUTF();
                    int nodeIndex = in.readInt();
                    
                    com.ds.ratelimiter.model.ApiRequest request = new com.ds.ratelimiter.model.ApiRequest(clientId, path, method);
                    com.ds.ratelimiter.model.ApiResponse response = cluster.sendToNode(request, nodeIndex);
                    
                    out.writeInt(response.getStatusCode());
                    out.writeUTF(response.getMessage());
                    out.writeUTF(response.getGatewayName());
                    out.writeInt(response.getRemainingTokens());
                    out.flush();
                } else if ("GET_REMAINING_TOKENS".equals(cmd)) {
                    String clientId = in.readUTF();
                    String path = in.readUTF();
                    String details = cluster != null ? cluster.getRemainingTokensDetailed(clientId, path) : "Cluster offline";
                    out.writeUTF(details);
                    out.flush();
                } else if ("UPDATE_REMAINING_TOKENS_TRIGGER".equals(cmd)) {
                    SwingUtilities.invokeLater(() -> updateRemainingTokens());
                }
            }
        } catch (Exception e) {
            // Socket disconnected properly
        }
    }

    public void registerClient(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }
        for (int i = 0; i < targetClientBox.getItemCount(); i++) {
            if (targetClientBox.getItemAt(i).equals(clientId)) {
                return;
            }
        }
        targetClientBox.addItem(clientId);
    }

    // public void setClientFrame(ClientDemoFrame clientFrame) {
    //     this.clientFrame = clientFrame;
    // }

    public GatewayCluster getCluster() {
        if (cluster == null) {
            resetCluster();
        }
        return cluster;
    }

    private JPanel createTopPanel() {
        JPanel configPanel = new JPanel(new GridLayout(7, 1, 0, 0));
        configPanel.setBorder(BorderFactory.createTitledBorder("API Gateway Engine Config Matrix"));

        java.awt.Dimension uniformRowSize = new java.awt.Dimension(1100, 34);
        javax.swing.border.Border bottomLine = BorderFactory.createMatteBorder(0, 0, 1, 0, java.awt.Color.LIGHT_GRAY);

        JPanel r1 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r1.setPreferredSize(uniformRowSize); r1.setBorder(bottomLine);
        r1.add(chkTokenBucket); r1.add(new JLabel("   Capacity:")); r1.add(tbCapacitySpinner); r1.add(new JLabel("   Refill/s:")); r1.add(tbRefillSpinner);
        configPanel.add(r1);

        JPanel r2 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r2.setPreferredSize(uniformRowSize); r2.setBorder(bottomLine);
        r2.add(chkFixedWindow); r2.add(new JLabel("   Window (ms):")); r2.add(fwLengthSpinner); r2.add(new JLabel("   Limit:")); r2.add(fwLimitSpinner);
        configPanel.add(r2);

        JPanel r3 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r3.setPreferredSize(uniformRowSize); r3.setBorder(bottomLine);
        r3.add(chkSlidingWindow); r3.add(new JLabel("   Window (ms):")); r3.add(swLengthSpinner); r3.add(new JLabel("   Limit:")); r3.add(swLimitSpinner);
        configPanel.add(r3);

        JPanel rClientAction = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        rClientAction.setPreferredSize(uniformRowSize); rClientAction.setBorder(bottomLine);
        rClientAction.add(new JLabel("Target Tenant Client: ")); rClientAction.add(targetClientBox);
        rClientAction.add(new JLabel("   ")); rClientAction.add(applyTokenButton);
        configPanel.add(rClientAction);

        JPanel r4 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r4.setPreferredSize(uniformRowSize); r4.setBorder(bottomLine);
        r4.add(chkLoadShedder); r4.add(new JLabel("   /payments min %:")); r4.add(lsPaymentsSpinner);
        configPanel.add(r4);

        JPanel r5 = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        r5.setPreferredSize(uniformRowSize); r5.setBorder(bottomLine);
        r5.add(new JLabel("Internal Server Limit Max Rate (req/ms):")); r5.add(serverMaxRateSpinner);
        configPanel.add(r5);

        JPanel rShedderAction = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        rShedderAction.setPreferredSize(uniformRowSize);
        rShedderAction.add(applyLoadShedderButton);
        configPanel.add(rShedderAction);

        JPanel matrixWrapper = new JPanel(new BorderLayout());
        matrixWrapper.add(configPanel, BorderLayout.CENTER);

        return matrixWrapper;
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

    public void updateRemainingTokens() {
        // Safe decoupled UI status tracking 
        String clientId = lastRemoteClientId != null ? lastRemoteClientId : "client-1";
        String selectedPath = lastRemotePath != null ? lastRemotePath : "/users";
        
        String inspectionPath = "/users";
        if (selectedPath != null && !selectedPath.contains("Randomize")) {
            inspectionPath = selectedPath;
        }
        
        if (cluster != null) {
            remainingLabel.setText("Remaining Tokens -> " + cluster.getRemainingTokensDetailed(clientId, inspectionPath));
        }
    }

	// updates rules and state
    private void resetCluster() {
        RateLimitConfig newConfig = new RateLimitConfig(
                chkTokenBucket.isSelected(), 
                ((Number) tbCapacitySpinner.getValue()).intValue(), 
                ((Number) tbRefillSpinner.getValue()).doubleValue(),
                
                chkFixedWindow.isSelected(), 
                ((Number) fwLengthSpinner.getValue()).longValue(), 
                ((Number) fwLimitSpinner.getValue()).intValue(),
                
                chkSlidingWindow.isSelected(), 
                ((Number) swLengthSpinner.getValue()).longValue(), 
                ((Number) swLimitSpinner.getValue()).intValue(),
                
                chkLoadShedder.isSelected(), 
                ((Number) lsPaymentsSpinner.getValue()).doubleValue(), 
                ((Number) serverMaxRateSpinner.getValue()).intValue()
        );

        if (cluster == null) {
            cluster = new GatewayCluster(newConfig);
            log("Distributed Cluster initialized.");
        } else {
            String target = (String) targetClientBox.getSelectedItem();
            cluster.updateConfig(target, newConfig);
            cluster.resetLimiter(); 
            log("Rules updated for: " + target + ". Database wiped.");
        }

        updateRemainingTokens();
    }

    public void log(String text) {
        java.awt.Color color = java.awt.Color.BLACK;
        if (text.contains("status=") && !text.contains("status=200")) {
            if (text.contains("status=429")) {
                color = new java.awt.Color(255, 0, 0);
            } else if (text.contains("status=404")) {
                color = new java.awt.Color(255, 179, 0);
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