package com.auditor.agentaudit.support;

import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Lets the application context start in tests without Ollama.
 *
 * <p>The application deliberately refuses to start when the LLM is missing, so
 * a test context supplies a stand-in {@link ChatModel} for {@code ChatClient}
 * to be built from, and switches off the startup availability check via
 * {@code agentaudit.ollama.check-on-startup=false} in test properties.
 */
@TestConfiguration
public class StubChatModelConfig {

    @Bean
    @Primary
    ChatModel stubChatModel() {
        return Mockito.mock(ChatModel.class);
    }
}
