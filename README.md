# Burp Suite AI chat Integration Extension

This Burp Suite extension integrates Ollama's LLM capabilities into Burp Suite, providing AI-powered interactive chat functionality in each request editor screen. Each request gets its own independent chat tab, allowing simultaneous conversations about different requests.

## Features

- **Request Editor Chat Integration**:
  - Independent chat tab for each request editor screen
  - Separate chat history per request
  - Interactive chat with LLM directly in the request editor screen
  - Support for multimodal interactions (images) with compatible models
  - Keyboard shortcuts (Ctrl+Enter) for quick message sending
  - Chat history management with clear chat functionality
  - Background processing for non-blocking UI experience

- **Custom Configuration**: 
  - Configure [Ollama](https://github.com/ollama/ollama) server settings
  - Select and configure LLM model
  - Manage custom HTTP headers
  - Configure proxy settings for Ollama debugging
  - Multimodal support toggle for compatible models
  - Configurable timeout settings (connect, read, write)
  - Custom chat API endpoint configuration

- **Request/Response Analysis**: 
  - Option to include current request/response in chat conversations

- **Image Support**:
  - Attach and analyze images in chat conversations
  - Support for multiple image formats (jpg, jpeg, png, gif, bmp)


- **Performance Features**:
  - Configurable timeout settings
  - Streaming responses for real-time feedback
  - Background processing for long-running operations

## Prerequisites

- Ollama running locally or on a remote server
- A compatible LLM model loaded in Ollama (default: deepseek-r1:1.5b)

## Installation

1. Download the latest release JAR file from the releases page
2. In Burp Suite, go to Extensions tab
3. Click "Add" button
4. Select "Extension Type" as Java
5. Select the downloaded JAR file
6. Click "Next" to load the extension

## Configuration

### Basic Settings
1. Go to the "LLM Config" tab in Burp Suite
2. Configure the Ollama server URL (default: http://localhost:11434)
3. Set the model name (default: deepseek-r1:1.5b)
4. Customize the system prompt if needed 
5. Add any required custom headers (e.g., for authentication)


## Building from Source

```bash
mvn clean package
```

The built extension will be in the `target` directory as `burp-llm-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Contributing

Contributions are welcome! Please feel free to submit pull requests.

## License

This project is licensed under the MIT License - see the LICENSE file for details. 
