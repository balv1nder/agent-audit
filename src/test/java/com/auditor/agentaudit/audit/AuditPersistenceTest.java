package com.auditor.agentaudit.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AuditPersistenceTest {

    @Autowired
    private AgentSessionRepository sessions;

    @Autowired
    private AuditEventRepository events;

    @Test
    void aStartedSessionIsRecordedAsInProgress() {
        AgentSession session = sessions.save(
                AgentSession.start("session-001", "Why did Portfolio A VaR increase?"));

        AgentSession stored = sessions.findById(session.getId()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(stored.getClientSessionId()).isEqualTo("session-001");
        assertThat(stored.getStartedAt()).isNotNull();
        // An unfinished session holds no answer -- that absence is the evidence.
        assertThat(stored.getFinalAnswer()).isNull();
        assertThat(stored.getCompletedAt()).isNull();
    }

    @Test
    void completingASessionStoresTheAnswer() {
        AgentSession session = sessions.save(AgentSession.start("session-002", "VaR?"));
        session.complete("EUR 12.0M");
        sessions.save(session);

        AgentSession stored = sessions.findById(session.getId()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(stored.getFinalAnswer()).isEqualTo("EUR 12.0M");
        assertThat(stored.getCompletedAt()).isNotNull();
    }

    @Test
    void aFailedSessionKeepsTheReasonAndNoAnswer() {
        AgentSession session = sessions.save(AgentSession.start("session-003", "VaR?"));
        session.fail("Ollama unreachable");
        sessions.save(session);

        AgentSession stored = sessions.findById(session.getId()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(SessionStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo("Ollama unreachable");
        assertThat(stored.getFinalAnswer()).isNull();
    }

    @Test
    void eventsComeBackInTheOrderTheyHappened() {
        AgentSession session = sessions.save(AgentSession.start("session-004", "VaR?"));
        String id = session.getId();

        // Saved out of order on purpose: ordering must come from sequenceNo,
        // not from insertion order or a millisecond-resolution timestamp.
        events.save(new AuditEvent(id, 2, AuditEventType.TOOL_CALL, "getVaR",
                "{\"portfolio\":\"Portfolio A\",\"date\":\"2026-10-06\"}"));
        events.save(new AuditEvent(id, 0, AuditEventType.USER_REQUEST, null, "{\"question\":\"VaR?\"}"));
        events.save(new AuditEvent(id, 3, AuditEventType.TOOL_RESULT, "getVaR", "{\"result\":\"EUR 12.0M\"}"));
        events.save(new AuditEvent(id, 1, AuditEventType.LLM_REQUEST, null, "{}"));

        List<AuditEvent> timeline = events.findBySessionIdOrderBySequenceNoAsc(id);

        assertThat(timeline).extracting(AuditEvent::getEventType).containsExactly(
                AuditEventType.USER_REQUEST,
                AuditEventType.LLM_REQUEST,
                AuditEventType.TOOL_CALL,
                AuditEventType.TOOL_RESULT);
    }

    @Test
    void theToolCallPayloadPreservesArgumentsAndResult() {
        AgentSession session = sessions.save(AgentSession.start("session-005", "VaR?"));
        String id = session.getId();
        events.save(new AuditEvent(id, 0, AuditEventType.TOOL_CALL, "getVaR",
                "{\"portfolio\":\"Portfolio A\",\"date\":\"2026-10-06\"}"));
        events.save(new AuditEvent(id, 1, AuditEventType.TOOL_RESULT, "getVaR",
                "{\"result\":\"VaR for Portfolio A on 2026-10-06 is EUR 12.0M\"}"));

        List<AuditEvent> timeline = events.findBySessionIdOrderBySequenceNoAsc(id);

        assertThat(timeline.get(0).getPayload()).contains("Portfolio A").contains("2026-10-06");
        assertThat(timeline.get(1).getPayload()).contains("EUR 12.0M");
        assertThat(timeline.get(0).getDetail()).isEqualTo("getVaR");
    }

    @Test
    void eventsAreScopedToTheirOwnSession() {
        AgentSession first = sessions.save(AgentSession.start("shared-label", "Q1"));
        AgentSession second = sessions.save(AgentSession.start("shared-label", "Q2"));
        events.save(new AuditEvent(first.getId(), 0, AuditEventType.USER_REQUEST, null, "{}"));
        events.save(new AuditEvent(second.getId(), 0, AuditEventType.USER_REQUEST, null, "{}"));
        events.save(new AuditEvent(second.getId(), 1, AuditEventType.LLM_REQUEST, null, "{}"));

        assertThat(events.countBySessionId(first.getId())).isEqualTo(1);
        assertThat(events.countBySessionId(second.getId())).isEqualTo(2);
        // Two runs under one caller label remain two distinct records.
        assertThat(sessions.findByClientSessionIdOrderByStartedAtDesc("shared-label")).hasSize(2);
    }
}
