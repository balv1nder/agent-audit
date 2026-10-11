package com.auditor.agentaudit.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auditor.agentaudit.audit.AgentSession;
import com.auditor.agentaudit.audit.AgentSessionRepository;
import com.auditor.agentaudit.audit.AuditEvent;
import com.auditor.agentaudit.audit.AuditEventRepository;
import com.auditor.agentaudit.audit.SessionSummaryView;
import com.auditor.agentaudit.audit.SessionView;

import lombok.extern.slf4j.Slf4j;

/**
 * Read-only access to the audit record.
 *
 * <p>There is deliberately no way to write or amend a session through the API.
 * The record is produced by the run and nothing else.
 */
@RestController
@RequestMapping("/api/audit")
@Slf4j
public class AuditController {

    private final AgentSessionRepository sessions;
    private final AuditEventRepository events;

    public AuditController(AgentSessionRepository sessions, AuditEventRepository events) {
        this.sessions = sessions;
        this.events = events;
    }

    /** One session and every step it took. */
    @GetMapping("/sessions/{auditSessionId}")
    public ResponseEntity<SessionView> session(@PathVariable String auditSessionId) {
        log.info("[AuditController::session]::reading auditSessionId={}", auditSessionId);

        AgentSession session = sessions.findById(auditSessionId).orElse(null);

        if (session == null) {
            log.warn("[AuditController::session]::no session for auditSessionId={}", auditSessionId);
            return ResponseEntity.notFound().build();
        }

        List<AuditEvent> timeline = events.findBySessionIdOrderBySequenceNoAsc(session.getId());
        return ResponseEntity.ok(SessionView.from(session, timeline));
    }

    /** The most recent runs, newest first. */
    @GetMapping("/sessions")
    public List<SessionSummaryView> recent() {
        return summarise(sessions.findTop20ByOrderByStartedAtDesc());
    }

    /** Every run recorded under one caller-supplied label. */
    @GetMapping("/clients/{clientSessionId}/sessions")
    public List<SessionSummaryView> byClientLabel(@PathVariable String clientSessionId) {
        return summarise(sessions.findByClientSessionIdOrderByStartedAtDesc(clientSessionId));
    }

    private List<SessionSummaryView> summarise(List<AgentSession> found) {
        List<SessionSummaryView> summaries = new ArrayList<>();
        for (AgentSession session : found) {
            summaries.add(SessionSummaryView.from(session));
        }
        return summaries;
    }
}
