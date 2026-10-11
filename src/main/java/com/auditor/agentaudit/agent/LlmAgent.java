package com.auditor.agentaudit.agent;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.auditor.agentaudit.audit.AuditContext;
import com.auditor.agentaudit.audit.AuditEventType;
import com.auditor.agentaudit.audit.AuditService;

import lombok.extern.slf4j.Slf4j;

/**
 * The agent. There is no non-LLM alternative by design.
 *
 * <p>It opens an audit session, hands the question to the model, and closes the
 * session with the answer or the failure. The tool calls in between record
 * themselves -- this class never sees them, because Spring AI invokes tools
 * reflectively from inside {@code call()}.
 *
 * <p>A fallback that answered the same question by other means would make the
 * system quietly inconsistent: the same request would produce a different
 * answer depending on whether a daemon happened to be running, with nothing in
 * the response to say which path ran.
 */
@Service
@Slf4j
public class LlmAgent implements Agent {

    private final ChatClient chatClient;
    private final AuditService auditService;

    public LlmAgent(ChatClient chatClient, AuditService auditService) {
        this.chatClient = chatClient;
        this.auditService = auditService;
    }

    @Override
    public AgentAnswer answer(String clientSessionId, String question) {
        log.info("[LlmAgent::answer]::handling clientSessionId={} question={}", clientSessionId, question);

        AuditContext.Scope scope = auditService.startSession(clientSessionId, question);
        try {
            auditService.record(AuditEventType.LLM_REQUEST, null,
                    Map.of("question", question));

            String text = chatClient
                    .prompt()
                    .user(question)
                    .call()
                    .content();

            auditService.record(AuditEventType.LLM_RESPONSE, null,
                    Map.of("answer", text == null ? "" : text));
            auditService.completeSession(text);

            log.info("[LlmAgent::answer]::answered auditSessionId={}", scope.sessionId());
            return new AgentAnswer(scope.sessionId(), clientSessionId, question, text);

        } catch (RuntimeException e) {
            // The record must show the failure. Without this the session would
            // stay IN_PROGRESS with no reason attached.
            log.error("[LlmAgent::answer]::run failed auditSessionId={} with exception: {}",
                    scope.sessionId(), e.getMessage());
            auditService.failSession(e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;

        } finally {
            // Threads are pooled: a context left behind would attach the next
            // request's events to this session.
            AuditContext.clear();
        }
    }
}
