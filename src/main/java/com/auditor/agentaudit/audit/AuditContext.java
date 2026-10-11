package com.auditor.agentaudit.audit;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Carries the current session id down to code that cannot be given it.
 *
 * <p>Tool methods are not called by us. Spring AI resolves a tool by name and
 * invokes it reflectively, so there is no call site where a session id could be
 * passed as an argument, and changing the tool signatures to carry one would
 * expose it to the model -- which must not choose it.
 *
 * <p>The invocation does happen on the request thread: a tool call was observed
 * on the same {@code nio-8082-exec-2} thread as the request that triggered it.
 * So a {@link ThreadLocal} reaches the tools without touching their signatures.
 *
 * <p>The limitation is the honest part: this holds only while tool execution
 * stays on the calling thread. Asynchronous or parallel tool execution would
 * need the context propagated explicitly.
 */
public final class AuditContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private AuditContext() {
    }

    /** The session in scope on this thread, and its running event counter. */
    public record Scope(String sessionId, AtomicInteger sequence) {

        public int nextSequence() {
            return sequence.getAndIncrement();
        }
    }

    public static Scope open(String sessionId) {
        Scope scope = new Scope(sessionId, new AtomicInteger(0));
        CURRENT.set(scope);
        return scope;
    }

    /**
     * The session in scope on this thread, or null when there is none.
     *
     * <p>Null is a valid answer, not an error: a tool called outside a session
     * -- from a unit test, say -- still works, it simply records nothing.
     */
    public static Scope current() {
        return CURRENT.get();
    }

    /**
     * Must be called in a finally block. A thread pool reuses threads, so a
     * context left behind would attach the next request's events to this
     * session -- a silent corruption of the record.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
