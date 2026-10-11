package com.auditor.agentaudit.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** The session's steps in the order they happened. */
    List<AuditEvent> findBySessionIdOrderBySequenceNoAsc(String sessionId);

    int countBySessionId(String sessionId);
}
