package com.auditor.agentaudit.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>It hands the question to the model, which decides which financial tools to
 * call. Spring AI executes those tools and feeds their results back, so the
 * figures in the answer come from {@code FinancialService} rather than from the
 * model's own recall.
 *
 * <p>Note how little happens here. The orchestration -- deciding which tool to
 * call, invoking it, returning the result to the model -- lives inside
 * {@code ChatClient}. That is exactly why Phase 3 cannot audit the run from this
 * class alone: the interesting steps happen below this line, out of sight.
 */
@Service
@Slf4j
public class LlmAgent implements Agent {

    private final ChatClient chatClient;

    public LlmAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public AgentAnswer answer(String sessionId, String question) {
        log.info("[LlmAgent::answer]::handling sessionId={} question={}", sessionId, question);

        String text = chatClient
                .prompt()
                .user(question)
                .call()
                .content();

        log.info("[LlmAgent::answer]::answered sessionId={} answer={}", sessionId, text);

        return new AgentAnswer(sessionId, question, text);
    }
}
