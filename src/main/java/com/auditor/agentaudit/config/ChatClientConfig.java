package com.auditor.agentaudit.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Builds the single {@link ChatClient} the application talks to.
 *
 * <p>Spring AI's autoconfiguration already gives us a {@code ChatClient.Builder}
 * wired to Ollama (because the Ollama starter is on the classpath). All we add
 * here is the system prompt -- the standing instruction the model receives
 * before every user question.
 */
@Configuration
@Profile("llm")
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            You are a financial analysis assistant.
            Answer questions about portfolio risk clearly and concisely.
            If you do not know a figure, say so plainly rather than guessing.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
