package com.auditor.agentaudit.agent;

/**
 * What the agent returns for a single question.
 *
 * <p>Note how little is in here: a session id, the question, the answer. That is
 * deliberate -- it is exactly the "final answer only" view that an auditor cannot
 * work with. The audit trail we build in a later phase is what fills the gap.
 */
public record AgentAnswer(
        String sessionId,
        String question,
        String answer
) {}
