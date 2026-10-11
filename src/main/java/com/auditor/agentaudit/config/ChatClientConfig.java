package com.auditor.agentaudit.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.auditor.agentaudit.tools.FinancialTools;

/**
 * Builds the single {@link ChatClient} the application talks to.
 *
 * <p>Spring AI's autoconfiguration already supplies a {@code ChatClient.Builder}
 * wired to Ollama. We add two things: the system prompt, and the tools the model
 * is allowed to call.
 */
@Configuration
public class ChatClientConfig {

    /**
     * The standing instruction sent before every user message.
     *
     * <p>The rules are blunt on purpose. A 3B model follows short, explicit
     * instructions far more reliably than nuanced ones, and the rule that
     * matters most here -- never invent a figure -- is the one that makes the
     * answers auditable.
     */
    private static final String SYSTEM_PROMPT = """
            You are a financial risk assistant. You answer questions about
            portfolio Value at Risk (VaR).

            How to answer:
            - Call a tool to get the facts. Never state a number that did not
              come from a tool.
            - Never compare, rank or calculate anything yourself. The tools do
              that and tell you the answer.
            - Once a tool has returned, write your answer using only what it
              said. Repeat its conclusion in your own words. Do not add
              suggestions, caveats or instructions.
            - If a tool says data was not found, say that plainly.

            Answer in one or two sentences.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, FinancialTools financialTools) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(financialTools)
                .build();
    }
}
