package com.auditor.agentaudit.audit;

import java.time.Instant;

/**
 * One step of a session as the audit API returns it.
 *
 * <p>Separate from the {@link AuditEvent} entity on purpose: the entity maps to
 * a database table, this maps to the JSON a reviewer reads. Changing one should
 * not force a change in the other.
 */
public record EventView(
        int sequence,
        AuditEventType type,
        String detail,
        Instant timestamp,
        String payload,
        String traceId,
        String spanId
) {

    public static EventView from(AuditEvent event) {
        return new EventView(
                event.getSequenceNo(),
                event.getEventType(),
                event.getDetail(),
                event.getTimestamp(),
                event.getPayload(),
                event.getTraceId(),
                event.getSpanId());
    }
}
