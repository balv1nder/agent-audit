package com.auditor.agentaudit.audit;

/**
 * Where a session ended up.
 *
 * <p>{@link #IN_PROGRESS} is not a transient detail: a session left in this
 * state is the visible evidence that a run started and never finished, which is
 * exactly the case a reviewer most needs to see.
 */
public enum SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
