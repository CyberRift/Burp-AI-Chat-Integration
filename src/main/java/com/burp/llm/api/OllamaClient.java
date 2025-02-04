package com.burp.llm.api;

import com.burp.llm.config.ConfigSettings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import okhttp3.*;
import java.io.IOException;
import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.ArrayList;

public class OllamaClient {
    private OkHttpClient client;
    private final ConfigSettings configSettings;
    private final Gson gson;
    private List<JsonObject> messageHistory;

    public OllamaClient(ConfigSettings configSettings) {
        this.configSettings = configSettings;
        this.gson = new Gson();
        this.client = buildClient();
        this.messageHistory = new ArrayList<>();
        
        configSettings.addChangeListener(() -> {
            this.client = buildClient();
        });
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(configSettings.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
            .writeTimeout(configSettings.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
            .readTimeout(configSettings.getReadTimeoutSeconds(), TimeUnit.SECONDS);

        if (configSettings.isUseProxy()) {
            String proxyHost = configSettings.getProxyHost();
            int proxyPort = configSettings.getProxyPort();
            
            if (proxyHost != null && !proxyHost.trim().isEmpty() && proxyPort > 0 && proxyPort <= 65535) {
                Proxy proxy = new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost.trim(), proxyPort)
                );
                builder.proxy(proxy);
            }
        }

        return builder.build();
    }

    public void chat(String prompt, String systemPrompt, List<String> base64Images, Consumer<String> onChunk) throws IOException {
        chatInternal(prompt, systemPrompt, base64Images, onChunk, false);
    }

    private void chatInternal(String prompt, String systemPrompt, List<String> base64Images, Consumer<String> onChunk, boolean isAnalysis) throws IOException {
        String url = configSettings.getOllamaServer() + configSettings.getChatApiEndpoint();
        
        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("model", configSettings.getModel());
        jsonRequest.addProperty("stream", true);

        JsonArray messages = new JsonArray();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
        }

        if (!isAnalysis && !messageHistory.isEmpty()) {
            for (JsonObject message : messageHistory) {
                JsonObject messageCopy = message.deepCopy();
                messages.add(messageCopy);
            }
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        
        if (configSettings.isMultimodalModel() && base64Images != null && !base64Images.isEmpty()) {
            JsonArray imagesArray = new JsonArray();
            for (String base64Image : base64Images) {
                imagesArray.add(base64Image);
            }
            userMessage.add("images", imagesArray);
        }
        
        messages.add(userMessage);
        jsonRequest.add("messages", messages);

        String jsonBody = gson.toJson(jsonRequest);
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.get("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .post(requestBody);

        for (ConfigSettings.CustomHeader header : configSettings.getCustomHeaders()) {
            if (header.getName() != null && !header.getName().trim().isEmpty()) {
                requestBuilder.addHeader(header.getName().trim(), header.getValue());
            }
        }

        Request request = requestBuilder.build();
        StringBuilder fullResponse = new StringBuilder();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new IOException("Unexpected response code: " + response.code() + "\nError: " + errorBody);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response from server");
            }

            try (BufferedReader reader = new BufferedReader(body.charStream())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        JsonObject jsonResponse = gson.fromJson(line, JsonObject.class);
                        if (jsonResponse.has("message")) {
                            JsonObject messageObj = jsonResponse.getAsJsonObject("message");
                            if (messageObj.has("content")) {
                                String content = messageObj.get("content").getAsString();
                                if (content != null && !content.isEmpty()) {
                                    onChunk.accept(content);
                                    fullResponse.append(content);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing response line: " + e.getMessage());
                    }
                }
            }
        }

        if (!isAnalysis && fullResponse.length() > 0) {
            messageHistory.add(userMessage.deepCopy());
            JsonObject assistantMessage = new JsonObject();
            assistantMessage.addProperty("role", "assistant");
            assistantMessage.addProperty("content", fullResponse.toString());
            messageHistory.add(assistantMessage.deepCopy());
        }
    }

    public String chat(String prompt, String systemPrompt) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        chat(prompt, systemPrompt, null, chunk -> fullResponse.append(chunk));
        return fullResponse.toString();
    }

    public String chat(String prompt, String systemPrompt, List<String> base64Images) throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        chat(prompt, systemPrompt, base64Images, chunk -> fullResponse.append(chunk));
        return fullResponse.toString();
    }

    public void analyzeRequest(String request, String response, String question) throws IOException {
        analyzeRequest(request, response, question, null, null);
    }

    public void analyzeRequest(String request, String response, String question, List<String> base64Images) throws IOException {
        analyzeRequest(request, response, question, base64Images, null);
    }

    public void analyzeRequest(String request, String response, String question, List<String> base64Images, Consumer<String> onChunk) throws IOException {
        String prompt = String.format("""
            HTTP Request:
            %s
            
            HTTP Response:
            %s
            
            Question: %s
            """, request, response, question);

        String systemPrompt = configSettings.isUseSystemPrompt() ? configSettings.getSystemPrompt() : "";
        
        if (onChunk != null) {
            chat(prompt, systemPrompt, base64Images, onChunk);
        } else {
            chat(prompt, systemPrompt, base64Images);
        }
    }

    public void clearHistory() {
        messageHistory.clear();
    }

    public boolean isMultimodalModel() {
        return configSettings.isMultimodalModel();
    }

    public ConfigSettings getConfigSettings() {
        return configSettings;
    }
} 