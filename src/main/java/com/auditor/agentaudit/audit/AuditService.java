package com.auditor.agentaudit.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Writes the audit record as the run proceeds.
 *
 * <p>Every method commits in its own transaction ({@code REQUIRES_NEW}). That
 * is the whole design: if the record were written inside the request's
 * transaction, a failure would roll it back and the sessions worth reviewing --
 * the ones that broke -- would be precisely the ones that left no trace.
 */
@Service
@Slf4j
public class AuditService {

    private final AgentSessionRepository sessions;
    private final AuditEventRepository events;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public AuditService(AgentSessionRepository sessions, AuditEventRepository events,
                        ObjectMapper objectMapper, Tracer tracer) {
        this.sessions = sessions;
        this.events = events;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    /**
     * Opens a session, persists it as IN_PROGRESS, and records the question.
     * Both happen before any work, so an interrupted run is still visible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditContext.Scope startSession(String clientSessionId, String question) {
        AgentSession session = sessions.save(AgentSession.start(clientSessionId, question));
        AuditContext.Scope scope = AuditContext.open(session.getId());

        log.info("[AuditService::startSession]::opened auditSessionId={} clientSessionId={}",
                session.getId(), clientSessionId);

        record(AuditEventType.USER_REQUEST, null, Map.of("question", question));
        return scope;
    }

    /** Appends an event to whichever session is in scope on this thread. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType type, String detail, Map<String, ?> payload) {
        AuditContext.Scope scope = AuditContext.current();

        if (scope == null) {
            log.debug("[AuditService::record]::no session in scope, skipping type={}", type);
            return;
        }

        AuditEvent event = new AuditEvent(
                scope.sessionId(), scope.nextSequence(), type, detail, toJson(payload));
        correlateWithTrace(event);
        events.save(event);

        log.debug("[AuditService::record]::sessionId={} seq={} type={} detail={}",
                scope.sessionId(), event.getSequenceNo(), type, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeSession(String answer) {
        AuditContext.Scope scope = AuditContext.current();

        if (scope == null) {
            log.debug("[AuditService::completeSession]::no session in scope, skipping");
            return;
        }

        record(AuditEventType.SESSION_COMPLETED, null, Map.of("answer", answer));

        AgentSession session = sessions.findById(scope.sessionId()).orElse(null);
        if (session != null) {
            session.complete(answer);
            sessions.save(session);
        }

        log.info("[AuditService::completeSession]::sessionId={} completed", scope.sessionId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failSession(String reason) {
        AuditContext.Scope scope = AuditContext.current();

        if (scope == null) {
            log.debug("[AuditService::failSession]::no session in scope, skipping");
            return;
        }

        record(AuditEventType.SESSION_FAILED, null, Map.of("reason", reason));

        AgentSession session = sessions.findById(scope.sessionId()).orElse(null);
        if (session != null) {
            session.fail(reason);
            sessions.save(session);
        }

        log.error("[AuditService::failSession]::sessionId={} failed with exception: {}",
                scope.sessionId(), reason);
    }

    /**
     * Stamps the event with the OpenTelemetry trace and span it happened in.
     *
     * <p>This is the bridge between the two records. The audit event is kept
     * for years; the trace is sampled and expires. While both exist, these ids
     * let a reviewer move from "what the agent did" to "how long it took and
     * what it called", and afterwards the audit event still stands on its own.
     */
    private void correlateWithTrace(AuditEvent event) {
        Span span = tracer.currentSpan(); //which span am I in

        if (span == null) { //no tracing active
            return;
        }

        event.correlate(span.context().traceId(), span.context().spanId());
    }

    /**
     * A payload that cannot be serialised must not lose the event. The failure
     * is recorded in place of the payload rather than thrown.
     *
     * <p>Spring Boot 4 ships Jackson 3, whose {@code JacksonException} is
     * unchecked -- so this catch is a deliberate choice, not a compiler
     * requirement.
     */
    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JacksonException e) {
            log.error("[AuditService::toJson]::could not serialise payload with exception: {}",
                    e.getMessage());
            return "{\"serialisationError\":\"" + e.getMessage() + "\"}";
        }
    }

    /** Convenience for ordered payloads built up field by field. */
    public static Map<String, Object> payload(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }
}
