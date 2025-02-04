package com.burp.llm.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import com.burp.llm.api.OllamaClient;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.swing.text.DefaultCaret;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;

public class LLMRequestEditorTab implements ExtensionProvidedHttpRequestEditor {
    private final MontoyaApi api;
    private final OllamaClient ollamaClient;
    private final JPanel component;
    private final JTextArea chatArea;
    private final JTextArea inputArea;
    private final JCheckBox includeRequestResponseCheckbox;
    private final JButton attachImageButton;
    private final JLabel imageStatusLabel;
    private List<String> attachedImages;
    private volatile boolean isReceivingResponse;
    private HttpRequestResponse currentRequestResponse;

    public LLMRequestEditorTab(MontoyaApi api, OllamaClient ollamaClient, EditorCreationContext creationContext) {
        this.api = api;
        this.ollamaClient = ollamaClient;
        this.attachedImages = new ArrayList<>();
        this.isReceivingResponse = false;
        
        // Initialize main component
        component = new JPanel(new BorderLayout());
        
        // Chat Area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        DefaultCaret caret = (DefaultCaret) chatArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        
        // Input Panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        
        // Controls Panel
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        includeRequestResponseCheckbox = new JCheckBox("Include Request/Response", true);
        attachImageButton = new JButton("Attach Image");
        imageStatusLabel = new JLabel();
        
        controlsPanel.add(includeRequestResponseCheckbox);
        controlsPanel.add(attachImageButton);
        controlsPanel.add(imageStatusLabel);
        
        // Input Area
        inputArea = new JTextArea(3, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        
        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton sendButton = new JButton("Send");
        JButton clearButton = new JButton("Clear Chat");
        JButton clearImagesButton = new JButton("Clear Images");
        
        buttonPanel.add(clearImagesButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(sendButton);
        
        // Assemble input panel
        inputPanel.add(controlsPanel, BorderLayout.NORTH);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Add components to main panel
        component.add(chatScrollPane, BorderLayout.CENTER);
        component.add(inputPanel, BorderLayout.SOUTH);
        
        // Initialize UI state
        updateImageControls();
        
        // Action Listeners
        sendButton.addActionListener(e -> sendMessage());
        clearButton.addActionListener(e -> clearChat());
        clearImagesButton.addActionListener(e -> {
            attachedImages.clear();
            updateImageControls();
        });
        attachImageButton.addActionListener(e -> attachImage());
        
        // Enter key to send message
        inputArea.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && evt.isControlDown()) {
                    sendMessage();
                    evt.consume();
                }
            }
        });
    }

    private void updateImageControls() {
        boolean isMultimodal = ollamaClient.isMultimodalModel();
        attachImageButton.setEnabled(isMultimodal && !isReceivingResponse);
        
        if (isMultimodal) {
            imageStatusLabel.setText(String.format("📎 %d image(s) attached", attachedImages.size()));
            imageStatusLabel.setForeground(Color.BLACK);
        } else {
            imageStatusLabel.setText("⚠️ Current model doesn't support images");
            imageStatusLabel.setForeground(new Color(200, 0, 0));
        }
    }

    private void attachImage() {
        if (isReceivingResponse) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Image files", "jpg", "jpeg", "png", "gif", "bmp"));
        
        if (fileChooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                byte[] imageBytes = Files.readAllBytes(file.toPath());
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                attachedImages.add(base64Image);
                updateImageControls();
            } catch (IOException ex) {
                api.logging().logToError("Error reading image file: " + ex.getMessage());
                JOptionPane.showMessageDialog(component,
                    "Error reading image file: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void sendMessage() {
        if (isReceivingResponse) {
            return;
        }

        String userMessage = inputArea.getText().trim();
        if (userMessage.isEmpty()) {
            return;
        }

        // Add user message to chat
        appendToChatArea("You", userMessage + (attachedImages.isEmpty() ? "" : " [with " + attachedImages.size() + " image(s)]"));

        // Clear input area and disable controls
        inputArea.setText("");
        isReceivingResponse = true;
        updateControls(false);

        // Start response on a new line
        appendToChatArea("Assistant", "");

        // Create a copy of necessary data for the background thread
        final String finalUserMessage = userMessage;
        final List<String> finalAttachedImages = new ArrayList<>(attachedImages);
        final HttpRequestResponse requestResponse = currentRequestResponse;
        final boolean includeRequestResponse = includeRequestResponseCheckbox.isSelected();

        // Run chat operation in background thread
        new Thread(() -> {
            try {
                Consumer<String> chunkHandler = chunk -> SwingUtilities.invokeLater(() -> {
                    chatArea.append(chunk);
                });

                if (includeRequestResponse && requestResponse != null) {
                    String request = requestResponse.request().toString();
                    String response = requestResponse.response() != null ? 
                        requestResponse.response().toString() : "";
                    
                    ollamaClient.analyzeRequest(
                        request,
                        response,
                        finalUserMessage,
                        finalAttachedImages.isEmpty() ? null : finalAttachedImages,
                        chunkHandler
                    );
                } else {
                    ollamaClient.chat(
                        finalUserMessage,
                        ollamaClient.getConfigSettings().isUseSystemPrompt() ? 
                            ollamaClient.getConfigSettings().getSystemPrompt() : "",
                        finalAttachedImages,
                        chunkHandler
                    );
                }

                // Add final newlines
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("\n\n");
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    appendToChatArea("System", "Error: " + ex.getMessage());
                    api.logging().logToError("Error in chat: " + ex.getMessage());
                });
            } finally {
                // Re-enable controls
                SwingUtilities.invokeLater(() -> {
                    isReceivingResponse = false;
                    updateControls(true);
                    
                    // Clear images after sending
                    attachedImages.clear();
                    updateImageControls();
                });
            }
        }).start();
    }

    private void updateControls(boolean enabled) {
        inputArea.setEnabled(enabled);
        attachImageButton.setEnabled(enabled && ollamaClient.isMultimodalModel());
        includeRequestResponseCheckbox.setEnabled(enabled);
    }

    private void clearChat() {
        chatArea.setText("");
        attachedImages.clear();
        updateImageControls();
        ollamaClient.clearHistory();  // Clear the message history
    }

    private void appendToChatArea(String sender, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        chatArea.append(String.format("[%s] %s: %s", timestamp, sender, message));
        if (!message.isEmpty()) {
            chatArea.append("\n\n");
        }
    }

    @Override
    public JComponent uiComponent() {
        return component;
    }

    @Override
    public String caption() {
        return "AI Chat";
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        return true;  // Enable for all requests
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.currentRequestResponse = requestResponse;
    }

    @Override
    public boolean isModified() {
        return false;  // This editor doesn't modify the request
    }

    @Override
    public HttpRequest getRequest() {
        return currentRequestResponse != null ? currentRequestResponse.request() : null;
    }

    @Override
    public Selection selectedData() {
        return null;  // No text selection functionality needed
    }
} 