package com.auditor.agentaudit.audit;

import java.time.Instant;

/**
 * One line per run, for listing sessions without their events.
 */
public record SessionSummaryView(
        String auditSessionId,
        String clientSessionId,
        String question,
        SessionStatus status,
        Instant startedAt
) {

    public static SessionSummaryView from(AgentSession session) {
        return new SessionSummaryView(
                session.getId(),
                session.getClientSessionId(),
                session.getUserQuestion(),
                session.getStatus(),
                session.getStartedAt());
    }
}
