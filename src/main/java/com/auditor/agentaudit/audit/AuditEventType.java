package com.auditor.agentaudit.audit;

/**
 * The kinds of step worth preserving from an agent run.
 *
 * <p>The list is deliberately short. It covers what a reviewer needs to
 * reconstruct the run -- what was asked, what the agent did, what came back,
 * and how it ended -- without trying to capture every internal detail.
 */
public enum AuditEventType {

    /** The question arrived. Always the first event in a session. */
    USER_REQUEST,

    /** The agent asked the model to do something. */
    LLM_REQUEST,

    /** The model replied, either with prose or with a request to call tools. */
    LLM_RESPONSE,

    /** A tool was invoked, with the arguments the model supplied. */
    TOOL_CALL,

    /** A tool returned successfully. */
    TOOL_RESULT,

    /** A tool threw. The failure is recorded rather than lost. */
    TOOL_ERROR,

    /** The session produced an answer. */
    SESSION_COMPLETED,

    /** The session ended without an answer. */
    SESSION_FAILED
}
