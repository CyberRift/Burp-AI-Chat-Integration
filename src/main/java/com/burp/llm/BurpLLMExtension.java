package com.burp.llm;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import com.burp.llm.config.ConfigSettings;
import com.burp.llm.ui.ConfigTab;
import com.burp.llm.ui.LLMRequestEditorTab;
import com.burp.llm.api.OllamaClient;

public class BurpLLMExtension implements BurpExtension {
    private MontoyaApi api;
    private Logging logging;
    private ConfigSettings configSettings;
    private OllamaClient ollamaClient;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        this.logging = api.logging();
        
        // Register extension name
        api.extension().setName("AI Chat Integration");
        
        // Initialize configuration settings
        this.configSettings = new ConfigSettings();
        
        // Initialize shared OllamaClient
        this.ollamaClient = new OllamaClient(configSettings);
        
        // Initialize UI components
        ConfigTab configTab = new ConfigTab(api, configSettings);
        
        // Register the custom request editor tab
        api.userInterface().registerHttpRequestEditorProvider(
            (creationContext) -> new LLMRequestEditorTab(api, ollamaClient, creationContext)
        );
        
        // Add the config tab to Burp's UI
        api.userInterface().registerSuiteTab("AI Config", configTab);
        
        logging.logToOutput("Burp AI Chat Integration Extension loaded successfully!");
    }
} 