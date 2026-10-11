package com.auditor.agentaudit.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Refuses to start unless Ollama is reachable and the configured model is
 * present.
 *
 * <p>The LLM is not an optional extra here, so a missing daemon is a
 * misconfiguration rather than a condition to degrade around. Failing during
 * startup, with an instruction the reader can act on, is better than starting
 * and failing on the first question.
 *
 * <p>Disabled in tests via {@code agentaudit.ollama.check-on-startup=false};
 * nothing else should turn it off.
 */
@Component
@ConditionalOnProperty(name = "agentaudit.ollama.check-on-startup", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OllamaAvailabilityCheck {

    private final String baseUrl;
    private final String model;

    public OllamaAvailabilityCheck(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.chat.options.model}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @PostConstruct
    void verify() {
        log.info("[OllamaAvailabilityCheck::verify]::checking {} for model {}", baseUrl, model);

        String tags;
        try {
            tags = RestClient.create().get().uri(baseUrl + "/api/tags").retrieve().body(String.class);
        } catch (Exception e) {
            log.error("[OllamaAvailabilityCheck::verify]::daemon unreachable at {} with exception: {}",
                    baseUrl, e.getMessage());
            throw new IllegalStateException(
                    "Ollama is not reachable at " + baseUrl + ". Start it with: ollama serve", e);
        }

        if (tags == null || !tags.contains("\"" + model + "\"")) {
            log.error("[OllamaAvailabilityCheck::verify]::model {} is not installed", model);
            throw new IllegalStateException(
                    "Ollama is running at " + baseUrl + " but the model '" + model
                            + "' is not installed. Install it with: ollama pull " + model);
        }

        log.info("[OllamaAvailabilityCheck::verify]::ollama is available and {} is installed", model);
    }
}
