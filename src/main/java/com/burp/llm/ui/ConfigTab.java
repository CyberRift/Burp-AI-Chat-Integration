package com.burp.llm.ui;

import burp.api.montoya.MontoyaApi;
import com.burp.llm.config.ConfigSettings;
import com.burp.llm.config.ConfigSettings.CustomHeader;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

public class ConfigTab extends JPanel {
    private final ConfigSettings configSettings;
    private final MontoyaApi api;
    private final JTextField serverField;
    private final JTextField modelField;
    private final JTextField proxyHostField;
    private final JTextField proxyPortField;
    private final JCheckBox useProxyCheckbox;
    private final HeadersTableModel headersTableModel;
    private final JCheckBox multimodalCheckbox;
    private final JTextField connectTimeoutField;
    private final JTextField writeTimeoutField;
    private final JTextField readTimeoutField;
    private final JCheckBox useSystemPromptCheckbox;
    private final JTextArea systemPromptArea;
    private final JTextField chatApiEndpointField;
    private final DocumentChangeListener serverListener;
    private final DocumentChangeListener modelListener;

    private class DocumentChangeListener implements javax.swing.event.DocumentListener {
        private final Runnable action;

        public DocumentChangeListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }
    }

    private class HeadersTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Header Name", "Value"};

        @Override
        public int getRowCount() {
            return configSettings.getCustomHeaders().size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ConfigSettings.CustomHeader header = configSettings.getCustomHeaders().get(row);
            return switch (column) {
                case 0 -> header.getName();
                case 1 -> header.getValue();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            ConfigSettings.CustomHeader header = configSettings.getCustomHeaders().get(row);
            String strValue = (String) value;
            
            // If the user starts typing, clear the placeholder text
            if (column == 0 && header.getName().equals("Header Name") && !strValue.equals("Header Name")) {
                header.setName(strValue);
            } else if (column == 1 && header.getValue().equals("Header Value") && !strValue.equals("Header Value")) {
                header.setValue(strValue);
            } else if (!strValue.equals("Header Name") && !strValue.equals("Header Value")) {
                // Normal case - just set the value
                switch (column) {
                    case 0 -> header.setName(strValue);
                    case 1 -> header.setValue(strValue);
                }
            }
            
            // Notify the config settings that a change has occurred
            configSettings.notifyListeners();
            fireTableCellUpdated(row, column);
        }

        public void addHeader() {
            // Add a new header with placeholder text
            configSettings.addCustomHeader(new ConfigSettings.CustomHeader("Header Name", "Header Value"));
            int newRow = getRowCount() - 1;
            fireTableRowsInserted(newRow, newRow);
        }

        public void removeHeader(int row) {
            if (row >= 0 && row < configSettings.getCustomHeaders().size()) {
                ConfigSettings.CustomHeader header = configSettings.getCustomHeaders().get(row);
                configSettings.removeCustomHeader(header);
                fireTableRowsDeleted(row, row);
            }
        }
    }

    private class HeaderCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                if (value != null && (value.toString().equals("Header Name") || value.toString().equals("Header Value"))) {
                    setForeground(Color.GRAY);
                    setFont(getFont().deriveFont(Font.ITALIC));
                } else {
                    setForeground(Color.BLACK);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
            }
            return c;
        }
    }

    public ConfigTab(MontoyaApi api, ConfigSettings configSettings) {
        this.api = api;
        this.configSettings = configSettings;
        
        // Initialize all fields first
        this.serverField = new JTextField(configSettings.getOllamaServer(), 30);
        this.modelField = new JTextField(configSettings.getModel(), 20);
        this.proxyHostField = new JTextField(configSettings.getProxyHost(), 20);
        this.proxyPortField = new JTextField(String.valueOf(configSettings.getProxyPort()), 5);
        this.useProxyCheckbox = new JCheckBox("Use Proxy", configSettings.isUseProxy());
        this.headersTableModel = new HeadersTableModel();
        this.multimodalCheckbox = new JCheckBox("Multimodal Support", configSettings.isMultimodalModel());
        this.connectTimeoutField = new JTextField(String.valueOf(configSettings.getConnectTimeoutSeconds()), 5);
        this.writeTimeoutField = new JTextField(String.valueOf(configSettings.getWriteTimeoutSeconds()), 5);
        this.readTimeoutField = new JTextField(String.valueOf(configSettings.getReadTimeoutSeconds()), 5);
        this.useSystemPromptCheckbox = new JCheckBox("Use System Prompt", configSettings.isUseSystemPrompt());
        this.systemPromptArea = new JTextArea(configSettings.getSystemPrompt(), 5, 40);
        this.chatApiEndpointField = new JTextField(configSettings.getChatApiEndpoint(), 20);
        
        // Initialize document listeners
        this.serverListener = new DocumentChangeListener(() -> validateAndUpdateField(serverField, "Server URL cannot be empty"));
        this.modelListener = new DocumentChangeListener(() -> validateAndUpdateField(modelField, "Model name cannot be empty"));

        // Set layout and build UI
        setLayout(new BorderLayout());
        buildUI();
        
        // Add listeners after UI is built
        setupListeners();
    }

    private void buildUI() {
        // Create main panel with GridBagLayout
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.NORTHWEST;

        // Create titled panels for each section
        JPanel serverPanel = createServerPanel();
        JPanel systemPromptPanel = createSystemPromptPanel();
        JPanel timeoutPanel = createTimeoutPanel();
        JPanel proxyPanel = createProxyPanel();
        JPanel headersPanel = createHeadersPanel();

        // Add panels to main panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        mainPanel.add(serverPanel, gbc);

        gbc.gridy = 1;
        mainPanel.add(systemPromptPanel, gbc);

        gbc.gridy = 2;
        mainPanel.add(timeoutPanel, gbc);

        gbc.gridy = 3;
        mainPanel.add(proxyPanel, gbc);

        gbc.gridy = 4;
        gbc.weighty = 1.0;  // Give extra vertical space to headers panel
        mainPanel.add(headersPanel, gbc);

        // Add save button at the bottom
        JButton saveAllButton = new JButton("Save All Settings");
        saveAllButton.addActionListener(e -> saveAllSettings());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(saveAllButton);
        
        gbc.gridy = 5;
        gbc.weighty = 0.0;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(buttonPanel, gbc);

        // Add scroll pane
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createServerPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Server Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Server URL
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Ollama Server URL:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(serverField, gbc);

        // Chat API Endpoint
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Chat API Endpoint:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(chatApiEndpointField, gbc);

        // Model Name
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Model Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(modelField, gbc);

        // Multimodal Support
        gbc.gridx = 1;
        gbc.gridy = 3;
        panel.add(multimodalCheckbox, gbc);

        return panel;
    }

    private JPanel createSystemPromptPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "System Prompt Configuration"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        panel.add(useSystemPromptCheckbox, gbc);

        systemPromptArea.setLineWrap(true);
        systemPromptArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(systemPromptArea);
        scrollPane.setPreferredSize(new Dimension(400, 100));

        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, gbc);

        return panel;
    }

    private JPanel createTimeoutPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Timeout Settings"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Connect timeout
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Connect:"), gbc);

        gbc.gridx = 1;
        panel.add(connectTimeoutField, gbc);

        gbc.gridx = 2;
        panel.add(new JLabel("seconds"), gbc);

        // Write timeout
        gbc.gridx = 3;
        panel.add(new JLabel("Write:"), gbc);

        gbc.gridx = 4;
        panel.add(writeTimeoutField, gbc);

        gbc.gridx = 5;
        panel.add(new JLabel("seconds"), gbc);

        // Read timeout
        gbc.gridx = 6;
        panel.add(new JLabel("Read:"), gbc);

        gbc.gridx = 7;
        panel.add(readTimeoutField, gbc);

        gbc.gridx = 8;
        gbc.weightx = 1.0;
        panel.add(new JLabel("seconds"), gbc);

        return panel;
    }

    private JPanel createProxyPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Proxy Settings"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(useProxyCheckbox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Host:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(proxyHostField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Port:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.2;
        panel.add(proxyPortField, gbc);

        return panel;
    }

    private JPanel createHeadersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Custom Headers"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Headers table
        JTable headersTable = new JTable(headersTableModel);
        HeaderCellRenderer headerRenderer = new HeaderCellRenderer();
        headersTable.getColumnModel().getColumn(0).setCellRenderer(headerRenderer);
        headersTable.getColumnModel().getColumn(1).setCellRenderer(headerRenderer);
        
        headersTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        headersTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        headersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        headersTable.setRowHeight(25);

        JScrollPane tableScroll = new JScrollPane(headersTable);
        tableScroll.setPreferredSize(new Dimension(600, 200));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel.add(tableScroll, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addHeaderButton = new JButton("Add Header");
        JButton removeHeaderButton = new JButton("Remove Selected");
        
        addHeaderButton.addActionListener(e -> {
            headersTableModel.addHeader();
            int newRow = headersTable.getRowCount() - 1;
            if (newRow >= 0) {
                headersTable.setRowSelectionInterval(newRow, newRow);
                headersTable.scrollRectToVisible(headersTable.getCellRect(newRow, 0, true));
            }
        });

        removeHeaderButton.addActionListener(e -> {
            int selectedRow = headersTable.getSelectedRow();
            if (selectedRow != -1) {
                headersTableModel.removeHeader(selectedRow);
            }
        });

        buttonPanel.add(addHeaderButton);
        buttonPanel.add(removeHeaderButton);

        gbc.gridy = 1;
        gbc.weighty = 0.0;
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private void setupListeners() {
        // Add document listeners
        serverField.getDocument().addDocumentListener(serverListener);
        modelField.getDocument().addDocumentListener(modelListener);
        
        // Add chat API endpoint listener
        chatApiEndpointField.getDocument().addDocumentListener(new DocumentChangeListener(() -> {
            String endpoint = chatApiEndpointField.getText().trim();
            if (endpoint.isEmpty()) {
                chatApiEndpointField.setBackground(new Color(255, 200, 200));
                chatApiEndpointField.setToolTipText("Chat API endpoint cannot be empty");
            } else {
                chatApiEndpointField.setBackground(Color.WHITE);
                chatApiEndpointField.setToolTipText(null);
                configSettings.setChatApiEndpoint(endpoint);
            }
        }));
        
        // Add timeout field listeners
        connectTimeoutField.getDocument().addDocumentListener(new DocumentChangeListener(() -> {
            try {
                int timeout = Integer.parseInt(connectTimeoutField.getText().trim());
                if (timeout > 0) {
                    configSettings.setConnectTimeoutSeconds(timeout);
                    connectTimeoutField.setBackground(Color.WHITE);
                } else {
                    connectTimeoutField.setBackground(new Color(255, 200, 200));
                }
            } catch (NumberFormatException ex) {
                connectTimeoutField.setBackground(new Color(255, 200, 200));
            }
        }));

        writeTimeoutField.getDocument().addDocumentListener(new DocumentChangeListener(() -> {
            try {
                int timeout = Integer.parseInt(writeTimeoutField.getText().trim());
                if (timeout > 0) {
                    configSettings.setWriteTimeoutSeconds(timeout);
                    writeTimeoutField.setBackground(Color.WHITE);
                } else {
                    writeTimeoutField.setBackground(new Color(255, 200, 200));
                }
            } catch (NumberFormatException ex) {
                writeTimeoutField.setBackground(new Color(255, 200, 200));
            }
        }));

        readTimeoutField.getDocument().addDocumentListener(new DocumentChangeListener(() -> {
            try {
                int timeout = Integer.parseInt(readTimeoutField.getText().trim());
                if (timeout > 0) {
                    configSettings.setReadTimeoutSeconds(timeout);
                    readTimeoutField.setBackground(Color.WHITE);
                } else {
                    readTimeoutField.setBackground(new Color(255, 200, 200));
                }
            } catch (NumberFormatException ex) {
                readTimeoutField.setBackground(new Color(255, 200, 200));
            }
        }));

        // Add system prompt listeners
        useSystemPromptCheckbox.addActionListener(e -> {
            boolean enabled = useSystemPromptCheckbox.isSelected();
            systemPromptArea.setEnabled(enabled);
            configSettings.setUseSystemPrompt(enabled);
        });

        systemPromptArea.getDocument().addDocumentListener(new DocumentChangeListener(() -> {
            configSettings.setSystemPrompt(systemPromptArea.getText());
        }));

        // Add action listeners for proxy fields
        useProxyCheckbox.addActionListener(e -> {
            boolean enabled = useProxyCheckbox.isSelected();
            proxyHostField.setEnabled(enabled);
            proxyPortField.setEnabled(enabled);
        });

        // Add validation listeners for proxy fields
        proxyHostField.getDocument().addDocumentListener(new DocumentChangeListener(() -> {
            if (useProxyCheckbox.isSelected() && proxyHostField.getText().trim().isEmpty()) {
                proxyHostField.setBackground(new Color(255, 200, 200));
            } else {
                proxyHostField.setBackground(Color.WHITE);
            }
        }));

        proxyPortField.getDocument().addDocumentListener(new DocumentChangeListener(() -> {
            try {
                int port = Integer.parseInt(proxyPortField.getText().trim());
                if (port > 0 && port <= 65535) {
                    proxyPortField.setBackground(Color.WHITE);
                } else {
                    proxyPortField.setBackground(new Color(255, 200, 200));
                }
            } catch (NumberFormatException ex) {
                proxyPortField.setBackground(new Color(255, 200, 200));
            }
        }));
        
        // Add action listener for multimodal checkbox
        multimodalCheckbox.addActionListener(e -> 
            configSettings.setMultimodalModel(multimodalCheckbox.isSelected())
        );
    }

    private boolean validateAllFields() {
        boolean isValid = true;
        
        // Validate server URL
        if (serverField.getText().trim().isEmpty()) {
            serverField.setBackground(new Color(255, 200, 200));
            api.logging().logToError("Server URL cannot be empty");
            isValid = false;
        } else {
            serverField.setBackground(Color.WHITE);
        }
        
        // Validate model name
        if (modelField.getText().trim().isEmpty()) {
            modelField.setBackground(new Color(255, 200, 200));
            api.logging().logToError("Model name cannot be empty");
            isValid = false;
        } else {
            modelField.setBackground(Color.WHITE);
        }

        // Validate chat API endpoint
        if (chatApiEndpointField.getText().trim().isEmpty()) {
            chatApiEndpointField.setBackground(new Color(255, 200, 200));
            api.logging().logToError("Chat API endpoint cannot be empty");
            isValid = false;
        } else {
            chatApiEndpointField.setBackground(Color.WHITE);
        }

        // Validate timeout fields
        try {
            int connectTimeout = Integer.parseInt(connectTimeoutField.getText().trim());
            if (connectTimeout <= 0) {
                connectTimeoutField.setBackground(new Color(255, 200, 200));
                api.logging().logToError("Connect timeout must be greater than 0");
                isValid = false;
            }
        } catch (NumberFormatException ex) {
            connectTimeoutField.setBackground(new Color(255, 200, 200));
            api.logging().logToError("Invalid connect timeout value");
            isValid = false;
        }

        try {
            int writeTimeout = Integer.parseInt(writeTimeoutField.getText().trim());
            if (writeTimeout <= 0) {
                writeTimeoutField.setBackground(new Color(255, 200, 200));
                api.logging().logToError("Write timeout must be greater than 0");
                isValid = false;
            }
        } catch (NumberFormatException ex) {
            writeTimeoutField.setBackground(new Color(255, 200, 200));
            api.logging().logToError("Invalid write timeout value");
            isValid = false;
        }

        try {
            int readTimeout = Integer.parseInt(readTimeoutField.getText().trim());
            if (readTimeout <= 0) {
                readTimeoutField.setBackground(new Color(255, 200, 200));
                api.logging().logToError("Read timeout must be greater than 0");
                isValid = false;
            }
        } catch (NumberFormatException ex) {
            readTimeoutField.setBackground(new Color(255, 200, 200));
            api.logging().logToError("Invalid read timeout value");
            isValid = false;
        }
        
        // Validate proxy settings if enabled
        if (useProxyCheckbox.isSelected()) {
            if (proxyHostField.getText().trim().isEmpty()) {
                proxyHostField.setBackground(new Color(255, 200, 200));
                api.logging().logToError("Proxy host cannot be empty when proxy is enabled");
                isValid = false;
            } else {
                proxyHostField.setBackground(Color.WHITE);
            }
            
            try {
                int port = Integer.parseInt(proxyPortField.getText().trim());
                if (port <= 0 || port > 65535) {
                    proxyPortField.setBackground(new Color(255, 200, 200));
                    api.logging().logToError("Invalid proxy port number (must be between 1-65535)");
                    isValid = false;
                } else {
                    proxyPortField.setBackground(Color.WHITE);
                }
            } catch (NumberFormatException ex) {
                proxyPortField.setBackground(new Color(255, 200, 200));
                api.logging().logToError("Invalid proxy port number");
                isValid = false;
            }
        }
        
        return isValid;
    }

    private void saveAllSettings() {
        if (!validateAllFields()) {
            return;
        }

        try {
            // Get values from UI components
            String server = serverField.getText().trim();
            String model = modelField.getText().trim();
            String chatEndpoint = chatApiEndpointField.getText().trim();
            boolean useProxy = useProxyCheckbox.isSelected();
            String proxyHost = proxyHostField.getText().trim();
            int proxyPort = 8080; // Default value
            
            try {
                proxyPort = Integer.parseInt(proxyPortField.getText().trim());
                if (proxyPort <= 0 || proxyPort > 65535) {
                    api.logging().logToError("Invalid proxy port number (must be between 1-65535)");
                    return;
                }
            } catch (NumberFormatException ex) {
                if (useProxy) {
                    api.logging().logToError("Invalid proxy port number");
                    return;
                }
            }

            // Save all settings at once
            configSettings.updateSettings(server, model, useProxy, proxyHost, proxyPort);
            
            // Save chat endpoint
            configSettings.setChatApiEndpoint(chatEndpoint);
            
            // Save system prompt settings
            configSettings.setUseSystemPrompt(useSystemPromptCheckbox.isSelected());
            configSettings.setSystemPrompt(systemPromptArea.getText());
            
            // Update UI state
            proxyHostField.setEnabled(useProxy);
            proxyPortField.setEnabled(useProxy);
            
            api.logging().logToOutput("All settings saved successfully");
            
        } catch (Exception ex) {
            api.logging().logToError("Error saving settings: " + ex.getMessage());
        }
    }

    private void validateAndUpdateField(JTextField field, String errorMessage) {
        if (field.getText().trim().isEmpty()) {
            field.setBackground(new Color(255, 200, 200));
            field.setToolTipText(errorMessage);
        } else {
            field.setBackground(Color.WHITE);
            field.setToolTipText(null);
        }
    }

    // Update the config change listener to properly handle UI updates
    private void updateUIFromConfig() {
        SwingUtilities.invokeLater(() -> {
            // Temporarily remove listeners
            serverField.getDocument().removeDocumentListener(serverListener);
            modelField.getDocument().removeDocumentListener(modelListener);
            
            try {
                serverField.setText(configSettings.getOllamaServer());
                modelField.setText(configSettings.getModel());
                chatApiEndpointField.setText(configSettings.getChatApiEndpoint());
                useProxyCheckbox.setSelected(configSettings.isUseProxy());
                proxyHostField.setText(configSettings.getProxyHost());
                proxyPortField.setText(String.valueOf(configSettings.getProxyPort()));
                multimodalCheckbox.setSelected(configSettings.isMultimodalModel());
                connectTimeoutField.setText(String.valueOf(configSettings.getConnectTimeoutSeconds()));
                writeTimeoutField.setText(String.valueOf(configSettings.getWriteTimeoutSeconds()));
                readTimeoutField.setText(String.valueOf(configSettings.getReadTimeoutSeconds()));
                useSystemPromptCheckbox.setSelected(configSettings.isUseSystemPrompt());
                systemPromptArea.setText(configSettings.getSystemPrompt());
                headersTableModel.fireTableDataChanged();
            } finally {
                // Restore the listeners
                serverField.getDocument().addDocumentListener(serverListener);
                modelField.getDocument().addDocumentListener(modelListener);
            }
        });
    }
} 