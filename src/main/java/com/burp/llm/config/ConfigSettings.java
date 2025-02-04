package com.burp.llm.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConfigSettings {
    private String ollamaServer = "http://localhost:11434";
    private String model = "deepseek-r1:1.5b";
    private final List<CustomHeader> customHeaders;
    private final List<ConfigChangeListener> listeners;
    private String proxyHost = "";
    private int proxyPort = 8080;
    private boolean useProxy = false;
    private boolean isMultimodalModel = false;
    private int connectTimeoutSeconds = 30;
    private int writeTimeoutSeconds = 30;
    private int readTimeoutSeconds = 60;
    private boolean useSystemPrompt = false;
    private String systemPrompt = "";
    private String chatApiEndpoint = "/api/chat";

    public ConfigSettings() {
        this.customHeaders = new CopyOnWriteArrayList<>();
        this.listeners = new ArrayList<>();
    }

    public String getOllamaServer() {
        return ollamaServer;
    }

    public void setOllamaServer(String ollamaServer) {
        this.ollamaServer = ollamaServer;
        notifyListeners();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
        notifyListeners();
    }

    public List<CustomHeader> getCustomHeaders() {
        return new ArrayList<>(customHeaders);
    }

    public void addCustomHeader(CustomHeader header) {
        customHeaders.add(header);
        notifyListeners();
    }

    public void removeCustomHeader(CustomHeader header) {
        customHeaders.remove(header);
        notifyListeners();
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost != null ? proxyHost.trim() : "";
        notifyListeners();
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        if (proxyPort > 0 && proxyPort <= 65535) {
            this.proxyPort = proxyPort;
            notifyListeners();
        }
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
        notifyListeners();
    }

    public void addChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    public void notifyListeners() {
        // Create a copy of listeners to avoid concurrent modification
        List<ConfigChangeListener> listenersCopy = new ArrayList<>(listeners);
        for (ConfigChangeListener listener : listenersCopy) {
            try {
                listener.onConfigChanged();
            } catch (Exception e) {
                // Log any errors but continue notifying other listeners
                System.err.println("Error notifying config listener: " + e.getMessage());
            }
        }
    }

    // Add method to update settings without triggering listeners
    public void updateSettings(String server, String model, boolean useProxy, 
                             String proxyHost, int proxyPort) {
        this.ollamaServer = server;
        this.model = model;
        this.useProxy = useProxy;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        // Only notify once after all updates are complete
        notifyListeners();
    }

    public static class CustomHeader {
        private String name;
        private String value;

        public CustomHeader(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public interface ConfigChangeListener {
        void onConfigChanged();
    }

    public boolean isMultimodalModel() {
        return isMultimodalModel;
    }

    public void setMultimodalModel(boolean multimodalModel) {
        this.isMultimodalModel = multimodalModel;
        notifyListeners();
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        if (connectTimeoutSeconds > 0) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            notifyListeners();
        }
    }

    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
    }

    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) {
        if (writeTimeoutSeconds > 0) {
            this.writeTimeoutSeconds = writeTimeoutSeconds;
            notifyListeners();
        }
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        if (readTimeoutSeconds > 0) {
            this.readTimeoutSeconds = readTimeoutSeconds;
            notifyListeners();
        }
    }

    public boolean isUseSystemPrompt() {
        return useSystemPrompt;
    }

    public void setUseSystemPrompt(boolean useSystemPrompt) {
        this.useSystemPrompt = useSystemPrompt;
        notifyListeners();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        notifyListeners();
    }

    public String getChatApiEndpoint() {
        return chatApiEndpoint;
    }

    public void setChatApiEndpoint(String chatApiEndpoint) {
        if (chatApiEndpoint != null && !chatApiEndpoint.trim().isEmpty()) {
            // Ensure the endpoint starts with a forward slash
            this.chatApiEndpoint = chatApiEndpoint.trim().startsWith("/") ? 
                chatApiEndpoint.trim() : "/" + chatApiEndpoint.trim();
            notifyListeners();
        }
    }
} 