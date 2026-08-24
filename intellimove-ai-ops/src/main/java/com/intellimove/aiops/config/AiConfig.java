package com.intellimove.aiops.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI ChatClient instances.
 * Supports both Ollama (local) and OpenAI (cloud) providers.
 * Provider is selected via ai.ops.provider property (default: ollama).
 */
@Configuration
public class AiConfig {

    @Value("${ai.ops.provider:ollama}")
    private String provider;

    @Bean("aiOpsChatClient")
    public ChatClient aiOpsChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are IntelliMove's AI Operations Assistant. You help administrators analyze operational data. Use the provided tools to get real data before answering questions. Never fabricate data. Always provide actionable insights based on the tool results.")
                .build();
    }

    @Bean("aiSupportChatClient")
    public ChatClient aiSupportChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are IntelliMove's customer support agent. Help customers with ride issues, payments, and refunds. Always pass the customer's ID to tools. Never access another customer's data. Be helpful and empathetic.")
                .build();
    }
}
