package com.auditor.agentaudit.agent;

/**
 * The seam between the web layer and whatever actually answers the question.
 *
 * <p>Phase 1 implementation is deterministic Java ({@link DeterministicAgent}).
 * Phase 2 adds an LLM-backed implementation behind this same interface, so the
 * controller, the audit layer and the tests never need to know which one is live.
 */
public interface Agent {

    AgentAnswer answer(String sessionId, String question);
}
