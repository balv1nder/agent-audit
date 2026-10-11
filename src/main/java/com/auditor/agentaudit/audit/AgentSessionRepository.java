package com.auditor.agentaudit.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSessionRepository extends JpaRepository<AgentSession, String> {

    /** Every run recorded under one caller-supplied label, newest first. */
    List<AgentSession> findByClientSessionIdOrderByStartedAtDesc(String clientSessionId);

    List<AgentSession> findTop20ByOrderByStartedAtDesc();
}
