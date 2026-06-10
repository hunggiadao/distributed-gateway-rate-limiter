package com.ds.ratelimiter.gui;

import com.ds.ratelimiter.gateway.GatewayCluster;
import com.ds.ratelimiter.limiter.RateLimitConfig;
import com.ds.ratelimiter.model.ApiRequest;
import com.ds.ratelimiter.model.ApiResponse;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;

/**
 * The GatewayDemoFrame class represents the GUI for the distributed API gateway and rate limiter.
 * It allows users to configure rate-limiting rules, monitor client activity, and manage the gateway cluster.
 * The class also handles communication with clients via a TCP server.
 */
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

	/**
     * Constructs the GatewayDemoFrame GUI.
     * Initializes the gateway cluster, sets up the GUI layout, and starts the TCP server.
     */
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

        startTCPServer();

        javax.swing.Timer autoRefreshTimer = new javax.swing.Timer(100, e -> updateRemainingTokens());
        autoRefreshTimer.start();
    }

	private JPanel createTopPanel() {
        JPanel configPanel = new JPanel(new GridLayout(7, 1, 0, 0));
        configPanel.setBorder(BorderFactory.createTitledBorder("API Gateway Engine Config Matrix"));

        Dimension uniformRowSize = new Dimension(1100, 34);
        javax.swing.border.Border bottomLine = BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY);

        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        r1.setPreferredSize(uniformRowSize); r1.setBorder(bottomLine);
        r1.add(chkTokenBucket); r1.add(new JLabel("   Capacity:")); r1.add(tbCapacitySpinner); r1.add(new JLabel("   Refill/s:")); r1.add(tbRefillSpinner);
        configPanel.add(r1);

        JPanel r2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        r2.setPreferredSize(uniformRowSize); r2.setBorder(bottomLine);
        r2.add(chkFixedWindow); r2.add(new JLabel("   Window (ms):")); r2.add(fwLengthSpinner); r2.add(new JLabel("   Limit:")); r2.add(fwLimitSpinner);
        configPanel.add(r2);

        JPanel r3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        r3.setPreferredSize(uniformRowSize); r3.setBorder(bottomLine);
        r3.add(chkSlidingWindow); r3.add(new JLabel("   Window (ms):")); r3.add(swLengthSpinner); r3.add(new JLabel("   Limit:")); r3.add(swLimitSpinner);
        configPanel.add(r3);

        JPanel rClientAction = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rClientAction.setPreferredSize(uniformRowSize); rClientAction.setBorder(bottomLine);
        rClientAction.add(new JLabel("Target Tenant Client: ")); rClientAction.add(targetClientBox);
        rClientAction.add(new JLabel("   ")); rClientAction.add(applyTokenButton);
        configPanel.add(rClientAction);

        JPanel r4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        r4.setPreferredSize(uniformRowSize); r4.setBorder(bottomLine);
        r4.add(chkLoadShedder); r4.add(new JLabel("   /payments min %:")); r4.add(lsPaymentsSpinner);
        configPanel.add(r4);

        JPanel r5 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        r5.setPreferredSize(uniformRowSize); r5.setBorder(bottomLine);
        r5.add(new JLabel("Internal Server Limit Max Rate (req/ms):")); r5.add(serverMaxRateSpinner);
        configPanel.add(r5);

        JPanel rShedderAction = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
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
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(tokensButton);
        panel.add(remainingLabel);
        panel.add(clearButton);
        return panel;
    }

	/**
     * Starts the TCP server to handle client connections.
     * Each client connection is handled in a separate thread.
     */
    private void startTCPServer() {
        new Thread(() -> {
			int port = 12345;
            try (ServerSocket serverSocket = new ServerSocket(port)) {
				// CRITICAL: Set the reuse option BEFORE binding
				if (serverSocket.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
					// can only toggle SO_REUSEPORT on Linux
					serverSocket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
				}
				// on Windows, skip, cannot do that

				serverSocket.bind(new InetSocketAddress(port));

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleTCPClientConnection(clientSocket)).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

	/**
     * Handles a TCP client connection.
     * Processes commands sent by the client and responds accordingly.
     *
     * @param socket The client socket.
     */
    private void handleTCPClientConnection(Socket socket) {
        // CRITICAL FIX: Matching the raw Data Streams on the backend router
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {
            
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
                    
                    ApiRequest request = new ApiRequest(clientId, path, method);
                    ApiResponse response = cluster.sendRoundRobin(request);
                    
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
                    
                    ApiRequest request = new ApiRequest(clientId, path, method);
                    ApiResponse response = cluster.sendToNode(request, nodeIndex);
                    
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

	/**
     * Registers a client in the target client dropdown if it is not already registered.
     *
     * @param clientId The client ID to register.
     */
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

	/**
     * Retrieves the current GatewayCluster instance, initializing it if necessary.
     *
     * @return The GatewayCluster instance.
     */
    public GatewayCluster getCluster() {
        if (cluster == null) {
            resetCluster();
        }
        return cluster;
    }

	/**
     * Updates the remaining tokens label in the GUI by retrieving the token details from the cluster.
     */
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

	/**
     * Logs a message to the console log area with appropriate color coding for status codes.
     *
     * @param text The message to log.
     */
    public void log(String text) {
        Color color = Color.BLACK;
        if (text.contains("status=") && !text.contains("status=200")) {
			// not OK
            if (text.contains("status=429")) {
				// Too many requests
                color = new Color(255, 0, 0);
            } else if (text.contains("status=404")) {
				// No backend service
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
}