package com.auditor.agentaudit.controller;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/agent/ask}. */
public record AskRequest(
        @NotBlank(message = "sessionId must not be blank") String sessionId,
        @NotBlank(message = "question must not be blank") String question
) {}
