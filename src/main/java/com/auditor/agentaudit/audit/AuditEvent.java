package com.auditor.agentaudit.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One step within a session, preserved with its inputs and outputs.
 *
 * <p>{@code payload} holds the detail as JSON -- the arguments a tool was called
 * with, the value it returned. Recording that a tool ran is not evidence;
 * recording what it was asked and what it answered is.
 *
 * <p>Events are append-only. Nothing in this class mutates an event after
 * construction, which is what lets the sequence be trusted as a record.
 */
@Getter
@Entity
@Table(name = "audit_event", indexes = @Index(name = "idx_event_session", columnList = "session_id, sequence_no"))
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    /**
     * Position within the session. Timestamps alone are not enough: several
     * events can share a millisecond, and the order is the point.
     */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AuditEventType eventType;

    /** Free-text label, e.g. the tool name. Null where it does not apply. */
    @Column(length = 100)
    private String detail;

    @Column(length = 8000)
    private String payload;

    @Column(nullable = false)
    private Instant timestamp;

    /** Correlates this event with OpenTelemetry. Populated in Phase 4. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "span_id", length = 64)
    private String spanId;

    protected AuditEvent() {
        // for Hibernate
    }

    public AuditEvent(String sessionId, int sequenceNo, AuditEventType eventType,
                      String detail, String payload) {
        this.sessionId = sessionId;
        this.sequenceNo = sequenceNo;
        this.eventType = eventType;
        this.detail = detail;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    public void correlate(String traceId, String spanId) {
        this.traceId = traceId;
        this.spanId = spanId;
    }
}
