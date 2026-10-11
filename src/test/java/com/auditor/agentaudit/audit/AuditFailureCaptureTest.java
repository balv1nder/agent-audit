package com.auditor.agentaudit.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.auditor.agentaudit.support.StubChatModelConfig;
import com.auditor.agentaudit.tools.FinancialTools;

/**
 * The project claims that failures are preserved rather than lost. These tests
 * hold it to that claim.
 *
 * <p>They run against the real {@link AuditService} and repositories, because
 * the behaviour under test is what actually reaches the database.
 */
@SpringBootTest
@Import(StubChatModelConfig.class)
class AuditFailureCaptureTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AgentSessionRepository sessions;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private FinancialTools financialTools;

    @AfterEach
    void clearThreadContext() {
        AuditContext.clear();
    }

    @Test
    void aToolThatThrowsIsRecordedWithTheException() {
        AuditContext.Scope scope = auditService.startSession("fail-1", "VaR for Portfolio Z?");

        // The tool is expected to propagate -- recording must not swallow it.
        assertThatThrownBy(() -> financialTools.getVaR("Portfolio Z", "2026-10-06"))
                .isInstanceOf(IllegalArgumentException.class);

        List<AuditEvent> timeline = events.findBySessionIdOrderBySequenceNoAsc(scope.sessionId());

        assertThat(timeline).extracting(AuditEvent::getEventType).containsExactly(
                AuditEventType.USER_REQUEST,
                AuditEventType.TOOL_CALL,
                AuditEventType.TOOL_ERROR);

        AuditEvent toolCall = timeline.get(1);
        assertThat(toolCall.getDetail()).isEqualTo("getVaR");
        // The arguments the model supplied are preserved, not just the fact of a call.
        assertThat(toolCall.getPayload()).contains("Portfolio Z").contains("2026-10-06");

        AuditEvent error = timeline.get(2);
        assertThat(error.getDetail()).isEqualTo("getVaR");
        assertThat(error.getPayload())
                .contains("IllegalArgumentException")
                .contains("No VaR data found");
    }

    @Test
    void aFailedSessionKeepsTheReasonAndProducesNoAnswer() {
        AuditContext.Scope scope = auditService.startSession("fail-2", "Why did VaR move?");
        auditService.record(AuditEventType.LLM_REQUEST, null, Map.of("question", "Why did VaR move?"));

        auditService.failSession("IllegalStateException: Ollama unreachable");

        AgentSession stored = sessions.findById(scope.sessionId()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(SessionStatus.FAILED);
        assertThat(stored.getFailureReason()).contains("Ollama unreachable");
        assertThat(stored.getFinalAnswer()).isNull();
        assertThat(stored.getCompletedAt()).isNotNull();

        assertThat(events.findBySessionIdOrderBySequenceNoAsc(scope.sessionId()))
                .extracting(AuditEvent::getEventType)
                .containsExactly(
                        AuditEventType.USER_REQUEST,
                        AuditEventType.LLM_REQUEST,
                        AuditEventType.SESSION_FAILED);
    }

    @Test
    void aRunThatNeverFinishesStaysInProgressWithItsEventsIntact() {
        // Nothing calls complete or fail -- this is the process-died case.
        AuditContext.Scope scope = auditService.startSession("fail-3", "VaR?");
        auditService.record(AuditEventType.LLM_REQUEST, null, Map.of());
        auditService.record(AuditEventType.TOOL_CALL, "getVaR",
                Map.of("portfolio", "Portfolio A", "date", "2026-10-06"));

        AgentSession stored = sessions.findById(scope.sessionId()).orElseThrow();

        // The absence of an answer and the IN_PROGRESS status together are the
        // evidence that the run started and never came back.
        assertThat(stored.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(stored.getCompletedAt()).isNull();
        assertThat(stored.getFinalAnswer()).isNull();
        assertThat(events.countBySessionId(scope.sessionId())).isEqualTo(3);
    }

    @Test
    void eventsRecordedBeforeAFailureAreStillThere() {
        AuditContext.Scope scope = auditService.startSession("fail-4", "VaR for Portfolio Z?");

        try {
            financialTools.getVaR("Portfolio Z", "2026-10-06");
        } catch (IllegalArgumentException expected) {
            auditService.failSession(expected.getMessage());
        }

        // Each audit write commits in its own transaction, so the failing run
        // cannot roll back the record of its own failure.
        assertThat(events.findBySessionIdOrderBySequenceNoAsc(scope.sessionId()))
                .extracting(AuditEvent::getEventType)
                .containsExactly(
                        AuditEventType.USER_REQUEST,
                        AuditEventType.TOOL_CALL,
                        AuditEventType.TOOL_ERROR,
                        AuditEventType.SESSION_FAILED);

        assertThat(sessions.findById(scope.sessionId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void aToolCalledWithNoSessionStillWorksAndRecordsNothing() {
        // Tools must remain usable outside an audited run.
        String result = financialTools.getVaR("Portfolio A", "2026-10-06");

        assertThat(result).contains("EUR 12.0M");
        assertThat(AuditContext.current()).isNull();
    }

    @Test
    void sequenceNumbersStayContiguousAcrossAFailure() {
        AuditContext.Scope scope = auditService.startSession("fail-5", "VaR for Portfolio Z?");

        try {
            financialTools.getVaR("Portfolio Z", "2026-10-06");
        } catch (IllegalArgumentException expected) {
            auditService.failSession(expected.getMessage());
        }

        List<AuditEvent> timeline = events.findBySessionIdOrderBySequenceNoAsc(scope.sessionId());

        // No gaps: a missing number would mean an event was lost.
        assertThat(timeline).extracting(AuditEvent::getSequenceNo).containsExactly(0, 1, 2, 3);
    }
}
