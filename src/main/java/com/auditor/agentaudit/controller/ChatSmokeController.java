package com.auditor.agentaudit.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * TEMPORARY -- Phase 2a only.
 *
 * <p>A bare pipe to the model: no tools, no audit trail, no financial data.
 * It exists purely to prove that Spring Boot can reach Ollama and get a
 * sentence back. Deleted once {@code LlmAgent} works in Phase 2c.
 */
@RestController
@Profile("llm")
@Slf4j
public class ChatSmokeController {

    private final ChatClient chatClient;

    public ChatSmokeController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/api/dev/chat")
    public String chat(@RequestParam String q) {
        log.info("[ChatSmokeController::chat]::sending prompt to model");

        return chatClient
                .prompt()          // start building a request
                .user(q)           // the user's message
                .call()            // send it, block until the model replies
                .content();        // pull the text out of the response
    }
}
