package com.auditor.agentaudit.agent;

/**
 * What the agent returns for a single question.
 *
 * <p>{@code auditSessionId} is the link from the answer to its evidence: pass
 * it to {@code GET /api/audit/sessions/{id}} to see what the agent actually
 * did. Without it the answer would be exactly the unverifiable artefact this
 * project exists to argue against.
 */
public record AgentAnswer(
        String auditSessionId,
        String sessionId,
        String question,
        String answer
) {}
