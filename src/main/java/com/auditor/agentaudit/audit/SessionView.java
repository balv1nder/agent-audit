package com.auditor.agentaudit.audit;

import java.time.Instant;
import java.util.List;

/**
 * A session and every step it took, as the audit API returns it.
 *
 * <p>The entity calls its key {@code id}; here it is {@code auditSessionId},
 * which is the name the rest of the system uses for it.
 */
public record SessionView(
        String auditSessionId,
        String clientSessionId,
        String question,
        String finalAnswer,
        SessionStatus status,
        String failureReason,
        Instant startedAt,
        Instant completedAt,
        int eventCount,
        List<EventView> events
) {

    public static SessionView from(AgentSession session, List<AuditEvent> events) {
        List<EventView> eventViews = new java.util.ArrayList<>();
        for (AuditEvent event : events) {
            eventViews.add(EventView.from(event));
        }

        return new SessionView(
                session.getId(),
                session.getClientSessionId(),
                session.getUserQuestion(),
                session.getFinalAnswer(),
                session.getStatus(),
                session.getFailureReason(),
                session.getStartedAt(),
                session.getCompletedAt(),
                eventViews.size(),
                eventViews);
    }
}
