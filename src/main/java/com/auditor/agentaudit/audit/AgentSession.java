package com.auditor.agentaudit.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One logical interaction: a question asked, and whatever came of it.
 *
 * <p>The identifier is generated here rather than taken from the caller. A
 * caller may reuse "session-001" across runs, but an audit record has to point
 * at exactly one execution, so each request gets its own id and the caller's
 * label is kept alongside it for correlation.
 *
 * <p>A plain class with getters, not a record: Hibernate instantiates entities
 * reflectively and proxies them for lazy loading, so it needs a no-argument
 * constructor and non-final fields.
 */
@Getter
@Entity
@Table(name = "agent_session")
public class AgentSession {

    @Id
    @Column(length = 36)
    private String id;

    /** The caller's own label for this conversation. Not unique. */
    @Column(name = "client_session_id")
    private String clientSessionId;

    @Column(name = "user_question", length = 2000, nullable = false)
    private String userQuestion;

    /** Null until the session completes. Null on a failed session, by design. */
    @Column(name = "final_answer", length = 4000)
    private String finalAnswer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AgentSession() {
        // for Hibernate
    }

    private AgentSession(String id, String clientSessionId, String userQuestion, Instant startedAt) {
        this.id = id;
        this.clientSessionId = clientSessionId;
        this.userQuestion = userQuestion;
        this.startedAt = startedAt;
        this.status = SessionStatus.IN_PROGRESS;
    }

    /** A session begins IN_PROGRESS and is persisted before any work is done. */
    public static AgentSession start(String clientSessionId, String userQuestion) {
        return new AgentSession(
                UUID.randomUUID().toString(), clientSessionId, userQuestion, Instant.now());
    }

    public void complete(String answer) {
        this.finalAnswer = answer;
        this.status = SessionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String reason) {
        this.failureReason = reason;
        this.status = SessionStatus.FAILED;
        this.completedAt = Instant.now();
    }

}
